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

	public static void main(String[] args) throws IOException, UIMAException, SAXException {
		String serverUri = args[0];
		String apiKey = args[1];
		String itemListId = args[2];
		String outputDir = args[3];
		String descriptorDir = args[4];

		CollectionReaderDescription reader = CollectionReaderFactory.createReaderDescription(
				ItemListCollectionReader.class, 
				ItemListCollectionReader.PARAM_VLAB_BASE_URL, serverUri,
				ItemListCollectionReader.PARAM_VLAB_API_KEY, apiKey,
				ItemListCollectionReader.PARAM_VLAB_ITEM_LIST_ID, itemListId,
				ItemListCollectionReader.PARAM_INCLUDE_RAW_DOCS, false);
		reader.toXML(new FileOutputStream(new File(descriptorDir, "ItemListCollectionReader.xml")));
		AnalysisEngineDescription casWriter = AnalysisEngineFactory.createEngineDescription(
				XmiWriterCasConsumer.class,
				XmiWriterCasConsumer.PARAM_OUTPUTDIR, outputDir);
		casWriter.toXML(new FileOutputStream(new File(descriptorDir, "ItemListToXmiAE.xml")));
		SimplePipeline.runPipeline(reader, casWriter);
	}
}
