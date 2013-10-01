package com.nicta.hls.uimavlab;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.ClientResponse;

public class VLabRestClient {

	private String serverBaseUri;
	private String apiKey;

	public VLabRestClient(String serverBaseUri, String apiKey) {
		this.serverBaseUri = serverBaseUri;
		this.apiKey = apiKey;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class ItemList {
		public String getName() {
			return name;
		}

		public int getNumItems() {
			return num_items;
		}

		public String[] getItems() {
			return items;
		}

		private String name;
		private int num_items;
		private String[] items;
	}

	public String getItemListUri(String itemListId) {
		return String.format("%s/item_lists/%s.json", serverBaseUri, itemListId);
	}

	private ClientRequest getRequest(String uri) {
		ClientRequest req = new ClientRequest(uri);
		req.header("X-API-KEY", apiKey);
		return req;
	}

	public ItemList getItemList(String itemListId) throws Exception {
		return getItemListFromUri(getItemListUri(itemListId));
	}

	public ItemList getItemListFromUri(String itemListUri) throws Exception {
		ClientRequest req = getRequest(itemListUri);
		ClientResponse<ItemList> resp = req.get(ItemList.class);
		return resp.getEntity();
	}

	
	public static void main(String[] args) {
		String serverUri = args[0];
		String apiKey = args[1];
		String itemListId = args[2];

		VLabRestClient client = new VLabRestClient(serverUri, apiKey);
		try {
			System.out.println(client.getItemList(itemListId).getItems()[0]);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
