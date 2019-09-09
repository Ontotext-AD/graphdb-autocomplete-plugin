package com.ontotext.trree.plugin.autocomplete;

import org.eclipse.rdf4j.OpenRDFException;
import org.eclipse.rdf4j.query.MalformedQueryException;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.util.List;

/**
 * Created by desislava on 11/11/15.
 */
public class TestAutocompleteQueries extends AutocompletePluginTestBase {
    @Parameterized.Parameters(name = "useAskControl = {0}")
    public static List<Object[]> getParams() {
        return AutocompletePluginTestBase.getParams();
    }

    public TestAutocompleteQueries(boolean useAskControl) {
        super(useAskControl);
    }

    @Before
    public void enableBeforePlugin() throws Exception {
        enablePlugin();
    }

    @Test
    public void testCreateAutocompleteIndex() throws OpenRDFException {
        executeQueryAndVerifyResults("Sam", 1);
    }

    @Test
    public void shouldAutocompleteLocalNameWithoutPrefix() throws RepositoryException, MalformedQueryException, QueryEvaluationException {
        connection.add(vf.createIRI("s:1"), vf.createIRI("p:1"), vf.createIRI("a:abcde"));
        connection.add(vf.createIRI("s:1"), vf.createIRI("p:2"), vf.createIRI("b:abcab"));
        executeQueryAndVerifyResults("ab", 2);

    }

    @Test
    public void shouldAutocompleteLocalNameWithPrefix() throws OpenRDFException {
        connection.add(vf.createIRI("prefix:hijk"), vf.createIRI("prefix:2"), vf.createIRI("http://ontotext.com/ffff"));
        // Unfortunately setting offsets in LocalNameTokenizer breaks the one char autocomplete
        executeQueryAndVerifyResults("prefix:;h" , 1);
        executeQueryAndVerifyResults("prefix:;hi" , 1);
        executeQueryAndVerifyResults("http://ontotext.com/;ff", 1);
        executeQueryAndVerifyResults("prefix:;", 2);
    }

    @Test
    public void shouldAutocompleteWhenLocalNameCamelCase() throws RepositoryException, MalformedQueryException, QueryEvaluationException {
        connection.add(vf.createIRI("wine:WhiteBurgundy"), vf.createIRI("wine:madeFromGrape"), vf.createIRI("wine:ChardonnayGrape"));
        executeQueryAndVerifyResults("wine:;Gr", 2);
    }

    @Test
    public void shouldAutocompleteUpperCaseQuery() throws RepositoryException, MalformedQueryException, QueryEvaluationException {
        connection.add(vf.createIRI("wine:SomeThing"), vf.createIRI("wine:OtherThing"), vf.createIRI("wine:boo"));
        executeQueryAndVerifyResults("wine:;TH", 2);
    }

    @Test
    public void shouldAutocompleteTwoWords() throws RepositoryException, MalformedQueryException, QueryEvaluationException {
        connection.add(vf.createIRI("wine:hasMoo"), vf.createIRI("wine:CorbansPrivateBinSauvignonBlanc"), vf.createIRI("wine:Moo"));
        executeQueryAndVerifyResults("wine:;hasMo", 1);
    }

    @Test
    public void shouldAutocompleteWhenLocalNameWithUnderscore() throws RepositoryException, MalformedQueryException, QueryEvaluationException {
        connection.add(vf.createIRI("wine1:white_burgundy"), vf.createIRI("wine1:made_from_drape"), vf.createIRI("wine1:chardonnay_drape"));
        executeQueryAndVerifyResults("wine1:;dr", 2);
    }

    @Test
    public void shouldAutocompleteWhenLocalNameNumber() throws RepositoryException, MalformedQueryException, QueryEvaluationException {
        connection.add(vf.createIRI("wine1:SaucelitoCanyonZinfandel1998"), vf.createIRI("wine1:madeFromGrape"), vf.createIRI("wine1:ChardonnayGrape"));
        executeQueryAndVerifyResults("wine1:;19", 1);
    }

    @Test
    public void shouldAutocompleteWhenLocalNameUpperCase() throws RepositoryException, MalformedQueryException, QueryEvaluationException {
        connection.add(vf.createIRI("wine1:WhiteBURgundy"), vf.createIRI("wine1:madeFromGrape"), vf.createIRI("wine1:ChardonnayGrape"));
        executeQueryAndVerifyResults("wine1:;bu", 1);
        executeQueryAndVerifyResults("wine1:;rg", 1);
    }

    @Test
    public void shouldNotAutocompleteSpecialURIs() throws RepositoryException, MalformedQueryException, QueryEvaluationException {
        executeQueryAndVerifyResults("http://www.ontotext.com/;", 0);
    }
}
