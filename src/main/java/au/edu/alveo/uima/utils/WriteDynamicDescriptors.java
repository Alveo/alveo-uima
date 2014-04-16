package au.edu.alveo.uima.utils;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import au.edu.alveo.uima.ItemListCollectionReader;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.resource.ResourceInitializationException;
import org.xml.sax.SAXException;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by amack on 7/04/14.
 *
 * Writes descriptors which need to be dynamically-generated (because of being based on
 * a type system derived from server-side types) to a specified location
 */
public class WriteDynamicDescriptors {
	protected static class CLParams {

		@Parameter(names = {"-u", "--server-url"}, required = true, description = "Base URL of Alveo server")
		private String serverUrl;

		@Parameter(names = {"-k", "--api-key"}, required = true,
				description = "API key for for your user account, obtainable from the web interface")
		private String apiKey;

		@Parameter(names = { "--help", "-h", "-?" }, help = true, description = "Display this help text")
		private boolean help;

		@Parameter(names = { "-d", "--dir"}, required = true,
				  description = "Directory where descriptors will be written")
		private String dirName;

	}

	private static String usage = String.format("Dynamically generate descriptors for " +
			"the type system and collection reader for the provided Alveo server " +
			"and API key. Useful for non-uimaFIT-based pipelines. Note that the " +
			"uimaFIT maven plugin is not able to help here because the type system " +
			"(and therefore the collection reader descriptor) must be dynamically-generated " +
			"on the basis of output from the provided server");

	public static void main(String[] args) throws Exception {
		CLParams params = new CLParams();
		JCommander jcom = new JCommander(params, args);
		jcom.setProgramName(WriteDynamicDescriptors.class.getName());
		if (params.help) {
			System.err.println(usage);
			jcom.usage();
			return;
		}
		writeDescriptors(params.serverUrl, params.apiKey, params.dirName);
	}

	private static void writeDescriptors(String serverUrl, String apiKey, String dirName)
			throws ResourceInitializationException, IOException, SAXException {
		CollectionReaderDescription reader = ItemListCollectionReader.createDescription(
				ItemListCollectionReader.PARAM_ALVEO_BASE_URL, serverUrl,
				ItemListCollectionReader.PARAM_ALVEO_API_KEY, apiKey);
		File readerXML = new File(dirName, "ItemListCollectionReader.xml");
		OutputStream readerOS = new BufferedOutputStream(new FileOutputStream(readerXML));
		reader.toXML(readerOS);
		File tsXML = new File(dirName, "typesystem-full.xml");
		OutputStream tsOS = new BufferedOutputStream(new FileOutputStream(tsXML));
		reader.getCollectionReaderMetaData().getTypeSystem().toXML(tsOS);
	}
}
