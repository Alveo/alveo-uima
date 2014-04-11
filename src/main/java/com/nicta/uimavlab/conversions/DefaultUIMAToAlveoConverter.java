package com.nicta.uimavlab.conversions;

import com.nicta.uimavlab.UIMAAlveoTypeMapping;
import com.nicta.vlabclient.TextRestAnnotation;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.util.TypeSystemUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by amack on 11/04/14.
 */
public class DefaultUIMAToAlveoConverter implements UIMAToAlveoConverter {
	private final String[] annTypeFeatureNames;
	private final String[] labelFeatureNames;
	private TypeSystem currentTypeSystem;
	private List<Feature> annTypeFeatures;
	private List<Feature> labelFeatures;

	public DefaultUIMAToAlveoConverter(String[] annTypeFeatureNames, String[] labelFeatureNames) {
		this.annTypeFeatureNames = annTypeFeatureNames;
		this.labelFeatureNames = labelFeatureNames;
	}

	@Override
	public void setTypeSystem(TypeSystem ts) throws AnalysisEngineProcessException {
		if (ts.equals(currentTypeSystem))
			return;
		currentTypeSystem = ts;
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
	@Override
	public TextRestAnnotation convertToAlveo(AnnotationFS ann) {
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
		return UIMAAlveoTypeMapping.getUriForTypeName(uimaTypeName);
	}
}
