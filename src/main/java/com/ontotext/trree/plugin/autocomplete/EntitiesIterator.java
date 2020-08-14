package com.ontotext.trree.plugin.autocomplete;

import com.ontotext.trree.sdk.Entities;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.util.BytesRef;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Value;

import java.io.IOException;
import java.util.Set;

/**
 * Iterates over all data entities in the repository. These entities if stored in the autocomplete index will have
 * the following payload:
 * - 8 bytes representing the entity long ID
 * - 1 byte representing whether a label was indexed (always 0/false in this iterator)
 */
class EntitiesIterator extends AbstractEntitiesIterator {
    private final Entities entities;

    EntitiesIterator(Entities entities, AutocompleteIndex autocompleteIndex) {
        super(autocompleteIndex);
        this.entities = entities;
    }

    @Override
    public BytesRef payload() {
        return autocompleteIndex.getEntityIDAsPayload(currentIteratorIndex, false);
    }

    @Override
    public BytesRef next() {
        while (++currentIteratorIndex <= entities.size() && !autocompleteIndex.isShouldInterrupt()) {
            Value v = entities.get(currentIteratorIndex);
            if (v instanceof IRI) {
                if (!AutocompleteIndex.SPECIAL_ENTITIES.contains(v.stringValue())) {
                    currentURI = (IRI) v;
                    currentLocalName = currentURI.getLocalName();

                    return new BytesRef(currentLocalName);
                }
            }
        }

        return null;
    }
}
