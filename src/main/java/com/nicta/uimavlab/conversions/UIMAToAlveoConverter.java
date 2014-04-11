package com.nicta.uimavlab.conversions;

import com.nicta.vlabclient.TextRestAnnotation;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;

/**
 * Created by amack on 11/04/14.
 */
public interface UIMAToAlveoConverter {
	void setTypeSystem(TypeSystem ts) throws AnalysisEngineProcessException;

	public TextRestAnnotation convertToAlveo(AnnotationFS ann);

	public String getAlveoTypeUriForTypeName(String uimaTypeName);
}
