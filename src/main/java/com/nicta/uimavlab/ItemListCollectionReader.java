/**
 * 
 */
package com.nicta.uimavlab;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.nicta.vlabclient.entity.EntityNotFoundException;
import com.nicta.vlabclient.entity.HCSvLabException;
import com.nicta.vlabclient.entity.InvalidServerAddressException;
import com.nicta.vlabclient.entity.UnauthorizedAPIKeyException;
import org.apache.uima.UimaContext;
import org.apache.uima.cas.ArrayFS;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.component.CasCollectionReader_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.descriptor.TypeCapability;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.factory.ConfigurationParameterFactory;
import static org.apache.uima.fit.factory.ConfigurationParameterFactory.ConfigurationData;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.DocumentAnnotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeDescription;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;
import org.apache.uima.util.TypeSystemUtil;
import org.openrdf.OpenRDFException;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.repository.RepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nicta.uimavlab.types.ItemAnnotation;
import com.nicta.uimavlab.types.ItemMetadata;
import com.nicta.uimavlab.types.UnknownItemAnnotation;
import com.nicta.uimavlab.types.VLabDocSource;
import com.nicta.uimavlab.types.VLabItemSource;
import com.nicta.uimavlab.types.itemanns.DubiousNonsenseAnnotation;
import com.nicta.uimavlab.types.itemanns.ElongationAnnotation;
import com.nicta.uimavlab.types.itemanns.IntonationAnnotation;
import com.nicta.uimavlab.types.itemanns.LatchedUtteranceAnnotation;
import com.nicta.uimavlab.types.itemanns.MicroPauseAnnotation;
import com.nicta.uimavlab.types.itemanns.PauseAnnotation;
import com.nicta.uimavlab.types.itemanns.SpeakerAnnotation;
import com.nicta.vlabclient.RestClient;
import com.nicta.vlabclient.entity.Item;
import com.nicta.vlabclient.entity.ItemList;
import com.nicta.vlabclient.entity.TextAnnotation;
import com.nicta.vlabclient.entity.TextDocument;
import com.nicta.vlabclient.UnknownValueException;
import com.nicta.vlabclient.UnsupportedLDSchemaException;

/**
 * @author amack
 * 
 */
@TypeCapability(outputs = { 
		"com.nicta.uimavlab.types.VLabItemSource",
		"com.nicta.uimavlab.types.VLabDocSource",
		"com.nicta.uimavlab.types.UnknownItemAnnotation",
		"com.nicta.uimavlab.types.GeneratedItemAnnotation",
		"com.nicta.uimavlab.types.ItemMetadata"
})
public class ItemListCollectionReader extends CasCollectionReader_ImplBase {
	private static final Logger LOG = LoggerFactory.getLogger(ItemListCollectionReader.class);

	public static final String PARAM_VLAB_BASE_URL = "vLabBaseUrl";
	public static final String PARAM_VLAB_ITEM_LIST_ID = "ItemListId";
	public static final String PARAM_VLAB_API_KEY = "vLabApiKey";
	public static final String PARAM_INCLUDE_RAW_DOCS = "includeRawDocs";
	public static final String PARAM_INCLUDE_ANNOTATIONS = "includeAnnotations";

	@ConfigurationParameter(name = PARAM_VLAB_ITEM_LIST_ID, mandatory = true, description = "Item ID which should be retrieved and converted into a "
			+ "set of UIMA CAS documents")
	private String itemListId;

	@ConfigurationParameter(name = PARAM_VLAB_BASE_URL, mandatory = true, 
			description = "Base URL for the HCS vLab REST/JSON API server "
			+ "- eg http://vlab.example.org/ ; the URL for the item list "
			+ " will be constructed by appending 'item_lists/{item_list_id}.json' to this URL")
	private String baseUrl;

	@ConfigurationParameter(name = PARAM_VLAB_API_KEY, mandatory = true, description = "API key for the vLab account (available from the web interface")
	private String apiKey;

	@ConfigurationParameter(name = PARAM_INCLUDE_RAW_DOCS, mandatory = false, description = "Include raw document sources as separate SofAs")
	private boolean includeRawDocs = false;
	
	@ConfigurationParameter(name = PARAM_INCLUDE_ANNOTATIONS, mandatory = false, description = "Include textual annotations when they are present")
	private boolean includeAnnotations = true;

	private ItemList itemList;
	private Iterator<? extends Item> itemsIter;
	private int itemsFetched;
	private int totalItems;
	private Map<String, Type> urisToAnnTypes = new HashMap<String, Type>();

	/**
	 * 
	 */
	public ItemListCollectionReader() {
		// TODO Auto-generated constructor stub
	}

