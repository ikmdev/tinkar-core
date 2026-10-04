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

import dev.ikm.tinkar.common.id.IntIds;
import dev.ikm.tinkar.common.service.PluggableService;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.coordinate.Coordinates;
import dev.ikm.tinkar.coordinate.stamp.StampCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StampPositionRecord;
import dev.ikm.tinkar.coordinate.stamp.StateSet;
import dev.ikm.tinkar.coordinate.view.ViewCoordinateRecord;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculatorWithCache;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.StampEntityVersion;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpression;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.fixtures.ForkedJvm;
import dev.ikm.tinkar.fixtures.StoreDigest;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.fixtures.TestTags;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.reasoner.service.ReasonerService;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The store is temporal: a view fixed at a past time sees the knowledge base as it was.
 * This test finds the times at which the store changed, and at each of a selection of
 * them classifies the knowledge base as of that time and reads it through the view's
 * calculators. What it finds at each time must equal the recorded reference for that
 * time, so a release produces the same inferences and the same answers at each point in
 * history as the release before it.
 *
 * <p>A store built from release files changes at a few dozen times, its release dates, and
 * every one of them is worth a classification. A store built by authoring, as the starter
 * data is, changes at hundreds of times milliseconds apart. So the selection is the first
 * and last times and others spaced evenly between them by position, {@value #DEFAULT_TIMES}
 * in all unless the {@value #MAX_TIMES_PROPERTY} system property says otherwise; a store
 * with no more times than that has all of them classified.
 *
 * <p>The view stands on the path the content was written on. A path sees its origins as
 * of the origin's own time, not the view's, and the development path's origin is "latest":
 * a view on the development path fixed at a past time still sees everything on the
 * primordial path, where the starter data is, and so reads the same at every time. The
 * times are therefore the times of stamps on the content's path, and the view is fixed on
 * that path.
 *
 * <p>Each time is a stage in its own JVM ({@link ForkedJvm}). Nothing is written to the
 * store: the comparison is of the reasoner's results, not of saved semantics, so every
 * time is classified against the same data.
 *
 * <p>The reference is {@code temporal-classification-starter-data.properties} beside this
 * class's resources. A run writes what it observed to
 * {@code target/.../observed.properties}; recording a new reference is the deliberate act
 * of copying that file over the reference, after looking at why it changed. This is the
 * small form of the release validation that does the same over SNOMED CT's release dates
 * and also compares with SNOMED International's inferred form
 * (IKE-Network/ike-issues#1210).
 */
@Tag(TestTags.STAGED)
class TemporalClassificationIT {

    private static final Logger LOG = LoggerFactory.getLogger(TemporalClassificationIT.class);

    private static final File WORK = TestConstants.createFilePathInTargetFromClassName.apply(TemporalClassificationIT.class);
    private static final String REFERENCE = "temporal-classification-starter-data.properties";

    private static final String STORE = "store";
    private static final String IMPORT_FILE = "import.file";
    private static final String TIMES = "times";
    private static final String TIME = "time";
    private static final String PATH = "path";

    static final String MAX_TIMES_PROPERTY = "temporal.max.times";
    static final int DEFAULT_TIMES = 6;

    @Test
    void everyTimeInTheStoreClassifiesAndReadsAsRecorded() throws IOException {
        FileUtil.recursiveDelete(WORK);
        assertTrue(WORK.mkdirs(), "Could not create " + WORK);
        Properties in = new Properties();
        in.setProperty(STORE, new File(WORK, "store").getPath());
        in.setProperty(IMPORT_FILE, TestConstants.PB_STARTER_DATA.getPath());
        in.setProperty(PATH, TinkarTerm.PRIMORDIAL_PATH.publicId().asUuidArray()[0].toString());

        Properties imported = ForkedJvm.run(ImportAndFindTimes.class, in);
        List<String> allTimes = List.of(imported.getProperty(TIMES).split(","));
        assertTrue(!allTimes.isEmpty() && !allTimes.get(0).isBlank(), "The store holds no stamp times");
        List<String> times = select(allTimes, Integer.getInteger(MAX_TIMES_PROPERTY, DEFAULT_TIMES));
        LOG.info("Classifying at {} of the {} times at which the store changed", times.size(), allTimes.size());

        TreeMap<String, String> observed = new TreeMap<>();
        observed.put("times", Integer.toString(allTimes.size()));
        observed.put("times.classified", Integer.toString(times.size()));
        for (String time : times) {
            Properties at = new Properties();
            at.putAll(imported);
            at.setProperty(TIME, time);
            Properties result = ForkedJvm.run(ClassifyAt.class, at);
            for (String key : result.stringPropertyNames()) {
                if (key.startsWith("at.")) {
                    observed.put(key, result.getProperty(key));
                }
            }
            LOG.info("At {} ({}): {} concepts classified in {} ms", time, Instant.ofEpochMilli(Long.parseLong(time)),
                    result.getProperty("at." + time + ".classified.concepts"), result.getProperty("classify.millis"));
        }

        File observedFile = new File(WORK, "observed.properties");
        Properties toWrite = new Properties();
        toWrite.putAll(observed);
        try (OutputStream out = Files.newOutputStream(observedFile.toPath())) {
            toWrite.store(out, "TemporalClassificationIT: what this run observed. Copy over src/test/resources/.../"
                    + REFERENCE + " to record it as the reference.");
        }

        Properties reference = new Properties();
        try (InputStream stream = TemporalClassificationIT.class.getResourceAsStream(REFERENCE)) {
            if (stream == null) {
                fail("There is no reference (" + REFERENCE + "). This run observed " + observed.size()
                        + " values, written to " + observedFile + "; record them as the reference.");
            }
            reference.load(stream);
        }
        List<String> differences = new ArrayList<>();
        TreeSet<String> keys = new TreeSet<>(observed.keySet());
        keys.addAll(reference.stringPropertyNames());
        for (String key : keys) {
            String expected = reference.getProperty(key);
            String actual = observed.get(key);
            if (expected == null || !expected.equals(actual)) {
                differences.add(key + ": reference " + expected + ", observed " + actual);
            }
        }
        assertEquals(List.of(), differences, "Differences from the recorded reference (observed values are in " + observedFile + ")");
    }

    /**
     * The first and last of the times and others spaced evenly between them by position,
     * {@code max} in all; every time when there are no more than {@code max}.
     */
    static List<String> select(List<String> sortedTimes, int max) {
        if (max < 2 || sortedTimes.size() <= max) {
            return sortedTimes;
        }
        TreeSet<Integer> positions = new TreeSet<>();
        for (int i = 0; i < max; i++) {
            positions.add((int) Math.round(i * (sortedTimes.size() - 1) / (double) (max - 1)));
        }
        List<String> selected = new ArrayList<>();
        for (int position : positions) {
            selected.add(sortedTimes.get(position));
        }
        return selected;
    }

    private static int pathNid(Properties in) {
        return PrimitiveData.nid(UUID.fromString(in.getProperty(PATH)));
    }

    /** Stage 1: a new store, the data loaded, and every time at which the content's path changed. */
    static class ImportAndFindTimes implements ForkedJvm.Stage {
        @Override
        public void run(Properties in, Properties out) {
            out.putAll(in);
            TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, new File(in.getProperty(STORE)));
            try {
                new LoadEntitiesFromProtobufFile(new File(in.getProperty(IMPORT_FILE))).compute();
                int pathNid = pathNid(in);
                TreeSet<Long> times = new TreeSet<>();
                EntityService.get().forEachStampEntity(stamp -> {
                    for (StampEntityVersion version : stamp.versions()) {
                        long time = version.time();
                        // Not the sentinels: uncommitted, canceled, and the pre-inception time.
                        if (version.pathNid() == pathNid
                                && time != Long.MAX_VALUE && time != Long.MIN_VALUE && time != PrimitiveData.PRE_INCEPTION_TIME) {
                            times.add(time);
                        }
                    }
                });
                StringBuilder list = new StringBuilder();
                for (long time : times) {
                    list.append(list.isEmpty() ? "" : ",").append(time);
                }
                out.setProperty(TIMES, list.toString());
            } finally {
                TestHelper.stopDatabase();
            }
        }
    }

    /** One time: the store as of that time, classified and read. Writes nothing to the store. */
    static class ClassifyAt implements ForkedJvm.Stage {
        @Override
        public void run(Properties in, Properties out) throws Exception {
            String time = in.getProperty(TIME);
            String prefix = "at." + time + ".";
            TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, new File(in.getProperty(STORE)));
            try {
                StampPositionRecord position = StampPositionRecord.make(Long.parseLong(time), pathNid(in));
                StampCoordinateRecord stamps = StampCoordinateRecord.make(StateSet.ACTIVE, position, IntIds.set.empty());
                ViewCoordinateRecord coordinate = ViewCoordinateRecord.make(stamps,
                        Coordinates.Language.UsEnglishRegularName(), Coordinates.Logic.ElPlusPlus(),
                        Coordinates.Navigation.stated(), Coordinates.Edit.Default());
                ViewCalculator view = ViewCalculatorWithCache.getCalculator(coordinate);

                // Read through the view's calculators as of the time.
                AtomicLong visibleConcepts = new AtomicLong();
                AtomicLong described = new AtomicLong();
                EntityService.get().forEachConceptEntity(concept -> {
                    if (view.latestIsActive(concept.nid())) {
                        visibleConcepts.incrementAndGet();
                        if (view.getDescriptionText(concept.nid()).isPresent()) {
                            described.incrementAndGet();
                        }
                    }
                });
                out.setProperty(prefix + "visible.concepts", Long.toString(visibleConcepts.get()));
                out.setProperty(prefix + "described.concepts", Long.toString(described.get()));
                out.setProperty(prefix + "stated.descendants.of.root",
                        Long.toString(view.navigationCalculator().descendentsOf(TinkarTerm.ROOT_VERTEX.nid()).size()));

                // Classify as of the time.
                ReasonerService reasoner = PluggableService.load(ReasonerService.class).stream()
                        .map(ServiceLoader.Provider::get)
                        .findFirst().orElseThrow(() -> new IllegalStateException("No ReasonerService is provided"));
                long start = System.currentTimeMillis();
                reasoner.init(view, TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN, TinkarTerm.EL_PLUS_PLUS_INFERRED_AXIOMS_PATTERN);
                reasoner.extractData(quiet());
                reasoner.loadData(quiet());
                reasoner.computeInferences();
                reasoner.buildNecessaryNormalForm();
                out.setProperty("classify.millis", Long.toString(System.currentTimeMillis() - start));

                // The reasoner's results, named by public id so the hash is the same in every store.
                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                long[] hash = new long[2];
                AtomicLong parentEdges = new AtomicLong();
                AtomicLong inEquivalenceSets = new AtomicLong();
                reasoner.getReasonerConceptSet().forEach(nid -> {
                    TreeSet<String> parents = new TreeSet<>();
                    reasoner.getParents(nid).forEach(parent -> parents.add(StoreDigest.ids(parent)));
                    TreeSet<String> equivalent = new TreeSet<>();
                    reasoner.getEquivalent(nid).forEach(other -> equivalent.add(StoreDigest.ids(other)));
                    parentEdges.addAndGet(parents.size());
                    if (equivalent.size() > 1) {
                        inEquivalenceSets.incrementAndGet();
                    }
                    LogicalExpression normalForm = reasoner.getNecessaryNormalForm(nid);
                    String text = StoreDigest.ids(nid) + " parents " + parents + " equivalent " + equivalent
                            + " form " + (normalForm == null ? "none" : StoreDigest.render(normalForm.sourceGraph()));
                    ByteBuffer buffer = ByteBuffer.wrap(sha256.digest(text.getBytes(StandardCharsets.UTF_8)));
                    hash[0] += buffer.getLong();
                    hash[1] += buffer.getLong();
                });
                out.setProperty(prefix + "classified.concepts", Long.toString(reasoner.getReasonerConceptSet().size()));
                out.setProperty(prefix + "inferred.parent.edges", Long.toString(parentEdges.get()));
                out.setProperty(prefix + "concepts.in.equivalence.sets", Long.toString(inEquivalenceSets.get()));
                out.setProperty(prefix + "inferred.hash", "%016x%016x".formatted(hash[0], hash[1]));
            } finally {
                TestHelper.stopDatabase();
            }
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
}
