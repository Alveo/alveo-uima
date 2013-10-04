/**
 * 
 */
package com.nicta.hls.uimavlab;

import java.io.IOException;
import java.util.Iterator;

import org.apache.uima.UimaContext;
import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.fit.component.CasCollectionReader_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;

import com.nicta.hls.vlabclient.RestClient;
import com.nicta.hls.vlabclient.VLabDocument;
import com.nicta.hls.vlabclient.VLabItem;
import com.nicta.hls.vlabclient.VLabItemList;

/**
 * @author amack
 *
 */
public class ItemListCollectionReader extends CasCollectionReader_ImplBase {

	public static final String PARAM_VLAB_BASE_URL = "vlabBaseUrl";
	public static final String PARAM_VLAB_ITEM_LIST_ID = "vlabItemListId";
	
	public static final String PARAM_VLAB_API_KEY = "vLabApiKey";
	
	@ConfigurationParameter(name = PARAM_VLAB_ITEM_LIST_ID, mandatory = true,
			description = "Item ID which should be retrieved and converted into a " +
					"set of UIMA CAS documents")
	private String itemListId;
	
	@ConfigurationParameter(name = PARAM_VLAB_BASE_URL, mandatory = true,
			description = "Base URL for the HCS vLab REST/JSON API server " + 
					"- eg http://vlab.example.org/ ; the URL for the item list " +
					" will be constructed by appending 'item_lists/{item_list_id}.json'" +
					"to this URL")
	private String baseUrl;
	
	@ConfigurationParameter(name = PARAM_VLAB_API_KEY, mandatory = true,
			description = "API key for the vLab account (available from the web interface")
	private String apiKey;
	
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

	/* (non-Javadoc)
	 * @see org.apache.uima.collection.CollectionReader#getNext(org.apache.uima.cas.CAS)
	 */
	public void getNext(CAS cas) throws IOException, CollectionException {
		++itemsFetched;
		storeItemInCas(itemsIter.next(), cas); 
	}

	private void storeItemInCas(VLabItem next, CAS cas) {
		CAS mainView = cas.createView("00: _PRIMARY_ITEM_");
		mainView.setSofaDataString(next.primaryText(), "text/plain");
		int ctr = 1;
		for (VLabDocument vd : next.documents()) {
			CAS view = cas.createView(String.format("%02d: %s", ctr, vd.getType()));
			++ctr;
			view.setSofaDataString(vd.rawText(), "text/plain");
		}
	}

	/* (non-Javadoc)
	 * @see org.apache.uima.collection.base_cpm.BaseCollectionReader#hasNext()
	 */
	public boolean hasNext() throws IOException, CollectionException {
		return itemsIter.hasNext();
	}

	/* (non-Javadoc)
	 * @see org.apache.uima.collection.base_cpm.BaseCollectionReader#getProgress()
	 */
	public Progress[] getProgress() {
		return new Progress[] { new ProgressImpl(itemsFetched, totalItems, Progress.ENTITIES) };
	}

}
