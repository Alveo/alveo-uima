package com.nicta.uimavlab.conversions;

import au.edu.alveo.client.TextRestAnnotation;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;

/**
 * Created by amack on 11/04/14.
 */
public interface UIMAToAlveoAnnConverter {
	void setTypeSystem(TypeSystem ts);

	public TextRestAnnotation convertToAlveo(AnnotationFS ann) throws NotInitializedException, InvalidAnnotationTypeException;

	public String getAlveoTypeUriForTypeName(String uimaTypeName);

	public boolean handlesTypeName(String uimaTypeName);

	/**
	 * Created by amack on 14/04/14.
	 */
	public class NotInitializedException extends Exception {
		public NotInitializedException(String s) {
			super(s);
		}
	}

	public class InvalidAnnotationTypeException extends Exception {
		public InvalidAnnotationTypeException(String s) {
			super(s);
		}
	}
}
