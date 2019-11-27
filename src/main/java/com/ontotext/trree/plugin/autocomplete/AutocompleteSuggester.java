package com.ontotext.trree.plugin.autocomplete;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.suggest.analyzing.BlendedInfixSuggester;
import org.apache.lucene.store.Directory;

import java.io.IOException;

/**
 * Our own extension of Lucene's suggester.
 *
 * The blended suggester provides better handling of how results are sorted
 * (in connection with weight based on the length of the local part).
 */
public class AutocompleteSuggester extends BlendedInfixSuggester {

    private final static int MAX_BUFFERED_DOCS_DEFAULT = 5000;
    private final static double RAM_BUFFER_SIZE_MB_DEFAULT = 256.0;

    public AutocompleteSuggester(Directory dir, Analyzer analyzer) throws IOException {
        super(dir, analyzer);
    }

    public AutocompleteSuggester(Directory dir, Analyzer indexAnalyzer, Analyzer queryAnalyzer, int minPrefixChars, BlenderType blenderType, int numFactor, boolean commitOnBuild) throws IOException {
        super(dir, indexAnalyzer, queryAnalyzer, minPrefixChars, blenderType, numFactor, commitOnBuild);
    }

    public AutocompleteSuggester(Directory dir, Analyzer indexAnalyzer, Analyzer queryAnalyzer, int minPrefixChars, BlenderType blenderType, int numFactor, Double exponent, boolean commitOnBuild, boolean allTermsRequired, boolean highlight) throws IOException {
        super(dir, indexAnalyzer, queryAnalyzer, minPrefixChars, blenderType, numFactor, exponent, commitOnBuild, allTermsRequired, highlight);
    }

    @Override
    protected IndexWriterConfig getIndexWriterConfig(Analyzer indexAnalyzer, IndexWriterConfig.OpenMode openMode) {
        IndexWriterConfig iwConfig = super.getIndexWriterConfig(indexAnalyzer, openMode);
        // Set maxBufferedDocs large enough to prevent the writer from flushing based on document count
        iwConfig.setMaxBufferedDocs(MAX_BUFFERED_DOCS_DEFAULT);
        //More RAM before flushing means Lucene writes larger segments to begin with which means less merging later.
        iwConfig.setRAMBufferSizeMB(RAM_BUFFER_SIZE_MB_DEFAULT);
        // This makes close() rollback the transaction.
        iwConfig.setCommitOnClose(false);
        return iwConfig;
    }

    @Override
    protected void addPrefixMatch(StringBuilder sb, String surface, String analyzed, String prefixToken) {
        // Code copied from super so we can use another tag instead of <b>, check for changes upstream when you
        // update the Lucene version
        if (prefixToken.length() >= surface.length()) {
            addWholeMatch(sb, surface, analyzed);
            return;
        }
        sb.append(getOpeningHighlightTag());
        sb.append(surface.substring(0, prefixToken.length()));
        sb.append(getClosingHighlightingTag());
        sb.append(surface.substring(prefixToken.length()));
    }

    @Override
    protected void addWholeMatch(StringBuilder sb, String surface, String analyzed) {
        // Code copied from super so we can use another tag instead of <b>, check for changes upstream when you
        // update the Lucene version
        sb.append(getOpeningHighlightTag());
        sb.append(surface);
        sb.append(getClosingHighlightingTag());
    }

    private static String getOpeningHighlightTag() {
        // We use a classic ascii control character, "Start of Text"
        return "\u0002";
    }

    private static String getClosingHighlightingTag() {
        // We use a classic ascii control character, "End of Text"
        return "\u0003";
    }

    public static String htmlifyHighlight(Object highlight) {
        return StringEscapeUtils.escapeHtml4(highlight.toString())
                .replace(getOpeningHighlightTag(), "<b>")
                .replace(getClosingHighlightingTag(), "</b>");
    }
}
