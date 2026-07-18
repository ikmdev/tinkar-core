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

import dev.ikm.tinkar.common.id.IntIdList;
import dev.ikm.tinkar.common.id.IntIdSet;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.time.DateTimeUtil;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.FieldDefinitionForEntity;
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.entity.PatternEntityVersion;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.builder.generator.TaxonomySectioner;
import dev.ikm.tinkar.entity.builder.generator.TaxonomySectioner.Section;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiom;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpression;
import dev.ikm.tinkar.terms.DefaultsTemplateTerm;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.State;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.list.ImmutableList;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Extracts the standard koncept definitions YAML from a <em>loaded knowledge base</em> —
 * the store the change set is already materialized into by the exporting service. It reads
 * the real content directly from the description and stated-axiom semantics — no view
 * coordinates required for that part, so it works on a bare set-only store whose
 * coordinate concepts are referenced but not present. This is the one standardized
 * extraction; it works for any store, whether loaded from a ledger's ephemeral write or an
 * imported change set — not a ledger-specific path.
 * <p>
 * Section grouping ({@link TaxonomySectioner}) and the active/retired distinction on
 * comments genuinely need a resolved "current state," so those two facets alone use a
 * {@link StampCalculator} — the same {@code DevelopmentLatestActiveOnly} coordinate the
 * #869 ledger generator already resolves this store against (IKE-Network/ike-issues#877),
 * so a store reaching this method already has the Development-path anchor that coordinate
 * needs. The description/axiom reading below is untouched by this and stays
 * coordinate-independent, exactly as before.
 * <p>
 * The produced YAML is the {@code koncept-asciidoc-extension} definition source (label,
 * definition, DL {@code axiom}, {@code broader} parent identifiers, {@code section},
 * {@code since}, {@code comments}, {@code retiredComments}, {@code narrative}, {@code uuids},
 * {@code kind}); the extension renders it into the comprehensive standard glossary. A
 * {@code kind: pattern} entry additionally carries {@code referencedComponentMeaning}/
 * {@code referencedComponentPurpose} (what a semantic of this pattern's referenced component
 * means and is for) and {@code fields} (each field's own {@code meaning}/{@code purpose}/
 * {@code dataType}) -- see {@link #patternShape}.
 * <p>
 * {@code narrative} -- curated, long-form AsciiDoc prose (as opposed to the short
 * {@code definition} gloss) -- is deliberately not read by the no-arg {@link #extractYaml()}:
 * the pattern such content lives on (for example {@code IKE-Network/ike-issues#879}'s
 * "Prose element pattern (RichSurfaceTerms)") is an IKE-ecosystem convention minted outside
 * tinkar-core, not part of upstream Tinkar, so this class stays agnostic about it by default.
 * A caller that knows which pattern its store uses for narrative content passes its nid to
 * {@link #extractYaml(Integer)}.
 * <p>
 * A component is in the set when it has a fully qualified name description in this store —
 * referenced-but-unwritten externals (for example {@code TinkarTerm} parents) carry a nid
 * but no description here, so they are naturally excluded.
 */
public final class KonceptExtractor {

    private KonceptExtractor() {
    }

    private static final int FIELD_TEXT = 1;
    private static final int FIELD_TYPE = 3;
    private static final int COMMENT_FIELD_TEXT = 0;
    private static final int NARRATIVE_FIELD_TEXT = 0;

    // Same section shape already proven against this store by LedgerGeneratorMain /
    // GeneratorEndToEndIT (IKE-Network/ike-issues#869) -- reused, not reinvented.
    private static final int SPLIT_THRESHOLD = 60;
    private static final int MAX_DEPTH = 4;
    private static final int RESIDUAL_BATCH_SIZE = 50;

    /**
     * Extracts the koncepts YAML for every concept and pattern in the open store, with no
     * {@code narrative:} field on any entry. See {@link #extractYaml(Integer)} to include it.
     *
     * @return the koncepts YAML text
     */
    public static String extractYaml() {
        return extractYaml(null);
    }

    /**
     * Extracts the koncepts YAML for every concept and pattern in the open store.
     *
     * @param narrativePatternNid the nid of the pattern whose semantics carry curated,
     *                            long-form AsciiDoc prose (a {@code narrative:} field is
     *                            emitted for a koncept with an active semantic of this
     *                            pattern), or {@code null} to omit {@code narrative:} entirely
     * @return the koncepts YAML text
     */
    public static String extractYaml(Integer narrativePatternNid) {
        StampCalculator calculator = Calculators.Stamp.DevelopmentLatestActiveOnly();

        List<Integer> conceptNids = new ArrayList<>();
        PrimitiveData.get().forEachConceptNid(conceptNids::add);
        List<Integer> patternNids = new ArrayList<>();
        PrimitiveData.get().forEachPatternNid(patternNids::add);

        Map<Integer, String> identifierByNid = new LinkedHashMap<>();
        Map<Integer, String> labelByNid = new LinkedHashMap<>();
        Map<String, Integer> identifierToNid = new LinkedHashMap<>();
        for (int nid : concat(conceptNids, patternNids)) {
            String fqn = descriptionText(nid, TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid());
            if (fqn == null) {
                continue;
            }
            String label = label(fqn);
            String identifier = identifier(label);
            if (identifierToNid.putIfAbsent(identifier, nid) != null) {
                throw new IllegalStateException("Koncept identifier collision on " + identifier
                        + " (" + fqn + ")");
            }
            identifierByNid.put(nid, identifier);
            labelByNid.put(nid, label);
        }

        Map<Integer, String> sectionByNid = sectionsByNid(calculator, identifierByNid);

        Map<String, Integer> byLabel = new TreeMap<>();
        for (Map.Entry<Integer, String> e : labelByNid.entrySet()) {
            byLabel.put(e.getValue() + " " + e.getKey(), e.getKey());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Extracted by KonceptExtractor from the knowledge base — DO NOT EDIT.\n");
        sb.append("# Standard koncept definitions for the koncept-asciidoc-extension.\n\n");
        for (int nid : byLabel.values()) {
            boolean isPattern = patternNids.contains(nid);
            sb.append(identifierByNid.get(nid)).append(":\n");
            sb.append("  label: ").append(yaml(labelByNid.get(nid))).append('\n');
            String definition = descriptionText(nid, TinkarTerm.DEFINITION_DESCRIPTION_TYPE.nid());
            if (definition != null) {
                sb.append("  definition: ").append(yaml(definition)).append('\n');
            }
            sb.append("  kind: ").append(isPattern ? "pattern" : "concept").append('\n');
            if (isPattern) {
                PatternShape shape = patternShape(nid, identifierByNid);
                if (shape.referencedComponentMeaning() != null) {
                    sb.append("  referencedComponentMeaning: ")
                            .append(shape.referencedComponentMeaning()).append('\n');
                }
                if (shape.referencedComponentPurpose() != null) {
                    sb.append("  referencedComponentPurpose: ")
                            .append(shape.referencedComponentPurpose()).append('\n');
                }
                if (shape.referencedComponentExample() != null) {
                    sb.append("  referencedComponentExample: ")
                            .append(exampleYaml(shape.referencedComponentExample(), identifierByNid.values()))
                            .append('\n');
                }
                List<PatternFieldShape> resolvedFields = shape.fields().stream()
                        .filter(f -> f.meaning() != null && f.purpose() != null && f.dataType() != null)
                        .toList();
                if (!resolvedFields.isEmpty()) {
                    sb.append("  fields:\n");
                    for (PatternFieldShape field : resolvedFields) {
                        sb.append("    - meaning: ").append(field.meaning()).append('\n');
                        sb.append("      purpose: ").append(field.purpose()).append('\n');
                        sb.append("      dataType: ").append(field.dataType()).append('\n');
                        if (field.example() != null) {
                            sb.append("      example: ")
                                    .append(exampleYaml(field.example(), identifierByNid.values()))
                                    .append('\n');
                        }
                    }
                }
            }
            String section = sectionByNid.get(nid);
            if (section != null) {
                sb.append("  section: ").append(yaml(section)).append('\n');
            }

            List<String> parentIds = new ArrayList<>();
            List<String> parentLabels = new ArrayList<>();
            for (int parentNid : statedParents(nid)) {
                String pid = identifierByNid.get(parentNid);
                if (pid != null) {
                    parentIds.add(pid);
                    parentLabels.add(labelByNid.get(parentNid));
                }
            }
            if (!parentLabels.isEmpty()) {
                sb.append("  axiom: ").append(yaml("⊑ " + String.join(" ⊓ ", parentLabels))).append('\n');
                sb.append("  broader: [").append(String.join(", ", parentIds)).append("]\n");
            }

            long since = earliestStampTime(nid);
            if (since != Long.MAX_VALUE) {
                sb.append("  since: ").append(yaml(DateTimeUtil.format(since))).append('\n');
            }
            List<String> comments = activeComments(nid, calculator);
            if (!comments.isEmpty()) {
                sb.append("  comments:\n");
                for (String comment : comments) {
                    sb.append("    - ").append(yaml(comment)).append('\n');
                }
            }
            List<RetiredComment> retired = retiredComments(nid, calculator);
            if (!retired.isEmpty()) {
                sb.append("  retiredComments:\n");
                for (RetiredComment retiredComment : retired) {
                    sb.append("    - text: ").append(yaml(retiredComment.text())).append('\n');
                    sb.append("      retiredAt: ")
                            .append(yaml(DateTimeUtil.format(retiredComment.retiredAt()))).append('\n');
                }
            }
            if (narrativePatternNid != null) {
                String narrative = narrativeText(nid, calculator, narrativePatternNid);
                if (narrative != null) {
                    sb.append("  narrative: ").append(yamlBlock(narrative));
                }
            }
            // seeAlso is deliberately not emitted yet: no Tinkar pattern for associations
            // or replacement/retirement links is wired anywhere today (confirmed by
            // exhaustive search -- see IKE-Network/ike-issues#877). koncept-asciidoc-
            // extension's KonceptDefinition still parses a seeAlso key so nothing here
            // needs to change again once such a pattern exists.

            UUID uuid = PrimitiveData.publicId(nid).asUuidArray()[0];
            sb.append("  uuids:\n    - ").append(uuid).append('\n');
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Buckets every extracted component into a stable section key: a genuine taxonomy
     * subtree is keyed by its root's own koncept identifier (so a renderer can look the
     * root up directly for its title/definition); the residual catch-all -- concepts
     * unreached by stated navigation, plus every pattern, since patterns never enter that
     * taxonomy -- has no single meaningful root, so its batches are keyed positionally
     * ({@code Residual1}, {@code Residual2}, ...), matching how {@link TaxonomySectioner}'s
     * own javadoc describes them.
     * <p>
     * {@link TaxonomySectioner}'s own residual sweep walks {@code EntityService}'s
     * concept/pattern entity iteration, which silently skips a nid that has a description
     * (so it's in {@code identifierByNid}, and gets emitted) but no materializable entity
     * chronology of its own -- true for a handful of primordial/meta-schema value concepts
     * (for example {@code TinkarTerm.PRIMORDIAL_STATE}) referenced only as STAMP dimensions,
     * never as an authored component. Every extracted koncept still needs a value, so
     * whatever {@link TaxonomySectioner} doesn't cover falls into one final fixed bucket
     * here rather than emitting no {@code section:} at all.
     */
    private static Map<Integer, String> sectionsByNid(StampCalculator calculator,
                                                        Map<Integer, String> identifierByNid) {
        TaxonomySectioner sectioner = TaxonomySectioner.fromStatedAxioms(calculator);
        List<Section> sections = sectioner.sectionsCoveringFullStore(
                TinkarTerm.ROOT_VERTEX.nid(), SPLIT_THRESHOLD, MAX_DEPTH, RESIDUAL_BATCH_SIZE);
        Map<Integer, String> sectionByNid = new LinkedHashMap<>();
        int residualIndex = 0;
        for (Section section : sections) {
            String key;
            if (section.rootNid() == TinkarTerm.ROOT_VERTEX.nid()) {
                residualIndex++;
                key = "Residual" + residualIndex;
            } else {
                key = identifierByNid.getOrDefault(section.rootNid(), "Section" + section.rootNid());
            }
            for (int memberNid : section.members()) {
                sectionByNid.put(memberNid, key);
            }
        }
        for (int nid : identifierByNid.keySet()) {
            sectionByNid.putIfAbsent(nid, "Unclassified");
        }
        return sectionByNid;
    }

    /**
     * The earliest stamp time across a component's own version chain -- coordinate-
     * independent, like {@link #latestVersion}, so "since" resolves even on a bare
     * set-only store.
     */
    private static long earliestStampTime(int nid) {
        Entity<? extends EntityVersion> entity = EntityHandle.getEntityOrThrow(nid);
        long earliest = Long.MAX_VALUE;
        for (EntityVersion version : entity.versions()) {
            long time = Entity.getStamp(version.stampNid()).time();
            if (time < earliest) {
                earliest = time;
            }
        }
        return earliest;
    }

    /**
     * Text of every {@link TinkarTerm#COMMENT_PATTERN} semantic on a component whose
     * current version, per {@code calculator}, is active.
     */
    private static List<String> activeComments(int componentNid, StampCalculator calculator) {
        List<String> comments = new ArrayList<>();
        for (int semanticNid : EntityService.get().semanticNidsForComponentOfPattern(
                componentNid, TinkarTerm.COMMENT_PATTERN.nid())) {
            Latest<SemanticEntityVersion> latest = calculator.latestSemanticVersion(semanticNid);
            if (latest.isPresent()) {
                comments.add(latest.get().fieldValues().get(COMMENT_FIELD_TEXT).toString());
            }
        }
        return comments;
    }

    /**
     * The text of a component's currently active semantic of {@code narrativePatternNid} --
     * curated, long-form AsciiDoc prose, as opposed to the short {@code definition} gloss.
     * Only the first active semantic found is used; a hub koncept is expected to carry at
     * most one (the same convention {@link #descriptionText} already relies on for a
     * component's single current name of a given type).
     */
    private static String narrativeText(int componentNid, StampCalculator calculator, int narrativePatternNid) {
        for (int semanticNid : EntityService.get().semanticNidsForComponentOfPattern(
                componentNid, narrativePatternNid)) {
            Latest<SemanticEntityVersion> latest = calculator.latestSemanticVersion(semanticNid);
            if (latest.isPresent()) {
                return latest.get().fieldValues().get(NARRATIVE_FIELD_TEXT).toString();
            }
        }
        return null;
    }

    /**
     * A comment's text immediately before it was retired, and the time of that retirement --
     * present only for a {@link TinkarTerm#COMMENT_PATTERN} semantic whose calculator-resolved
     * current version is absent (nothing active) because its latest version by time is
     * itself inactive.
     */
    private record RetiredComment(String text, long retiredAt) {
    }

    private static List<RetiredComment> retiredComments(int componentNid, StampCalculator calculator) {
        List<RetiredComment> retired = new ArrayList<>();
        for (int semanticNid : EntityService.get().semanticNidsForComponentOfPattern(
                componentNid, TinkarTerm.COMMENT_PATTERN.nid())) {
            if (calculator.latestSemanticVersion(semanticNid).isPresent()) {
                continue;
            }
            SemanticEntity<?> semantic = EntityHandle.get(semanticNid).expectSemantic();
            List<SemanticEntityVersion> versions = new ArrayList<>();
            for (SemanticEntityVersion version : semantic.versions()) {
                versions.add(version);
            }
            if (versions.size() < 2) {
                continue; // no prior active version to report
            }
            versions.sort(Comparator.comparingLong(v -> Entity.getStamp(v.stampNid()).time()));
            SemanticEntityVersion current = versions.get(versions.size() - 1);
            SemanticEntityVersion prior = versions.get(versions.size() - 2);
            StampEntity<?> currentStamp = Entity.getStamp(current.stampNid());
            if (currentStamp.state() == State.INACTIVE) {
                retired.add(new RetiredComment(
                        prior.fieldValues().get(COMMENT_FIELD_TEXT).toString(), currentStamp.time()));
            }
        }
        return retired;
    }

    /**
     * The current text of a component's description of the given type, read directly from
     * the description-pattern semantics (latest version by stamp time), or null if none.
     */
    private static String descriptionText(int componentNid, int descriptionTypeNid) {
        int[] descriptionSemanticNids = EntityService.get().semanticNidsForComponentOfPattern(
                componentNid, TinkarTerm.DESCRIPTION_PATTERN.nid());
        for (int semanticNid : descriptionSemanticNids) {
            SemanticEntityVersion version = latestVersion(EntityHandle.get(semanticNid).expectSemantic());
            if (version == null) {
                continue;
            }
            ImmutableList<Object> fields = version.fieldValues();
            Object type = fields.get(FIELD_TYPE);
            if (type instanceof dev.ikm.tinkar.terms.EntityFacade typeFacade
                    && typeFacade.nid() == descriptionTypeNid) {
                return fields.get(FIELD_TEXT).toString();
            }
        }
        return null;
    }

    /**
     * The stated is-a parents: the concept axioms of the stated logical expression,
     * read directly from the stated-axioms semantic. For authored taxonomy content these
     * are the supertypes; role fillers would also appear and are a future refinement.
     */
    private static List<Integer> statedParents(int componentNid) {
        int[] axiomNids = EntityService.get().semanticNidsForComponentOfPattern(
                componentNid, TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid());
        List<Integer> parents = new ArrayList<>();
        for (int semanticNid : axiomNids) {
            SemanticEntityVersion version = latestVersion(EntityHandle.get(semanticNid).expectSemantic());
            if (version == null || version.fieldValues().isEmpty()) {
                continue;
            }
            if (version.fieldValues().get(0) instanceof DiTreeEntity diTree) {
                LogicalExpression expression = new LogicalExpression(diTree);
                for (LogicalAxiom.Atom.ConceptAxiom axiom
                        : expression.nodesOfType(LogicalAxiom.Atom.ConceptAxiom.class)) {
                    parents.add(axiom.concept().nid());
                }
            }
        }
        return parents;
    }

    /**
     * A pattern's own top-level referenced-component meaning/purpose -- Tinkar's wire
     * schema calls this pair {@code ReferencedComponentMeaning}/{@code ReferencedComponentPurpose};
     * the Java entity API calls the identical field {@code semanticMeaning}/{@code semanticPurpose}
     * ("[Meaning] of &lt;referenced component&gt; for [purpose] in [pattern]," per
     * {@code PatternVersion}'s own javadoc) -- plus its field definitions, each with its own
     * meaning, purpose, and data type.
     *
     * @param referencedComponentMeaning the koncept identifier of the referenced-component
     *                                   meaning concept, or {@code null} if unresolvable
     * @param referencedComponentPurpose the koncept identifier of the referenced-component
     *                                   purpose concept, or {@code null} if unresolvable
     * @param referencedComponentExample the referenced component of a real, deterministically
     *                                   chosen semantic of this pattern already in the store —
     *                                   a koncept identifier if it resolves to one, otherwise its
     *                                   display text — or {@code null} if this pattern has no
     *                                   semantics yet
     * @param fields                     the pattern's own field definitions, in declared order
     */
    private record PatternShape(String referencedComponentMeaning, String referencedComponentPurpose,
                                 String referencedComponentExample, List<PatternFieldShape> fields) {
    }

    /**
     * One field of a pattern: its own meaning, purpose, and data type, each a koncept
     * identifier, or {@code null} if that field's concept isn't itself resolvable (no FQN
     * description in this store).
     *
     * @param example this field's actual value on the same example semantic used for
     *                {@link PatternShape#referencedComponentExample()} — a koncept identifier
     *                if the value resolves to one, otherwise its display text — or {@code null}
     *                if no example semantic was found
     */
    private record PatternFieldShape(String meaning, String purpose, String dataType, String example) {
    }

    /**
     * Reads a pattern's own referenced-component meaning/purpose and field definitions from
     * its latest version by time -- coordinate-independent, like {@link #latestVersion},
     * since a pattern's own shape doesn't change version-to-version the way a component's
     * state does. Each referenced concept resolves to its koncept identifier via
     * {@code identifierByNid} -- the same universe {@link #extractYaml} already built from
     * this store's own fully-qualified-name descriptions, so a meaning/purpose/dataType
     * concept without one here resolves to {@code null} rather than a broken reference.
     */
    private static PatternShape patternShape(int patternNid, Map<Integer, String> identifierByNid) {
        PatternEntity<PatternEntityVersion> pattern = EntityService.get().getEntityFast(patternNid);
        PatternEntityVersion version = pattern.lastVersion();
        if (version == null) {
            return new PatternShape(null, null, null, List.of());
        }
        List<PatternFieldShape> fields = new ArrayList<>();
        for (FieldDefinitionForEntity field : version.fieldDefinitions()) {
            fields.add(new PatternFieldShape(
                    identifierByNid.get(field.meaningNid()),
                    identifierByNid.get(field.purposeNid()),
                    identifierByNid.get(field.dataTypeNid()),
                    null));
        }

        String referencedComponentExample = null;
        ExampleSemantic example = exampleSemanticOf(patternNid);
        if (example != null) {
            referencedComponentExample = displayText(example.referencedComponentNid(), identifierByNid);
            for (int i = 0; i < fields.size() && i < example.fieldValues().size(); i++) {
                PatternFieldShape f = fields.get(i);
                fields.set(i, new PatternFieldShape(f.meaning(), f.purpose(), f.dataType(),
                        displayText(example.fieldValues().get(i), identifierByNid)));
            }
        }

        return new PatternShape(
                identifierByNid.get(version.semanticMeaningNid()),
                identifierByNid.get(version.semanticPurposeNid()),
                referencedComponentExample,
                fields);
    }

    /**
     * The referenced component and field values of one real semantic of {@code patternNid},
     * chosen deterministically as the earliest-authored (by {@link #earliestStampTime}) —
     * reproducible across regenerations as long as this store's own authoring order doesn't
     * change, unlike picking by nid (assignment order is an implementation detail).
     *
     * @param patternNid the pattern whose semantics to search
     * @return the chosen semantic's referenced component and field values, or {@code null} if
     *         this pattern has no semantics with a resolvable current version in this store
     */
    private record ExampleSemantic(int referencedComponentNid, ImmutableList<Object> fieldValues) {
    }

    private static ExampleSemantic exampleSemanticOf(int patternNid) {
        int bestNid = -1;
        long bestTime = Long.MAX_VALUE;
        for (int semanticNid : EntityService.get().semanticNidsOfPattern(patternNid)) {
            SemanticEntity<?> candidate = EntityHandle.get(semanticNid).expectSemantic();
            if (latestVersion(candidate) == null || isDefaultsOrTemplateContent(candidate)) {
                continue;
            }
            long time = earliestStampTime(semanticNid);
            if (time < bestTime) {
                bestTime = time;
                bestNid = semanticNid;
            }
        }
        if (bestNid == -1) {
            return null;
        }
        SemanticEntity<?> semantic = EntityHandle.get(bestNid).expectSemantic();
        return new ExampleSemantic(semantic.referencedComponentNid(), latestVersion(semantic).fieldValues());
    }

    /**
     * Whether a semantic is defaults/template content rather than a domain assertion:
     * any version stamped in
     * {@link DefaultsTemplateTerm#DEFAULTS_AND_TEMPLATES_MODULE} — the category's
     * bidirectional live-and-die invariant makes one such version equivalent to all.
     * An example must be a domain assertion, so a pattern's default value semantic is
     * never its "example" (IKE-Network/ike-issues#885 — the affirmative-scope rule:
     * consumers of domain content exclude the support category).
     *
     * @param semantic the candidate example semantic
     * @return {@code true} when the semantic is defaults/template content
     */
    private static boolean isDefaultsOrTemplateContent(SemanticEntity<?> semantic) {
        int defaultsModuleNid = DefaultsTemplateTerm.DEFAULTS_AND_TEMPLATES_MODULE.nid();
        for (SemanticEntityVersion candidateVersion : semantic.versions()) {
            if (candidateVersion.stamp().moduleNid() == defaultsModuleNid) {
                return true;
            }
        }
        return false;
    }

    /**
     * Display text for one example value — an entity-valued field (or the referenced
     * component itself) resolves to its koncept identifier when this store has one, otherwise
     * to {@link PrimitiveData#text}; every other {@link dev.ikm.tinkar.component.FieldDataType}
     * a semantic field can hold falls back to a plain rendering, mirroring
     * {@code SemanticVersionRecord.toString()}'s existing per-type handling without depending
     * on that debug format.
     */
    private static String displayText(int nid, Map<Integer, String> identifierByNid) {
        String identifier = identifierByNid.get(nid);
        return identifier != null ? identifier : PrimitiveData.text(nid);
    }

    private static String displayText(Object value, Map<Integer, String> identifierByNid) {
        return switch (value) {
            case null -> null;
            case EntityFacade entity -> displayText(entity.nid(), identifierByNid);
            case Instant instant -> DateTimeUtil.format(instant);
            case IntIdList intIdList -> intIdList.isEmpty() ? "(none)"
                    : String.join(", ", intIdList.intStream()
                            .mapToObj(memberNid -> displayText(memberNid, identifierByNid)).toList());
            case IntIdSet intIdSet -> intIdSet.isEmpty() ? "(none)"
                    : String.join(", ", intIdSet.intStream()
                            .mapToObj(memberNid -> displayText(memberNid, identifierByNid)).toList());
            default -> value.toString();
        };
    }

    /**
     * YAML for one example value: a bare, unquoted koncept identifier when {@code value} is one
     * (matching {@code meaning}/{@code purpose}/{@code dataType}'s existing unquoted style, so
     * the renderer can badge-link it), otherwise a quoted literal via {@link #yaml} — free text
     * is vanishingly unlikely to collide with the single-word PascalCase identifiers this store
     * mints, so no separate reference/literal flag is needed.
     */
    private static String exampleYaml(String value, java.util.Collection<String> identifiers) {
        return identifiers.contains(value) ? value : yaml(value);
    }

    /** The latest version of a semantic by stamp time, or null. */
    private static SemanticEntityVersion latestVersion(SemanticEntity<?> semantic) {
        if (semantic == null) {
            return null;
        }
        SemanticEntityVersion latest = null;
        long latestTime = Long.MIN_VALUE;
        for (SemanticEntityVersion version : semantic.versions()) {
            StampEntity<?> stamp = Entity.getStamp(version.stampNid());
            if (stamp.time() >= latestTime) {
                latestTime = stamp.time();
                latest = version;
            }
        }
        return latest;
    }

    private static List<Integer> concat(List<Integer> a, List<Integer> b) {
        List<Integer> out = new ArrayList<>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return out;
    }

    /**
     * The stable koncept identifier for a label: the label in PascalCase.
     *
     * @param label the display label (tag-stripped)
     * @return the PascalCase identifier
     */
    static String identifier(String label) {
        StringBuilder sb = new StringBuilder();
        for (String word : label.split("[^A-Za-z0-9]+")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        if (sb.length() == 0) {
            throw new IllegalStateException("Label reduces to an empty identifier: \"" + label + "\"");
        }
        if (Character.isDigit(sb.charAt(0))) {
            sb.insert(0, 'N');
        }
        return sb.toString();
    }

    /** The display label: a fully qualified name with its trailing parenthesized tag stripped. */
    static String label(String fqn) {
        return fqn.replaceAll("\\s*\\([^)]*\\)\\s*$", "").trim();
    }

    private static String yaml(String text) {
        return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    /**
     * A YAML literal block scalar ({@code |}), indented two spaces past the field's own
     * indent -- unlike {@link #yaml}'s single-line double-quoted style, this preserves
     * embedded newlines (paragraph breaks) literally, which multi-paragraph {@code narrative}
     * text genuinely has and the other, single-line fields never do.
     */
    private static String yamlBlock(String text) {
        StringBuilder sb = new StringBuilder("|\n");
        for (String line : text.split("\n", -1)) {
            sb.append("    ").append(line).append('\n');
        }
        return sb.toString();
    }
}
