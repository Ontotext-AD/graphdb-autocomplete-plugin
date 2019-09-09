package com.ontotext.trree.plugin.autocomplete;

import com.ontotext.test.functional.base.SingleRepositoryFunctionalTest;
import com.ontotext.test.utils.StandardUtils;
import org.eclipse.rdf4j.common.io.IOUtil;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.*;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.config.RepositoryConfig;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public abstract class AutocompletePluginTestBase extends SingleRepositoryFunctionalTest {
	static {
		System.setProperty("graphdb.license.file", System.getProperty("user.dir") + "/graphdb.license");
	}

	private static final Logger LOG = LoggerFactory.getLogger(AutocompletePluginTestBase.class);

	private static final String AUTOCOMPLETE_QUERY_START = "SELECT ?s ?g WHERE { GRAPH ?g { ?s <http://www.ontotext.com/plugins/autocomplete#query> \"";
	private static final String GET_INDEX_STATUS = "SELECT ?s WHERE { ?o <http://www.ontotext.com/plugins/autocomplete#status> ?s . }";
	private static final String IS_PLUGIN_ENABLED = "ASK WHERE { ?o <http://www.ontotext.com/plugins/autocomplete#enabled> ?s . }";
	private static final String SHOULD_INDEX_IRIS = "ASK WHERE { ?o <http://www.ontotext.com/plugins/autocomplete#indexIRIs> ?s . }";

	private static final String SET_SHOULD_INDEX_IRIS_INSERT = "INSERT DATA { _:s <http://www.ontotext.com/plugins/autocomplete#indexIRIs> \"%s\" . }";
	private static final String SET_SHOULD_INDEX_IRIS_ASK = "ASK { GRAPH <http://www.ontotext.com/plugins/autocomplete#control> { _:s <http://www.ontotext.com/plugins/autocomplete#indexIRIs> \"%s\" . } }";

	private static final String SET_ENABLE_INSERT = "INSERT DATA { _:s <http://www.ontotext.com/plugins/autocomplete#enabled> \"%s\" . }";
	private static final String SET_ENABLE_ASK = "ASK { GRAPH <http://www.ontotext.com/plugins/autocomplete#control> { _:s <http://www.ontotext.com/plugins/autocomplete#enabled> \"%s\" . } }";

	private static final String SET_REINDEX_INSERT = "INSERT DATA { _:s <http://www.ontotext.com/plugins/autocomplete#reIndex> true . }";
	private static final String SET_REINDEX_ASK = "ASK { GRAPH <http://www.ontotext.com/plugins/autocomplete#control> { _:s <http://www.ontotext.com/plugins/autocomplete#reIndex> true . } }";

	private static final String ADD_LABEL_CONFIG_INSERT = "INSERT DATA { <%s> <http://www.ontotext.com/plugins/autocomplete#addLabelConfig> \"%s\" }";
	private static final String ADD_LABEL_CONFIG_ASK = "ASK { GRAPH <http://www.ontotext.com/plugins/autocomplete#control> { <%s> <http://www.ontotext.com/plugins/autocomplete#addLabelConfig> \"%s\" } }";

	private static final String REMOVE_LABEL_CONFIG_INSERT = "INSERT DATA { <%s> <http://www.ontotext.com/plugins/autocomplete#removeLabelConfig> \"\" }";
	private static final String REMOVE_LABEL_CONFIG_ASK = "ASK { GRAPH <http://www.ontotext.com/plugins/autocomplete#control> { <%s> <http://www.ontotext.com/plugins/autocomplete#removeLabelConfig> \"\" } }";

	@Parameterized.Parameters
	static List<Object[]> getParams() {
		return Arrays.asList(new Object[][] { {false}, {true} });
	}

	RepositoryConnection connection;
	private boolean useAskControl;

	AutocompletePluginTestBase(boolean useAskControl) {
		this.useAskControl = useAskControl;
	}

	@Override
	protected RepositoryConfig createRepositoryConfiguration() {
		// Test with the transactional entity pool as this is much likelier to discover potential issues
		System.setProperty("graphdb.engine.entity-pool-implementation", "transactional");
		return StandardUtils.createOwlimSe("owl-horst-optimized");
	}

	@Before
	public void setupConn() throws RepositoryException {
		connection = getRepository().getConnection();
	}

	@After
	public void closeConn() throws RepositoryException {
		connection.close();
	}

	private TupleQueryResult executeSparqlQuery(String query) throws Exception {
		return connection
				.prepareTupleQuery(QueryLanguage.SPARQL, query)
				.evaluate();
	}

    protected TupleQueryResult executeSparqlQueryFromFile(String fileName) throws Exception {
        String query = IOUtil.readString(
                getClass().getResourceAsStream("/" + fileName + ".sparql"));

        return executeSparqlQuery(query);
    }

	void enablePlugin() throws Exception {
		setEnablePlugin(true);
		while (!getPluginStatus().startsWith(IndexStatus.READY.toString())) {
			Thread.sleep(1000L);
		}
	}

	void setEnablePlugin(boolean enablePlugin) throws Exception {
		connection.begin();
		if (useAskControl) {
			connection.prepareBooleanQuery(String.format(SET_ENABLE_ASK, enablePlugin)).evaluate();
		} else {
			connection.prepareUpdate(String.format(SET_ENABLE_INSERT, enablePlugin)).execute();
		}
		connection.commit();
	}

	void setShouldIndexIris(boolean shouldIndexIris) throws Exception {
		connection.begin();
		if (useAskControl) {
			connection.prepareBooleanQuery(String.format(SET_SHOULD_INDEX_IRIS_ASK, shouldIndexIris)).evaluate();
		} else {
			connection.prepareUpdate(String.format(SET_SHOULD_INDEX_IRIS_INSERT, shouldIndexIris)).execute();
		}
		connection.commit();
	}

	void disablePlugin() throws Exception {
		setEnablePlugin(false);
	}

	boolean isPluginEnabled() {
		return connection.prepareBooleanQuery(IS_PLUGIN_ENABLED).evaluate();
	}

	boolean shouldIndexIRIs() {
		return connection.prepareBooleanQuery(SHOULD_INDEX_IRIS).evaluate();
	}

	void reindex() throws Exception {
		connection.begin();
		if (useAskControl) {
			connection.prepareBooleanQuery(SET_REINDEX_ASK).evaluate();
		} else {
			connection.prepareUpdate(SET_REINDEX_INSERT).execute();
		}
		connection.commit();
		while (!getPluginStatus().startsWith(IndexStatus.READY.toString())) {
			Thread.sleep(1000L);
		}
	}

	String getPluginStatus() throws MalformedQueryException, RepositoryException, QueryEvaluationException {
		TupleQuery tq = connection.prepareTupleQuery(QueryLanguage.SPARQL, GET_INDEX_STATUS);
		return getFoundSubjects(tq.evaluate()).get(0);
	}

	private List<String> getFoundSubjects(TupleQueryResult result) throws QueryEvaluationException {
		List<String> foundSubjects = new LinkedList<>();
		try {
			while (result.hasNext()) {
				BindingSet next = result.next();
				Binding s = next.getBinding("s");
				Binding g = next.getBinding("g");
				if (g != null) {
					foundSubjects.add(s.getValue().stringValue() + "; " + g.getValue().stringValue());
				} else {
					foundSubjects.add(s.getValue().stringValue());
				}
			}
		} finally {
			result.close();
		}
		return foundSubjects;
	}

	/**
	 * Gets all projected values of the query as key-value pairs (binding-collection of its values)
	 *
	 * @param tqr {@link TupleQueryResult} instance which contains a list of all projected values
	 * @return query results a map of (binding-collection of its values)
	 */
	public Map<String, List<String>> getResultsForAllQueryBindings(TupleQueryResult tqr) {
		Map<String, List<String>> resultMap = new HashMap<>();

		String currentBinding = null;
		try {
			List<String> bindings = tqr.getBindingNames();
			for (String binding : bindings) {
				resultMap.put(binding, new ArrayList<>());
			}

			while (tqr.hasNext()) {
				BindingSet bs = tqr.next();

				for (Map.Entry<String, List<String>> entry : resultMap.entrySet()) {
					currentBinding = entry.getKey();
					List<String> values = entry.getValue();
					Value value = bs.getValue(currentBinding);
					if (value == null) continue;
					values.add(value.stringValue());
				}
			}
		} catch (QueryEvaluationException e) {
			LOG.error("Failed to get results for binding ?{}. Query results corrupted?!", currentBinding);
		} finally {
			closeQueryResult(tqr);
		}
		return resultMap;
	}

	private void closeQueryResult(TupleQueryResult tqr) {
		try {
			tqr.close();
		} catch (QueryEvaluationException e) {
			LOG.error("Error closing tuple query result!");
		}
	}

	void executeQueryAndVerifyResults(String pluginQuery, int expected) throws RepositoryException, MalformedQueryException, QueryEvaluationException {
		String sparqlQuery = AUTOCOMPLETE_QUERY_START + pluginQuery + "\" . } }";
		TupleQuery tq = connection.prepareTupleQuery(QueryLanguage.SPARQL, sparqlQuery);
		List<String> foundSubjects = getFoundSubjects(tq.evaluate());
		assertEquals(expected, foundSubjects.size());
	}

	List<String> executeQueryAndGetResults(String pluginQuery) throws RepositoryException, MalformedQueryException, QueryEvaluationException {
		String sparqlQuery = AUTOCOMPLETE_QUERY_START + pluginQuery + "\" . } }";
		TupleQuery tq = connection.prepareTupleQuery(QueryLanguage.SPARQL, sparqlQuery);
		return getFoundSubjects(tq.evaluate());
	}

	void importData(String fileName, RDFFormat format) throws RepositoryException, IOException, RDFParseException {
		connection.begin();
		connection.add(new File(fileName), "urn:base", format);
		connection.commit();
	}

	void addLanguageConfig(IRI labelPredicate, String languages) {
		connection.begin();
		if (useAskControl) {
			connection.prepareBooleanQuery(String.format(ADD_LABEL_CONFIG_ASK, labelPredicate.stringValue(), languages)).evaluate();
		} else {
			connection.prepareUpdate(String.format(ADD_LABEL_CONFIG_INSERT, labelPredicate.stringValue(), languages)).execute();
		}
		connection.commit();
	}

	void removeLanguageConfig(IRI labelPredicate) {
		connection.begin();
		if (useAskControl) {
			connection.prepareBooleanQuery(String.format(REMOVE_LABEL_CONFIG_ASK, labelPredicate.stringValue())).evaluate();
		} else {
			connection.prepareUpdate(String.format(REMOVE_LABEL_CONFIG_INSERT, labelPredicate.stringValue())).execute();
		}
		connection.commit();
	}

	Map<IRI, String> listLanguageConfigs() {
		Map<IRI, String> result = new HashMap<>();
		TupleQuery tupleQuery = connection.prepareTupleQuery("select ?iri ?language { ?iri <http://www.ontotext.com/plugins/autocomplete#labelConfig> ?language }");
		try (TupleQueryResult tqr = tupleQuery.evaluate()) {
			while (tqr.hasNext()) {
				BindingSet bs = tqr.next();
				result.put((IRI) bs.getBinding("iri").getValue(), bs.getBinding("language").getValue().stringValue());
			}
		}

		return result;
	}

	void restartRepository() {
		connection.close();
		getRepository().shutDown();
		getRepository().initialize();
		connection = getRepository().getConnection();
	}

	protected void waitForRankStatus(String status) {
		int counter = 20;
		String currentStatus = "";
		while (counter-- > 0) {
			try (TupleQueryResult tqr = connection.prepareTupleQuery("SELECT ?o WHERE {_:b <http://www.ontotext.com/owlim/RDFRank#status> ?o}").evaluate()) {
				if (tqr.hasNext()) {
					currentStatus = tqr.next().getBinding("o").getValue().stringValue();
				}
			}

			if (currentStatus.equals(status) || currentStatus.startsWith(status)) {
				break;
			}

			try {
				Thread.sleep(300);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		assertTrue("Plugin status", currentStatus.startsWith(status));

	}
}
