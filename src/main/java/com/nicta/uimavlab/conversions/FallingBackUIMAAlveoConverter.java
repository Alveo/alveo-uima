package com.nicta.uimavlab.conversions;

import com.nicta.vlabclient.TextRestAnnotation;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by amack on 14/04/14.
 */
public class FallingBackUIMAAlveoConverter implements UIMAToAlveoAnnConverter {
	private final List<UIMAToAlveoAnnConverter> converters = new ArrayList<UIMAToAlveoAnnConverter>();

	public FallingBackUIMAAlveoConverter(List<? extends UIMAToAlveoAnnConverter> converters) {
		this.converters.addAll(converters);
	}

	public FallingBackUIMAAlveoConverter(UIMAToAlveoAnnConverter... converters) {
		for (UIMAToAlveoAnnConverter conv : converters)
			this.converters.add(conv);
	}

	@Override
	public void setTypeSystem(TypeSystem ts) {
		for (UIMAToAlveoAnnConverter conv : converters)
			conv.setTypeSystem(ts);
	}

	@Override
	public TextRestAnnotation convertToAlveo(AnnotationFS ann) throws NotInitializedException, InvalidAnnotationTypeException {
		String typeName = ann.getType().getName();
		for (UIMAToAlveoAnnConverter conv : converters) {
			if (conv.handlesTypeName(typeName))
				return conv.convertToAlveo(ann);
		}
		throw new NoConverterMatchException("No configured converter matched");
	}

	@Override
	public String getAlveoTypeUriForTypeName(String uimaTypeName) {
		for (UIMAToAlveoAnnConverter conv : converters) {
			if (conv.handlesTypeName(uimaTypeName))
				return conv.getAlveoTypeUriForTypeName(uimaTypeName);
		}
		throw new NoConverterMatchException("No configured converter matched");
	}

	@Override
	public boolean handlesTypeName(String uimaTypeName) {
		for (UIMAToAlveoAnnConverter conv : converters) {
			if (conv.handlesTypeName(uimaTypeName))
				return true;
		}
		return false;
	}

	public class NoConverterMatchException extends RuntimeException {
		public NoConverterMatchException(String s) {
			super(s);
		}
	}

	public static FallingBackUIMAAlveoConverter withDefault(List<? extends UIMAToAlveoAnnConverter> converters) {
		return withDefault(converters, new DefaultUIMAToAlveoAnnConverter());
	}

	public static FallingBackUIMAAlveoConverter withDefault(List<? extends UIMAToAlveoAnnConverter> converters,
			String[] annTypeFeatureNames, String[] labelTypeFeatureNames) {
		return withDefault(converters, new DefaultUIMAToAlveoAnnConverter(annTypeFeatureNames, labelTypeFeatureNames));
	}

	protected static FallingBackUIMAAlveoConverter withDefault(List<? extends UIMAToAlveoAnnConverter> converters,
			DefaultUIMAToAlveoAnnConverter defaultConv) {
		List<UIMAToAlveoAnnConverter> componentConverters = new ArrayList<UIMAToAlveoAnnConverter>(converters.size() + 1);
		componentConverters.addAll(converters);
		componentConverters.add(defaultConv);
		return new FallingBackUIMAAlveoConverter(componentConverters);
	}
}
