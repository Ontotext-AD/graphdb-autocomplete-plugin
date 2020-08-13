package com.ontotext.trree.plugin.autocomplete;

import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests basic indexing/removal of labels.
 */
public class TestAutocompleteLabels extends AutocompletePluginTestBase {
    // "This" intentionally misspelled as "Dhis" to avoid interference from well known entities
    private static final List<String> EXPECTED_SUGGESTIONS = Arrays.asList(
            "urn:beta; <b>Dhis</b> is beta! &lt;urn:beta&gt;",
            "urn:gamma; <b>Dhis</b> is gamma! &lt;urn:gamma&gt;",
            "urn:alpha; <b>Dhis</b> is alpha! &lt;urn:alpha&gt;"
            );
    @Parameterized.Parameters(name = "useAskControl = {0}")
    public static List<Object[]> getParams() {
        return AutocompletePluginTestBase.getParams();
    }

    public TestAutocompleteLabels(boolean useAskControl) {
        super(useAskControl);
    }

    @Test
    public void loadThenIndex() throws Exception {
        insertSomeData();
        enablePlugin();
        testValidSuggestions();
    }

    @Test
    public void indexThenLoad() throws Exception {
        enablePlugin();
        insertSomeData();
        testValidSuggestions();
    }

    public void testValidSuggestions() throws Exception {
        List<String> results = executeQueryAndGetResults(";dhis");
        assertEquals(3, results.size());
        assertEquals(EXPECTED_SUGGESTIONS, results);

        results = executeQueryAndGetResults(";dhis is");
        assertEquals(3, results.size());

        results = executeQueryAndGetResults(";dhis    is");
        assertEquals(3, results.size());

        removeSomeData();

        List<String> results2 = executeQueryAndGetResults(";dhis");
        System.out.println(String.join("\n", results2));
        assertEquals(2, results2.size());

        addLanguageConfig(vf.createIRI("urn:label"), "en");
        reindex();
        insertMoreData();

        List<String> results3 = executeQueryAndGetResults(";dhis");
        System.out.println(String.join("\n", results3));
        assertEquals(3, results3.size());
    }

    private void insertSomeData() {
        connection.begin();
        connection.add(vf.createIRI("urn:alpha"), RDFS.LABEL, vf.createLiteral("Dhis is alpha!"));
        connection.add(vf.createIRI("urn:beta"), RDFS.LABEL, vf.createLiteral("Dhis is beta!"));
        connection.add(vf.createIRI("urn:gamma"), RDFS.LABEL, vf.createLiteral("Dhis is gamma!"));
        connection.commit();
    }

    private void insertMoreData() {
        connection.begin();
        connection.add(vf.createIRI("urn:delta"), vf.createIRI("urn:label"), vf.createLiteral("Dhis is delta with alt label!", "en"));
        connection.commit();
    }

    private void removeSomeData() {
        connection.begin();
        connection.remove(vf.createIRI("urn:beta"), RDFS.LABEL, null);
        connection.commit();
    }
}
