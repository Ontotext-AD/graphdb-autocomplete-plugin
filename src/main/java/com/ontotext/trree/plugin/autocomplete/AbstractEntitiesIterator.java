package com.ontotext.trree.plugin.autocomplete;

import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.util.BytesRef;
import org.eclipse.rdf4j.model.IRI;

import java.util.Set;

/**
 * Created by Pavel Mihaylov on 13/08/2020.
 */
public abstract class AbstractEntitiesIterator implements InputIterator {
    protected long currentIteratorIndex = 0L;
    protected IRI currentURI;
    protected String currentLocalName;
    protected final AutocompleteIndex autocompleteIndex;

    AbstractEntitiesIterator(AutocompleteIndex autocompleteIndex) {
        this.autocompleteIndex = autocompleteIndex;
    }

    @Override
    public long weight() {
        return autocompleteIndex.getWeight(currentIteratorIndex, currentLocalName);
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
}
