package com.ontotext.trree.plugin.autocomplete;

import org.eclipse.rdf4j.query.MalformedQueryException;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TestAutocompleteRDFStar extends AutocompletePluginTestBase {

    private static final List<String> EXPECTED_SUGGESTIONS = Arrays.asList(
            "http://test/rABC; label for simple and <b>testa</b>ble triple &lt;http://test/rABC&gt;"
    );

    private static final List<String> EXPECTED__RECURSIVE_SUGGESTIONS = Arrays.asList(
            "http://test/rFoo; label for <b>recur</b>sive nested triple &lt;http://test/rFoo&gt;"
    );


    @Parameterized.Parameters(name = "useAskControl = {0}")
    public static List<Object[]> getParams() {
        return AutocompletePluginTestBase.getParams();
    }

    public TestAutocompleteRDFStar(boolean useAskControl) {
        super(useAskControl);
    }

    @Test
    public void loadThenIndex() throws Exception {
        importData("src/test/resources/import/rdf-star.ttls", RDFFormat.TURTLE);
        enablePlugin();
        testFindIRIsByLabels();
    }


    public void testFindIRIsByLabels() throws RepositoryException, MalformedQueryException, QueryEvaluationException {
        List<String> results = executeQueryAndGetResults(";testa");
        assertEquals(EXPECTED_SUGGESTIONS, results);
        results = executeQueryAndGetResults(";recur");
        assertEquals(EXPECTED__RECURSIVE_SUGGESTIONS, results);
    }
}
