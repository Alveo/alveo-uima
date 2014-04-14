package com.nicta.uimavlab.conversions;

import com.nicta.vlabclient.TextRestAnnotation;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;

/**
 * Created by amack on 11/04/14.
 */
public interface UIMAToAlveoAnnConverter {
	void setTypeSystem(TypeSystem ts) throws AnalysisEngineProcessException;

	public TextRestAnnotation convertToAlveo(AnnotationFS ann) throws NotInitializedException;

	public String getAlveoTypeUriForTypeName(String uimaTypeName);

	public boolean handlesTypeName(String uimaTypeName);

	/**
	 * Created by amack on 14/04/14.
	 */
	public class NotInitializedException extends Exception {
		public NotInitializedException(String s) {
		}
	}
}
