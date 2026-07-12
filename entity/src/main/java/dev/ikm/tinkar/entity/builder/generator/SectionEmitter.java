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
package dev.ikm.tinkar.entity.builder.generator;

import dev.ikm.tinkar.coordinate.language.calculator.LanguageCalculator;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.builder.generator.ComponentDecompiler.ComponentSource;
import dev.ikm.tinkar.entity.builder.generator.TaxonomySectioner.Section;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.EntityProxy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Emits one compilable Java source file per {@link Section} — a package-private class
 * with a static {@code compose(KnowledgeSet)} method, exactly the shape the starter-set
 * scaffold's own section classes take ({@code ConceptSet.compose(KnowledgeSet)} in
 * {@code ike-build-standards}), and one aggregator class implementing
 * {@code KnowledgeSetSource} that calls every section in turn (IKE-Network/
 * ike-issues#869, #873).
 * <p>
 * Every declaration in a section runs under the same declared stamp tuple — the
 * inception epoch — so stamp identity converges across every section file by
 * construction (tuple-derived identity, per {@link dev.ikm.tinkar.entity.builder.Stamp}):
 * no shared stamp constant needs threading between files.
 */
public final class SectionEmitter {

    private SectionEmitter() {
    }

    /**
     * Emits one section class's full source. A component this method cannot emit —
     * today, only a missing fully-qualified-name description — is skipped with a
     * manifest note rather than aborting the whole section: one bad component must
     * not silently discard every other declaration already written into this file.
     *
     * @param packageName the target package
     * @param className   the section class's simple name
     * @param section     the section to emit
     * @param calculator  resolves latest-active state for every member
     * @param languageCalculator resolves the true FQN description — birth-FQN keying
     *                    must use this, never {@link EntityFacade#description()},
     *                    which is not guaranteed to be the fully-qualified form and
     *                    can collide across unrelated concepts that share a synonym
     * @param resolver    resolves concept/pattern references to source expressions
     * @param moduleRef   the source expression for the stamp's module dimension (a
     *                    {@code TinkarTerm} constant or resolver fallback — the set's
     *                    own module concept, once one has been minted)
     * @param authorRef   the source expression for the stamp's author dimension
     * @return the section class's full compilable source text, paired with any
     *         manifest notes — components skipped or field values that needed
     *         hand-authoring
     */
    public static EmittedSection emitSection(String packageName, String className, Section section,
                                             StampCalculator calculator, LanguageCalculator languageCalculator,
                                             TinkarTermReferenceResolver resolver, String moduleRef, String authorRef) {
        StringBuilder source = new StringBuilder();
        List<String> notes = new ArrayList<>();
        source.append("package ").append(packageName).append(";\n\n");
        source.append("import dev.ikm.tinkar.common.id.PublicIds;\n");
        source.append("import dev.ikm.tinkar.common.service.PrimitiveData;\n");
        source.append("import dev.ikm.tinkar.entity.builder.ActiveStamp;\n");
        source.append("import dev.ikm.tinkar.entity.builder.KnowledgeSet;\n");
        source.append("import dev.ikm.tinkar.entity.builder.Stamp;\n");
        source.append("import dev.ikm.tinkar.terms.EntityProxy;\n");
        source.append("import dev.ikm.tinkar.terms.TinkarTerm;\n");
        source.append("import java.time.Instant;\n");
        source.append("import java.util.UUID;\n\n");
        source.append("/** The \"").append(section.name()).append("\" section — a taxonomy subtree of the")
                .append(" retrofitted starter set (IKE-Network/ike-issues#869). */\n");
        source.append("final class ").append(className).append(" {\n\n");
        source.append("    private ").append(className).append("() {\n    }\n\n");
        source.append("    static void compose(KnowledgeSet set) {\n");
        source.append("        ActiveStamp inception = Stamp.active(PrimitiveData.INCEPTION_EPOCH, ")
                .append(authorRef).append(", ").append(moduleRef).append(", TinkarTerm.DEVELOPMENT_PATH);\n\n");

        for (int memberNid : section.members()) {
            emitComponent(memberNid, calculator, languageCalculator, resolver, source, notes, section.name());
        }

        source.append("    }\n}\n");
        return new EmittedSection(source.toString(), List.copyOf(notes));
    }

    private static void emitComponent(int memberNid, StampCalculator calculator,
                                      LanguageCalculator languageCalculator, TinkarTermReferenceResolver resolver,
                                      StringBuilder source, List<String> notes, String sectionName) {
        EntityHandle handle = EntityHandle.get(memberNid);
        boolean isPattern = handle.isPattern();
        EntityFacade component = isPattern ? EntityProxy.Pattern.make(memberNid) : EntityProxy.Concept.make(memberNid);

        // Checked as a value BEFORE decompiling — never as a thrown-and-caught
        // exception. A missing FQN is an anticipated, routine condition (like every
        // other "cannot express this" outcome in this package); using an exception
        // for it would force a catch broad enough to also swallow a genuinely
        // unexpected failure inside decompile() (a malformed axiom tree, for
        // example) under the same "skipped" note, masking a real bug as routine.
        Optional<String> rawFqn = languageCalculator.getFullyQualifiedNameText(component);
        if (rawFqn.isEmpty()) {
            notes.add("Skipped component nid " + memberNid + " in section \"" + sectionName
                    + "\": no fully-qualified-name description — every component must carry one");
            return;
        }

        ComponentSource componentSource = ComponentDecompiler.decompile(component, calculator, resolver);
        notes.addAll(componentSource.manifestNotes());

        String fqn = TinkarTermReferenceResolver.escapeForJavaStringLiteral(rawFqn.get());
        String declaredId = TinkarTermReferenceResolver.publicIdLiteral(component.publicId());
        source.append("        set.").append(isPattern ? "pattern(" : "concept(")
                .append('"').append(fqn).append("\", ").append(declaredId).append(").at(inception)\n");
        for (String verbLine : componentSource.verbLines()) {
            source.append("                ").append(verbLine).append('\n');
        }
        source.append("                ;\n\n");
    }

    /**
     * Emits the aggregator class: a {@code KnowledgeSetSource} implementation that
     * composes one {@code KnowledgeSet.of(setUuid)} and calls every section's
     * {@code compose(KnowledgeSet)} in turn, returning the composed set — the same
     * shape as the starter-set scaffold's own {@code @className@Source}.
     *
     * @param packageName        the target package
     * @param className          the aggregator class's simple name
     * @param setUuid            the knowledge set's own UUID literal
     * @param sectionClassNames  every section class's simple name, in composition order
     * @return the aggregator class's full compilable source text
     */
    public static String emitAggregator(String packageName, String className, String setUuid,
                                        List<String> sectionClassNames) {
        StringBuilder source = new StringBuilder();
        source.append("package ").append(packageName).append(";\n\n");
        source.append("import dev.ikm.tinkar.entity.builder.KnowledgeSet;\n");
        source.append("import dev.ikm.tinkar.entity.builder.KnowledgeSetSource;\n\n");
        source.append("/** Composes every section of the retrofitted starter set (IKE-Network/ike-issues#869). */\n");
        source.append("public final class ").append(className).append(" implements KnowledgeSetSource {\n\n");
        source.append("    public ").append(className).append("() {\n    }\n\n");
        source.append("    @Override\n");
        source.append("    public KnowledgeSet compose() {\n");
        source.append("        KnowledgeSet set = KnowledgeSet.of(\"").append(setUuid).append("\");\n");
        for (String sectionClassName : sectionClassNames) {
            source.append("        ").append(sectionClassName).append(".compose(set);\n");
        }
        source.append("        return set;\n");
        source.append("    }\n}\n");
        return source.toString();
    }

    /**
     * One section class's emitted source, paired with any manifest notes — a
     * component skipped for lacking a fully-qualified-name description, or a field
     * value {@link ComponentDecompiler} could not express.
     *
     * @param source        the section class's full compilable source text
     * @param manifestNotes anything this emission could not express — never silent
     */
    public record EmittedSection(String source, List<String> manifestNotes) {
    }
}
