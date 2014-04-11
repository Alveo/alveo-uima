package com.nicta.uimavlab;

import com.google.common.collect.Lists;
import com.nicta.uimavlab.types.VLabItemSource;
import com.nicta.vlabclient.RestClient;
import com.nicta.vlabclient.TextRestAnnotation;
import com.nicta.vlabclient.entity.EntityNotFoundException;
import com.nicta.vlabclient.entity.InvalidAnnotationException;
import com.nicta.vlabclient.entity.InvalidServerAddressException;
import com.nicta.vlabclient.entity.Item;
import com.nicta.vlabclient.entity.UnauthorizedAPIKeyException;
import com.nicta.vlabclient.entity.UploadIntegrityException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.fit.component.CasConsumer_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.util.TypeSystemUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by amack on 4/04/14.
 */
public class ItemAnnotationUploader extends CasConsumer_ImplBase {
	private static final Logger LOG = LoggerFactory.getLogger(ItemAnnotationUploader.class);

	public static final String PARAM_VLAB_BASE_URL = "vLabBaseUrl";
	public static final String PARAM_VLAB_API_KEY = "vLabApiKey";
	public static final String PARAM_LABEL_FEATURE_NAMES = "labelFeatureNames";
	public static final String PARAM_ANNTYPE_FEATURE_NAMES = "annTypeFeatureNames";
	public static final String PARAM_UPLOADABLE_UIMA_TYPE_NAMES = "uploadableUimaTypeNames";

	/** The default feature name which, if found, is used to set the type of an annotation */
	public static final String DEFAULT_ANNTYPE_FEATURE = "com.nicta.uimavlab.types.ItemAnnotation:annType";

	/** The default feature name which, if found, is used to set the label of an annotation */
	public static final String DEFAULT_LABEL_FEATURE = "com.nicta.uimavlab.types.ItemAnnotation:label";

	@ConfigurationParameter(name = PARAM_VLAB_BASE_URL, mandatory = true,
			description = "Base URL for the HCS vLab REST/JSON API server "
					+ "- eg http://vlab.example.org/ ; the URL for the item list "
					+ " will be constructed by appending 'item_lists/{item_list_id}.json' to this URL")
	private String baseUrl;

	@ConfigurationParameter(name = PARAM_VLAB_API_KEY, mandatory = true,
			description = "API key for the vLab account (available from the web interface")
	private String apiKey;

	@ConfigurationParameter(name = PARAM_LABEL_FEATURE_NAMES, mandatory = false,
			description = "Fully-qualified feature names on UIMA annotations, the values of which will be mapped to " +
					"labels in HCS vLab (first match will be used); " +
					"the default entry is 'com.nicta.uimavlab.types.ItemAnnotation:label' " +
					"which you probably want to include if you set this parameter")
	private String[] labelFeatureNames = new String[] { DEFAULT_LABEL_FEATURE };

	@ConfigurationParameter(name = PARAM_ANNTYPE_FEATURE_NAMES, mandatory = false,
			description = "Fully-qualified feature names on UIMA annotations, the values of which will be mapped to " +
					"annotation type URIs in HCS vLab (using the first match after processing the list in order); " +
					"The default entry is 'com.nicta.uimavlab.types.ItemAnnotation:annType' " +
					"which you almost certainly want to include if you set this parameter. If no match is found " +
					"in this list a type URI is automatically created from the qualified type name")
	private String[] annTypeFeatureNames = new String[] { DEFAULT_ANNTYPE_FEATURE };

	@ConfigurationParameter(name = PARAM_UPLOADABLE_UIMA_TYPE_NAMES, mandatory = false,
			description = "If this parameter is set, only UIMA types with names matching those in this list," +
					"or their subtypes, will be uploaded")
	private String[] uploadableUimaTypeNames = null;

	private RestClient apiClient;
	private ItemCASAdapter casAdapter;
	private List<Feature> annTypeFeatures = new ArrayList<Feature>();
	private List<Feature> labelFeatures = new ArrayList<Feature>();
	private TypeSystem currentTypeSystem = null;
	private Set<Type> uploadableUimaTypes = null;
	private Map<String, Set<Feature>> cachedFeatureSets = new HashMap<String, Set<Feature>>();

	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {
		super.initialize(context);
		try {
			apiClient = new RestClient(baseUrl, apiKey);
		} catch (InvalidServerAddressException e) {
			throw new ResourceInitializationException(e);
		}

	}

