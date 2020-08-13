package com.ontotext.trree.plugin.autocomplete;

import org.apache.lucene.util.BytesRef;
import org.eclipse.rdf4j.model.IRI;

import java.io.IOException;
import java.util.Iterator;

/**
 * Iterates over well known entities provided by RDF4J vocabulary classes. These entities will have the following payload:
 * - 8 zeroed bytes representing the entity ID = 0 (to distinguish them from data entities)
 * - 1 byte representing whether a label was indexed (always 0/false in this iterator)
 * - a varying number of other bytes representing the UTF-8 string value of the IRI
 */
public class WellKnownEntitiesIterator extends AbstractEntitiesIterator {
    private final Iterator<IRI> wkeItr;

    WellKnownEntitiesIterator(AutocompleteIndex autocompleteIndex) throws IOException {
        super(autocompleteIndex);
        wkeItr = WellKnownEntities.getWellKnownEntities().iterator();
    }

    @Override
    public BytesRef payload() {
        return autocompleteIndex.getIRIAsPayload(currentURI, false);
    }

    @Override
    public BytesRef next() throws IOException {
        if (wkeItr.hasNext() && !autocompleteIndex.isShouldInterrupt()) {
            currentURI = wkeItr.next();
            currentLocalName = currentURI.getLocalName();

            return new BytesRef(currentLocalName);
        }

        return null;
    }
}
