package com.nicta.uimavlab;

import com.nicta.uimavlab.types.ItemMetadata;
import com.nicta.uimavlab.types.VLabDocSource;
import com.nicta.uimavlab.types.VLabItemSource;
import com.nicta.vlabclient.UnknownValueException;
import com.nicta.vlabclient.UnsupportedLDSchemaException;
import com.nicta.vlabclient.entity.Item;
import com.nicta.vlabclient.entity.TextAnnotation;
import com.nicta.vlabclient.entity.TextDocument;
import org.apache.uima.cas.ArrayFS;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.resource.metadata.TypeDescription;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A class, primarily for internal usage, which does the work of converting
 * items (from the HCS vLab API) and their annotations into an appropriate CAS
 */
class ItemCASAdapter {
	private static final Logger LOG = LoggerFactory.getLogger(ItemCASAdapter.class);

	private final boolean includeRawDocs;
	private final TypeSystemDescription tsd;
	private final boolean includeAnnotations;
	private final String serverBaseUrl;
	private Map<String, Type> urisToAnnTypes = new HashMap<String, Type>();


	public ItemCASAdapter(TypeSystemDescription tsd, String serverBaseUrl,
			boolean includeRawDocs, boolean includeAnnotations){
		this.tsd = tsd;
		this.serverBaseUrl = serverBaseUrl;
		this.includeRawDocs = includeRawDocs;
		this.includeAnnotations = includeAnnotations;
	}

	public void storeItemInCas(Item next, CAS cas) throws CASException {
		CAS mainView = cas.createView("00: _PRIMARY_ITEM_");
		mainView.setSofaDataString(next.primaryText(), "text/plain");
		storeMainItem(next, mainView);
		int ctr = 1;
		if (includeRawDocs) {
			for (TextDocument td : next.textDocuments()) {
				++ctr;
				try {
					String docType = td.getType();
					CAS view = cas.createView(String.format("%02d: %s", ctr, docType));
					storeSourceDoc(td, view);
				} catch (UnknownValueException e) {
					throw new CASException(e);
				}
			}
		}
	}

	private void storeAnnotations(Item next, AnnotationFS vlabItemSrc) throws CASException {
		List<TextAnnotation> anns;
		try {
			anns = next.getTextAnnotations();
		} catch (UnsupportedLDSchemaException e) {
			throw new CASException(e);
		}
		int ctr = 0;
		CAS cas = vlabItemSrc.getCAS();
		TypeSystem ts = cas.getTypeSystem();
		ArrayFS fsForAnns = cas.createArrayFS(anns.size());
		Feature annTypeFeature = ts.getFeatureByFullName("com.nicta.uimavlab.types.ItemAnnotation:annType");
		Feature labelFeature = ts.getFeatureByFullName("com.nicta.uimavlab.types.ItemAnnotation:label");

		for (TextAnnotation ta : anns) {
			Type type = getTypeForAnnotation(ts, ta.getType());
			AnnotationFS afs = cas.createAnnotation(type, ta.getStartOffset(), ta.getEndOffset());
			afs.setFeatureValueFromString(annTypeFeature, ta.getType());
			afs.setFeatureValueFromString(labelFeature, ta.getLabel());
			cas.addFsToIndexes(afs);
			fsForAnns.set(ctr++, afs);
		}
		vlabItemSrc.setFeatureValue(ts.getFeatureByFullName("com.nicta.uimavlab.types.VLabItemSource:annotations"),
				fsForAnns);
		cas.addFsToIndexes(fsForAnns);
	}

	private Type getTypeForAnnotation(TypeSystem typeSystem, String annTypeUri) {
		if (urisToAnnTypes.size() == 0)
			cacheAnnTypeUris(typeSystem);
		Type type = urisToAnnTypes.get(annTypeUri);
		if (type == null) {
			LOG.error("Unknown annotation type URI: {}", annTypeUri);
			type = typeSystem.getType("com.nicta.uimavlab.types.UnknownItemAnnotation");
		}
		return type;
	}

	private void cacheAnnTypeUris(TypeSystem typeSystem) {
		// XXX: this assumes that all CASes have effectively the same type system.
		// this will probably be true in practice generally, although
		// we should maybe clear urisToAnnTypes for each new CAS just in case?
		Iterator<Type> types = typeSystem.getTypeIterator();
		while (types.hasNext()) {
			Type type = types.next();
			TypeDescription td = tsd.getType(type.getName());
			if (td == null)
				continue;
			// we have, somewhat dubiously, stored the annotation URI in the type description
			urisToAnnTypes.put(td.getSourceUrlString(), type);
		}
	}

	private void storeSourceDoc(TextDocument td, CAS view) throws CASException {
		view.setSofaDataString(td.rawText(), "text/plain");
		VLabDocSource vlds = new VLabDocSource(view.getJCas());
		vlds.setServerBase(serverBaseUrl);
		vlds.setRawTextUrl(td.getDataUrl());
		try {
			vlds.setDocType(td.getType());
		} catch (UnknownValueException e) {
			throw new CASException(e);
		}
		vlds.addToIndexes();
	}

	private void storeMainItem(Item next, CAS mainView) throws CASException {
		VLabItemSource vlis = new VLabItemSource(mainView.getJCas());
		vlis.setSourceUri(next.getUri());
		vlis.setServerBase(serverBaseUrl);
		storeMetadata(next, vlis);
		if (includeAnnotations)
			storeAnnotations(next, vlis);
		vlis.addToIndexes();
	}

	private void storeMetadata(Item next, AnnotationFS vlabItemSrc) throws CASException {
		Map<String, String> orig = next.getMetadata();

		ItemMetadata metadata = new ItemMetadata(vlabItemSrc.getCAS().getJCas());
		// TODO: work out how to store all the missing metadata keys, as arbitrary key-value pairs
		//   see http://comments.gmane.org/gmane.comp.apache.uima.general/5179 for ideas
		metadata.setTitle(orig.get("http://purl.org/dc/terms/title"));
		metadata.setCollection(orig.get("http://purl.org/dc/terms/isPartOf"));
		metadata.setCreator(orig.get("http://purl.org/dc/terms/creator"));
		metadata.setIdentifier(orig.get("http://purl.org/dc/terms/identifier"));
		metadata.setDiscourseType(orig.get("http://www.language-archives.org/OLAC/1.1/discourse_type"));
		metadata.setRecordingDate(orig.get("http://www.language-archives.org/OLAC/1.1/recordingdate"));
		Feature metadataFeature = vlabItemSrc.getCAS().getTypeSystem().getFeatureByFullName(
				"com.nicta.uimavlab.types.VLabItemSource:metadata");
		vlabItemSrc.setFeatureValue(metadataFeature, metadata);
		vlabItemSrc.getCAS().setDocumentLanguage(orig.get("http://www.language-archives.org/OLAC/1.1/language"));
	}

}
