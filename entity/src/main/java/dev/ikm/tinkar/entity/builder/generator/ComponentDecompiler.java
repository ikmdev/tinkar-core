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

import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.FieldDefinitionForEntity;
import dev.ikm.tinkar.entity.PatternEntityVersion;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.PatternFacade;
import dev.ikm.tinkar.terms.TinkarTerm;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Decompiles one component's latest-active state — every semantic the calculator
 * resolves as current — into the ledger builder's declared-identity verb calls
 * (IKE-Network/ike-issues#869): identity-exact ingest, one inception stamp, latest
 * state only. Derived semantics (stated/inferred navigation, inferred axioms) are
 * excluded — regenerated at assembly, per the IKE-Network/ike-issues#872 ruling; the
 * #873 sectioning scan's own finding (the artifact's stated-navigation layer is itself
 * incomplete) is exactly why regeneration, not ingestion, is the right call.
 * <p>
 * Emits Java source LINES, not a compiled AST — the section emitter
 * (IKE-Network/ike-issues#869) assembles them into a compilable file. A line is either
 * a fluent verb call ({@code .semantic(...)}, {@code .isA(...)}) or, for content this
 * decompiler cannot express, a {@code // TODO} comment naming what needs hand
 * authoring — never a silent omission.
 */
public final class ComponentDecompiler {

    private ComponentDecompiler() {
    }

    /**
     * Decompiles one component (concept or pattern) into its declared-identity verb
     * lines, using the calculator's latest-active resolution for every semantic
     * attached to it (and, for each description found, the dialects attached to that
     * description).
     *
     * @param component  the concept or pattern to decompile
     * @param calculator resolves latest-active versions — never raw {@code versions()}.
     *                   Must use the same coordinate as any {@link TaxonomySectioner}
     *                   built alongside it (nothing enforces this — a mismatched pair
     *                   would silently section by one view of "current" while
     *                   declaring content from another)
     * @param resolver   resolves concept/pattern references to source expressions
     * @return the verb-call lines (in discovery order) and any hand-authoring notes
     */
    public static ComponentSource decompile(EntityFacade component, StampCalculator calculator,
                                            TinkarTermReferenceResolver resolver) {
        List<String> lines = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<EntityFacade> descriptions = new ArrayList<>();

        // EntityFacade.make(nid) returns a generic, kind-less wrapper — instanceof
        // PatternFacade would never match it regardless of the underlying entity's
        // real kind, so the store itself (not the facade's static type) decides.
        if (EntityHandle.get(component.nid()).isPattern()) {
            decompilePatternDefinition(EntityProxy.Pattern.make(component.nid()), calculator, resolver, lines, notes);
        }

        calculator.forEachSemanticVersionForComponent(component, (semanticVersion, entityVersion) -> {
            int patternNid = semanticVersion.patternNid();
            if (patternNid == TinkarTerm.STATED_NAVIGATION_PATTERN.nid()
                    || patternNid == TinkarTerm.INFERRED_NAVIGATION_PATTERN.nid()
                    || patternNid == TinkarTerm.EL_PLUS_PLUS_INFERRED_AXIOMS_PATTERN.nid()) {
                return; // Derived — excluded, regenerated at assembly (#872).
            }
            if (patternNid == TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid()) {
                decompileAxioms(semanticVersion, resolver, lines, notes);
                return;
            }
            if (patternNid == TinkarTerm.DESCRIPTION_PATTERN.nid()) {
                descriptions.add(EntityFacade.make(semanticVersion.chronology().nid()));
            }
            decompileGenericSemantic(semanticVersion, resolver, lines, notes);
        });

        for (EntityFacade description : descriptions) {
            decompileDialects(description, calculator, resolver, lines, notes);
        }

        return new ComponentSource(List.copyOf(lines), List.copyOf(notes));
    }

    /**
     * Decompiles a pattern's own structural definition — meaning, purpose, and field
     * definitions — into one {@code .meaning(...).purpose(...).field(...)...} verb
     * line. Every pattern in the starter set carries exactly one such definition;
     * absence means the pattern has no latest-active version, which this method
     * reports as a manifest note rather than silently emitting nothing (a pattern
     * with no definition cannot compile as a ledger declaration at all).
     */
    private static void decompilePatternDefinition(PatternFacade patternFacade, StampCalculator calculator,
                                                    TinkarTermReferenceResolver resolver, List<String> lines,
                                                    List<String> notes) {
        Latest<PatternEntityVersion> latest = calculator.latestPatternEntityVersion(patternFacade);
        if (!latest.isPresent()) {
            notes.add("Pattern " + patternFacade.description() + " has no latest-active definition version");
            return;
        }
        PatternEntityVersion version = latest.get();
        String meaningRef = resolver.resolve(EntityFacade.make(version.semanticMeaningNid())).sourceExpression();
        String purposeRef = resolver.resolve(EntityFacade.make(version.semanticPurposeNid())).sourceExpression();
        StringBuilder line = new StringBuilder(".meaning(").append(meaningRef).append(").purpose(").append(purposeRef).append(')');
        for (FieldDefinitionForEntity field : version.fieldDefinitions()) {
            String fieldMeaning = resolver.resolve(EntityFacade.make(field.meaningNid())).sourceExpression();
            String fieldPurpose = resolver.resolve(EntityFacade.make(field.purposeNid())).sourceExpression();
            String fieldDataType = resolver.resolve(EntityFacade.make(field.dataTypeNid())).sourceExpression();
            line.append(".field(").append(fieldMeaning).append(", ").append(fieldPurpose)
                    .append(", ").append(fieldDataType).append(')');
        }
        lines.add(line.toString());
    }

    private static void decompileAxioms(SemanticEntityVersion semanticVersion, TinkarTermReferenceResolver resolver,
                                        List<String> lines, List<String> notes) {
        DiTreeEntity tree = (DiTreeEntity) semanticVersion.fieldValues().get(0);
        AxiomDecompiler.Result result = AxiomDecompiler.decompile(tree);
        String declaredId = TinkarTermReferenceResolver.publicIdLiteral(semanticVersion.chronology().publicId());
        if (result.simpleIsA()) {
            // Declared identity via statedAxioms(PublicId, Consumer) — NOT
            // isA(ConceptFacade...), which is derived-identity only. There is no
            // declared-identity isA overload: EntityProxy implements both
            // ConceptFacade and PublicId, so isA(PublicId, ConceptFacade...) would be
            // genuinely ambiguous against isA(ConceptFacade...) for every real
            // argument (a structural conflict, not a naming one) — the verbose form
            // below is the only unambiguous way to declare an is-a axiom's identity.
            String conceptAxioms = result.parents().stream()
                    .map(parent -> "leb.ConceptAxiom(" + resolver.resolve(parent).sourceExpression() + ")")
                    .reduce((first, second) -> first + ", " + second)
                    .orElseThrow();
            lines.add(".statedAxioms(" + declaredId + ", leb -> leb.NecessarySet(leb.And("
                    + conceptAxioms + ")))");
            return;
        }
        notes.add("Stated axioms on " + semanticVersion.referencedComponentNid()
                + " are not the simple isA shape — hand-author statedAxioms(" + declaredId + ", ...):\n"
                + result.diagnosticDump());
        lines.add("// TODO hand-author statedAxioms(" + declaredId + ", ...) — see generator manifest");
    }

    private static void decompileDialects(EntityFacade description, StampCalculator calculator,
                                          TinkarTermReferenceResolver resolver, List<String> lines,
                                          List<String> notes) {
        calculator.forEachSemanticVersionForComponentOfPattern(description, TinkarTerm.US_DIALECT_PATTERN,
                (semanticVersion, entityVersion, patternVersion) ->
                        emitSemanticOn(description, semanticVersion, TinkarTerm.US_DIALECT_PATTERN, resolver, lines, notes));
        calculator.forEachSemanticVersionForComponentOfPattern(description, TinkarTerm.GB_DIALECT_PATTERN,
                (semanticVersion, entityVersion, patternVersion) ->
                        emitSemanticOn(description, semanticVersion, TinkarTerm.GB_DIALECT_PATTERN, resolver, lines, notes));
    }

    private static void decompileGenericSemantic(SemanticEntityVersion semanticVersion,
                                                 TinkarTermReferenceResolver resolver, List<String> lines,
                                                 List<String> notes) {
        // patternNid() is always a pattern — EntityFacade.make(nid) returns a
        // kind-less wrapper that is never instanceof PatternFacade, so wrapping it
        // that way would make the resolver's fallback emit EntityProxy.Concept.make(...)
        // for what is actually a pattern reference (uncompilable generated source,
        // the same hazard the isPattern()-detection comment above warns about).
        EntityFacade patternRef = EntityProxy.Pattern.make(semanticVersion.patternNid());
        emitGenericSemantic(null, semanticVersion, patternRef, resolver, lines, notes);
    }

    private static void emitSemanticOn(EntityFacade referencedComponent, SemanticEntityVersion semanticVersion,
                                       EntityFacade patternConstant, TinkarTermReferenceResolver resolver,
                                       List<String> lines, List<String> notes) {
        emitGenericSemantic(referencedComponent, semanticVersion, patternConstant, resolver, lines, notes);
    }

    /**
     * Emits one generic declared-identity semantic verb line — {@code .semantic(...)}
     * when {@code referencedComponent} is null (the semantic references its own
     * component), {@code .semanticOn(...)} otherwise. Every field is serialized
     * before any text is appended: if even one field's value type is unsupported,
     * the whole line becomes a hand-authoring placeholder comment (matching the
     * axiom fallback's convention) rather than a live verb call with a {@code null}
     * literal mixed in among real arguments — a {@code null} would compile cleanly
     * but throw at replay time ({@code ComponentLedger} rejects null field values),
     * failing the entire component's declaration statement for one bad field
     * instead of degrading gracefully.
     */
    private static void emitGenericSemantic(EntityFacade referencedComponent, SemanticEntityVersion semanticVersion,
                                            EntityFacade patternFacade, TinkarTermReferenceResolver resolver,
                                            List<String> lines, List<String> notes) {
        String declaredId = TinkarTermReferenceResolver.publicIdLiteral(semanticVersion.chronology().publicId());
        String patternRef = resolver.resolve(patternFacade).sourceExpression();
        String referencedId = referencedComponent == null ? null
                : TinkarTermReferenceResolver.publicIdLiteral(referencedComponent.publicId());
        String location = referencedId == null ? declaredId : declaredId + " (on " + referencedId + ")";

        // Every field is scanned before any decision is made — collecting a note for
        // EACH unsupported field, not just the first — so a semantic with more than
        // one hand-authoring-needed field never loses all but its first problem from
        // the manifest (the class's own contract: "never a silent omission").
        List<String> fieldExpressions = new ArrayList<>();
        List<String> unsupportedFields = new ArrayList<>();
        for (int index = 0; index < semanticVersion.fieldValues().size(); index++) {
            Object value = semanticVersion.fieldValues().get(index);
            try {
                fieldExpressions.add(fieldValueExpression(value, resolver));
            } catch (IllegalArgumentException e) {
                notes.add("Semantic " + location + " field " + index + " needs hand authoring: " + e.getMessage());
                unsupportedFields.add("field " + index + ": " + e.getMessage());
            }
        }
        if (!unsupportedFields.isEmpty()) {
            String verb = referencedId == null
                    ? ".semantic(" + patternRef + ", " + declaredId + ", /* ... */)"
                    : ".semanticOn(" + referencedId + ", " + patternRef + ", " + declaredId + ", /* ... */)";
            lines.add("// TODO hand-author " + verb + " — " + String.join("; ", unsupportedFields));
            return;
        }
        String fieldArgs = fieldExpressions.isEmpty() ? "" : ", " + String.join(", ", fieldExpressions);
        lines.add(referencedId == null
                ? ".semantic(" + patternRef + ", " + declaredId + fieldArgs + ")"
                : ".semanticOn(" + referencedId + ", " + patternRef + ", " + declaredId + fieldArgs + ")");
    }

    /**
     * Serializes one field value to a Java source expression. Supports the value
     * types this starter set's own semantics actually carry — {@link EntityFacade},
     * {@link String}, {@link Instant} — and reports (never guesses at) anything else,
     * so a future starter set's unsupported field type surfaces as a manifest note,
     * not a silently wrong or dropped value.
     */
    private static String fieldValueExpression(Object value, TinkarTermReferenceResolver resolver) {
        if (value instanceof EntityFacade facade) {
            return resolver.resolve(facade).sourceExpression();
        }
        if (value instanceof String text) {
            return '"' + TinkarTermReferenceResolver.escapeForJavaStringLiteral(text) + '"';
        }
        if (value instanceof Instant instant) {
            return "Instant.parse(\"" + instant + "\")";
        }
        throw new IllegalArgumentException("unsupported field value type: "
                + (value == null ? "null" : value.getClass().getName()));
    }

    /**
     * One component's decompiled verb-call lines and any hand-authoring notes.
     *
     * @param verbLines     the {@code .semantic(...)}/{@code .semanticOn(...)}/
     *                      {@code .isA(...)} calls, in discovery order
     * @param manifestNotes anything this decompiler could not express — never silent
     */
    public record ComponentSource(List<String> verbLines, List<String> manifestNotes) {
    }
}
