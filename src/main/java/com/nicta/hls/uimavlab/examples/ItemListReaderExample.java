package com.nicta.hls.uimavlab.examples;

import java.io.IOException;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;

import com.nicta.hls.uimavlab.ItemListCollectionReader;

public class ItemListReaderExample {

	public static void main(String[] args) throws IOException, UIMAException {
		String serverUri = args[0];
		String apiKey = args[1];
		String itemListId = args[2];
		String outputDir = args[3];

		CollectionReaderDescription reader = CollectionReaderFactory.createReaderDescription(
				ItemListCollectionReader.class, 
				ItemListCollectionReader.PARAM_VLAB_BASE_URL, serverUri,
				ItemListCollectionReader.PARAM_VLAB_API_KEY, apiKey,
				ItemListCollectionReader.PARAM_VLAB_ITEM_LIST_ID, itemListId);
		AnalysisEngineDescription casWriter = AnalysisEngineFactory.createEngineDescription(
				XmiWriterCasConsumer.class,
				XmiWriterCasConsumer.PARAM_OUTPUTDIR, outputDir);
		SimplePipeline.runPipeline(reader, casWriter);
	}
}