	private void initForTypeSystem(TypeSystem ts) throws AnalysisEngineProcessException {
//		if (ts.equals(currentTypeSystem))
//			return;
		currentTypeSystem = ts;
		TypeSystemDescription tsd = TypeSystemUtil.typeSystem2TypeSystemDescription(currentTypeSystem);
		casAdapter = new ItemCASAdapter(tsd, baseUrl, false, true);
		initFeatureMappings();
		try {
			initTypeWhitelist();
		} catch (MissingTypeNameException e) {
			throw new AnalysisEngineProcessException(e);
		}
	}

	private void initTypeWhitelist() throws MissingTypeNameException {
		if (uploadableUimaTypeNames != null) {
			uploadableUimaTypes = new HashSet<Type>();
			for (String tn : uploadableUimaTypeNames) {
				Type t = currentTypeSystem.getType(tn);
				if (t == null)
					continue;
				uploadableUimaTypes.add(t);
				for (Type subt : currentTypeSystem.getProperlySubsumedTypes(t))
					uploadableUimaTypes.add(subt);
			}
			if (uploadableUimaTypes.size() == 0)
				throw new MissingTypeNameException("Found no types matching " +
						uploadableUimaTypeNames + "; no annotations will be uploaded");
		}
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
	public void process(CAS aCAS) throws AnalysisEngineProcessException {
		// create a new CAS and store a copy of the old item in it
		// then iterate through the supplied CAS, keeping any annotations which
		// don't correspond to anything in the original item
		// then bulk-upload these annotations.
		initForTypeSystem(aCAS.getTypeSystem());
		Item apiItem;
		CAS casOfOrig;
		try {
			apiItem = getOriginalFromAPI(aCAS);
			casOfOrig = getCopyOfOriginalCAS(aCAS, apiItem);
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		} catch (UnauthorizedAPIKeyException e) {
			throw new AnalysisEngineProcessException(e);
		}

		List<TextRestAnnotation> uploadable = new ArrayList<TextRestAnnotation>();

		Set<TextRestAnnotation> oldAnns = new HashSet<TextRestAnnotation>();
		FSIterator<AnnotationFS> oldAnnIter = casOfOrig.getAnnotationIndex().iterator(true);
		while (oldAnnIter.hasNext()) {
			AnnotationFS oldAnn = oldAnnIter.next();
			oldAnns.add(convertToHCSvLab(oldAnn));
		}

		FSIterator<AnnotationFS> annIter = aCAS.getAnnotationIndex().iterator(true);

		while (annIter.hasNext()) {
			AnnotationFS ann = annIter.next();
			if (!isAnnTypeUploadable(ann))
				continue;
			if (casOfOrig.getAnnotationIndex(ann.getType()).contains(ann))
				continue; // annotation already existed in CAS - don't re-add
			TextRestAnnotation asAlveoAnn = convertToHCSvLab(ann);
			if (oldAnns.contains(asAlveoAnn)) // already existed post-conversion
				continue;
			uploadable.add(asAlveoAnn);
		}


		// if we don't upload in chunks we get a socket timeout
		for (List<TextRestAnnotation> chunk : Lists.partition(uploadable, 100)) {
			try {
				apiItem.storeNewAnnotations(chunk);
			} catch (EntityNotFoundException e) {
				throw new AnalysisEngineProcessException(e);
			} catch (UploadIntegrityException e) {
				throw new AnalysisEngineProcessException(e);
			} catch (InvalidAnnotationException e) {
				throw new AnalysisEngineProcessException(e);
			}
		}
	}

	private boolean isAnnTypeUploadable(AnnotationFS ann) {
		return uploadableUimaTypes == null || uploadableUimaTypes.contains(ann.getType());
	}

	private TextRestAnnotation convertToHCSvLab(AnnotationFS ann) {
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
			annType = UIMAAlveoTypeMapping.getUriForTypeName(ann.getType().getName());
		String label = ""; // don't guess for this one - just make it empty
		for (Feature lf : labelFeatures) {
			if (features.contains(lf)) { // XXX - O(n)
				label = ann.getFeatureValueAsString(lf);
				break;
			}
		}

		return new TextRestAnnotation(annType, label, ann.getBegin(), ann.getEnd());
	}

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

	private Item getOriginalFromAPI(CAS providedCas) throws UnauthorizedAPIKeyException, CASException {
		VLabItemSource vlis = JCasUtil.selectSingle(providedCas.getJCas(), VLabItemSource.class);
		String itemUri = vlis.getSourceUri();
		return apiClient.getItemByUri(itemUri);
	}

	private CAS getCopyOfOriginalCAS(CAS updatedCAS, Item origItem) throws CASException {
		CAS casForOrig = updatedCAS.createView("original");
		casAdapter.storeItemInCas(origItem, casForOrig);
		return casForOrig;
	}

	public class MissingTypeNameException extends Exception {
		public MissingTypeNameException(String s) {
			super(s);
		}
	}
}
