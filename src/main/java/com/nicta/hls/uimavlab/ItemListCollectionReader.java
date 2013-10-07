/**
 * 
 */
package com.nicta.hls.uimavlab;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import org.apache.uima.UimaContext;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.fit.component.CasCollectionReader_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.descriptor.TypeCapability;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;

import com.nicta.hls.uimavlab.types.ItemMetadata;
import com.nicta.hls.uimavlab.types.VLabDocSource;
import com.nicta.hls.uimavlab.types.VLabItemSource;
import com.nicta.hls.vlabclient.RestClient;
import com.nicta.hls.vlabclient.VLabDocument;
import com.nicta.hls.vlabclient.VLabItem;
import com.nicta.hls.vlabclient.VLabItemList;

/**
 * @author amack
 * 
 */
@TypeCapability(outputs = {"com.nicta.hls.uimavlab.types.VLabItemSource", "com.nicta.hls.uimavlab.types.VLabDocSource"})
public class ItemListCollectionReader extends CasCollectionReader_ImplBase {

	public static final String PARAM_VLAB_BASE_URL = "vlabBaseUrl";
	public static final String PARAM_VLAB_ITEM_LIST_ID = "vlabItemListId";
	public static final String PARAM_VLAB_API_KEY = "vLabApiKey";
	public static final String PARAM_INCLUDE_RAW_DOCS = "includeRawDocs";

	@ConfigurationParameter(name = PARAM_VLAB_ITEM_LIST_ID, mandatory = true, description = "Item ID which should be retrieved and converted into a "
			+ "set of UIMA CAS documents")
	private String itemListId;

	@ConfigurationParameter(name = PARAM_VLAB_BASE_URL, mandatory = true, description = "Base URL for the HCS vLab REST/JSON API server "
			+ "- eg http://vlab.example.org/ ; the URL for the item list "
			+ " will be constructed by appending 'item_lists/{item_list_id}.json'" + "to this URL")
	private String baseUrl;

	@ConfigurationParameter(name = PARAM_VLAB_API_KEY, mandatory = true, description = "API key for the vLab account (available from the web interface")
	private String apiKey;

	@ConfigurationParameter(name = PARAM_INCLUDE_RAW_DOCS, mandatory = false, description = "Include raw document sources as separate SofAs")
	private boolean includeRawDocs = false;

	private VLabItemList itemList;
	private Iterator<? extends VLabItem> itemsIter;
	private int itemsFetched;
	private int totalItems;

	/**
	 * 
	 */
	public ItemListCollectionReader() {
		// TODO Auto-generated constructor stub
	}

	public void initialize(UimaContext context) {
		fetchItemList();
	}

	private void fetchItemList() {
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

	private void storeItemInCas(VLabItem next, CAS cas) throws CASException {
		CAS mainView = cas.createView("00: _PRIMARY_ITEM_");
		mainView.setSofaDataString(next.primaryText(), "text/plain");
		storeMainItem(next, mainView);
		int ctr = 1;
		if (includeRawDocs) {
			for (VLabDocument vd : next.documents()) {
				++ctr;
				CAS view = cas.createView(String.format("%02d: %s", ctr, vd.getType()));
				storeSourceDoc(vd, view);
			}
		}
	}

	private void storeSourceDoc(VLabDocument vd, CAS view) throws CASException {
		view.setSofaDataString(vd.rawText(), "text/plain");
		VLabDocSource vlds = new VLabDocSource(view.getJCas());
		vlds.setServerBase(baseUrl);
		vlds.setRawTextUrl(vd.getRawTextUrl());
		vlds.setDocType(vd.getType());
		vlds.addToIndexes();
	}

	private void storeMainItem(VLabItem next, CAS mainView) throws CASException {
		VLabItemSource vlis = new VLabItemSource(mainView.getJCas());
		vlis.setSourceUri(next.getUri());
		vlis.setServerBase(baseUrl);
		storeMetadata(next, vlis);
		vlis.addToIndexes();
	}

	private void storeMetadata(VLabItem next, VLabItemSource vlis) throws CASException {
		Map<String, String> orig = next.getMetadata();
		ItemMetadata metadata = new ItemMetadata(vlis.getCAS().getJCas());
		metadata.setTitle("foo");
		metadata.setTitle(orig.get("Title:"));
		metadata.setCollection(orig.get("Collection:"));
		metadata.setWordCount(Integer.parseInt(orig.get("Word Count")));
		metadata.setContributor(orig.get("Contributor:"));
		metadata.setMode(orig.get("Mode:"));
		vlis.setMetadata(metadata);
		vlis.getCAS().setDocumentLanguage(orig.get("Language (ISO 639-3 Code):"));
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
