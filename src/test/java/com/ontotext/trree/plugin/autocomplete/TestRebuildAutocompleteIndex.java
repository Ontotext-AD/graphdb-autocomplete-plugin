package com.ontotext.trree.plugin.autocomplete;

import org.junit.Before;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.util.List;

/**
 * Created by desislava on 14.11.16.
 */
public class TestRebuildAutocompleteIndex extends AutocompletePluginTestBase {
    @Parameterized.Parameters(name = "useAskControl = {0}")
    public static List<Object[]> getParams() {
        return AutocompletePluginTestBase.getParams();
    }

    public TestRebuildAutocompleteIndex(boolean useAskControl) {
        super(useAskControl);
    }

    @Before
    public void enableBeforePlugin() throws Exception {
        enablePlugin();
        connection.add(vf.createIRI("s:1"), vf.createIRI("p:1"), vf.createIRI("a:abcde"));
        connection.add(vf.createIRI("s:1"), vf.createIRI("p:1"), vf.createIRI("a:xyz"));
    }

    @Test
    public void testReindex() throws Exception {
        reindex();
        executeQueryAndVerifyResults("a:;abc", 1);
    }

    @Test
    public void testClearAllAndReindex() throws Exception {
        // Execute clear all
        connection.begin();
        connection.prepareUpdate("clear all").execute();
        connection.commit();
        // Reindex autocomplete
        reindex();
        // Verify that previously inserted data isn't in plugin
        executeQueryAndVerifyResults("a:;abc", 0);
        // Add new data into repository
        connection.add(vf.createIRI("s:1"), vf.createIRI("p:1"), vf.createIRI("a:fjhijk"));
        connection.add(vf.createIRI("s:1"), vf.createIRI("p:1"), vf.createIRI("a:lmnop"));
        // Verify again that previously inserted data isn't in plugin
        executeQueryAndVerifyResults("a:;abc", 0);
        // Verify that newly inserted data is in plugin
        executeQueryAndVerifyResults("a:;fjh", 1);
        executeQueryAndVerifyResults("a:;lmn", 1);
    }

}
