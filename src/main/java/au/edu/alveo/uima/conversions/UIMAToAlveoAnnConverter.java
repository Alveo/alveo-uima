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
 *
 * The converter is also used when reading Alveo annotations and writing UIMA annotations,
 * since the URI conversion is used to establish a mapping between Alveo type URIs and UIMA types
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
	 * @throws NotInitializedException If the converter has not been appropriately initialized
	 *   (for instance, if the converter has only been set up for creating type URIs rather
	 *   than complete conversion)
	 * @throws InvalidAnnotationTypeException If the annotation type is not supported by the converter
	 */
	public TextRestAnnotation convertToAlveo(AnnotationFS ann) throws NotInitializedException, InvalidAnnotationTypeException;

	/** Get the Alveo type URI corresponding to the provided UIMA type name.
	 *
	 * This is used for creating Alveo annotations, but also when we are reading
	 * Alveo annotations and converting them to UIMA, since it enables us to determine
	 * what the corresponding Alveo URIs are for UIMA type names and thus determine a mapping
	 * */
	public String getAlveoTypeUriForTypeName(String uimaTypeName);

	/** Return whether the provided UIMA type name is handled by this converter
	 *
	 * This is used by {@link au.edu.alveo.uima.conversions.FallingBackUIMAAlveoConverter} to
	 * determine whether to convert using this implementation or to iterate to the next
	 * item in the chain
	 * */
	public boolean handlesTypeName(String uimaTypeName);

	/**
	 * An Exception which indicates that the converter has not been properly
	 * initialized for the requested operation
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
