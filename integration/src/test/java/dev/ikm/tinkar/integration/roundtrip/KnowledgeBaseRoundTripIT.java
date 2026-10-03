/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.integration.roundtrip;

import dev.ikm.tinkar.common.service.PluggableService;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.entity.export.ExportEntitiesToProtobufFile;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.fixtures.ForkedJvm;
import dev.ikm.tinkar.fixtures.StoreDigest;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.fixtures.TestTags;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.reasoner.service.ClassifierResults;
import dev.ikm.tinkar.reasoner.service.ReasonerService;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A knowledge base through its whole life, on starter data: imported; classified;
 * queried; exported; and the export imported into a fresh store that must hold what the
 * exported store held. Each store lifetime is a stage in its own JVM ({@link ForkedJvm}).
 *
 * <p>This is the small form of the release validation that runs the same stages over
 * SNOMED CT (IKE-Network/ike-issues#1210). The numbers each stage records are the
 * baseline for this data.
 */
@Tag(TestTags.STAGED)
class KnowledgeBaseRoundTripIT {

    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeBaseRoundTripIT.class);

    private static final File WORK = TestConstants.createFilePathInTargetFromClassName.apply(KnowledgeBaseRoundTripIT.class);

    private static final String STORE = "store";
    private static final String RESTORED_STORE = "restored.store";
    private static final String IMPORT_FILE = "import.file";
    private static final String EXPORT_FILE = "export.file";

    private static final String IMPORTED = "imported.";
    private static final String CLASSIFIED = "classified.";
    private static final String EXPORTED = "exported.";
    private static final String RESTORED = "restored.";

    @Test
    void starterDataIsImportedClassifiedQueriedExportedAndRestored() {
        FileUtil.recursiveDelete(WORK);
        assertTrue(WORK.mkdirs(), "Could not create " + WORK);
        Properties in = new Properties();
        in.setProperty(STORE, new File(WORK, "store").getPath());
        in.setProperty(RESTORED_STORE, new File(WORK, "restored").getPath());
        in.setProperty(IMPORT_FILE, TestConstants.PB_STARTER_DATA.getPath());
        in.setProperty(EXPORT_FILE, new File(WORK, "export.pb.zip").getPath());

        Properties result = ForkedJvm.run(Import.class, in);
        result = ForkedJvm.run(Classify.class, result);
        result = ForkedJvm.run(Query.class, result);
        result = ForkedJvm.run(Export.class, result);
        result = ForkedJvm.run(Restore.class, result);

        StoreDigest imported = StoreDigest.load(result, IMPORTED);
        StoreDigest classified = StoreDigest.load(result, CLASSIFIED);
        StoreDigest exported = StoreDigest.load(result, EXPORTED);
        StoreDigest restored = StoreDigest.load(result, RESTORED);

        // Import
        assertTrue(imported.concepts() > 0 && imported.semantics() > 0, "The import left the store empty: " + imported);
        assertEquals(0, imported.unrendered(), "Field values the digest could not render");

        // Classify: the same concepts, defined further
        long classifiedConcepts = number(result, "classify.concepts");
        assertTrue(classifiedConcepts > 0, "The reasoner classified no concepts");
        assertEquals(imported.concepts(), classified.concepts(), "Classification must not add or remove concepts");
        assertTrue(classified.semantics() > imported.semantics(),
                "Classification wrote no inferred semantics: " + imported.semantics() + " before and after");
        assertTrue(!classified.hash().equals(imported.hash()), "The digest did not notice what classification wrote");

        // Query: what classification wrote is what navigation and description lookup read
        assertEquals(0, number(result, "query.concepts.without.description"), "Concepts with no description under the default view");
        assertTrue(number(result, "query.descendants.of.root") > 0, "The root concept has no descendants under the default view");
        assertEquals(0, number(result, "query.classified.concepts.without.parents"),
                "Classified concepts, other than the root, with no parent in the inferred navigation");

        // Export and restore: closing, reopening and a protobuf round trip lose nothing
        assertEquals(java.util.List.of(), exported.differencesFrom(classified),
                "The store after it was closed and reopened, against the store as classified");
        assertEquals(exported.entities(), number(result, "export.count"), "Entities written to the export file");
        assertEquals(java.util.List.of(), restored.differencesFrom(exported),
                "The fresh store after importing the export, against the store that was exported");

        LOG.info("Starter data baseline: {} entities, {} versions; import {} ms, classify {} ms ({} concepts), "
                        + "queries {} ms, export {} ms ({} bytes), restore {} ms",
                exported.entities(), exported.versions(), result.getProperty("import.millis"),
                result.getProperty("classify.millis"), classifiedConcepts, result.getProperty("query.millis"),
                result.getProperty("export.millis"), result.getProperty("export.bytes"), result.getProperty("restore.millis"));
    }

    private static long number(Properties properties, String key) {
        return Long.parseLong(properties.getProperty(key));
    }

    /** A stage with a store: opened before the work, closed after it, the properties passed through. */
    private abstract static class StoreStage implements ForkedJvm.Stage {
        abstract String storeProperty();

        abstract void work(Properties in, Properties out) throws Exception;

        @Override
        public final void run(Properties in, Properties out) throws Exception {
            out.putAll(in);
            TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, new File(in.getProperty(storeProperty())));
            try {
                work(in, out);
            } finally {
                TestHelper.stopDatabase();
            }
        }
    }

    /** Stage 1: a new store and the data loaded into it. */
    static class Import extends StoreStage {
        @Override
        String storeProperty() {
            return STORE;
        }

        @Override
        void work(Properties in, Properties out) {
            long start = System.currentTimeMillis();
            new LoadEntitiesFromProtobufFile(new File(in.getProperty(IMPORT_FILE))).compute();
            out.setProperty("import.millis", Long.toString(System.currentTimeMillis() - start));
            StoreDigest.ofOpenStore().store(out, IMPORTED);
        }
    }

    /** Stage 2: the store reopened, classified, and the inferred results written. */
    static class Classify extends StoreStage {
        @Override
        String storeProperty() {
            return STORE;
        }

        @Override
        void work(Properties in, Properties out) throws Exception {
            ReasonerService reasoner = PluggableService.load(ReasonerService.class).stream()
                    .map(ServiceLoader.Provider::get)
                    .findFirst().orElseThrow(() -> new IllegalStateException("No ReasonerService is provided"));
            long start = System.currentTimeMillis();
            reasoner.init(Calculators.View.Default(), TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN,
                    TinkarTerm.EL_PLUS_PLUS_INFERRED_AXIOMS_PATTERN);
            reasoner.extractData(quiet());
            reasoner.loadData(quiet());
            reasoner.computeInferences();
            reasoner.buildNecessaryNormalForm();
            ClassifierResults results = reasoner.writeInferredResults();
            out.setProperty("classify.millis", Long.toString(System.currentTimeMillis() - start));
            out.setProperty("classify.reasoner", reasoner.getName());
            out.setProperty("classify.concepts", Long.toString(reasoner.getReasonerConceptSet().size()));
            out.setProperty("classify.inferred.changes", Long.toString(results.getConceptsWithInferredChanges().size()));
            StoreDigest.ofOpenStore().store(out, CLASSIFIED);
        }

        private static TrackingCallable<Object> quiet() {
            return new TrackingCallable<>() {
                @Override
                protected Object compute() {
                    return null;
                }
            };
        }
    }

    /** Stage 3: the store reopened and read through the default view's calculators. */
    static class Query extends StoreStage {
        @Override
        String storeProperty() {
            return STORE;
        }

        @Override
        void work(Properties in, Properties out) {
            ViewCalculator view = Calculators.View.Default();
            long start = System.currentTimeMillis();
            int root = TinkarTerm.ROOT_VERTEX.nid();
            AtomicLong concepts = new AtomicLong();
            AtomicLong withoutDescription = new AtomicLong();
            PrimitiveData.get().forEachConceptNid(nid -> {
                concepts.incrementAndGet();
                if (view.getDescriptionText(nid).filter(text -> !text.isBlank()).isEmpty()) {
                    withoutDescription.incrementAndGet();
                }
            });
            var descendants = view.navigationCalculator().descendentsOf(root);
            AtomicLong withoutParents = new AtomicLong();
            descendants.intStream().forEach(nid -> {
                if (view.navigationCalculator().parentsOf(nid).isEmpty()) {
                    withoutParents.incrementAndGet();
                }
            });
            out.setProperty("query.millis", Long.toString(System.currentTimeMillis() - start));
            out.setProperty("query.concepts", Long.toString(concepts.get()));
            out.setProperty("query.concepts.without.description", Long.toString(withoutDescription.get()));
            out.setProperty("query.descendants.of.root", Long.toString(descendants.size()));
            out.setProperty("query.classified.concepts.without.parents", Long.toString(withoutParents.get()));
        }
    }

    /** Stage 4: the store reopened and exported to protobuf. */
    static class Export extends StoreStage {
        @Override
        String storeProperty() {
            return STORE;
        }

        @Override
        void work(Properties in, Properties out) throws Exception {
            StoreDigest.ofOpenStore().store(out, EXPORTED);
            File exportFile = new File(in.getProperty(EXPORT_FILE));
            long start = System.currentTimeMillis();
            long exported = new ExportEntitiesToProtobufFile(exportFile).compute().getTotalCount();
            out.setProperty("export.millis", Long.toString(System.currentTimeMillis() - start));
            out.setProperty("export.count", Long.toString(exported));
            out.setProperty("export.bytes", Long.toString(exportFile.length()));
        }
    }

    /** Stage 5: a fresh store and the export imported into it. */
    static class Restore extends StoreStage {
        @Override
        String storeProperty() {
            return RESTORED_STORE;
        }

        @Override
        void work(Properties in, Properties out) {
            long start = System.currentTimeMillis();
            new LoadEntitiesFromProtobufFile(new File(in.getProperty(EXPORT_FILE))).compute();
            out.setProperty("restore.millis", Long.toString(System.currentTimeMillis() - start));
            StoreDigest.ofOpenStore().store(out, RESTORED);
        }
    }
}
