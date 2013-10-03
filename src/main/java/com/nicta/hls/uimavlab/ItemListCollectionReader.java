/**
 * 
 */
package com.nicta.hls.uimavlab;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.uima.UimaContext;
import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.fit.component.CasCollectionReader_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.util.Progress;

/**
 * @author amack
 *
 */
public class ItemListCollectionReader extends CasCollectionReader_ImplBase {

	public static final String PARAM_ITEM_LIST_URL = "itemListUrl";
	public static final String PARAM_VLAB_API_KEY = "vLabApiKey";
	
	@ConfigurationParameter(name = PARAM_ITEM_LIST_URL, mandatory = true,
			description = "URL for the item list in the HCS vLab REST/JSON API " + 
					"- eg http://vlab.example.org/item_lists/lang_samples1.json")
	private URI itemListUri;
	
	@ConfigurationParameter(name = PARAM_VLAB_API_KEY, mandatory = true,
			description = "API key for the vLab account (available from the web interface")
	private String apiKey;
	
	private List<URI> itemUris = new ArrayList<URI>();
	
	
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
	}

	/* (non-Javadoc)
	 * @see org.apache.uima.collection.CollectionReader#getNext(org.apache.uima.cas.CAS)
	 */
	public void getNext(CAS arg0) throws IOException, CollectionException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.apache.uima.collection.base_cpm.BaseCollectionReader#getProgress()
	 */
	public Progress[] getProgress() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.apache.uima.collection.base_cpm.BaseCollectionReader#hasNext()
	 */
	public boolean hasNext() throws IOException, CollectionException {
		// TODO Auto-generated method stub
		return false;
	}

}
