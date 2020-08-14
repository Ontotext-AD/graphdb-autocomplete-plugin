package com.ontotext.trree.plugin.autocomplete;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.vocabulary.SKOS;
import org.eclipse.rdf4j.model.vocabulary.WGS84;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.junit.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.Assert.assertTrue;

/**
 * Created by Pavel Mihaylov on 13/08/2020.
 */
public class TestWellKnownEntities {
    @Test
    public void test() throws IOException {
        final Set<IRI> wellKnownEntities = WellKnownEntities.getWellKnownEntities();
        assertTrue(wellKnownEntities.size() > 1500);
        assertTrue(wellKnownEntities.contains(XSD.DATETIME));
        assertTrue(wellKnownEntities.contains(SKOS.BROADER));
        assertTrue(wellKnownEntities.contains(WGS84.SPATIAL_THING));
    }
}
