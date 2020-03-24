package com.ontotext.trree.plugin.autocomplete;

import com.ontotext.trree.sdk.Entities;
import com.ontotext.trree.sdk.StatementIterator;
import com.ontotext.trree.sdk.Statements;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.util.BytesRef;

import java.io.Closeable;
import java.io.IOException;
import java.util.Set;

/**
 * An InputIterator over labels (label predicate and language(s) are determined by LabelConfig).
 */
class LabelsIterator implements InputIterator, AutoCloseable, Closeable {
    private final LabelConfig labelConfig;
    private final Entities entities;
    private final AutocompleteIndex autocompleteIndex;
    private final StatementIterator statementIterator;

    private long currentSubject = 0L;
    private String currentLabel;

    LabelsIterator(Entities entities, Statements statements, LabelConfig labelConfig, AutocompleteIndex autocompleteIndex) {
        this.autocompleteIndex = autocompleteIndex;
        this.entities = entities;
        this.labelConfig = labelConfig;
        this.statementIterator = statements.get(0, labelConfig.labelId, 0);
    }

    @Override
    public long weight() {
        return autocompleteIndex.getWeight(currentSubject, currentLabel);
    }

    @Override
    public BytesRef payload() {
        return autocompleteIndex.getEntityIDAsPayload(currentSubject, true);
    }

    @Override
    public boolean hasPayloads() {
        return true;
    }

    @Override
    public Set<BytesRef> contexts() {
        return autocompleteIndex.getLabelContexts();
    }

    @Override
    public boolean hasContexts() {
        return true;
    }

    @Override
    public BytesRef next() throws IOException {
        while (statementIterator.next() && !autocompleteIndex.isShouldInterrupt()) {
            currentSubject = statementIterator.subject;
            if (entities.getType(currentSubject) == Entities.Type.URI  || entities.getType(currentSubject) == Entities.Type.TRIPLE) {
                String language = entities.getLanguage(statementIterator.object);
                if (!labelConfig.languageMatches(language)) {
                    continue;
                }
                currentLabel = entities.get(statementIterator.object).stringValue();
                return new BytesRef(currentLabel);
            }
        }

        return null;
    }

    @Override
    public void close() {
        statementIterator.close();
    }
}
