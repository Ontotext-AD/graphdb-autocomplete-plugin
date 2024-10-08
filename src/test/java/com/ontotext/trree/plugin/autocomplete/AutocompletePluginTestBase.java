package com.ontotext.trree.plugin.autocomplete;
import com.ontotext.graphdb.Config;
import com.ontotext.test.TemporaryLocalFolder;
import com.ontotext.test.functional.base.SingleRepositoryFunctionalTest;
import com.ontotext.test.utils.StandardUtils;
import org.eclipse.rdf4j.common.io.IOUtil;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Triple;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.*;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.config.RepositoryConfig;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.helpers.NTriplesUtil;
import org.junit.*;
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
	private static final Logger LOG = LoggerFactory.getLogger(AutocompletePluginTestBase.class);
	private static final String AUTOCOMPLETE_QUERY_START = "SELECT ?s ?g WHERE { GRAPH ?g { ?s <http://www.ontotext.com/plugins/autocomplete#query> \"";
	private static final String AUTOCOMPLETE_QUERY_UNBOUNDED_OBJECT_EMPTY = "SELECT * WHERE { ?entity <http://www.ontotext.com/plugins/autocomplete#query> ?query . }";
	private static final String AUTOCOMPLETE_QUERY_UNBOUNDED_OBJECT_START = "SELECT ?s ?g WHERE { GRAPH ?g { VALUES ?q {\"";
	private static final String AUTOCOMPLETE_QUERY_UNBOUNDED_OBJECT_END = "\"} ?s <http://www.ontotext.com/plugins/autocomplete#query> ?q } }";
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
	@ClassRule
	public static TemporaryLocalFolder tmpFolder = new TemporaryLocalFolder();
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
	@BeforeClass
	public static void setWorkDir() {
		System.setProperty("graphdb.home.work", String.valueOf(tmpFolder.getRoot()));
		Config.reset();
	}
	@AfterClass
	public static void resetWorkDir() {
		System.clearProperty("graphdb.home.work");
		Config.reset();
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
					foundSubjects.add(asNTripleString(s.getValue()) + "; " + asNTripleString(g.getValue()));
				} else {
					foundSubjects.add(asNTripleString(s.getValue()));
				}
			}
		} finally {
			result.close();
		}
		return foundSubjects;
	}
	private String asNTripleString(Value r) {
		if (r instanceof Triple) {
			return NTriplesUtil.toNTriplesString(r);
		}
		return r.stringValue();
	}
	void executeQueryAndVerifyResults(String pluginQuery, int expected) throws RepositoryException, MalformedQueryException, QueryEvaluationException {
		String sparqlQuery = AUTOCOMPLETE_QUERY_START + pluginQuery + "\" . } }";
		TupleQuery tq = connection.prepareTupleQuery(QueryLanguage.SPARQL, sparqlQuery);
		List<String> foundSubjects = getFoundSubjects(tq.evaluate());
		assertEquals(expected, foundSubjects.size());
	}
	void executeQueryWithUnboundedObjectAndVerifyResults(String pluginQuery, int expected) throws RepositoryException, MalformedQueryException, QueryEvaluationException {
		String copyQuery = AUTOCOMPLETE_QUERY_UNBOUNDED_OBJECT_START + pluginQuery + AUTOCOMPLETE_QUERY_UNBOUNDED_OBJECT_END;
		TupleQuery tq = connection.prepareTupleQuery(QueryLanguage.SPARQL, copyQuery);
		List<String> foundSubjects = getFoundSubjects(tq.evaluate());
		assertEquals(expected, foundSubjects.size());
	}
	void executeQueryWithUnboundedObjectAndVerifyNoExceptionIsThrown() throws RepositoryException, MalformedQueryException, QueryEvaluationException  {
		TupleQuery tq = connection.prepareTupleQuery(QueryLanguage.SPARQL, AUTOCOMPLETE_QUERY_UNBOUNDED_OBJECT_EMPTY);
		List<String> foundSubjects = getFoundSubjects(tq.evaluate());
		// must always return empty due to the StatementIterator.FALSE
		assertEquals(0, foundSubjects.size());
	}
	List<String> executeQueryAndGetResults(String pluginQuery) throws RepositoryException, MalformedQueryException, QueryEvaluationException {
		String sparqlQuery = AUTOCOMPLETE_QUERY_START + pluginQuery + "\" . } }";
		TupleQuery tq = connection.prepareTupleQuery(QueryLanguage.SPARQL, sparqlQuery);
		return getFoundSubjects(tq.evaluate());
	}
	List<String> executeCustomQueryAndGetResults(String sparqlQueryStart, String sparqlQueryEnd, String pluginQuery)
			throws RepositoryException, MalformedQueryException, QueryEvaluationException {
		String finalQuery = sparqlQueryStart + pluginQuery + sparqlQueryEnd;
		TupleQuery tq = connection.prepareTupleQuery(QueryLanguage.SPARQL, finalQuery);
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
		getRepository().init();
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
