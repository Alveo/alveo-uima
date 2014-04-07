package com.nicta.uimavlab;

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
import org.apache.uima.cas.CASRuntimeException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.admin.CASFactory;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.fit.component.CasConsumer_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.util.TypeSystemUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by amack on 4/04/14.
 */
public class ItemAnnotationUploader extends CasConsumer_ImplBase {
	public static final String PARAM_VLAB_BASE_URL = "vLabBaseUrl";
	public static final String PARAM_VLAB_API_KEY = "vLabApiKey";
	public static final String PARAM_LABEL_FEATURE_NAMES = "labelFeatureNames";
	public static final String PARAM_ANNTYPE_FEATURE_NAMES = "annTypeFeatureNames";

	/** The default feature name which, if found, is used to set the type of an annotation */
	public static final String DEFAULT_ANNTYPE_FEATURE = "com.nicta.uimavlab.types.ItemAnnotation:label";

	/** The default feature name which, if found, is used to set the label of an annotation */
	public static final String DEFAULT_LABEL_FEATURE = "com.nicta.uimavlab.types.ItemAnnotation:annType";

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

	private RestClient apiClient;
	private ItemCASAdapter casAdapter;
	private List<Feature> annTypeFeatures = new ArrayList<Feature>();
	private List<Feature> labelFeatures = new ArrayList<Feature>();
	private TypeSystem currentTypeSystem = null;

	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {
		super.initialize(context);
		try {
			apiClient = new RestClient(baseUrl, apiKey);
		} catch (InvalidServerAddressException e) {
			throw new ResourceInitializationException(e);
		}

	}

	private void initForTypeSystem(TypeSystem ts) {
		if (ts.equals(currentTypeSystem))
			return;
		currentTypeSystem = ts;
		TypeSystemDescription tsd = TypeSystemUtil.typeSystem2TypeSystemDescription(currentTypeSystem);
		casAdapter = new ItemCASAdapter(tsd, baseUrl, false, true);
		initFeatureMappings();

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
		// start here - create a new CAS and store a copy of the old item in it
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

		FSIterator<AnnotationFS> annIter = aCAS.getAnnotationIndex().iterator(true);
		while (annIter.hasNext()) {
			AnnotationFS ann = annIter.next();
			if (casOfOrig.getAnnotationIndex().contains(ann))
				continue; // annotation already existed - don't re-add
			uploadable.add(convertToHCSvLab(ann));
		}

		try {
			apiItem.storeNewAnnotations(uploadable);
		} catch (EntityNotFoundException e) {
			throw new AnalysisEngineProcessException(e);
		} catch (UploadIntegrityException e) {
			throw new AnalysisEngineProcessException(e);
		} catch (InvalidAnnotationException e) {
			throw new AnalysisEngineProcessException(e);
		}
	}

	private TextRestAnnotation convertToHCSvLab(AnnotationFS ann) {
		String annType = null;
		for (Feature atf : annTypeFeatures) {
			try {
				annType = ann.getFeatureValueAsString(atf);
				break;
			} catch (CASRuntimeException e) {
			}
		}
		if (annType == null) // haven't found anything - make en educated guess
			annType = mapTypeNameToURI(ann.getType().getName());
		String label = ""; // don't guess for this one - just make it empty
		for (Feature lf : labelFeatures) {
			try {
				label = ann.getFeatureValueAsString(lf);
				break;
			} catch (CASRuntimeException e) {
			}
		}

		return new TextRestAnnotation(annType, label, ann.getBegin(), ann.getEnd());
	}

	/** convert a java-style type name to a URI by
	 * 	splitting on '.', reversing the order of all components but the last
	 * 	and putting the last on the end after a '/'. This will be syntactically
	 * 	valid even if the semantics is not ideal
	 */
	private String mapTypeNameToURI(String name) {
		String[] comps = name.split("\\.");
		StringBuilder sb = new StringBuilder("http://");
		for (int i = comps.length - 2; i >= 0; i++) {
			sb.append(comps[i]);
			sb.append(".");
		}
		sb.append(comps[comps.length - 1]);
		return sb.toString();
	}

	private Item getOriginalFromAPI(CAS providedCas) throws UnauthorizedAPIKeyException, CASException {
		VLabItemSource vlis = JCasUtil.selectSingle(providedCas.getJCas(), VLabItemSource.class);
		String itemUri = vlis.getSourceUri();
		return apiClient.getItemByUri(itemUri);
	}

	private CAS getCopyOfOriginalCAS(CAS newCAS, Item origItem) throws CASException {
		CAS casForOrig = CASFactory.createCAS(newCAS.getTypeSystem()).getCAS();
		casAdapter.storeItemInCas(origItem, casForOrig);
		return casForOrig;
	}
}
