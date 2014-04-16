package au.edu.alveo.uima.conversions;

import au.edu.alveo.client.TextRestAnnotation;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;

/**
 * The interface for converting UIMA annotations to Alveo annotations.
 *
 * Implementations of this interface can be used to customise the conversion to
 * an Alveo-compatible format, instead of the default conversion behaviour
 * provided by {@link au.edu.alveo.uima.conversions.DefaultUIMAToAlveoAnnConverter}
 */
public interface UIMAToAlveoAnnConverter {

	/** Set the internal type system of this converter.
	 *
	 * This method is called before the other conversion methods,
	 * so it is always possible to know the type system in advance.
	 *
	 * @param ts The new type system
	 */
	void setTypeSystem(TypeSystem ts);

	/** Convert the supplied UIMA annotation structure to the Alveo format.
	 *
	 * Implementations should ensure that the type URI on the returned annotation is the
	 * same as the one returned by {@link #getAlveoTypeUriForTypeName(String)}.
	 *
	 * @param ann The UIMA CAS annotation data
	 * @return a {@code TextRestAnnotation} instance suitable for uploading to an Alveo server.
	 * @throws NotInitializedException
	 * @throws InvalidAnnotationTypeException
	 */
	public TextRestAnnotation convertToAlveo(AnnotationFS ann) throws NotInitializedException, InvalidAnnotationTypeException;

	/** Get the Alveo type URI corresponding to the provided UIMA type name */
	public String getAlveoTypeUriForTypeName(String uimaTypeName);

	/** Return whether the provided UIMA type name is handled by this converter
	 *
	 * This is used by {@link au.edu.alveo.uima.conversions.FallingBackUIMAAlveoConverter} to
	 * determine whether to convert using this implementation or to iterate to the next
	 * item in the chain
	 * */
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
