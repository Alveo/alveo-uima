package com.nicta.uimavlab.examples;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.uima.UIMAFramework;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.CASRuntimeException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.collection.CasConsumerDescription;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.fit.component.CasConsumer_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceProcessException;
import org.apache.uima.util.InvalidXMLException;
import org.apache.uima.util.TypeSystemUtil;
import org.apache.uima.util.UriUtils;
import org.apache.uima.util.XMLInputSource;
import org.apache.uima.util.XMLSerializer;
import org.xml.sax.SAXException;

/**
 * A simple CAS consumer that writes the CAS to XMI format.
 * 
 * UIMAfit compatible
 * 
 * <p>
 * This CAS Consumer takes one parameter:
 * <ul>
 * <li><code>OutputDirectory</code> - path to directory into which output files
 * will be written</li>
 * </ul>
 */
public class XmiWriterCasConsumer extends CasConsumer_ImplBase {
	/**
	 * Name of configuration parameter that must be set to the path of a
	 * directory into which the output files will be written.
	 */
	private boolean typeSystemWritten = false;
	private static final String TYPE_SYSTEM_BASENAME = "typesystem.xml";
	private static final String DOCS_BASENAME = "docs";

	private File typeSystemXml() {
		return new File(mOutputDir, TYPE_SYSTEM_BASENAME);
	}

	private File docDir() {
		return new File(mOutputDir, DOCS_BASENAME);
	}


	public static final String PARAM_OUTPUTDIR = "OutputDirectory";

	@ConfigurationParameter(name = PARAM_OUTPUTDIR, mandatory = true)
	private File mOutputDir;

	private int mDocNum;

	public void initialize(UimaContext context) throws ResourceInitializationException {
	    super.initialize(context);
		mDocNum = 0;
		if (!docDir().exists())
			docDir().mkdirs();
	}

	/**
	 * Processes the CAS which was populated by the TextAnalysisEngines. <br>
	 * In this case, the CAS is converted to XMI and written into the output
	 * file .
	 * 
	 * @param aCAS
	 *            a CAS which has been populated by the TAEs
	 * 
	 * @throws ResourceProcessException
	 *             if there is an error in processing the Resource
	 * 
	 * @see org.apache.uima.collection.base_cpm.CasObjectProcessor#processCas(org.apache.uima.cas.CAS)
	 */
	@Override
	public void process(CAS aCAS) throws AnalysisEngineProcessException {
		String modelFileName = null;

		JCas jcas;
		try {
			jcas = aCAS.getJCas();
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		}

		File outFile = new File(docDir(), String.format("doc_%05d.xml", mDocNum++)); // Jira
																		// UIMA-629
		// serialize XCAS and write to output file
		try {
			writeXmi(jcas.getCas(), outFile, modelFileName);
		} catch (IOException e) {
			throw new AnalysisEngineProcessException(e);
		} catch (SAXException e) {
			throw new AnalysisEngineProcessException(e);
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		}
	}

	/**
	 * Serialize a CAS to a file in XMI format
	 * 
	 * @param aCas
	 *            CAS to serialize
	 * @param name
	 *            output file
	 * @throws SAXException
	 * @throws Exception
	 * 
	 * @throws ResourceProcessException
	 */
	private void writeXmi(CAS aCas, File name, String modelFileName) throws IOException,
			SAXException, CASException {
		if (!typeSystemWritten)
			writeTypeSystem(aCas);
		FileOutputStream out = null;

		try {
			// write XMI
			out = new FileOutputStream(name);
			XmiCasSerializer ser = new XmiCasSerializer(aCas.getTypeSystem());
			XMLSerializer xmlSer = new XMLSerializer(out, false);
			ser.serialize(aCas, xmlSer.getContentHandler());
		} finally {
			if (out != null) {
				out.close();
			}
		}
	}

	/**
	 * Parses and returns the descriptor for this collection reader. The
	 * descriptor is stored in the uima.jar file and located using the
	 * ClassLoader.
	 * 
	 * @return an object containing all of the information parsed from the
	 *         descriptor.
	 * 
	 * @throws InvalidXMLException
	 *             if the descriptor is invalid or missing
	 */
	public static CasConsumerDescription getDescription() throws InvalidXMLException {
		InputStream descStream = XmiWriterCasConsumer.class
				.getResourceAsStream("XmiWriterCasConsumer.xml");
		return UIMAFramework.getXMLParser().parseCasConsumerDescription(
				new XMLInputSource(descStream, null));
	}

	private void writeTypeSystem(CAS aCas) throws IOException, CASRuntimeException, SAXException, CASException {
		OutputStream typeOS = new BufferedOutputStream(new FileOutputStream(typeSystemXml()));
		try {
			TypeSystemUtil.typeSystem2TypeSystemDescription(aCas.getTypeSystem()).toXML(typeOS);
		} finally {
			typeOS.close();
		}
	}

	public static URL getDescriptorURL() {
		return XmiWriterCasConsumer.class.getResource("XmiWriterCasConsumer.xml");
	}

}
