package com.ontotext.trree.plugin.autocomplete;

import com.ontotext.trree.plugin.autocomplete.lucene.LocalNameAnalyzer;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.analyzing.AnalyzingInfixSuggester;
import org.apache.lucene.search.suggest.analyzing.BlendedInfixSuggester;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AnalyzingInfixSuggesterBug {
    public static void main(String args[]) throws IOException, InterruptedException {

        // By default, create 10 reader threads.

        final Path tmpDir = Files.createTempDirectory(AnalyzingInfixSuggesterBug.class.getName());
        final Analyzer analyzer = new LocalNameAnalyzer();

        // Populate the data first.
        Directory dir = FSDirectory.open(tmpDir);
        AnalyzingInfixSuggester suggester = new AnalyzingInfixSuggester(dir, analyzer,analyzer, AnalyzingInfixSuggester.DEFAULT_MIN_PREFIX_CHARS, true);
        suggester.add(new BytesRef("hasRagshda"), null, 0, null);
        suggester.add(new BytesRef("hasHrala"), null, 0, null);
        suggester.commit();
        suggester.close();

        Directory dir1 = null;
        dir1 = FSDirectory.open(tmpDir);
        AnalyzingInfixSuggester suggester1 = new AnalyzingInfixSuggester(dir1, analyzer);

        List<Lookup.LookupResult> result = suggester1.lookup("hasH", false, 10);

        for (Lookup.LookupResult lookupResult : result) {
            System.out.println(lookupResult.key);
        }

        suggester1.close();

    }
}