	public static CollectionReaderDescription createDescription(Object... confData) throws ResourceInitializationException {
		ConfigurationData confDataParsed = ConfigurationParameterFactory.createConfigurationData(confData);
		String itemListId = null, vlabUrl = null, vlabApiKey = null;
		// since we don't yet have a reader, we need to semi-manually parse the params
		for (int i = 0; i < confDataParsed.configurationParameters.length; i++) {
			String paramName = confDataParsed.configurationParameters[i].getName();
			Object value = confDataParsed.configurationValues[i];
			if (paramName.equals(PARAM_VLAB_API_KEY))
				vlabApiKey = (String) value;
			else if (paramName.equals(PARAM_VLAB_BASE_URL))
				vlabUrl = (String) value;
			else if (paramName.equals(PARAM_VLAB_ITEM_LIST_ID))
				itemListId = (String) value;
		}
		if (itemListId == null || vlabApiKey == null || vlabUrl == null)
			throw new ResourceInitializationException(ResourceInitializationException.CONFIG_SETTING_ABSENT,
					new Object[] {PARAM_VLAB_API_KEY + ", " + PARAM_VLAB_BASE_URL + ", " + PARAM_VLAB_ITEM_LIST_ID});
		TypeSystemDescription tsd;
		try {
			tsd = ItemListCollectionReader.getTypeSystemDescription(vlabUrl, vlabApiKey, itemListId);
		} catch (Exception e) {
			throw new ResourceInitializationException(e);
		}
		return CollectionReaderFactory.createReaderDescription(ItemListCollectionReader.class,
				tsd, confData);
	}

	protected static TypeSystemDescription getTypeSystemDescription(String vlabUrl, String vlabApiKey, String itemListId)
			throws UnauthorizedAPIKeyException, EntityNotFoundException,
			InvalidServerAddressException, ResourceInitializationException, URISyntaxException, OpenRDFException {
		RestClient client = new RestClient(vlabUrl, vlabApiKey);
		TypeSystemAutoGenerator tsag = new TypeSystemAutoGenerator(client);
		for (String corpusName : getCorpusNames())
			tsag.addCorpus(corpusName);
		return tsag.getTypeSystemDescription();
	}

	/** Get a list of known collections (corpora) */
	public static Collection<String> getCorpusNames() {
		// XXX: horrible hack.
		// this should be calling a REST API method,
		// but currently this doesn't exist.
		// TODO: Once https://track.intersect.org.au/browse/HCSVLAB-868
		// is fixed, this should be replaced with a call to that method.
		// Note that we can't even set this in the UIMA descriptor, since
		// we want to know these before the reader has been instantiatied.
		// If extra collections are listed but are not found or
		// have insufficient permissions, they will be ignored with a
		// logged warning.
		String[] corpusNames = new String[] {
				"ace",
				"art",
				"austalk",
				"austlit",
				"avozes",
				"braidedchannels",
				"cooee",
				"gcsause",
				"ice",
				"jakartan_indonesian",
				"mbep",
				"mitcheldelbridge",
				"monash",
				"paradisec",
				"pixar",
				"rirusyd"
		};
		return Arrays.asList(corpusNames);
	}

	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {
		try {
			fetchItemList();
		} catch (HCSvLabException e) {
			throw new ResourceInitializationException(e);
		}
	}

	private void fetchItemList() throws HCSvLabException {
		RestClient client = new RestClient(baseUrl, apiKey);
		try {
			itemList = client.getItemList(itemListId);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		itemsIter = itemList.getCatalogItems().listIterator();
		itemsFetched = 0;
		totalItems = itemList.numItems();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.uima.collection.CollectionReader#getNext(org.apache.uima.cas
	 * .CAS)
	 */
	public void getNext(CAS cas) throws IOException, CollectionException {
		++itemsFetched;
		try {
			storeItemInCas(itemsIter.next(), cas);
		} catch (CASException e) {
			throw new CollectionException(e);
		}
	}

	private void storeItemInCas(Item next, CAS cas) throws CASException {
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
		TypeSystemDescription tsd = getProcessingResourceMetaData().getTypeSystem();
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
		vlds.setServerBase(baseUrl);
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
		vlis.setServerBase(baseUrl);
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.uima.collection.base_cpm.BaseCollectionReader#hasNext()
	 */
	public boolean hasNext() throws IOException, CollectionException {
		return itemsIter.hasNext();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.uima.collection.base_cpm.BaseCollectionReader#getProgress()
	 */
	public Progress[] getProgress() {
		return new Progress[] { new ProgressImpl(itemsFetched, totalItems, Progress.ENTITIES) };
	}

}
