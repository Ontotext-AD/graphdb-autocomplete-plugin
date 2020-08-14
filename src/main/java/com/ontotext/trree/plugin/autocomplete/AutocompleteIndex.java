package com.ontotext.trree.plugin.autocomplete;

import com.google.common.primitives.Longs;
import com.ontotext.trree.plugin.autocomplete.lucene.LocalNameAnalyzer;
import com.ontotext.trree.sdk.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.analyzing.AnalyzingInfixSuggester;
import org.apache.lucene.search.suggest.analyzing.BlendedInfixSuggester;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Triple;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.helpers.NTriplesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Created by desislava on 12/11/15.
 */
class AutocompleteIndex {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutocompleteIndex.class);

    // A sequence of all uppercase letters, at least 3
    private static final Pattern ALL_UPPER_PATTERN = Pattern.compile("\\p{Lu}\\p{Lu}\\p{Lu}+");

    private Path indexDir;
    private LocalNameAnalyzer analyzer;
    private AnalyzingInfixSuggester suggester;
    private final AutocompletePlugin autocompletePlugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> buildIndexTask;
    private Throwable error;

    private RDFRankProvider rdfRankPlugin;
    private boolean loadedRDFPlugin = false;
    private boolean shouldInterrupt;
    private ThreadsafePluginConnecton threadsafePluginConnecton;

    // TODO: Check why this was needed
    static final Set<String> SPECIAL_ENTITIES = new HashSet<String>() {{
        add("http://www.ontotext.com/check_prp_irp");
        add("http://www.ontotext.com/check_prp_npa2");
        add("http://www.ontotext.com/scm_int");
        add("http://www.ontotext.com/_owl_allDisjProp");
        add("http://www.ontotext.com/check_prp_npa1");
        add("http://www.ontotext.com/check_prp_asyp");
        add("http://www.ontotext.com/check_cls_nothing2");
        add("http://www.ontotext.com/check_cls_com");
        add("http://www.ontotext.com/check_prp_pdw");
        add("http://www.ontotext.com/_cls-oo");
        add("http://www.ontotext.com/_interOf");
        add("http://www.ontotext.com/_AllDisjointClasses");
        add("http://www.ontotext.com/_oneOf");
        add("http://www.ontotext.com/_owl_allDisjClasses");
        add("http://www.ontotext.com/_AllDisjointProperties");
        add("http://www.ontotext.com/_allTypes");
        add("http://www.ontotext.com/isInconsistentWith");
        add("http://www.ontotext.com/check_cax_dw");
        add("http://www.ontotext.com/_typeByInt");
        add("http://www.ontotext.com/_cls-oo");
        add("http://www.ontotext.com/_unionOf");
        add("http://www.ontotext.com/_interOf");
        add("http://www.ontotext.com/_oneOf");
        add("http://www.ontotext.com/_allTypes");
        add("http://www.ontotext.com/_union");
        add("http://www.ontotext.com/_typeByInt");
    }};

    public boolean isShouldInterrupt() {
        return shouldInterrupt;
    }

    boolean hasBuiltIndex;

    AutocompleteIndex(AutocompletePlugin plugin) {
        this.autocompletePlugin = plugin;
        initLuceneConfig();
    }

    private void initLuceneConfig() {
        indexDir = AutocompletePluginUtils.resolveIndexDirectory(autocompletePlugin.getDataDir());
        try {
            if (Files.exists(indexDir) && !Files.isDirectory(indexDir)) {
                // Path exists but isn't a directory. Delete it.
                Files.delete(indexDir);
            }

            analyzer = new LocalNameAnalyzer();

            restartLuceneConfig();

            // Files.list returns a stream that should be closed
            try (Stream<Path> list = Files.list(indexDir)) {
                if (list.count() > 0) {
                    hasBuiltIndex = true;
                }
            }

        } catch (IOException e) {
            throw new PluginException("Could not create index directory: " + indexDir, e);
        } catch (Exception e1) {
            LOGGER.error("Could not create suggester", e1);
            throw new PluginException("Could not create suggester", e1);
        } catch (Error e2) {
            LOGGER.error("Could not create suggester", e2);
            throw e2;
        }
    }

    private void restartLuceneConfig() throws IOException {
        FSDirectory directory = FSDirectory.open(indexDir);
        suggester = new AutocompleteSuggester(directory, analyzer, analyzer,
                AnalyzingInfixSuggester.DEFAULT_MIN_PREFIX_CHARS,
                BlendedInfixSuggester.BlenderType.POSITION_LINEAR, 1, true);
    }

    long getWeight(long id, String localName) {
        RDFRankProvider rdfRankPlugin = getRDFRankProvider();
        double rank = 0;
        if (rdfRankPlugin != null && id > 0) {
            rank = rdfRankPlugin.getNormalizedRank(id);
        }

        if (rank > 0.01) {
            rank = 1 - rank;
            // By raising to the fourth power we get a better spread of otherwise close ranks
            return Math.round(10_000 - (rank * rank * rank * rank) * 1_000);
        } else {
            return 1_000 - localName.length();
        }
    }

    Set<BytesRef> getURINamespaceAsContext(IRI currentURI) {
        return Collections.singleton(new BytesRef(currentURI.getNamespace()));
    }

    Set<BytesRef> getLabelContexts() {
        return Collections.singleton(new BytesRef("label"));
    }

    BytesRef getEntityIDAsPayload(long id, boolean isLabel) {
        BytesRefBuilder bb = new BytesRefBuilder();
        bb.append(Longs.toByteArray(id), 0, Long.BYTES);

        if (isLabel) {
            bb.append((byte) 1);
        } else {
            bb.append((byte) 0);
        }

        return bb.get();
    }

    BytesRef getIRIAsPayload(IRI iri, boolean isLabel) {
        BytesRefBuilder bb = new BytesRefBuilder();
        // A mock entity ID that is always zero
        bb.append(Longs.toByteArray(0), 0, Long.BYTES);

        if (isLabel) {
            bb.append((byte) 1);
        } else {
            bb.append((byte) 0);
        }

        // The IRI as a string represented as UTF-8 bytes
        final byte[] bytes = iri.stringValue().getBytes(StandardCharsets.UTF_8);
        bb.append(bytes, 0, bytes.length);

        return bb.get();
    }

    IndexStatus status() {
        if (error != null) {
            return IndexStatus.ERROR;
        } else if (buildIndexTask == null) {
            return hasBuiltIndex ? (autocompletePlugin.configuredAndActualConfigsDiffer ? IndexStatus.READY_CONFIG : IndexStatus.READY)
                    : IndexStatus.NONE;
        } else if (isShouldInterrupt()) {
            return IndexStatus.CANCELED;
        } else if (buildIndexTask.isDone()) {
            return autocompletePlugin.configuredAndActualConfigsDiffer ? IndexStatus.READY_CONFIG : IndexStatus.READY;
        } else {
            return IndexStatus.BUILDING;
        }
    }

    String error() {
        if (error != null) {
            if (error instanceof ClosedByInterruptException || error instanceof AlreadyClosedException) {
                return "Indexing was interrupted.";
            }
            return error.toString();
        }
        return null;
    }

    synchronized void interrupt() {
        if (status() != IndexStatus.BUILDING || buildIndexTask == null) {
            LOGGER.info("Index status is " + status());
            return;
        }

        LOGGER.info("Interrupting building index. " + status());
        shouldInterrupt = true;
    }


    synchronized void buildIndex(PluginConnection pluginConnection) {
        if (status() == IndexStatus.BUILDING) {
            LOGGER.info("Index is already building.");
            return;
        }

        // Synced to plugin to keep AutocompleteUpdateListener.entityAdded()
        // and the full build from race conditioning into each other.
        synchronized (autocompletePlugin) {
            shouldInterrupt = false;
            hasBuiltIndex = false;
            error = null;

            final AutocompleteIndex that = this;

            if (threadsafePluginConnecton != null) {
                // Close previous collections (should be already closed by the finally in the thread but who knows)
                threadsafePluginConnecton.close();
            }

            threadsafePluginConnecton = pluginConnection.getThreadsafeConnection();
            Entities threadsafeEntities = threadsafePluginConnecton.getEntities();
            Statements threadsafeStatements = threadsafePluginConnecton.getStatements();

            buildIndexTask = executor.submit(() -> {
                try {
                    synchronized (autocompletePlugin) {
                        autocompletePlugin.updateActualConfig(threadsafeEntities);

                        LOGGER.info("Start Building Index..");

                        List<InputIterator> iterators = new ArrayList<>();

                        if (autocompletePlugin.actualShouldIndexIRIs) {
                            iterators.add(new WellKnownEntitiesIterator(that));
                            iterators.add(new EntitiesIterator(threadsafeEntities, that));
                        }

                        for (LabelConfig labelConfig : autocompletePlugin.labelConfigs) {
                            if (labelConfig.labelId > 0) { // labelId may not be there yet => skip that label
                                iterators.add(new LabelsIterator(threadsafeEntities,
                                        threadsafeStatements, labelConfig, that));
                            }
                        }

                        if (iterators.isEmpty()) {
                            LOGGER.warn("Neither IRIs nor labels are configured for autocomplete indexing. The index will be empty.");
                        }

                        try (CompositeInputIterator iterator = new CompositeInputIterator(iterators)){
                            // Sync so we avoid clashes with clonedEntities being added while we are indexing in the background
                            suggester.build(iterator);
                        }

                        if (shouldInterrupt) {
                            rollback();
                            LOGGER.info("Building index was interrupted.");
                        } else {
                            commitAndRefresh();
                            hasBuiltIndex = true;
                            LOGGER.info("Index built. Ready to use!");
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Index was not built.", e);
                    that.error = e;
                } catch (Throwable t) {
                    LOGGER.error("Index was not built.", t);
                    that.error = t;
                    throw new PluginException("Could not build index", t);
                } finally {
                    threadsafePluginConnecton.close();
                }
            });
        }
    }

    void index(long id, IRI currentURI) {
        try {
            final String localName = currentURI.getLocalName();
            final BytesRef uriLocalNameForIndex = new BytesRef(localName);
            synchronized (autocompletePlugin) {
                // Sync so we avoid adding new documents while committing (see commitAndRefresh() and rollback() too)
                suggester.add(uriLocalNameForIndex, getURINamespaceAsContext(currentURI),
                        getWeight(id, localName), getEntityIDAsPayload(id, false));
            }
        } catch (IOException e) {
            LOGGER.error("Could not index uri. ", e);
            throw new PluginException("Could not index uri: " + currentURI, e);
        }
    }

    void index(long id, String string) {
        try {
            final BytesRef stringForIndex = new BytesRef(string);
            synchronized (autocompletePlugin) {
                // Sync so we avoid adding new documents while committing (see commitAndRefresh() and rollback() too)
                if (id == 0) {
                    // Zero id means "remove" and it's not an actual remove as the Lucene auto-suggest thing doesn't
                    // support remove. Instead we fake it by updating the payload to a null one.
                    suggester.update(stringForIndex, null, 0, null);
                } else {
                    suggester.add(stringForIndex, getLabelContexts(),
                            getWeight(id, string), getEntityIDAsPayload(id, true));
                }
            }
        } catch (IOException e) {
            LOGGER.error("Could not index string. ", e);
            throw new PluginException("Could not index string: " + string, e);
        }
    }

    void commitAndRefresh() throws IOException {
        if (suggester == null) {
            return;
        }
        synchronized (autocompletePlugin) {
            // Sync so we avoid committing while new entities are being added (see index() too)
            suggester.commit();
            suggester.refresh();
        }
    }

    void rollback() throws IOException {
        if (suggester == null) {
            return;
        }
        synchronized (autocompletePlugin) {
            // Sync so we avoid rollbacking while new entities are being added (see index() too)
            suggester.close();
            try {
                // There is no rollback() without close() so we just restart the Lucene stuff.
                restartLuceneConfig();
            } catch (Exception e) {
                LOGGER.error("Something went wrong when rolling back, disabling plugin.", e);
                //autocompletePlugin.
            }
        }
    }

    Collection<Result> findEntities(String namespace, String query, Entities entities, int count) {
        if (suggester == null) {
            throw new PluginException("Suggester should not be null ");
        }
        List<Lookup.LookupResult> results;
        IndexStatus st = status();
        if (!IndexStatus.statusIsOk(st)) {
            LOGGER.info("Index is not ready. Status: " + st);
            if (st == IndexStatus.ERROR) {
                LOGGER.error("Error is: " + error());
            }
            return Collections.emptyList();
        }

        try {
            if (suggester.getCount() == 0) {
                results = Collections.emptyList();
            } else {
                results = getResultsForQuery(query, namespace, count);

                // Add alternatives for cases like "USR" not matching "USRegion"
                if (ALL_UPPER_PATTERN.matcher(query).matches()) {
                    String altQuery = query.substring(0, query.length() - 1) + " " + query.charAt(query.length() - 1);
                    results = mergeResults(results, getResultsForQuery(altQuery, namespace, count), count);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Could not execute query. ", e);
            throw new PluginException("Could not execute query: " + query, e);
        } catch (Exception e1) {
            LOGGER.error("Could not lookup results. ", e1);
            throw new PluginException("Could not lookup results. Try index rebuild. ", e1);
        }

        Map<String, Result> resultEntities = new LinkedHashMap<>();
        for (Lookup.LookupResult result : results) {
            // TODO: old byte array, 64 bytes
            if (result.payload.bytes.length == 0) {
                // this is a deleted label
                continue;
            }
            long id = Longs.fromByteArray(result.payload.bytes);
            Value val;
            if (id == 0) {
                // Indexed an IRI that has no entity id, get it from payload
                String iri = new String(result.payload.bytes, 9, result.payload.bytes.length - 9, StandardCharsets.UTF_8);
                val = SimpleValueFactory.getInstance().createIRI(iri);
            } else {
                val = entities.get(id);
                if (val == null) {
                    // Value might be missing because we indexed but the entity was rolled back.
                    continue;
                }
            }
            if (!(val instanceof IRI || val instanceof Triple)) {
                LOGGER.error("Oops, found a non URI or Triple in results. This should not happen: " + id + " => " + val);
                assert false;
                continue;
            }
            Result newResult = new Result(id, (Resource) val, result.highlightKey.toString(), result.payload.bytes[8] != 0);
            Result previousResult = resultEntities.get(newResult.resource.stringValue());
            if (previousResult == null || newResult.isBetterThan(previousResult)) {
                resultEntities.put(newResult.resource.stringValue(), newResult);
            }
        }
        return resultEntities.values();
    }

    public void shutDown() {
        try {
            suggester.close();
        } catch (IOException e) {
            LOGGER.error("Could not shutdown suggester. ", e);
        }
    }

    public RDFRankProvider getRDFRankProvider() {
        if (!loadedRDFPlugin) {
            rdfRankPlugin = autocompletePlugin.getPluginLocator().locateRDFRankProvider();
            loadedRDFPlugin = true;
            if (rdfRankPlugin == null) {
                LOGGER.warn("The RDF Rank plugin was not found and will not be used.");
            }
        }
        return rdfRankPlugin;
    }

    private List<Lookup.LookupResult> getResultsForQuery(String query, String namespace, int maxResults) throws IOException {
        if (!StringUtils.isEmpty(namespace)) {
            return suggester.lookup(query, Collections.singleton(new BytesRef(namespace)), false, maxResults);
        } else {
            return suggester.lookup(query, false, maxResults);
        }
    }


    private List<Lookup.LookupResult> mergeResults(@Nullable List<Lookup.LookupResult> result1, @Nullable  List<Lookup.LookupResult> result2,
                                                  int maxResults) {
        if (result2 == null || result2.isEmpty()) {
            if (result1 == null) {
                return Collections.emptyList();
            } else {
                return result1;
            }
        } else if (result1 == null || result1.isEmpty()) {
            return result2;
        }

        List<Lookup.LookupResult> mergedResult = new ArrayList<>(Math.min(result1.size() + result2.size(), maxResults));
        Iterator<Lookup.LookupResult> resultIterator1 = result1.iterator();
        Iterator<Lookup.LookupResult> resultIterator2 = result2.iterator();

        while (mergedResult.size() < maxResults && (resultIterator1.hasNext() || resultIterator2.hasNext())) {
            if (resultIterator1.hasNext()) {
                mergedResult.add(resultIterator1.next());
            }
            if (resultIterator2.hasNext()) {
                mergedResult.add(resultIterator2.next());
            }
        }

        return mergedResult;
    }

    static class Result {
        long id;
        Resource resource;
        String highlight;
        boolean isLabel;

        Result(long id, Resource resource, String highlight, boolean isLabel) {
            this.id = id;
            this.resource = resource;
            this.isLabel = isLabel;
            if (isLabel) {
                this.highlight = highlight + " " + NTriplesUtil.toNTriplesString(resource);
            } else if (resource instanceof IRI){
                this.highlight = ((IRI) resource).getNamespace() + highlight;
            }
            this.highlight = AutocompleteSuggester.htmlifyHighlight(this.highlight);
        }

        boolean isBetterThan(Result other) {
            return (this.isLabel && !other.isLabel) || (this.isLabel == other.isLabel && this.highlight.length() < other.highlight.length());
        }
    }
}
