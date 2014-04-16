package au.edu.alveo.uima.conversions;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by amack on 11/04/14.
 */
public class UIMAAlveoTypeNameMapping {
	public static String getTypeNameForUri(String typeURI) throws URISyntaxException {
		URI uri = new URI(typeURI);
		Stack<String> packageComps = new Stack<String>();
		if (uri.getHost() == null)
			throw new URISyntaxException(typeURI, "URI has no hostname component");
		String[] origHostComps = uri.getHost().split("\\.");
		// need to go from small-endian domain to big-endian
		// like a java package name
 		for (int i = origHostComps.length - 1; i >= 0; i--)
			packageComps.add(sanitizeLower(origHostComps[i])); // get rid of eg '-'
		for (String pathComp : uri.getPath().split("/")) {
			if (!pathComp.isEmpty())
				packageComps.add(pathComp);
		}
		if (uri.getFragment() != null)
			packageComps.add(uri.getFragment());
		String typeName = sanitizeUpperCamel(packageComps.pop()); // last element is the name eg SpeakerAnnotation
		StringBuffer fqTypeName = new StringBuffer();
		for (String pc : packageComps) {
			fqTypeName.append(sanitizeLower(pc));
			fqTypeName.append(".");
		}
		fqTypeName.append(typeName);
		return fqTypeName.toString();
	}

	private static String sanitizeLower(String source) {
		/** Convert from a semi-arbitrary source string to a
		 * string suitable for use in a Java/UIMA-friendly
		 * fully qualified type name
		 */
		String charsStripped = source.replaceAll("\\W+", "");
		return prefixNumerals(charsStripped);
	}

	private static String sanitizeUpperCamel(String source) {
		/** Convert from a semi-arbitrary source string to a
		 * Java/UIMA-friendly type name, camel-cased
		 * like a conventional class by capitalizing
		 * letters which occur after non-alphanumeric ones
		 */
		StringBuffer camelCased = new StringBuffer();
		Matcher m = Pattern.compile("(?:^|\\W+)(\\w)?").matcher(source); // separator should match underscores too
		while (m.find())
			m.appendReplacement(camelCased, m.group(1).toUpperCase());
		m.appendTail(camelCased);
		return prefixNumerals(camelCased.toString());
	}

	private static String prefixNumerals(String s) {
		if (s.length() > 0 && Character.isDigit(s.codePointAt(0)))
			return "N" + s;
		else
			return s;
	}

	/** convert a java-style type name to a URI by
	 * 	splitting on '.', reversing the order of all components but the last
	 * 	and putting the last on the end after a '/'. This will be syntactically
	 * 	valid even if the semantics is not ideal
	 */
	public static String getUriForTypeName(String name) {
		String[] comps = name.split("\\.");
		StringBuilder sb = new StringBuilder("http://");
		sb.append(comps[comps.length - 2]);
		for (int i = comps.length - 3; i >= 0; i--) {
			sb.append(".");
			sb.append(comps[i]);
		}
		sb.append("/");
		sb.append(comps[comps.length - 1]);
		return sb.toString();
	}
}
