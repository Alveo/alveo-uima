package com.nicta.uimavlab;

import com.nicta.vlabclient.RestClient;
import com.nicta.vlabclient.util.TypeUriFixer;
import org.apache.uima.fit.factory.TypeSystemDescriptionFactory;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeDescription;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.openrdf.OpenRDFException;
import org.openrdf.query.BindingSet;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.sparql.SPARQLRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by amack on 27/03/14.
 * <p/>
 * Used to map between annotation type URIs from from HCSvLab Annotations to UIMA types
 */
public class TypeSystemAutoGenerator {
	private static final Logger LOG = LoggerFactory.getLogger(TypeSystemAutoGenerator.class);
	private RestClient restClient;
	private final Set<String> knownCorpora = new HashSet<String>(20);
	private Set<String> knownTypeNames = new HashSet<String>(40);

	private final TypeSystemDescription tsd;

	public TypeSystemAutoGenerator(RestClient rc) throws ResourceInitializationException {
		restClient = rc;
		tsd = TypeSystemDescriptionFactory.createTypeSystemDescription();
	}


	public void addCorpus(String corpusName) throws URISyntaxException,
			OpenRDFException {
		/** Read the types corresponding to the corpus (aka 'collection', but this already
		 * has meaning in Java, so we use 'corpus' for clarity. No-op if the corpus has already
		 * been added.
		 */
		if (knownCorpora.contains(corpusName))
			return;
		importTypesForCorpus(corpusName);
		knownCorpora.add(corpusName);
	}

	public TypeSystemDescription getTypeSystemDescription() {
		return tsd;
	}

	private void insertType(String sourceUri) throws URISyntaxException {
		String possTypeName = rawTypeNameForURI(sourceUri);
		String actualTypeName;
		if (knownTypeNames.contains(possTypeName)) {
			while (true) {
				int idx = 2;
				actualTypeName = String.format("%s_%02d", possTypeName, idx);
				if (!knownTypeNames.contains(possTypeName))
					break;
				idx++;
			}
		} else {
			actualTypeName = possTypeName;
		}
		TypeDescription td = tsd.addType(actualTypeName,
				String.format("Automatically-generated type for URI %s", sourceUri),
				"com.nicta.uimavlab.types.GeneratedItemAnnotation");
		try {
			td.setSourceUrl(new URL(sourceUri)); // not TOO much of hack
			  // - this is supposed to be the URL the item was parsed from
			  // so it's sort of valid to put the URI in here
			  // otherwise we're going to have to mess around with subclassing
			  // TypeDescription to put in a custom field
		} catch (MalformedURLException e) {
			throw new URISyntaxException("Error treating URI as URL: ", e.toString());
		}
//		urisToTypeNames.put(sourceUri, actualTypeName);
	}

	private void importTypesForCorpus(String corpusName) throws URISyntaxException,
			QueryEvaluationException, MalformedQueryException, RepositoryException {
		try {
			for (String typeUri : getTypeURIsForCorpus(corpusName))
				insertType(typeUri);
		} catch (QueryEvaluationException e) {
			Throwable cause = e.getCause();
			// if it's just an authorization problem, that's probably
			// because the corpus name is invalid.
			// this could be because we're hardcoding the corpus names
			// due to https://track.intersect.org.au/browse/HCSVLAB-868
			// XXX: once fixed, take out this catch clause
			boolean isRepoException = cause instanceof RepositoryException;
			boolean isAuth = isRepoException && cause.getMessage().contains("not authorized");
			boolean isMissing = isRepoException && cause.getMessage().contains("no such resource");
			if (isMissing)
				LOG.error("Collection {} was not found", corpusName);
			else if (isAuth)
				LOG.error("Insufficient priveleges for collection {}", corpusName);
			else
				throw e;
		}
	}

	private static String rawTypeNameForURI(String typeURI) throws URISyntaxException {
		URI uri = new URI(typeURI);
		Stack<String> packageComps = new Stack<String>();
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
		Matcher m = Pattern.compile("(?:^|\\W+)(\\w)?").matcher(source);
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

	private Collection<String> getTypeURIsForCorpus(String corpusName) throws QueryEvaluationException, MalformedQueryException, RepositoryException {
		SPARQLRepository repo = restClient.getSPARQLRepository(corpusName);
		String sparql = "SELECT DISTINCT ?type WHERE { ?ann <http://purl.org/dada/schema/0.2#type> ?type }";
		RepositoryConnection conn = repo.getConnection();
		TupleQuery query = conn.prepareTupleQuery(QueryLanguage.SPARQL, sparql);
		TupleQueryResult result = query.evaluate();
		result.getBindingNames();
		ArrayList<String> uris = new ArrayList<String>();
		while (result.hasNext()) {
			BindingSet bs = result.next();
			String typeUri = bs.getValue("type").stringValue();
			typeUri = TypeUriFixer.convertToUriIfNeeded(typeUri); // XXX: temp workaround
			uris.add(typeUri);
		}
		return uris;
	}
}
