package com.ontotext.trree.plugin.autocomplete;

import org.junit.Test;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests results for well known entities (static IRIs not stored in the repository) in three scenarios:
 * - any namespace + local name
 * - skos: + local name
 * - all in skos:
 *
 */
public class TestAutocompleteWellKnownEntities extends AutocompletePluginTestBase {
    @Parameterized.Parameters(name = "useAskControl = {0}")
    public static List<Object[]> getParams() {
        return AutocompletePluginTestBase.getParams();
    }

    public TestAutocompleteWellKnownEntities(boolean useAskControl) {
        super(useAskControl);
    }

    @Test
    public void testAnyBro() throws Exception {
        enablePlugin();
        // Any namespace "bro"
        List<String> anyResults = executeQueryAndGetResults(";bro");
        assertEquals(Arrays.asList(
                "http://usefulinc.com/ns/doap#browse; http://usefulinc.com/ns/doap#<b>bro</b>wse",
                "http://www.w3.org/2004/02/skos/core#broader; http://www.w3.org/2004/02/skos/core#<b>bro</b>ader",
                "http://www.w3.org/2004/02/skos/core#broadMatch; http://www.w3.org/2004/02/skos/core#<b>bro</b>adMatch",
                "http://www.w3.org/2004/02/skos/core#broaderTransitive; http://www.w3.org/2004/02/skos/core#<b>bro</b>aderTransitive"),
                anyResults);
    }

    @Test
    public void testSkosBro() throws Exception {
        enablePlugin();
        // skos: "bro"
        List<String> skosResults = executeQueryAndGetResults("http://www.w3.org/2004/02/skos/core#;bro");
        assertEquals(Arrays.asList(
                "http://www.w3.org/2004/02/skos/core#broader; http://www.w3.org/2004/02/skos/core#<b>bro</b>ader",
                "http://www.w3.org/2004/02/skos/core#broadMatch; http://www.w3.org/2004/02/skos/core#<b>bro</b>adMatch",
                "http://www.w3.org/2004/02/skos/core#broaderTransitive; http://www.w3.org/2004/02/skos/core#<b>bro</b>aderTransitive"),
                skosResults);
    }

    @Test
    public void testAllSkos() throws Exception {
        enablePlugin();
        // All within skos:
        List<String> skosResults = executeQueryAndGetResults("http://www.w3.org/2004/02/skos/core#;");
        assertEquals(32, skosResults.size());
        assertEquals(Arrays.asList(
                "http://www.w3.org/2004/02/skos/core#note; http://www.w3.org/2004/02/skos/core#note",
                "http://www.w3.org/2004/02/skos/core#member; http://www.w3.org/2004/02/skos/core#member",
                "http://www.w3.org/2004/02/skos/core#related; http://www.w3.org/2004/02/skos/core#related",
                "http://www.w3.org/2004/02/skos/core#example; http://www.w3.org/2004/02/skos/core#example",
                "http://www.w3.org/2004/02/skos/core#broader; http://www.w3.org/2004/02/skos/core#broader"),
                skosResults.subList(0, 5));
    }
}
