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

import dev.ikm.tinkar.common.id.EntityKey;
import dev.ikm.tinkar.common.id.IntIdList;
import dev.ikm.tinkar.common.id.IntIdSet;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.time.DateTimeUtil;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
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
import dev.ikm.tinkar.entity.graph.DiGraphEntity;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiom;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpression;
import dev.ikm.tinkar.terms.DefaultsTemplateTerm;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.State;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.list.ImmutableList;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
 * {@code dataType}, plus a live {@code example:} from a real semantic and a {@code default:}
 * from the pattern's default value semantic when one is active — IKE-Network/ike-issues#888)
 * -- see {@link #patternShape}.
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
                            .append(valueYaml(shape.referencedComponentExample(), identifierByNid.values()))
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
                                    .append(valueYaml(field.example(), identifierByNid.values()))
                                    .append('\n');
                        }
                        if (field.defaultValue() != null) {
                            sb.append("      default: ")
                                    .append(valueYaml(field.defaultValue(), identifierByNid.values()))
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
                sb.append("  since: ").append(yaml(DateTimeUtil.formatUtc(since))).append('\n');
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
                            .append(yaml(DateTimeUtil.formatUtc(retiredComment.retiredAt()))).append('\n');
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
     * @param example      this field's actual value on the same example semantic used for
     *                     {@link PatternShape#referencedComponentExample()} — a koncept identifier
     *                     if the value resolves to one, otherwise its display text — or {@code null}
     *                     if no example semantic was found
     * @param defaultValue this field's value on the pattern's default value semantic
     *                     (IKE-Network/ike-issues#888), rendered with the same display-text
     *                     conventions as {@code example} — or {@code null} if the pattern has no
     *                     currently active default value semantic
     */
    private record PatternFieldShape(String meaning, String purpose, String dataType, String example,
                                      String defaultValue) {
    }

    /**
     * Reads a pattern's own referenced-component meaning/purpose and field definitions from
     * its latest version by time -- coordinate-independent, like {@link #latestVersion},
     * since a pattern's own shape doesn't change version-to-version the way a component's
     * state does. Each referenced concept resolves to its koncept identifier via
     * {@code identifierByNid} -- the same universe {@link #extractYaml} already built from
     * this store's own fully-qualified-name descriptions, so a meaning/purpose/dataType
     * concept without one here resolves to {@code null} rather than a broken reference.
     * <p>
     * Two live-value channels ride on the shape, each independent of the other: the
     * example channel ({@link #exampleSemanticOf} — a real domain semantic, defaults
     * content excluded) and the defaults channel ({@link #defaultValueVersion} — the
     * pattern's default value semantic, resolved by computed identity). A pattern may
     * carry either, both, or neither.
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
                    null,
                    null));
        }

        String referencedComponentExample = null;
        ExampleSemantic example = exampleSemanticOf(patternNid);
        if (example != null) {
            referencedComponentExample = displayText(example.referencedComponentNid(), identifierByNid);
            for (int i = 0; i < fields.size() && i < example.fieldValues().size(); i++) {
                PatternFieldShape f = fields.get(i);
                fields.set(i, new PatternFieldShape(f.meaning(), f.purpose(), f.dataType(),
                        displayText(example.fieldValues().get(i), identifierByNid), f.defaultValue()));
            }
        }

        SemanticEntityVersion defaults = defaultValueVersion(patternNid);
        if (defaults != null) {
            for (int i = 0; i < fields.size() && i < defaults.fieldValues().size(); i++) {
                PatternFieldShape f = fields.get(i);
                fields.set(i, new PatternFieldShape(f.meaning(), f.purpose(), f.dataType(),
                        f.example(), displayText(defaults.fieldValues().get(i), identifierByNid)));
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
     * The latest version of a pattern's default value semantic when that version is active,
     * or {@code null} — the defaults documentation channel (IKE-Network/ike-issues#888).
     * Resolution is by computed identity, never iteration:
     * {@link UuidT5Generator#singleSemanticUuid} of the pattern's public id and
     * {@link DefaultsTemplateTerm#DEFAULT_VALUE_CONCEPT}'s public id — the same stock
     * convention {@code StampCalculator.getDefault(PatternFacade)} resolves, read here at
     * the chronicle level ({@link #latestVersion} by stamp time) in this extractor's
     * coordinate-independent style. The identity lookup is non-minting
     * ({@link PrimitiveData#getEntityKey(UUID)}), so a store with no defaults content is
     * untouched; a retired default (latest version inactive) yields {@code null} rather
     * than resurrecting an earlier active version.
     *
     * @param patternNid the pattern whose default value semantic to resolve
     * @return the defaults semantic's latest version when present and active, else {@code null}
     */
    private static SemanticEntityVersion defaultValueVersion(int patternNid) {
        UUID defaultsIdentity = UuidT5Generator.singleSemanticUuid(
                PrimitiveData.publicId(patternNid),
                DefaultsTemplateTerm.DEFAULT_VALUE_CONCEPT.publicId());
        Optional<EntityKey> key = PrimitiveData.getEntityKey(defaultsIdentity);
        if (key.isEmpty()) {
            return null;
        }
        Optional<SemanticEntity> semantic = EntityHandle.get(key.get().nid()).asSemantic();
        if (semantic.isEmpty()) {
            return null;
        }
        SemanticEntityVersion latest = latestVersion(semantic.get());
        if (latest == null || Entity.getStamp(latest.stampNid()).state() != State.ACTIVE) {
            return null;
        }
        return latest;
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
     * Display text for one live value (an example or a default) — an entity-valued field
     * (or the referenced component itself) resolves to its koncept identifier when this
     * store has one. A description semantic is the one semantic that carries text of its
     * own, so it renders as that text (for example the US Dialect Pattern's
     * referenced-component example is a description semantic — a dialect semantic
     * qualifies a description — and renders as the description's own string, for
     * example {@code "Uninitialized Component (SOLOR)"}, never a raw nid). Any other
     * semantic renders structurally as {@code <pattern> on <referenced component>} —
     * it carries no text of its own, and {@link PrimitiveData#text} is genuinely
     * unusable for one: {@code EntityProvider} seeds its debug string cache with the
     * semantic's uuid-list string on every {@code putEntity}, so in a freshly written
     * store the "description" of a semantic is its raw UUID. A stamp renders
     * structurally as {@code <state> <time> on <path>} — a stamp carries no
     * description semantics at all, so {@link PrimitiveData#text} would fall clear
     * through to its raw {@code <nid>} form. Any other entity falls back to
     * {@link PrimitiveData#text}. Every other
     * {@link dev.ikm.tinkar.component.FieldDataType} a semantic field can hold falls back
     * to a readable rendering, mirroring {@code SemanticVersionRecord.toString()}'s
     * existing per-type handling without depending on that debug format:
     * <ul>
     *   <li>{@link Instant} formats via {@link DateTimeUtil#format(Instant)}, which renders
     *       the pre-inception instant (historically "premundane") as its named sentinel
     *       ({@code Pre-inception}), never a raw epoch extreme;</li>
     *   <li>a byte array decodes as strict UTF-8 text when it is valid UTF-8 (the loud
     *       defaults convention carries readable text in byte form), otherwise as a length
     *       plus hex dump;</li>
     *   <li>an {@code Object[]} renders each element recursively, joined in brackets
     *       ({@code [a, b]}) so the array-ness stays visible even for one element;</li>
     *   <li>{@link DiTreeEntity}/{@link DiGraphEntity} have no display-text machinery of
     *       their own (only multi-line debug {@code toString}), so they render compactly by
     *       shape — {@code N-vertex tree}, {@code N-vertex cycle} when the graph is one
     *       simple cycle, else {@code N-vertex graph (E edges)};</li>
     *   <li>{@code Float}/{@code Long}/{@code Integer}/{@code BigDecimal} fall through to
     *       {@code toString()}, which already renders {@code NaN} and the sentinel numerals
     *       readably.</li>
     * </ul>
     */
    private static String displayText(int nid, Map<Integer, String> identifierByNid) {
        String identifier = identifierByNid.get(nid);
        if (identifier != null) {
            return identifier;
        }
        EntityHandle handle = EntityHandle.get(nid);
        Optional<SemanticEntity> semantic = handle.asSemantic();
        if (semantic.isPresent()) {
            if (semantic.get().patternNid() == TinkarTerm.DESCRIPTION_PATTERN.nid()) {
                SemanticEntityVersion description = latestVersion(semantic.get());
                if (description != null) {
                    return description.fieldValues().get(FIELD_TEXT).toString();
                }
            }
            return displayText(semantic.get().patternNid(), identifierByNid)
                    + " on " + displayText(semantic.get().referencedComponentNid(), identifierByNid);
        }
        Optional<StampEntity> stamp = handle.asStamp();
        if (stamp.isPresent()) {
            String state = stamp.get().state().name();
            return state.charAt(0) + state.substring(1).toLowerCase(Locale.ROOT)
                    + " " + DateTimeUtil.formatUtc(stamp.get().time())
                    + " on " + displayText(stamp.get().pathNid(), identifierByNid);
        }
        return PrimitiveData.text(nid);
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
            case byte[] bytes -> byteArrayText(bytes);
            case Object[] array -> "[" + String.join(", ", java.util.Arrays.stream(array)
                    .map(element -> displayText(element, identifierByNid)).toList()) + "]";
            case DiTreeEntity tree -> tree.vertexCount() + "-vertex tree";
            case DiGraphEntity<?> graph -> graphText(graph);
            default -> value.toString();
        };
    }

    /**
     * Readable text for a byte-array value: the bytes decoded as strict UTF-8 when they
     * are valid UTF-8 (the loud-defaults convention deliberately carries readable text in
     * byte form), otherwise the length and a hex dump — never Java's default
     * {@code [B@hash} identity string.
     *
     * @param bytes the field value
     * @return the decoded text, or {@code "N bytes: 0x…"} for non-UTF-8 content
     */
    private static String byteArrayText(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return bytes.length + " bytes: 0x" + HexFormat.of().formatHex(bytes);
        }
    }

    /**
     * Compact display text for a directed-graph value, by shape: {@code N-vertex cycle}
     * when the graph is one simple cycle (every vertex has exactly one successor and one
     * closed walk visits them all — the Data Type Defaults Pattern's deliberate two-vertex
     * cycle renders as {@code 2-vertex cycle}), otherwise {@code N-vertex graph (E edges)}.
     *
     * @param graph the field value
     * @return the compact shape description
     */
    private static String graphText(DiGraphEntity<?> graph) {
        int vertexCount = graph.vertexCount();
        if (vertexCount > 0 && isSimpleCycle(graph)) {
            return vertexCount + "-vertex cycle";
        }
        int edgeCount = 0;
        for (int vertexIndex = 0; vertexIndex < vertexCount; vertexIndex++) {
            edgeCount += graph.successors(vertexIndex).size();
        }
        return vertexCount + "-vertex graph (" + edgeCount + (edgeCount == 1 ? " edge)" : " edges)");
    }

    /**
     * Whether a directed graph is one simple cycle: starting from vertex 0, following each
     * vertex's single successor walks every vertex exactly once and closes back on vertex 0.
     *
     * @param graph the graph to test
     * @return {@code true} when the graph is a single simple cycle over all its vertices
     */
    private static boolean isSimpleCycle(DiGraphEntity<?> graph) {
        int vertexCount = graph.vertexCount();
        boolean[] visited = new boolean[vertexCount];
        int current = 0;
        for (int step = 0; step < vertexCount; step++) {
            if (graph.successors(current).size() != 1 || visited[current]) {
                return false;
            }
            visited[current] = true;
            current = graph.successors(current).get(0);
            if (current < 0 || current >= vertexCount) {
                return false;
            }
        }
        return current == 0;
    }

    /**
     * YAML for one live value (an example or a default): a bare, unquoted koncept identifier
     * when {@code value} is one (matching {@code meaning}/{@code purpose}/{@code dataType}'s
     * existing unquoted style, so the renderer can badge-link it), otherwise a quoted literal
     * via {@link #yaml} — free text is vanishingly unlikely to collide with the single-word
     * PascalCase identifiers this store mints, so no separate reference/literal flag is needed.
     */
    private static String valueYaml(String value, java.util.Collection<String> identifiers) {
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
