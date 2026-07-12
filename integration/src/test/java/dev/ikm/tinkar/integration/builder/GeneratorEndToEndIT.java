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
package dev.ikm.tinkar.integration.builder;

import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.language.calculator.LanguageCalculator;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.builder.KnowledgeSet;
import dev.ikm.tinkar.entity.builder.KnowledgeSetSource;
import dev.ikm.tinkar.entity.builder.generator.SectionEmitter;
import dev.ikm.tinkar.entity.builder.generator.TaxonomySectioner;
import dev.ikm.tinkar.entity.builder.generator.TaxonomySectioner.Section;
import dev.ikm.tinkar.entity.builder.generator.TinkarTermReferenceResolver;
import dev.ikm.tinkar.integration.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The full ledger-generator pipeline (IKE-Network/ike-issues#869), proven end to end
 * against the real unreasoned starter set in ONE store lifecycle: taxonomy-section →
 * emit Java source → compile in-process → load → {@code compose()} → {@code write()}
 * back into the SAME running store the source was decompiled from.
 * <p>
 * One store, not two: a second {@code PrimitiveData} lifecycle cannot start once the
 * first stops within a JVM (a documented platform limitation — see the integration
 * module's other store-lifecycle tests). Writing the generated ledger into the store
 * it was read from is a stronger proof than a fresh comparison store would be: because
 * every identity is declared and adopted exactly, replaying the generated content must
 * be an in-place merge — no new components minted, existing entities gain exactly one
 * new (inception) version, and every value the round trip carries must already match
 * what the calculator resolved as current. Any drift there is real corruption, not
 * measurement noise from two independently-loaded stores.
 */
class GeneratorEndToEndIT {

    private static final Logger LOG = LoggerFactory.getLogger(GeneratorEndToEndIT.class);

    @BeforeAll
    static void loadUnreasonedStarterSet() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA);
    }

    @AfterAll
    static void stop() {
        TestHelper.stopDatabase();
    }

    @Test
    @DisplayName("Generate, compile, load, compose, and replay the full starter set — identity-exact, no drift")
    void generateCompileReplayVerify() throws Exception {
        StampCalculator calculator = Calculators.Stamp.DevelopmentLatestActiveOnly();
        TinkarTermReferenceResolver resolver = TinkarTermReferenceResolver.build();
        LanguageCalculator languageCalculator = Calculators.Language.UsEnglishFullyQualifiedName(calculator.stampCoordinate());
        TaxonomySectioner sectioner = TaxonomySectioner.fromStatedNavigation(calculator);

        int conceptsBefore = countConcepts();
        int patternsBefore = countPatterns();
        int modelConceptVersionsBefore = EntityHandle.get(TinkarTerm.MODEL_CONCEPT.nid()).expectConcept()
                .versions().size();
        int userModuleVersionsBefore = EntityHandle.get(findByName("User module")).expectConcept()
                .versions().size();
        // Content, not just counts: a field-order or wrong-parent bug would produce
        // structurally valid, compiling, type-correct output while silently writing
        // the WRONG value into the RIGHT-shaped slot — no count-based assertion would
        // ever catch that. These snapshot actual FQN text and axiom parents so the
        // "after" checks below compare content, not just cardinality.
        int userModuleNid = findByName("User module");
        String userModuleFqnBefore = languageCalculator.getFullyQualifiedNameText(
                dev.ikm.tinkar.terms.EntityProxy.Concept.make(userModuleNid)).orElseThrow();
        String modelConceptFqnBefore = languageCalculator.getFullyQualifiedNameText(TinkarTerm.MODEL_CONCEPT)
                .orElseThrow();
        Set<Integer> userModuleParentsBefore = latestIsAParents(userModuleNid, calculator);
        int descriptionPatternVersionsBefore = EntityHandle.get(TinkarTerm.DESCRIPTION_PATTERN.nid()).expectPattern()
                .versions().size();

        List<Section> sections = sectioner.sectionsUnder(TinkarTerm.ROOT_VERTEX.nid(), 60, 4);
        int totalMembers = sections.stream().mapToInt(section -> section.members().size()).sum();
        LOG.info("Sectioned into {} files covering {} component slots", sections.size(), totalMembers);

        // Sections are not disjoint by design (TaxonomySectioner's own contract) — a
        // dual-parented concept is a member of every section whose root reaches it.
        // The generator must still declare each component exactly once: first section
        // to claim it wins, matching "the caller resolves as primary" in
        // TaxonomySectioner's javadoc.
        Set<Integer> assigned = new HashSet<>();
        List<Section> exclusiveSections = new ArrayList<>();
        for (Section section : sections) {
            List<Integer> exclusiveMembers = section.members().stream().filter(assigned::add).toList();
            exclusiveSections.add(new Section(section.rootNid(), exclusiveMembers));
        }
        // TaxonomySectioner only walks concepts reachable via stated navigation — the
        // ~60 unanchored meta-schema concepts the #873 scan found, and all 28
        // patterns (an entirely separate taxonomy), need a residual catch-all so the
        // round trip actually covers the full store, not just the navigable subset.
        List<Integer> residualMembers = new ArrayList<>();
        EntityService.get().forEachConceptEntity(concept -> {
            if (assigned.add(concept.nid())) {
                residualMembers.add(concept.nid());
            }
        });
        EntityService.get().forEachPatternEntity(pattern -> {
            if (assigned.add(pattern.nid())) {
                residualMembers.add(pattern.nid());
            }
        });
        // Chunked, not one section: a single compose() method over ~90 components can
        // approach the same 64KB bytecode-per-method limit that forced deeper taxonomy
        // splitting above.
        int batchSize = 50;
        for (int start = 0; start < residualMembers.size(); start += batchSize) {
            List<Integer> batch = residualMembers.subList(start, Math.min(start + batchSize, residualMembers.size()));
            exclusiveSections.add(new Section(TinkarTerm.ROOT_VERTEX.nid(), List.copyOf(batch)));
        }
        LOG.info("{} unanchored/pattern components in the residual catch-all section", residualMembers.size());

        int distinctMembers = assigned.size();
        LOG.info("{} distinct components after cross-section dedup", distinctMembers);

        Path sourceDir = Files.createTempDirectory("ledger-generator-it-src");
        Path classesDir = Files.createTempDirectory("ledger-generator-it-classes");
        String packageName = "network.ike.generatorit.sections";
        List<String> sectionClassNames = new ArrayList<>();
        List<String> emissionNotes = new ArrayList<>();
        int index = 0;
        for (Section section : exclusiveSections) {
            index++;
            if (section.members().isEmpty()) {
                continue;
            }
            String className = "Section" + index;
            sectionClassNames.add(className);
            SectionEmitter.EmittedSection emitted = SectionEmitter.emitSection(packageName, className, section,
                    calculator, languageCalculator, resolver, "TinkarTerm.DEVELOPMENT_MODULE", "TinkarTerm.USER");
            emissionNotes.addAll(emitted.manifestNotes());
            writeSourceFile(sourceDir, packageName, className, emitted.source());
        }
        assertEquals(List.of(), emissionNotes, "expected zero manifest notes for this starter set");
        String aggregatorClassName = "GeneratedStarterKnowledgeSource";
        String aggregatorSource = SectionEmitter.emitAggregator(packageName, aggregatorClassName,
                UUID.randomUUID().toString(), sectionClassNames);
        writeSourceFile(sourceDir, packageName, aggregatorClassName, aggregatorSource);

        compile(sourceDir, classesDir);

        try (URLClassLoader loader = new URLClassLoader(new URL[]{classesDir.toUri().toURL()},
                Thread.currentThread().getContextClassLoader())) {
            Class<?> aggregatorClass = loader.loadClass(packageName + "." + aggregatorClassName);
            KnowledgeSetSource generatedSource = (KnowledgeSetSource) aggregatorClass.getDeclaredConstructor().newInstance();
            KnowledgeSet set = generatedSource.compose();
            assertEquals(distinctMembers, set.declarations().size(),
                    "every distinct component (post cross-section dedup) is declared exactly once");
            set.write();
        }

        int conceptsAfter = countConcepts();
        int patternsAfter = countPatterns();
        assertEquals(conceptsBefore, conceptsAfter, "identity-exact ingest mints no new concepts");
        assertEquals(patternsBefore, patternsAfter, "identity-exact ingest mints no new patterns");

        int modelConceptVersionsAfter = EntityHandle.get(TinkarTerm.MODEL_CONCEPT.nid()).expectConcept()
                .versions().size();
        assertEquals(modelConceptVersionsBefore + 1, modelConceptVersionsAfter,
                "the round trip adds exactly one new (inception) version — a true merge, not a replace");

        int userModuleVersionsAfter = EntityHandle.get(findByName("User module")).expectConcept().versions().size();
        assertEquals(userModuleVersionsBefore + 1, userModuleVersionsAfter,
                "a leaf concept from a split section also merges cleanly");

        int descriptionPatternVersionsAfter = EntityHandle.get(TinkarTerm.DESCRIPTION_PATTERN.nid()).expectPattern()
                .versions().size();
        assertEquals(descriptionPatternVersionsBefore + 1, descriptionPatternVersionsAfter,
                "a pattern from the residual catch-all also merges cleanly, meaning and purpose intact");

        assertEquals(407, distinctMembers, "the residual catch-all closes the gap to full-store coverage");

        // Content checks: the calculator-resolved latest-active state after the round
        // trip must carry the SAME FQN text and the SAME isA parents as before — not
        // merely "a version was added" or "the count is unchanged". This is what
        // would actually catch a field-order or wrong-parent decompiler bug.
        String userModuleFqnAfter = languageCalculator.getFullyQualifiedNameText(
                dev.ikm.tinkar.terms.EntityProxy.Concept.make(userModuleNid)).orElseThrow();
        assertEquals(userModuleFqnBefore, userModuleFqnAfter,
                "the round trip must not change the calculator-resolved FQN text");
        String modelConceptFqnAfter = languageCalculator.getFullyQualifiedNameText(TinkarTerm.MODEL_CONCEPT)
                .orElseThrow();
        assertEquals(modelConceptFqnBefore, modelConceptFqnAfter,
                "the round trip must not change the calculator-resolved FQN text");
        Set<Integer> userModuleParentsAfter = latestIsAParents(userModuleNid, calculator);
        assertEquals(userModuleParentsBefore, userModuleParentsAfter,
                "the round trip must not change the calculator-resolved latest isA parents");

        LOG.info("Round trip verified: {} concepts, {} patterns unchanged; sampled concepts and one pattern"
                + " each gained exactly one inception version; FQN text and isA parents unchanged for"
                + " sampled concepts", conceptsAfter, patternsAfter);
    }

    /** The latest-active stated-axiom semantic's isA parent nids for one component, if simple isA. */
    private static Set<Integer> latestIsAParents(int componentNid, StampCalculator calculator) {
        Set<Integer> parents = new HashSet<>();
        calculator.forEachSemanticVersionForComponentOfPattern(
                dev.ikm.tinkar.terms.EntityProxy.Concept.make(componentNid),
                TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN,
                (semanticVersion, entityVersion, patternVersion) -> {
                    dev.ikm.tinkar.entity.graph.DiTreeEntity tree =
                            (dev.ikm.tinkar.entity.graph.DiTreeEntity) semanticVersion.fieldValues().get(0);
                    dev.ikm.tinkar.entity.builder.generator.AxiomDecompiler.Result result =
                            dev.ikm.tinkar.entity.builder.generator.AxiomDecompiler.decompile(tree);
                    if (result.simpleIsA()) {
                        result.parents().forEach(parent -> parents.add(parent.nid()));
                    }
                });
        return parents;
    }

    private static int countConcepts() {
        int[] count = {0};
        EntityService.get().forEachConceptEntity(concept -> count[0]++);
        return count[0];
    }

    private static int countPatterns() {
        int[] count = {0};
        EntityService.get().forEachPatternEntity(pattern -> count[0]++);
        return count[0];
    }

    private static int findByName(String name) {
        int[] found = {-1};
        EntityService.get().forEachConceptEntity(concept -> {
            if (found[0] == -1 && name.equals(dev.ikm.tinkar.common.service.PrimitiveData.text(concept.nid()))) {
                found[0] = concept.nid();
            }
        });
        if (found[0] == -1) {
            throw new IllegalStateException("No concept named \"" + name + "\" in the loaded store");
        }
        return found[0];
    }

    private static void writeSourceFile(Path sourceDir, String packageName, String className, String source)
            throws IOException {
        Path packageDir = sourceDir.resolve(packageName.replace('.', '/'));
        Files.createDirectories(packageDir);
        Files.writeString(packageDir.resolve(className + ".java"), source);
    }

    private static void compile(Path sourceDir, Path classesDir) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            List<Path> javaFiles;
            try (java.util.stream.Stream<Path> walk = Files.walk(sourceDir)) {
                javaFiles = walk.filter(path -> path.toString().endsWith(".java")).toList();
            }
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(javaFiles);
            List<String> options = List.of(
                    "-cp", System.getProperty("java.class.path"),
                    "--enable-preview",
                    "--release", Integer.toString(Runtime.version().feature()),
                    "-d", classesDir.toString());
            boolean success = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
            if (!success) {
                StringBuilder message = new StringBuilder("Generated source failed to compile:\n");
                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    message.append(diagnostic).append('\n');
                }
                fail(message.toString());
            }
        }
    }
}
