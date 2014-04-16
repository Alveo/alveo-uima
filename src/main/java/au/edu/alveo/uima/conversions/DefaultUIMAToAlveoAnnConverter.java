package au.edu.alveo.uima.conversions;

import au.edu.alveo.client.TextRestAnnotation;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The default converter for creating Alveo annotations from UIMA annotations.
 *
 * This is the fallback strategy in for converting. There are two important characteristics
 * of the Alveo annotations which must be derived from UIMA -- the <code>type</code> (not the <code>@type</code>"),
 * which is a URI, and the <code>label</code>, which is a free text string.
 *
 * The strategy employed by this converter is to first attempt to populate these values using
 * features whose names match those supplied as the <code>annTypeFeatureNames</code> and
 * <code>labelFeatureNames</code> arguments to the constructor. If these features are not found on the
 * annotation, a fallback value is used. For the <code>type</code> URI, this is created by automatically
 * converting the type name to a URI in a sensible way. For the label this is simply the empty string
 */
public class DefaultUIMAToAlveoAnnConverter implements UIMAToAlveoAnnConverter {
	private final String[] annTypeFeatureNames;
	private final String[] labelFeatureNames;
	private TypeSystem currentTypeSystem;
	private List<Feature> annTypeFeatures = new ArrayList<Feature>();
	private List<Feature> labelFeatures = new ArrayList<Feature>();

	public DefaultUIMAToAlveoAnnConverter(String[] annTypeFeatureNames, String[] labelFeatureNames) {
		this.annTypeFeatureNames = annTypeFeatureNames;
		this.labelFeatureNames = labelFeatureNames;
	}

	/** Initialize an instance which cannot convert types, but can convert URIs.
	 *
	 * This is used when we are reading Alveo annotations and converting them to
	 * UIMA, since it enables us to sensibly create a mapping between UIMA types
	 * and Alveo type URIs */
	public DefaultUIMAToAlveoAnnConverter() {
		this.annTypeFeatureNames = null;
		this.labelFeatureNames = null;
	}

	@Override
	public void setTypeSystem(TypeSystem ts) {
		if (ts.equals(currentTypeSystem))
			return;
		currentTypeSystem = ts;
		if (canConvertAnnotations())
			initFeatureMappings();
	}

	private Map<String, Set<Feature>> cachedFeatureSets = new HashMap<String, Set<Feature>>();

	private Set<Feature> getKnownFeatures(AnnotationFS ann) {
		// basic function to memoize feature sets
		String annType = ann.getType().getName();
		Set<Feature> knownFeatures = cachedFeatureSets.get(annType);
		if (knownFeatures == null) {
			knownFeatures = new TreeSet<Feature>();
			knownFeatures.addAll(ann.getType().getFeatures());
			cachedFeatureSets.put(annType, knownFeatures);
		}
		return knownFeatures;
	}


	private void initFeatureMappings() {
		for (String annTypeFN: annTypeFeatureNames) {
			Feature feat = currentTypeSystem.getFeatureByFullName(annTypeFN);
			if (feat != null)
				annTypeFeatures.add(feat);
		}
		for (String labelFN: labelFeatureNames) {
			Feature feat = currentTypeSystem.getFeatureByFullName(labelFN);
			if (feat != null)
				labelFeatures.add(feat);
		}
	}

	public boolean canConvertAnnotations() {
		return annTypeFeatureNames != null && labelFeatureNames != null;
	}

	@Override
	public boolean handlesTypeName(String uimaTypeName) {
		return true; // default - handles all
	}

	@Override
	public TextRestAnnotation convertToAlveo(AnnotationFS ann) throws NotInitializedException {
		if (!canConvertAnnotations())
			throw new NotInitializedException("This converter has not been initialized for full-scale type conversion");
		String annType = null;
		Set<Feature> features = getKnownFeatures(ann);
		for (Feature atf : annTypeFeatures) {
//			try {
//				annType = ann.getFeatureValueAsString(atf);
//				break;
//			} catch (CASRuntimeException e) {
//			}
			// not sure why the above doesn't work
			if (features.contains(atf)) { // XXX - O(n)
				annType = ann.getFeatureValueAsString(atf);
				break;
			}
		}
		if (annType == null) // haven't found anything - make en educated guess
			annType = getAlveoTypeUriForTypeName(ann.getType().getName());
		String label = ""; // don't guess for this one - just make it empty
		for (Feature lf : labelFeatures) {
			if (features.contains(lf)) { // XXX - O(n)
				label = ann.getFeatureValueAsString(lf);
				break;
			}
		}

		return new TextRestAnnotation(annType, label, ann.getBegin(), ann.getEnd());
	}

	@Override
	public String getAlveoTypeUriForTypeName(String uimaTypeName) {
		return UIMAAlveoTypeNameMapping.getUriForTypeName(uimaTypeName);
	}
}
