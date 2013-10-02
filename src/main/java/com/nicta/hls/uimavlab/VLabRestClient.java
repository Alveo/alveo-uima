package com.nicta.hls.uimavlab;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

public class VLabRestClient {

	private String serverBaseUri;
	private String apiKey;
	private Client client;

	public VLabRestClient(String serverBaseUri, String apiKey) {
		this.serverBaseUri = serverBaseUri;
		this.apiKey = apiKey;
		client = ClientBuilder.newClient();
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

	private Invocation.Builder getInvocBuilder(String uri) {
		return client.target(uri)
				.request("application/json")
				.header("X-API-KEY", apiKey);
	}

	public ItemList getItemList(String itemListId) throws Exception {
		return getItemListFromUri(getItemListUri(itemListId));
	}

	public ItemList getItemListFromUri(String itemListUri) throws Exception {
		return getInvocBuilder(itemListUri).get(ItemList.class);
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
