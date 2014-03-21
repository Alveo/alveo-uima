/**
 * 
 */
package com.nicta.uimavlab;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.nicta.vlabclient.entity.HCSvLabException;
import org.apache.uima.UimaContext;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.fit.component.CasCollectionReader_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.descriptor.TypeCapability;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;
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
		"com.nicta.uimavlab.types.ItemMetadata",
		"com.nicta.uimavlab.types.itemanns.*"
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

	/**
	 * 
	 */
	public ItemListCollectionReader() {
		// TODO Auto-generated constructor stub
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

	private void storeAnnotations(Item next, VLabItemSource vlis) throws CASException {
		List<TextAnnotation> anns;
		try {
			anns = next.getTextAnnotations();
		} catch (UnsupportedLDSchemaException e) {
			throw new CASException(e);
		}
		int ctr = 0;
		JCas jcas = vlis.getCAS().getJCas();
		vlis.setAnnotations(new FSArray(jcas, anns.size()));
		for (TextAnnotation ta : anns) {
			ItemAnnotation itAn = getItemAnnotationForType(jcas, ta.getType());
			itAn.setBegin(ta.getStartOffset());
			itAn.setEnd(ta.getEndOffset());
			itAn.setAnnType(ta.getType());
			itAn.setLabel(ta.getLabel());
			itAn.addToIndexes();
			vlis.setAnnotations(ctr++, itAn);
		}
	}
	
	private ItemAnnotation getItemAnnotationForType(JCas jcas, String annType) {
		if (annType.equals("speaker"))
			return new SpeakerAnnotation(jcas);
		else if (annType.equals("pause"))
			return new PauseAnnotation(jcas);
		else if (annType.equals("micropause"))
			return new MicroPauseAnnotation(jcas);
		else if (annType.equals("intonation"))
			return new IntonationAnnotation(jcas);
		else if (annType.equals("latched-utterance"))
			return new LatchedUtteranceAnnotation(jcas);
		else if (annType.equals("dubious-nonsense"))
			return new DubiousNonsenseAnnotation(jcas);
		else if (annType.equals("elongation"))
			return new ElongationAnnotation(jcas);
		LOG.info("Unknown annotation type {}", annType);
		return new UnknownItemAnnotation(jcas);
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

	private void storeMetadata(Item next, VLabItemSource vlis) throws CASException {
		Map<String, String> orig = next.getMetadata();
		ItemMetadata metadata = new ItemMetadata(vlis.getCAS().getJCas());
		// TODO: work out how to store all the missing metadata keys, as arbitrary key-value pairs
		//   see http://comments.gmane.org/gmane.comp.apache.uima.general/5179 for ideas
		metadata.setTitle(orig.get("http://purl.org/dc/terms/title"));
		metadata.setCollection(orig.get("http://purl.org/dc/terms/isPartOf"));
		metadata.setCreator(orig.get("http://purl.org/dc/terms/creator"));
		metadata.setIdentifier(orig.get("http://purl.org/dc/terms/identifier"));
		metadata.setDiscourseType(orig.get("http://www.language-archives.org/OLAC/1.1/discourse_type"));
		metadata.setRecordingDate(orig.get("http://www.language-archives.org/OLAC/1.1/recordingdate"));
		vlis.setMetadata(metadata);
		vlis.getCAS().setDocumentLanguage(orig.get("http://www.language-archives.org/OLAC/1.1/language"));
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
