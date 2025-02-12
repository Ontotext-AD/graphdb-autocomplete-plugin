package com.ontotext.trree.plugin.autocomplete;

import org.eclipse.rdf4j.common.exception.RDF4JException;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.query.MalformedQueryException;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.util.List;
import static org.junit.Assert.assertEquals;

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
    public void testCreateAutocompleteIndex() throws RDF4JException {
        executeQueryAndVerifyResults("Same", 1);
    }

    @Test
    public void shouldAutocompleteLocalNameWithoutPrefix() throws RepositoryException, MalformedQueryException, QueryEvaluationException {
        connection.add(vf.createIRI("s:1"), vf.createIRI("p:1"), vf.createIRI("a:xabcde"));
        connection.add(vf.createIRI("s:1"), vf.createIRI("p:2"), vf.createIRI("b:xabcab"));
        executeQueryAndVerifyResults("xab", 2);

    }

    @Test
    public void shouldAutocompleteLocalNameWithPrefix() throws RDF4JException {
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

    @Test
    public void shouldNotThrowExceptionWhenCallingAutocompleteOnUnboundObject() throws RepositoryException,
            MalformedQueryException, QueryEvaluationException {
        executeQueryWithUnboundedObjectAndVerifyNoExceptionIsThrown();
    }

    @Test
    public void shouldNotThrowExceptionWhenCallingAutocompleteOnUnboundObjectThenAutocomplete() throws RepositoryException,
            MalformedQueryException, QueryEvaluationException {
        connection.add(vf.createIRI("wine1:WhiteBurgundy"), vf.createIRI("wine1:madeFromGrape"), vf.createIRI("wine1:ChardonnayGrape"));
        connection.add(vf.createIRI("wine2:SauvignonBlanc"), vf.createIRI("wine2:madeFromGrape"), vf.createIRI("wine2:Semillion"));
        executeQueryWithUnboundedObjectAndVerifyResults("wine1:;bu\" \"wine2:;sau", 2);
    }

    @Test
    public void shouldAutocompleteQueriesAndReturnSameResult() throws RepositoryException, MalformedQueryException,
            QueryEvaluationException {
        IRI whiteBurgundy = vf.createIRI("wine1:WhiteBurgundy");
        IRI madeFromGrape = vf.createIRI("wine1:madeFromGrape");
        IRI chardonnayGrape = vf.createIRI("wine1:ChardonnayGrape");
        Literal labelName = vf.createLiteral("wine1:WhiteWine");

        connection.add(whiteBurgundy, madeFromGrape, chardonnayGrape);
        connection.add(whiteBurgundy, RDF.TYPE, labelName);

        IRI sauvignonBlanc = vf.createIRI("wine1:WhiteBurgundy");
        IRI semillion = vf.createIRI("wine1:ChardonnayGrape");

        connection.add(sauvignonBlanc, madeFromGrape, semillion);
        connection.add(whiteBurgundy, RDF.TYPE, labelName);

        String valueToBeAutocompleted = "wine1:;bu"; // the wine we are querying for

        String orderedQueryStart = "SELECT ?s WHERE { ?s <http://www.ontotext.com/plugins/autocomplete#query> \"";
        String orderedQueryEnd = "\" ;  }";

        String unorderedQueryStart = "SELECT ?s WHERE { ?s <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> ?value " +
                "; <http://www.ontotext.com/plugins/autocomplete#query> \"";
        String unorderedQueryEnd = "\" }";

        List<String> resultsFromOrderedQuery = executeCustomQueryAndGetResults(orderedQueryStart, orderedQueryEnd,
                valueToBeAutocompleted);
        List<String> resultsFromUnorderedQuery = executeCustomQueryAndGetResults(unorderedQueryStart, unorderedQueryEnd,
                valueToBeAutocompleted);

        /* Both queries should wield the same result, regardless of the positioning of the autocomplete index call
         * in the queries themselves. This ensures that there's no "good" or "bad" optimization plan depending solely
         * on where the autocomplete call is placed by a user when writing a query. */
        assertEquals(resultsFromOrderedQuery, resultsFromUnorderedQuery);
    }
}
