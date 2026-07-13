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
package dev.ikm.tinkar.entity.builder;

import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.language.calculator.LanguageCalculator;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.entity.builder.generator.SectionEmitter;
import dev.ikm.tinkar.entity.builder.generator.TaxonomySectioner;
import dev.ikm.tinkar.entity.builder.generator.TaxonomySectioner.Section;
import dev.ikm.tinkar.entity.builder.generator.TinkarTermReferenceResolver;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.terms.TinkarTerm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The full-set ledger-ingest entry point: loads a real protobuf artifact into an
 * ephemeral store, decompiles it via the #869 generator ({@link TaxonomySectioner} +
 * {@link SectionEmitter}), and writes one compilable Java source file per section —
 * turning {@code GeneratorEndToEndIT}'s proven, in-memory round trip into real,
 * persisted ledger source for a genesis repo's own ingested-foundation section
 * (IKE-Network/ike-issues#872).
 * <p>
 * Like {@link KbAssembleMain}/{@link BindingsMain}, a reflective seam for build
 * tooling: run via {@code exec:java} over a classpath carrying the entity, executor,
 * and ephemeral-store providers with their legacy {@code META-INF/services}
 * registrations. Unlike those, this is intentionally a one-time-per-target-repo
 * operation, not a repeated build step — the ingested-foundation section it produces
 * is regeneration-locked once landed (declared identities, not re-derived on every
 * build; future upstream updates arrive as change sets via the #847 lift machinery,
 * never by re-running this generator).
 * <p>
 * Arguments: {@code pbZip outputSourceRoot packageName moduleRef authorRef}.
 * {@code moduleRef}/{@code authorRef} are fully-qualified Java source expressions
 * (e.g. {@code network.ike.foundation.ike.terms.Ike.MODULE}) spliced literally into
 * every generated section's stamp — see {@link SectionEmitter#emitSection}.
 */
public final class LedgerGeneratorMain {

    private LedgerGeneratorMain() {
    }

    /**
     * Generates one section source file per taxonomy bucket (plus a residual
     * catch-all), and a delegating aggregator that composes them all onto a
     * caller-supplied {@code KnowledgeSet}.
     *
     * @param args {@code pbZip outputSourceRoot packageName moduleRef authorRef}
     * @throws Exception if the store cannot be opened, the artifact fails to load, a
     *                    section cannot be written, or any component needed hand
     *                    authoring (zero tolerance for a silently-skipped component in
     *                    a real ingest)
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException("Usage: LedgerGeneratorMain <pbZip> <outputSourceRoot>"
                    + " <packageName> <moduleRef> <authorRef>");
        }
        File pbZip = new File(args[0]);
        Path outputSourceRoot = Path.of(args[1]);
        String packageName = args[2];
        String moduleRef = args[3];
        String authorRef = args[4];
        if (!pbZip.isFile()) {
            throw new IllegalArgumentException("Not a file: " + pbZip);
        }

        CachingService.clearAll();
        PrimitiveData.selectControllerByName("Load Ephemeral Store");
        PrimitiveData.start();
        List<String> sectionClassNames = new ArrayList<>();
        int distinctMembers;
        try {
            new LoadEntitiesFromProtobufFile(pbZip).compute();

            StampCalculator calculator = Calculators.Stamp.DevelopmentLatestActiveOnly();
            TinkarTermReferenceResolver resolver = TinkarTermReferenceResolver.build();
            LanguageCalculator languageCalculator =
                    Calculators.Language.UsEnglishFullyQualifiedName(calculator.stampCoordinate());
            TaxonomySectioner sectioner = TaxonomySectioner.fromStatedNavigation(calculator);

            List<Section> sections = sectioner.sectionsCoveringFullStore(
                    TinkarTerm.ROOT_VERTEX.nid(), 60, 4, 50);
            distinctMembers = sections.stream().mapToInt(section -> section.members().size()).sum();

            List<String> emissionNotes = new ArrayList<>();
            int index = 0;
            for (Section section : sections) {
                index++;
                if (section.members().isEmpty()) {
                    continue;
                }
                String className = "Section" + index;
                sectionClassNames.add(className);
                SectionEmitter.EmittedSection emitted = SectionEmitter.emitSection(packageName, className, section,
                        calculator, languageCalculator, resolver, moduleRef, authorRef);
                emissionNotes.addAll(emitted.manifestNotes());
                writeSourceFile(outputSourceRoot, packageName, className, emitted.source());
            }
            if (!emissionNotes.isEmpty()) {
                throw new IllegalStateException("Zero tolerance for a silently-skipped component in a real"
                        + " ingest — " + emissionNotes.size() + " manifest note(s):\n"
                        + String.join("\n", emissionNotes));
            }

            String aggregatorSource = emitDelegatingAggregator(packageName, sectionClassNames);
            writeSourceFile(outputSourceRoot, packageName, "FoundationSet", aggregatorSource);
        } finally {
            PrimitiveData.stop();
        }
        System.out.println("Ledger generated: " + sectionClassNames.size() + " sections, "
                + distinctMembers + " distinct components, into " + outputSourceRoot);
    }

    /**
     * Emits the delegating aggregator: unlike {@link SectionEmitter#emitAggregator}
     * (which mints its own fresh {@code KnowledgeSet}), this composes every section
     * onto a {@code KnowledgeSet} the caller already created — a genesis repo's own
     * session-root constant, not a new identity.
     *
     * @param packageName       the target package
     * @param sectionClassNames every emitted section class's simple name, in
     *                          composition order
     * @return the aggregator class's full compilable source text
     */
    private static String emitDelegatingAggregator(String packageName, List<String> sectionClassNames) {
        StringBuilder source = new StringBuilder();
        source.append("package ").append(packageName).append(";\n\n");
        source.append("import dev.ikm.tinkar.entity.builder.KnowledgeSet;\n\n");
        source.append("/** Composes every ingested-foundation section onto the caller's KnowledgeSet"
                + " (IKE-Network/ike-issues#872). */\n");
        source.append("public final class FoundationSet {\n\n");
        source.append("    private FoundationSet() {\n    }\n\n");
        source.append("    public static void compose(KnowledgeSet set) {\n");
        for (String sectionClassName : sectionClassNames) {
            source.append("        ").append(sectionClassName).append(".compose(set);\n");
        }
        source.append("    }\n}\n");
        return source.toString();
    }

    private static void writeSourceFile(Path sourceRoot, String packageName, String className, String source)
            throws IOException {
        Path packageDir = sourceRoot.resolve(packageName.replace('.', '/'));
        Files.createDirectories(packageDir);
        Files.writeString(packageDir.resolve(className + ".java"), source);
    }
}
