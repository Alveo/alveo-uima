package au.edu.alveo.uima;

import au.edu.alveo.uima.conversions.UIMAToAlveoAnnConverter;
import au.edu.alveo.uima.types.ItemMetadata;
import au.edu.alveo.uima.types.VLabDocSource;
import au.edu.alveo.uima.types.AlveoItemSource;
import au.edu.alveo.client.UnknownValueException;
import au.edu.alveo.client.UnsupportedLDSchemaException;
import au.edu.alveo.client.entity.Item;
import au.edu.alveo.client.entity.TextAnnotation;
import au.edu.alveo.client.entity.TextDocument;
import org.apache.uima.cas.ArrayFS;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A class, primarily for internal usage, which does the work of converting
 * items (from the Alveo API) and their annotations into an appropriate CAS
 */
class ItemCASAdapter {
	private static final Logger LOG = LoggerFactory.getLogger(ItemCASAdapter.class);

	private final boolean includeRawDocs;
	private final boolean includeAnnotations;
	private final String serverBaseUrl;
	private Map<String, Type> urisToAnnTypes = new HashMap<String, Type>();
	private final UIMAToAlveoAnnConverter uimaToAlveoAnnConverter;


	public ItemCASAdapter(String serverBaseUrl, boolean includeRawDocs, boolean includeAnnotations,
			UIMAToAlveoAnnConverter uimaToAlveoAnnConverter) {
		this.serverBaseUrl = serverBaseUrl;
		this.includeRawDocs = includeRawDocs;
		this.includeAnnotations = includeAnnotations;
		this.uimaToAlveoAnnConverter = uimaToAlveoAnnConverter;
	}

	public void storeItemInCas(Item item, CAS cas) throws CASException {
		storeMainItem(item, cas);
		int ctr = 1;
		if (includeRawDocs) {
			for (TextDocument td : item.textDocuments()) {
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

	private void storeAnnotations(Item item, AnnotationFS vlabItemSrc) throws CASException {
		List<TextAnnotation> anns;
		try {
			anns = item.getTextAnnotations();
		} catch (UnsupportedLDSchemaException e) {
			throw new CASException(e);
		}
		int ctr = 0;
		CAS cas = vlabItemSrc.getCAS();
		TypeSystem ts = cas.getTypeSystem();
		ArrayFS fsForAnns = cas.createArrayFS(anns.size());
		Feature annTypeFeature = ts.getFeatureByFullName("au.edu.alveo.uima.types.ItemAnnotation:annType");
		Feature labelFeature = ts.getFeatureByFullName("au.edu.alveo.uima.types.ItemAnnotation:label");

		for (TextAnnotation ta : anns) {
			Type type = getTypeForAnnotation(ts, ta.getType());
			AnnotationFS afs = cas.createAnnotation(type, ta.getStartOffset(), ta.getEndOffset());
			afs.setFeatureValueFromString(annTypeFeature, ta.getType());
			afs.setFeatureValueFromString(labelFeature, ta.getLabel());
			cas.addFsToIndexes(afs);
			fsForAnns.set(ctr++, afs);
		}
		vlabItemSrc.setFeatureValue(ts.getFeatureByFullName("au.edu.alveo.uima.types.AlveoItemSource:annotations"),
				fsForAnns);
		cas.addFsToIndexes(fsForAnns);
	}

	private Type getTypeForAnnotation(TypeSystem typeSystem, String annTypeUri) {
		if (urisToAnnTypes.size() == 0)
			cacheAnnTypeUris(typeSystem);
		Type type = urisToAnnTypes.get(annTypeUri);
		if (type == null) {
			LOG.error("Unknown annotation type URI: {}", annTypeUri);
			type = typeSystem.getType("au.edu.alveo.uima.types.UnknownItemAnnotation");
		}
		return type;
	}

	private void cacheAnnTypeUris(TypeSystem typeSystem)  {
		// XXX: this assumes that all CASes have effectively the same type system.
		// this will probably be true in practice generally, although
		// we should maybe clear urisToAnnTypes for each new CAS just in case?
		uimaToAlveoAnnConverter.setTypeSystem(typeSystem);
		Iterator<Type> types = typeSystem.getTypeIterator();
		while (types.hasNext()) {
			Type type = types.next();
			String typeURI = uimaToAlveoAnnConverter.getAlveoTypeUriForTypeName(type.getName());
			urisToAnnTypes.put(typeURI, type);
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

	private void storeMainItem(Item item, CAS mainView) throws CASException {
		mainView.setSofaDataString(item.primaryText(), "text/plain");
		AlveoItemSource vlis = new AlveoItemSource(mainView.getJCas());
		vlis.setSourceUri(item.getUri());
		vlis.setServerBase(serverBaseUrl);
		storeMetadata(item, vlis);
		if (includeAnnotations)
			storeAnnotations(item, vlis);
		vlis.addToIndexes();
	}

	private void storeMetadata(Item item, AnnotationFS vlabItemSrc) throws CASException {
		Map<String, String> orig = item.getMetadata();

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
				"au.edu.alveo.uima.types.AlveoItemSource:metadata");
		vlabItemSrc.setFeatureValue(metadataFeature, metadata);
		vlabItemSrc.getCAS().setDocumentLanguage(orig.get("http://www.language-archives.org/OLAC/1.1/language").substring(0, 2));
	}

}
