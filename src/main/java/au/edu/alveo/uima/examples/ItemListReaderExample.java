package au.edu.alveo.uima.examples;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import au.edu.alveo.uima.ItemListCollectionReader;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.xml.sax.SAXException;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class ItemListReaderExample {
	protected static class CLParams {

		@Parameter(names = {"-u", "--server-url"}, required = true, description = "Base URL of Alveo server")
		private String serverUrl;

		@Parameter(names = {"-k", "--api-key"}, required = true,
				description = "API key for for your user account, obtainable from the web interface")
		private String apiKey;

		@Parameter(names = { "--help", "-h", "-?" }, help = true, description = "Display this help text")
		private boolean help;

		@Parameter(names = { "--descriptor-dir"}, required = false,
				description = "If provided, a directory where descriptors will be written")
		private String descriptorDir = null;

		@Parameter(names = { "-o", "--xmi-output-dir"}, required = true,
				description = "The directory where the XMI files produced will be written")
		private String xmiDir;

		@Parameter(names = { "-i", "--item-list-id"}, required = true, description =
				"The item list ID to convert to XMI")
		private String itemListId;

	}

	private static String usage = String.format("Instantiate a basic pipeline " +
			"using uimaFIT which reads items from an item list and writes the " +
			"output as XMI files in the requested directory. If --descriptor-dir " +
			"is set, the descriptors for the collection reader, analysis engine " +
			"and type system will be written to that directory.");


	public static void main(String[] args) throws IOException, UIMAException, SAXException {
		String serverUri, apiKey, itemListId, outputDir, descriptorDir;

		CLParams params = new CLParams();
		JCommander jcom = new JCommander(params, args);
		jcom.setProgramName(ItemListReaderExample.class.getName());
		if (params.help) {
			System.err.println(usage);
			jcom.usage();
			return;
		}
		runPipeline(params.serverUrl, params.apiKey, params.xmiDir, params.itemListId, params.descriptorDir);

	}

	private static void runPipeline(String serverUrl, String apiKey, String xmiDir, String itemListId,
			String descriptorDir)
			throws UIMAException, IOException, SAXException {
		CollectionReaderDescription reader = ItemListCollectionReader.createDescription(
				ItemListCollectionReader.PARAM_ALVEO_BASE_URL, serverUrl,
				ItemListCollectionReader.PARAM_ALVEO_API_KEY, apiKey,
				ItemListCollectionReader.PARAM_ALVEO_ITEM_LIST_ID, itemListId,
				ItemListCollectionReader.PARAM_INCLUDE_RAW_DOCS, false);
		AnalysisEngineDescription casWriter = AnalysisEngineFactory.createEngineDescription(
				XmiWriterCasConsumer.class, XmiWriterCasConsumer.PARAM_OUTPUTDIR, xmiDir);
		casWriter.getAnalysisEngineMetaData().setTypeSystem(reader.getCollectionReaderMetaData().getTypeSystem());
		if (descriptorDir != null) {
			OutputStream readerOS = new BufferedOutputStream(new FileOutputStream(
					new File(descriptorDir, "ItemListCollectionReader.xml")));
			reader.toXML(readerOS);
			OutputStream tsOS = new BufferedOutputStream(new FileOutputStream(
					new File(descriptorDir, "typesystem-full.xml")));
			reader.getCollectionReaderMetaData().getTypeSystem().toXML(tsOS);
			OutputStream cwOS = new BufferedOutputStream(new FileOutputStream(
					new File(descriptorDir, "ItemListToXmiAE.xml")));
			casWriter.toXML(cwOS);
		}
		SimplePipeline.runPipeline(reader, casWriter);

	}
}
