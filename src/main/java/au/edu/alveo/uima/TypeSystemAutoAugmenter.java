package au.edu.alveo.uima;

import au.edu.alveo.uima.conversions.UIMAAlveoTypeNameMapping;
import au.edu.alveo.client.RestClient;
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

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by amack on 27/03/14.
 * <p/>
 * Used to map between annotation type URIs from from HCSvLab Annotations to UIMA types
 */
public class TypeSystemAutoAugmenter {
	private static final Logger LOG = LoggerFactory.getLogger(TypeSystemAutoAugmenter.class);
	private RestClient restClient;
	private final Set<String> knownCorpora = new HashSet<String>(20);
	private Set<String> knownGeneratedTypeNames = new HashSet<String>(40);
	private Set<String> knownExistingTypeNames = new HashSet<String>(40);

	private final TypeSystemDescription tsd;

	public TypeSystemAutoAugmenter(RestClient rc) throws ResourceInitializationException {
		this(rc, TypeSystemDescriptionFactory.createTypeSystemDescription());
	}

	public TypeSystemAutoAugmenter(RestClient rc, TypeSystemDescription extTypeSystem) {
		restClient = rc;
		tsd = extTypeSystem;
		for (TypeDescription td : tsd.getTypes())
			knownExistingTypeNames.add(td.getName());
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
		String typeName;
		try {
			typeName = UIMAAlveoTypeNameMapping.getTypeNameForUri(sourceUri);
		} catch (URISyntaxException e) {
			LOG.error("Invalid type URI: {}", sourceUri);
			return;
		}
		TypeDescription td;
		if (knownExistingTypeNames.contains(typeName)) { // found a match in the existing URIs - assume it's the same class
			td = tsd.getType(typeName);
		} else {
			if (knownGeneratedTypeNames.contains(typeName)) { // duplicate type names generated.
			// hopefully this doesn't happen and all URLs map to unique types
			// XXX - should demand that callers provide explicit mappings for duplicates this method creates
				LOG.error("Found duplicate type names - URI {} maps to {} which already existed; " +
								"these types may be substituted for one another",
						sourceUri, typeName);
			}
			td = tsd.addType(typeName,
					String.format("Automatically-generated type for URI %s", sourceUri),
					"au.edu.alveo.uima.types.GeneratedItemAnnotation");
		}
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
			uris.add(typeUri);
		}
		return uris;
	}
}
