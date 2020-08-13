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

            "<<<http://test/a> <http://test/b> <http://test/c>>>; label for <b>sim</b>ple triple &lt;&lt;&lt;http://test/a&gt; &lt;http://test/b&gt; &lt;http://test/c&gt;&gt;&gt;"
    );

    private static final List<String> EXPECTED__RECURSIVE_SUGGESTIONS = Arrays.asList(

            "<<<http://test/foo> <http://www.w3.org/2000/01/rdf-schema#label> \"moo most inner\">>; label for <b>recur</b>sive nested triple &lt;&lt;&lt;http://test/foo&gt; &lt;http://www.w3.org/2000/01/rdf-schema#label&gt; &quot;moo most inner&quot;&gt;&gt;"
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
        importData("src/test/resources/import/rdf-star.ttls", RDFFormat.TURTLESTAR);
        enablePlugin();
        testFindIRIsByLabels();
    }


    public void testFindIRIsByLabels() throws RepositoryException, MalformedQueryException, QueryEvaluationException {
        List<String> results = executeQueryAndGetResults(";sim");
        assertEquals(EXPECTED_SUGGESTIONS, results);
        results = executeQueryAndGetResults(";recur");
        assertEquals(EXPECTED__RECURSIVE_SUGGESTIONS, results);
    }
}
