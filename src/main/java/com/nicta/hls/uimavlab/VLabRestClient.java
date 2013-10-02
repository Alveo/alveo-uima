package com.nicta.hls.uimavlab;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.MediaType;

import org.codehaus.jackson.annotate.JsonAnySetter;
import org.codehaus.jackson.annotate.JsonProperty;
import org.glassfish.jersey.jackson.JacksonFeature;

class JsonItemList {
	public String getName() {
		return name;
	}

	public int getNumItems() {
		return numItems;
	}

	public String[] getItems() {
		return items;
	}
	
	@JsonAnySetter
	public void handleUnknown(String key, Object value) {
		System.err.println("Unknown key: " +  key);
	}

	private String name;
	
	@JsonProperty(value="num_items")
	private int numItems;
	
	private String[] items;
}

class JsonCatalogItem {
	@JsonProperty(value = "catalog_url")
	private String catalogUrl;
	
	private Map<String, String> metadata;
	
	@JsonProperty(value = "primary_text_url")
	private String primaryTextUrl;
	
	@JsonProperty(value = "annotations_url")
	private String annotationsUrl;
	
	private JsonDocument[] documents;

	public String getCatalogUrl() {
		return catalogUrl;
	}
	public Map<String, String> getMetadata() {
		return metadata;
	}
	public String getPrimaryTextUrl() {
		return primaryTextUrl;
	}
	public JsonDocument[] getDocuments() {
		return documents;
	}
}

class JsonDocument {
	private String url;
	private String type;
	private String size;
	public String getUrl() {
		return url;
	}
	public String getType() {
		return type;
	}
	public String getSize() {
		return size;
	}
}

public class VLabRestClient {

	private String serverBaseUri;
	private String apiKey;
	private Client client;

	public VLabRestClient(String serverBaseUri, String apiKey) {
		this.serverBaseUri = serverBaseUri;
		this.apiKey = apiKey;
		client = ClientBuilder.newClient();
		client.register(JacksonFeature.class);
	}

	public class ItemList {
		private JsonItemList fromJson;
		private String uri;
		
		public ItemList(JsonItemList raw, String uri) {
			fromJson = raw;
			this.uri = uri;
		}
		
		public String getUri() {
			return uri;
		}
		
		public String[] itemUris() {
			return fromJson.getItems();
		}
		
		public String name() {
			return fromJson.getName();
		}
		
		public long numItems() {
			return fromJson.getNumItems();
		}
//		
//		private List<JsonCatalogItem> getJsonCatalogItems() {
//			List<JsonCatalogItem> jcis = new ArrayList<JsonCatalogItem>(numItems());
//				jcis.add(getJsonInvocBuilder(itemUri).get(JsonCatalogItem.class));
//			return jcis;
//		}

		public List<CatalogItem> getCatalogItems() {
			List<CatalogItem> cis = new ArrayList<CatalogItem>();
			for (String itemUri : itemUris()) {
				JsonCatalogItem jci = getJsonInvocBuilder(itemUri).get(JsonCatalogItem.class);
				cis.add(new CatalogItem(jci, itemUri));
			}
			return cis;
		}
	}
	
	public class CatalogItem {
		private JsonCatalogItem fromJson;
		private String uri;
		
		public CatalogItem(JsonCatalogItem raw, String uri) {
			fromJson = raw;
			this.uri = uri;
		}
		
//		public List<Document> documents() {
//		}
		
		public String getUri() {
			return uri;
		}
		
		public String primaryText() {
			return getTextInvocBuilder(fromJson.getPrimaryTextUrl()).get(String.class);
		}
	}

	public String getItemListUri(String itemListId) {
		return String.format("%s/item_lists/%s.json", serverBaseUri, itemListId);
	}

	public ItemList getItemList(String itemListId) throws Exception {
		return getItemListFromUri(getItemListUri(itemListId));
	}

	public ItemList getItemListFromUri(String itemListUri) throws Exception {
		return new ItemList(getJsonInvocBuilder(itemListUri).get(JsonItemList.class), itemListUri);
	}

	public String getItemListJson(String itemListId) throws Exception {
		return getItemListJsonFromUri(getItemListUri(itemListId));
	}
	
	public String getItemListJsonFromUri(String itemListUri) throws Exception {
		return getJsonInvocBuilder(itemListUri).get(String.class);
	}

		

	private Invocation.Builder getJsonInvocBuilder(String uri) {
		return getInvocBuilder(uri, MediaType.APPLICATION_JSON);
	}
	
	private Invocation.Builder getTextInvocBuilder(String uri) {
		return getInvocBuilder(uri, MediaType.TEXT_PLAIN);
	}

	
	private Invocation.Builder getInvocBuilder(String uri, String contType) {
		return client.target(uri)
				.request(contType).accept(contType)
				.header("X-API-KEY", apiKey);
	}

	
	public static void main(String[] args) {
		String serverUri = args[0];
		String apiKey = args[1];
		String itemListId = args[2];

		VLabRestClient client = new VLabRestClient(serverUri, apiKey);
		try {
			System.out.println(client.getItemListJson(itemListId));
			ItemList il = client.getItemList(itemListId);
			System.out.println(String.format("Found %d items", il.numItems())); 
			for (CatalogItem ci : il.getCatalogItems()) {
				System.out.println("\n" + ci.getUri());
				System.out.println(ci.primaryText());
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
