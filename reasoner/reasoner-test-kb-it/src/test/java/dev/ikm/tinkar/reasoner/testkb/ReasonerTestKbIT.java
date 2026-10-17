/*
 * Copyright © 2026 IKE Network (support@ike.network)
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
package dev.ikm.tinkar.reasoner.testkb;

import dev.ikm.tinkar.common.id.LongIdSet;
import dev.ikm.tinkar.common.id.LongIds;
import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.common.service.PluggableService;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ServiceKeys;
import dev.ikm.tinkar.common.service.ServiceProperties;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.logic.PremiseType;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.view.ViewCoordinateRecord;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculatorWithCache;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.DiTreeText;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.importer.elk.ReasonerTestKb;
import dev.ikm.tinkar.reasoner.hybrid.HybridReasonerService;
import dev.ikm.tinkar.reasoner.hybrid.IntervalReasonerService;
import dev.ikm.tinkar.reasoner.service.ReasonerService;
import dev.ikm.tinkar.terms.ComponentWithNid;
import dev.ikm.tinkar.terms.KernelPattern;
import org.eclipse.collections.api.set.primitive.ImmutableLongSet;
import org.eclipse.collections.impl.factory.primitive.LongSets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Each case of the reasoner test knowledge base, classified on its own by every reasoner service
 * on the class path: the starter set and the case's module, with every other case's module
 * excluded. Each entity the case expects results for — an inferred navigation semantic in the
 * case's module — must have the expected parents, and the expected equivalents (an inferred
 * equivalence semantic, or none). The expectations are read with a view of the case's module
 * alone, so a classification written into the knowledge base in Komet, under another module,
 * cannot stand in for them.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReasonerTestKbIT {

    private static final Path DATA = Path.of("target", "data");

    /** A case: its module and its path in the ELK corpus. */
    private record TestCase(long moduleNid, String id) {
    }

    /** What a case expects for one entity. */
    private record Expected(long nid, ImmutableLongSet parents, ImmutableLongSet equivalents) {
    }

    private List<TestCase> cases;

    @BeforeAll
    void loadKnowledgeBases() throws Exception {
        CachingService.clearAll();
        ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT,
                Files.createDirectories(Path.of("target", "stores", getClass().getSimpleName())).toFile());
        PrimitiveData.selectControllerByName("Load Ephemeral Store");
        PrimitiveData.start();
        load(DATA.resolve("ike-starter-set-unreasoned-pb.zip").toFile());
        load(DATA.resolve("reasoner-test-kb-unreasoned-pb.zip").toFile());

        Map<String, TestCase> byId = new TreeMap<>();
        ViewCalculator view = Calculators.View.Default();
        view.forEachSemanticVersionOfPattern(KernelPattern.IDENTIFIER_PATTERN.nid(), (version, _) -> {
            if (version.fieldValues().get(0) instanceof ComponentWithNid source
                    && source.nid() == ReasonerTestKb.CASE_IDENTIFIER_SOURCE.nid()) {
                String id = (String) version.fieldValues().get(1);
                byId.put(id, new TestCase(version.referencedComponentNid(), id));
            }
        });
        cases = List.copyOf(byId.values());
        assertFalse(cases.isEmpty(), "the reasoner test knowledge base has cases");
    }

    @AfterAll
    void stop() {
        PrimitiveData.stop();
    }

    private static void load(File file) {
        if (!file.isFile()) {
            fail(file + " is missing; the build copies it into target/data");
        }
        new LoadEntitiesFromProtobufFile(file).compute();
    }

    /**
     * Every reasoner service: those ServiceLoader finds on the class path, and the hybrid module's,
     * which register only for the module path (a class-path registration would change which
     * reasoner a {@code findFirst()} caller gets).
     */
    private static Map<String, Supplier<ReasonerService>> reasoners() {
        Map<String, Supplier<ReasonerService>> reasoners = new TreeMap<>();
        PluggableService.load(ReasonerService.class).stream()
                .forEach(provider -> reasoners.put(provider.type().getSimpleName(), provider::get));
        reasoners.putIfAbsent(HybridReasonerService.class.getSimpleName(), HybridReasonerService::new);
        reasoners.putIfAbsent(IntervalReasonerService.class.getSimpleName(), IntervalReasonerService::new);
        return reasoners;
    }

    @TestFactory
    Stream<DynamicTest> everyCaseClassifiesAsExpected() {
        Map<String, Supplier<ReasonerService>> reasoners = reasoners();
        assertFalse(reasoners.isEmpty(), "a reasoner service is on the class path");
        return reasoners.entrySet().stream().flatMap(reasoner -> cases.stream().map(testCase -> DynamicTest.dynamicTest(
                reasoner.getKey() + ": " + testCase.id(),
                () -> classifyAndCompare(reasoner.getValue().get(), testCase))));
    }

    private void classifyAndCompare(ReasonerService reasoner, TestCase testCase) throws Exception {
        ViewCalculator defaultView = Calculators.View.Default();
        ViewCoordinateRecord defaults = defaultView.viewCoordinateRecord();
        long[] otherCases = cases.stream().mapToLong(TestCase::moduleNid)
                .filter(nid -> nid != testCase.moduleNid()).toArray();
        ViewCalculator caseView = ViewCalculatorWithCache.getCalculator(defaults.withStampCoordinate(
                defaults.stampCoordinate().withExcludedModuleNids(LongIds.set.of(otherCases))));
        // The case's module, and the modules the two patterns are versioned in: a semantic is read
        // only through its pattern's version.
        long[] expectationModules = LongSets.mutable.of(testCase.moduleNid())
                .withAll(LongSets.immutable.of(moduleNidsOf(KernelPattern.INFERRED_NAVIGATION_PATTERN.nid())))
                .withAll(LongSets.immutable.of(moduleNidsOf(KernelPattern.INFERRED_EQUIVALENCE_PATTERN.nid())))
                .toArray();
        ViewCalculator expectationView = ViewCalculatorWithCache.getCalculator(defaults.withStampCoordinate(
                defaults.stampCoordinate().withModuleNids(LongIds.set.of(expectationModules))));

        List<Expected> expected = expectations(expectationView);
        assertFalse(expected.isEmpty(), testCase.id() + " expects results for some entity");

        reasoner.init(caseView, KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN,
                KernelPattern.EL_PLUS_PLUS_INFERRED_AXIOMS_PATTERN);
        try {
            reasoner.extractData(quiet());
            reasoner.loadData(quiet());
            reasoner.computeInferences();
            // Every classification Komet runs goes on to the normal form; it must build.
            reasoner.buildNecessaryNormalForm();
        } catch (Exception e) {
            throw new AssertionError(testCase.id() + " did not classify: " + e.getMessage()
                    + "\nStated:\n" + stated(caseView, expected), e);
        }

        List<String> mismatches = new ArrayList<>();
        for (Expected entity : expected) {
            ImmutableLongSet parents = orEmpty(reasoner.getParents(entity.nid()));
            ImmutableLongSet equivalents = orEmpty(reasoner.getEquivalent(entity.nid())).newWithout(entity.nid());
            if (!parents.equals(entity.parents())) {
                mismatches.add(PrimitiveData.text(entity.nid()) + ": parents " + names(parents)
                        + ", expected " + names(entity.parents()));
            }
            if (!equivalents.equals(entity.equivalents())) {
                mismatches.add(PrimitiveData.text(entity.nid()) + ": equivalents " + names(equivalents)
                        + ", expected " + names(entity.equivalents()));
            }
        }
        if (!mismatches.isEmpty()) {
            fail(testCase.id() + " classified differently than expected:\n  "
                    + String.join("\n  ", mismatches) + "\nStated:\n" + stated(caseView, expected));
        }
    }

    /** Every entity the case expects results for, read in the case's module alone. */
    private static List<Expected> expectations(ViewCalculator expectationView) {
        Map<Long, ImmutableLongSet> equivalents = new TreeMap<>();
        expectationView.forEachSemanticVersionOfPattern(KernelPattern.INFERRED_EQUIVALENCE_PATTERN.nid(),
                (version, _) -> equivalents.put(version.referencedComponentNid(),
                        set((LongIdSet) version.fieldValues().get(0))));
        List<Expected> expected = new ArrayList<>();
        expectationView.forEachSemanticVersionOfPattern(KernelPattern.INFERRED_NAVIGATION_PATTERN.nid(),
                (version, _) -> expected.add(new Expected(version.referencedComponentNid(),
                        set((LongIdSet) version.fieldValues().get(1)),
                        equivalents.getOrDefault(version.referencedComponentNid(), LongSets.immutable.empty()))));
        return expected;
    }

    private static String stated(ViewCalculator view, List<Expected> expected) {
        return expected.stream().map(entity -> {
            Latest<DiTreeEntity> tree = view.getAxiomTreeForEntity(entity.nid(), PremiseType.STATED);
            return PrimitiveData.text(entity.nid()) + ":\n"
                    + (tree.isPresent() ? DiTreeText.tree(tree.get(), PrimitiveData::text) : "(none)");
        }).collect(Collectors.joining("\n"));
    }

    private static long[] moduleNidsOf(long patternNid) {
        return EntityHandle.get(patternNid).expectEntity().versions().stream()
                .mapToLong(version -> version.stamp().moduleNid()).distinct().toArray();
    }

    private static ImmutableLongSet set(LongIdSet ids) {
        return LongSets.immutable.of(ids.toArray());
    }

    private static ImmutableLongSet orEmpty(ImmutableLongSet set) {
        return set == null ? LongSets.immutable.empty() : set;
    }

    private static String names(ImmutableLongSet nids) {
        return Arrays.stream(nids.toSortedArray()).mapToObj(PrimitiveData::text).sorted()
                .collect(Collectors.joining(", ", "{", "}"));
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
