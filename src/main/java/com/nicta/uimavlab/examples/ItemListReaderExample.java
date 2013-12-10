package com.nicta.uimavlab.examples;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.xml.sax.SAXException;

import com.nicta.uimavlab.ItemListCollectionReader;

public class ItemListReaderExample {
	public static void usage() {
		System.err
				.println("Usage: "
						+ ItemListReaderExample.class.getCanonicalName()
						+ " SERVER_URI API_KEY ITEM_LIST_ID OUTPUT_DIR [DESCRIPTOR_DIR]\n"
						+ "    SERVER_URI refers to the base URI of the HCS vLab server;\n"
						+ "    API_KEY is the API key for your user account, obtainable from the web interface;\n"
						+ "    ITEM_LIST_ID is the ID of a preconfigured item list you want to retrieve and \n "
						+ "       turn into a UIMA collection;\n"
						+ "    DESCRIPTOR_DIR, if provided, is a directory to write the configured descriptors to");
	}

	public static void main(String[] args) throws IOException, UIMAException, SAXException {
		String serverUri, apiKey, itemListId, outputDir, descriptorDir;

		try {
			serverUri = args[0];
			apiKey = args[1];
			itemListId = args[2];
			outputDir = args[3];
		} catch (ArrayIndexOutOfBoundsException e) {
			usage();
			System.exit(1);
			return;
		}

		try {
			descriptorDir = args[4];
		} catch (ArrayIndexOutOfBoundsException e) {
			descriptorDir = null;
		}
		CollectionReaderDescription reader = CollectionReaderFactory.createReaderDescription(
				ItemListCollectionReader.class, ItemListCollectionReader.PARAM_VLAB_BASE_URL,
				serverUri, ItemListCollectionReader.PARAM_VLAB_API_KEY, apiKey,
				ItemListCollectionReader.PARAM_VLAB_ITEM_LIST_ID, itemListId,
				ItemListCollectionReader.PARAM_INCLUDE_RAW_DOCS, false);
		AnalysisEngineDescription casWriter = AnalysisEngineFactory.createEngineDescription(
				XmiWriterCasConsumer.class, XmiWriterCasConsumer.PARAM_OUTPUTDIR, outputDir);
		if (descriptorDir != null) {
			reader.toXML(new FileOutputStream(new File(descriptorDir,
					"ItemListCollectionReader.xml")));
			casWriter.toXML(new FileOutputStream(new File(descriptorDir, "ItemListToXmiAE.xml")));
		}
		SimplePipeline.runPipeline(reader, casWriter);
	}
}
