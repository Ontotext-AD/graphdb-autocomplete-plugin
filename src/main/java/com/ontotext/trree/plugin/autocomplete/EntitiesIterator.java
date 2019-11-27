package com.ontotext.trree.plugin.autocomplete;

import com.ontotext.trree.sdk.Entities;
import com.ontotext.trree.sdk.Statements;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.util.BytesRef;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Value;

import java.io.IOException;
import java.util.Set;

/**
 * Created by desislava on 12/11/15.
 */
class EntitiesIterator implements InputIterator {

    private final Entities entities;
    private final Statements statements;
    private long currentIteratorIndex = 0L;
    private IRI currentURI;
    private String currentLocalName;
    private final AutocompleteIndex autocompleteIndex;

    private static final long SAMEAS_LONG_REPRESENTATION = 9L;

    EntitiesIterator(Entities entities, Statements statements, AutocompleteIndex autocompleteIndex) {
        this.autocompleteIndex = autocompleteIndex;
        this.entities = entities;
        this.statements = statements;
    }

    @Override
    public long weight() {
        return autocompleteIndex.getWeight(currentIteratorIndex, currentLocalName);
    }

    @Override
    public BytesRef payload() {
        return autocompleteIndex.getEntityIDAsPayload(currentIteratorIndex, false);
    }

    @Override
    public boolean hasPayloads() {
        return true;
    }

    @Override
    public Set<BytesRef> contexts() {
        return autocompleteIndex.getURINamespaceAsContext(currentURI);
    }

    @Override
    public boolean hasContexts() {
        return true;
    }

    @Override
    public BytesRef next() throws IOException {
        while (++currentIteratorIndex <= entities.size() && !autocompleteIndex.isShouldInterrupt()) {
            // Only entities for which there is a statement should go into index. See https://ontotext.atlassian.net/browse/GDB-4067
            if (currentIteratorIndex == SAMEAS_LONG_REPRESENTATION ||
                    statements.get(currentIteratorIndex, 0L, 0L).next() ||
                    statements.get(0L, currentIteratorIndex, 0L).next()  ||
                    statements.get(0L, 0L, currentIteratorIndex).next()) {
                Value v = entities.get(currentIteratorIndex);
                if (v instanceof IRI) {
                    if (!AutocompleteIndex.SPECIAL_ENTITIES.contains(v.stringValue())) {
                        currentURI = (IRI) v;
                        currentLocalName = currentURI.getLocalName();

                        return new BytesRef(currentLocalName);
                    }
                }
            }
        }

        return null;
    }
}
