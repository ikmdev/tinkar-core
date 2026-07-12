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
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIdList;
import dev.ikm.tinkar.common.id.PublicIdSet;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.PublicIdentifierRecord;
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.SemanticRecordBuilder;
import dev.ikm.tinkar.entity.SemanticVersionRecord;
import dev.ikm.tinkar.entity.SemanticVersionRecordBuilder;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.entity.StampVersionRecord;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpressionBuilder;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.factory.Lists;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The shared ledger state and replay machinery behind {@link ConceptBuilder} and
 * {@link PatternBuilder}: birth bookkeeping, chronological-scope enforcement, the
 * description ledgers (with their US-dialect acceptability semantics), and the
 * stamp/description writes. Package-private — the builders are the API.
 */
final class ComponentLedger {

    final PublicId componentId;
    /** The component's primordial (first) UUID — the T5 namespace for attached-semantic derivation. */
    final UUID componentUuid;
    final String birthFqn;

    final List<Stamp> componentStamps = new ArrayList<>();
    final List<DescriptionLedger> descriptions = new ArrayList<>();
    private final Map<UUID, GenericSemanticLedger> genericSemantics = new LinkedHashMap<>();
    private final List<VersionEntry<Consumer<LogicalExpressionBuilder>>> axiomVersions = new ArrayList<>();
    private final Set<UUID> writtenStamps = new HashSet<>();
    private final SessionRegistry registry;

    private PublicId declaredAxiomIdentity;
    private ActiveStamp birthStamp;
    private boolean descriptionsDeclaredExplicitly = false;
    private boolean fqnSeeded = false;
    private long lastStampTime = Long.MIN_VALUE;
    private boolean born = false;

    ComponentLedger(PublicId componentId, String birthFqn, SessionRegistry registry) {
        this.componentId = componentId;
        this.componentUuid = componentId.asUuidArray()[0];
        this.birthFqn = birthFqn;
        this.registry = registry;
    }

    boolean born() {
        return born;
    }

    /**
     * Records the birth scope: the component's first version, and the birth stamp for
     * the deferred fully-qualified-name seeding (see {@link #seedFqnIfImplicit()}).
     * Idempotent guard is the caller's {@code born()} check.
     */
    void birth(ActiveStamp stamp) {
        born = true;
        birthStamp = stamp;
        componentStamps.add(stamp);
    }

    /**
     * Seeds the derived-identity fully-qualified-name description — and its US-dialect
     * acceptability — at the birth stamp, unless the ledger declared any description
     * explicitly as a generic declared-identity semantic. The auto-seed is an authoring
     * convenience default; ingested content carries its descriptions' established
     * identities — the FQN included — and seeding a derived twin would break
     * identity-exact round trip. Idempotent; runs on demand (write, or the first
     * FQN-referencing verb).
     */
    private void seedFqnIfImplicit() {
        if (fqnSeeded || descriptionsDeclaredExplicitly) {
            return;
        }
        fqnSeeded = true;
        DescriptionLedger fqn = new DescriptionLedger(
                UuidT5Generator.get(componentUuid,
                        "fully-qualified-name|" + TinkarTerm.ENGLISH_LANGUAGE.publicId().asUuidArray()[0]),
                TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE, TinkarTerm.ENGLISH_LANGUAGE);
        fqn.versions.add(new VersionEntry<>(birthStamp, birthFqn));
        fqn.dialects.add(new VersionEntry<>(birthStamp, TinkarTerm.PREFERRED));
        descriptions.add(0, fqn);
    }

    void checkChronology(Stamp stamp) {
        registry.requireStampAgreement(stamp);
        if (stamp.time() < lastStampTime) {
            throw new IllegalArgumentException(
                    "Ledger scopes must be chronological: stamp time " + stamp.time()
                            + " precedes prior scope time " + lastStampTime + " for " + birthFqn);
        }
        lastStampTime = stamp.time();
    }

    void requireBornForRetirement() {
        if (!born) {
            throw new IllegalStateException(
                    "Cannot open a retirement scope before the birth scope: " + birthFqn);
        }
    }

    void requireBornForWrite() {
        if (!born) {
            throw new IllegalStateException("A declaration requires a birth scope before write: " + birthFqn);
        }
    }

    /**
     * Adds a new description of the given type; identity is derived from the authoring
     * context: {@code T5(component, kindKey|language|ordinal)}, where the ordinal is the
     * count of same-type descriptions already declared — stable because the ledger is
     * append-only.
     */
    void addDescription(EntityProxy.Concept type, String kindKey, String text, Stamp stamp) {
        long ordinal = descriptions.stream().filter(d -> d.type.equals(type)).count();
        UUID descriptionUuid = UuidT5Generator.get(componentUuid,
                kindKey + "|" + TinkarTerm.ENGLISH_LANGUAGE.publicId().asUuidArray()[0] + "|" + ordinal);
        DescriptionLedger description = new DescriptionLedger(descriptionUuid, type, TinkarTerm.ENGLISH_LANGUAGE);
        description.versions.add(new VersionEntry<>(stamp, text));
        description.dialects.add(new VersionEntry<>(stamp, TinkarTerm.PREFERRED));
        descriptions.add(description);
    }

    /**
     * Resolves the single live description of the given type carrying the given current
     * text — the reference mechanism for revise/retire verbs.
     */
    DescriptionLedger resolveLive(EntityProxy.Concept type, String currentText, String kindLabel) {
        List<DescriptionLedger> matches = descriptions.stream()
                .filter(d -> d.type.equals(type))
                .filter(d -> !d.retired())
                .filter(d -> d.currentText().equals(currentText))
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalArgumentException(
                    "No live " + kindLabel + " with text \"" + currentText + "\" on " + birthFqn);
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException(
                    "Ambiguous reference: " + matches.size() + " live " + kindLabel + "s with text \""
                            + currentText + "\" on " + birthFqn);
        }
        return matches.getFirst();
    }

    /** The fully qualified name's ledger — seeded on demand, always the first description. */
    DescriptionLedger fqnLedger() {
        if (descriptionsDeclaredExplicitly) {
            throw new IllegalStateException(
                    "The descriptions of " + birthFqn + " are declared explicitly with their"
                            + " established identities — revise the FQN by restating that generic"
                            + " semantic, not with reviseFullyQualifiedName");
        }
        seedFqnIfImplicit();
        return descriptions.getFirst();
    }

    /**
     * Records a new version of the component itself at the given stamp — birth,
     * revision, or retirement. One version per stamp: the store merge keys versions by
     * stamp, so a second component version at one stamp would silently collapse.
     */
    void addComponentStamp(Stamp stamp) {
        requireNewStamp(componentStamps, stamp, "the component chronology");
        componentStamps.add(stamp);
    }

    /**
     * Appends a version to a description ledger under the one-version-per-stamp guard —
     * the funnel for every revise and retire description verb.
     */
    void appendDescriptionVersion(DescriptionLedger description, Stamp stamp, String text) {
        requireNewStamp(description.versions.stream().map(VersionEntry::stamp).toList(), stamp,
                "description \"" + description.currentText() + "\"");
        description.versions.add(new VersionEntry<>(stamp, text));
    }

    /**
     * One version per stamp per ledger: rejects a stamp whose identity overlaps — by any
     * UUID, matching the store's merge-by-any-UUID identity — a stamp already carrying a
     * version in the same ledger. The store merge keys versions by stamp nid and lets
     * the newest payload silently replace an earlier one; this guard makes that a
     * compose-time error instead.
     */
    void requireNewStamp(Iterable<Stamp> stampsInUse, Stamp stamp, String what) {
        for (Stamp used : stampsInUse) {
            if (stampIdentityOverlaps(used, stamp)) {
                throw new IllegalArgumentException(
                        what + " of " + birthFqn + " already has a version at stamp "
                                + firstUuidOf(stamp.publicId()) + " — the store keys versions by"
                                + " stamp, so a second payload would silently replace the first");
            }
        }
    }

    /** Whether two stamps share any identity UUID — the store's merge-identity notion. */
    private static boolean stampIdentityOverlaps(Stamp first, Stamp second) {
        for (UUID firstUuid : first.publicId().asUuidArray()) {
            for (UUID secondUuid : second.publicId().asUuidArray()) {
                if (firstUuid.equals(secondUuid)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The current text of the first live definition description, if any. */
    java.util.Optional<String> currentDefinition() {
        return descriptions.stream()
                .filter(d -> d.type.equals(TinkarTerm.DEFINITION_DESCRIPTION_TYPE))
                .filter(d -> !d.retired())
                .findFirst()
                .map(DescriptionLedger::currentText);
    }

    // ------------------------------------------------------------ generic semantics

    /**
     * Appends a version to a generic declared-identity semantic — creating its ledger on
     * first use, resuming it on later ones. The semantic references this ledger's
     * component when {@code referencedComponent} is null, or the given established
     * identity otherwise (a dialect on a declared-identity description, for example).
     * Resume must agree on identity, pattern, and referenced component; and a semantic
     * takes at most one version per stamp — the store merge keys versions by stamp, so
     * a second payload under the same stamp would silently replace the first.
     * <p>
     * An explicit declaration of the fully-qualified-name description (a
     * description-pattern semantic whose type field is the FQN type) suppresses the
     * derived-identity FQN auto-seed — ingested content carries its FQN description's
     * established identity.
     */
    void addGenericVersion(EntityProxy.Pattern pattern, PublicId declaredIdentity,
                           PublicId referencedComponent, Stamp stamp, Object[] fieldValues) {
        if (pattern == null) {
            throw new IllegalArgumentException("A generic semantic requires its pattern: " + birthFqn);
        }
        requireDeclaredIdentity(declaredIdentity);
        // A reference to the component's own identity is a self-reference, whichever of
        // its UUIDs named it — normalize so the suppression gate and agreement see it.
        if (referencedComponent != null && identityOverlaps(referencedComponent, componentId)) {
            referencedComponent = null;
        }
        // All validation precedes any mutation: a rejected declaration must leave the
        // session exactly as it was, or a caught rejection corrupts a later write().
        boolean explicitDescription =
                referencedComponent == null && isExplicitDescriptionDeclaration(pattern, fieldValues);
        if (explicitDescription && fqnSeeded) {
            throw new IllegalStateException(
                    "The derived-identity fully qualified name of " + birthFqn + " was already"
                            + " seeded — declare established descriptions before any FQN-referencing"
                            + " verb or mid-ledger write");
        }
        List<Object> values = validateFieldValues(fieldValues);
        GenericSemanticLedger semantic = openGenericSemantic(pattern, declaredIdentity, referencedComponent);
        requireNewStamp(semantic.versions.stream().map(VersionEntry::stamp).toList(), stamp,
                "semantic " + semantic.semanticId);
        semantic.versions.add(new VersionEntry<>(stamp, values));
        if (explicitDescription) {
            descriptionsDeclaredExplicitly = true;
        }
    }

    /**
     * Appends a retirement version to a generic declared-identity semantic: the same
     * fields under an inactive stamp when {@code fieldValues} is empty (a pure status
     * change), or the given payload verbatim — retired versions carry field values too.
     */
    void retireGenericVersion(EntityProxy.Pattern pattern, PublicId declaredIdentity,
                              InactiveStamp stamp, Object[] fieldValues) {
        requireDeclaredIdentity(declaredIdentity);
        GenericSemanticLedger semantic = genericSemantics.get(firstUuidOf(declaredIdentity));
        if (semantic == null) {
            throw new IllegalArgumentException(
                    "No semantic with declared identity " + declaredIdentity + " on " + birthFqn
                            + " to retire — retirement follows declaration");
        }
        requireSemanticIdentityAgreement(semantic, declaredIdentity);
        requirePatternAgreement(semantic, pattern);
        List<Object> values = fieldValues.length == 0
                ? semantic.versions.getLast().value()
                : validateFieldValues(fieldValues);
        requireNewStamp(semantic.versions.stream().map(VersionEntry::stamp).toList(), stamp,
                "semantic " + semantic.semanticId);
        semantic.versions.add(new VersionEntry<>(stamp, values));
    }

    private GenericSemanticLedger openGenericSemantic(EntityProxy.Pattern pattern, PublicId declaredIdentity,
                                                      PublicId referencedComponent) {
        GenericSemanticLedger opened = genericSemantics.get(firstUuidOf(declaredIdentity));
        if (opened == null) {
            registry.registerIdentity(declaredIdentity,
                    "semantic " + firstUuidOf(declaredIdentity) + " on \"" + birthFqn + "\"");
            GenericSemanticLedger created =
                    new GenericSemanticLedger(declaredIdentity, pattern, referencedComponent);
            genericSemantics.put(firstUuidOf(declaredIdentity), created);
            return created;
        }
        requireSemanticIdentityAgreement(opened, declaredIdentity);
        requirePatternAgreement(opened, pattern);
        boolean sameReference = opened.referencedComponent == null
                ? referencedComponent == null
                : referencedComponent != null
                        && Arrays.equals(opened.referencedComponent.asUuidArray(),
                                referencedComponent.asUuidArray());
        if (!sameReference) {
            throw new IllegalArgumentException(
                    "Semantic " + declaredIdentity + " on " + birthFqn
                            + " is already opened with a different referenced component");
        }
        return opened;
    }

    private void requireSemanticIdentityAgreement(GenericSemanticLedger semantic, PublicId declaredIdentity) {
        if (!Arrays.equals(semantic.semanticId.asUuidArray(), declaredIdentity.asUuidArray())) {
            throw new IllegalArgumentException(
                    "Semantic " + declaredIdentity + " on " + birthFqn + " is already opened with"
                            + " identity " + semantic.semanticId + " — declared identities must agree exactly");
        }
    }

    private void requirePatternAgreement(GenericSemanticLedger semantic, EntityProxy.Pattern pattern) {
        if (pattern == null || !firstUuidOf(semantic.pattern.publicId()).equals(firstUuidOf(pattern.publicId()))) {
            throw new IllegalArgumentException(
                    "Semantic " + semantic.semanticId + " on " + birthFqn + " belongs to pattern "
                            + semantic.pattern.description() + " — patterns must agree on resume");
        }
    }

    private void requireDeclaredIdentity(PublicId declaredIdentity) {
        if (declaredIdentity == null || declaredIdentity.uuidCount() == 0) {
            throw new IllegalArgumentException(
                    "A generic semantic requires the declared identity it adopts, with at least one UUID: "
                            + birthFqn);
        }
    }

    /**
     * Validates that every field value is compose-safe: a value type the store accepts,
     * carried in identity form — never nids — so replay into a fresh store resolves the
     * references itself. Field-count and datatype fit against the pattern's field
     * definitions belong to the release verifier: at compose time the pattern chronology
     * need not be present at all.
     */
    private List<Object> validateFieldValues(Object[] fieldValues) {
        List<Object> values = new ArrayList<>(fieldValues.length);
        for (int index = 0; index < fieldValues.length; index++) {
            Object value = fieldValues[index];
            if (value == null) {
                throw new IllegalArgumentException(
                        "Field " + index + " on " + birthFqn + " is null — the store rejects null field values");
            }
            if (value instanceof Double) {
                throw new IllegalArgumentException(
                        "Field " + index + " on " + birthFqn + " is a double — the store narrows double"
                                + " to float silently; pass a Float");
            }
            if (value instanceof IntIdList || value instanceof IntIdSet) {
                throw new IllegalArgumentException(
                        "Field " + index + " on " + birthFqn + " is nid-based and not replay-stable —"
                                + " pass a PublicIdList or PublicIdSet");
            }
            if (value instanceof DiTreeEntity) {
                throw new IllegalArgumentException(
                        "Field " + index + " on " + birthFqn + " is a nid-based graph and not"
                                + " replay-stable — state axiom semantics with statedAxioms, which"
                                + " defers graph construction to write");
            }
            boolean supported = value instanceof String || value instanceof Boolean
                    || value instanceof Integer || value instanceof Long || value instanceof Float
                    || value instanceof BigDecimal || value instanceof Instant || value instanceof byte[]
                    || value instanceof EntityFacade || value instanceof PublicId
                    || value instanceof PublicIdList || value instanceof PublicIdSet;
            if (!supported) {
                throw new IllegalArgumentException(
                        "Field " + index + " on " + birthFqn + " has unsupported type "
                                + value.getClass().getName() + " — supported: String, Boolean, Integer,"
                                + " Long, Float, BigDecimal, Instant, byte[], EntityFacade, PublicId,"
                                + " PublicIdList, PublicIdSet");
            }
            values.add(value);
        }
        return List.copyOf(values);
    }

    /**
     * Whether a generic declaration is an explicit description of this component — any
     * well-formed description-pattern semantic, whatever its type field. Explicitly
     * declared descriptions mean the content arrives established (ingest), so the
     * derived FQN auto-seed must not add a twin.
     */
    private static boolean isExplicitDescriptionDeclaration(EntityProxy.Pattern pattern, Object[] fieldValues) {
        return firstUuidOf(pattern.publicId())
                .equals(firstUuidOf(TinkarTerm.DESCRIPTION_PATTERN.publicId()))
                && fieldValues.length == 4;
    }

    /** Whether two identities share any UUID — the store's merge-by-any-UUID identity notion. */
    private static boolean identityOverlaps(PublicId first, PublicId second) {
        for (UUID firstUuid : first.asUuidArray()) {
            for (UUID secondUuid : second.asUuidArray()) {
                if (firstUuid.equals(secondUuid)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static UUID firstUuidOf(PublicId publicId) {
        return publicId.asUuidArray()[0];
    }

    // ------------------------------------------------------------------ axioms

    /** Appends a stated-axiom version under the singleton semantic's derived identity. */
    void addAxiomVersion(Stamp stamp, Consumer<LogicalExpressionBuilder> axioms) {
        requireNewStamp(axiomVersions.stream().map(VersionEntry::stamp).toList(), stamp,
                "the stated-axiom semantic");
        axiomVersions.add(new VersionEntry<>(stamp, axioms));
    }

    /**
     * Appends a stated-axiom version under a declared identity — the identity the
     * stated-axiom semantic already carries in ingested or lifted content. The identity
     * must be declared at the semantic's first statement, and later statements must
     * agree (restating axioms plainly after a declared statement continues the same
     * semantic).
     */
    void addAxiomVersion(PublicId declaredIdentity, Stamp stamp, Consumer<LogicalExpressionBuilder> axioms) {
        if (declaredIdentity == null || declaredIdentity.uuidCount() == 0) {
            throw new IllegalArgumentException(
                    "A declared axiom identity requires a PublicId with at least one UUID: " + birthFqn);
        }
        if (declaredAxiomIdentity == null) {
            if (!axiomVersions.isEmpty()) {
                throw new IllegalArgumentException(
                        "The stated-axiom semantic of " + birthFqn + " is already versioned under its"
                                + " derived identity — declare the established identity at its first statement");
            }
            requireNewStamp(axiomVersions.stream().map(VersionEntry::stamp).toList(), stamp,
                    "the stated-axiom semantic");
            registry.registerIdentity(declaredIdentity,
                    "stated-axiom semantic of \"" + birthFqn + "\"");
            declaredAxiomIdentity = declaredIdentity;
        } else if (!Arrays.equals(declaredAxiomIdentity.asUuidArray(), declaredIdentity.asUuidArray())) {
            throw new IllegalArgumentException(
                    "The stated-axiom semantic of " + birthFqn + " is declared with identity "
                            + declaredAxiomIdentity + " — cannot restate it with " + declaredIdentity);
        } else {
            requireNewStamp(axiomVersions.stream().map(VersionEntry::stamp).toList(), stamp,
                    "the stated-axiom semantic");
        }
        axiomVersions.add(new VersionEntry<>(stamp, axioms));
    }

    // ------------------------------------------------------------------ replay

    int componentNid() {
        return nidFor(componentId);
    }

    /**
     * Writes the stamp entity for a declared stamp if this ledger has not written it
     * yet, and returns its nid. Stamp identity is tuple-derived — so re-writing the same
     * tuple is a no-op merge — or declared, when the stamp adopts an established
     * identity ({@link Stamp#declaredIdentity()}); a declared identity may carry
     * multiple UUIDs, all registered to the one nid.
     */
    int writeStamp(Stamp stamp) {
        registry.requireStampAgreement(stamp);
        PublicId stampId = stamp.publicId();
        UUID primordial = stampId.asUuidArray()[0];
        int stampNid = EntityService.get().nidForStamp(stampId);
        boolean alreadyWritten = false;
        for (UUID uuid : stampId.asUuidArray()) {
            if (writtenStamps.contains(uuid)) {
                alreadyWritten = true;
                break;
            }
        }
        if (!alreadyWritten) {
            for (UUID uuid : stampId.asUuidArray()) {
                writtenStamps.add(uuid);
            }
            RecordListBuilder<StampVersionRecord> versionRecords = RecordListBuilder.make();
            StampRecord stampEntity = new StampRecord(primordial.getMostSignificantBits(),
                    primordial.getLeastSignificantBits(), stampId.additionalUuidLongs(), stampNid,
                    versionRecords);
            versionRecords.add(new StampVersionRecord(stampEntity, stamp.state().nid(), stamp.time(),
                    nidFor(stamp.author().publicId()), nidFor(stamp.module().publicId()),
                    nidFor(stamp.path().publicId())));
            versionRecords.build();
            EntityService.get().putEntity(stampEntity);
        }
        return stampNid;
    }

    /** Writes every description ledger and its dialect-acceptability semantic. */
    void writeDescriptions(int componentNid) {
        seedFqnIfImplicit();
        for (DescriptionLedger description : descriptions) {
            writeDescription(description, componentNid);
        }
    }

    private void writeDescription(DescriptionLedger description, int componentNid) {
        int descriptionNid = nidFor(description.uuid);
        RecordListBuilder<SemanticVersionRecord> versions = RecordListBuilder.make();
        SemanticRecord bootstrap = newSemantic(PublicIds.of(description.uuid),
                TinkarTerm.DESCRIPTION_PATTERN, componentNid, versions);
        for (VersionEntry<String> version : description.versions) {
            versions.add(SemanticVersionRecordBuilder.builder()
                    .chronology(bootstrap)
                    .stampNid(writeStamp(version.stamp()))
                    .fieldValues(Lists.immutable.of(
                            description.language, version.value(),
                            TinkarTerm.DESCRIPTION_NOT_CASE_SENSITIVE, description.type))
                    .build());
        }
        EntityService.get().putEntity(
                SemanticRecordBuilder.builder(bootstrap).versions(versions.toImmutable()).build());

        writeDialect(description, descriptionNid);
    }

    private void writeDialect(DescriptionLedger description, int descriptionNid) {
        UUID dialectUuid = UuidT5Generator.get(description.uuid, "us-dialect");
        RecordListBuilder<SemanticVersionRecord> versions = RecordListBuilder.make();
        SemanticRecord bootstrap = newSemantic(PublicIds.of(dialectUuid),
                TinkarTerm.US_DIALECT_PATTERN, descriptionNid, versions);
        for (VersionEntry<EntityProxy.Concept> dialect : description.dialects) {
            versions.add(SemanticVersionRecordBuilder.builder()
                    .chronology(bootstrap)
                    .stampNid(writeStamp(dialect.stamp()))
                    .fieldValues(Lists.immutable.of(dialect.value()))
                    .build());
        }
        EntityService.get().putEntity(
                SemanticRecordBuilder.builder(bootstrap).versions(versions.toImmutable()).build());
    }

    /**
     * Writes the singleton stated-axiom semantic: identity declared when the ledger
     * adopted one, else derived from the component. Each version's {@link DiTreeEntity}
     * is composed here, at write — graph construction mints nids, so deferring it keeps
     * ledger composition store-free. Vertex UUIDs are minted deterministically from the
     * axiom identity and the version's stamp, in composition order: replaying the same
     * ledger reproduces the same graph bytes, which the store merge then no-ops instead
     * of silently replacing the version payload.
     */
    void writeAxioms(int componentNid) {
        if (axiomVersions.isEmpty()) {
            return;
        }
        PublicId axiomId = declaredAxiomIdentity != null
                ? declaredAxiomIdentity
                : PublicIds.of(UuidT5Generator.get(componentUuid, "el-plus-plus-stated-axioms"));
        UUID axiomUuid = firstUuidOf(axiomId);
        RecordListBuilder<SemanticVersionRecord> versions = RecordListBuilder.make();
        SemanticRecord bootstrap = newSemantic(axiomId,
                TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN, componentNid, versions);
        for (VersionEntry<Consumer<LogicalExpressionBuilder>> axiom : axiomVersions) {
            UUID stampUuid = firstUuidOf(axiom.stamp().publicId());
            int[] vertexOrdinal = {0};
            LogicalExpressionBuilder logicalExpressionBuilder = new LogicalExpressionBuilder(
                    UuidT5Generator.get(axiomUuid, "definition-root|" + stampUuid),
                    () -> UuidT5Generator.get(axiomUuid, stampUuid + "|" + vertexOrdinal[0]++));
            axiom.value().accept(logicalExpressionBuilder);
            DiTreeEntity tree = (DiTreeEntity) logicalExpressionBuilder.build().sourceGraph();
            versions.add(SemanticVersionRecordBuilder.builder()
                    .chronology(bootstrap)
                    .stampNid(writeStamp(axiom.stamp()))
                    .fieldValues(Lists.immutable.of(tree))
                    .build());
        }
        EntityService.get().putEntity(
                SemanticRecordBuilder.builder(bootstrap).versions(versions.toImmutable()).build());
    }

    /**
     * Writes every generic declared-identity semantic ledger. References are resolved to
     * nids here, at write: the referenced component and the pattern are minted from
     * their full public ids, so lookups by any of their UUIDs resolve — whether or not
     * their chronologies are present yet.
     */
    void writeGenericSemantics(int componentNid) {
        for (GenericSemanticLedger semantic : genericSemantics.values()) {
            int referencedNid = semantic.referencedComponent == null
                    ? componentNid
                    : nidFor(semantic.referencedComponent);
            RecordListBuilder<SemanticVersionRecord> versions = RecordListBuilder.make();
            SemanticRecord bootstrap = newSemantic(semantic.semanticId,
                    semantic.pattern, referencedNid, versions);
            for (VersionEntry<List<Object>> version : semantic.versions) {
                versions.add(SemanticVersionRecordBuilder.builder()
                        .chronology(bootstrap)
                        .stampNid(writeStamp(version.stamp()))
                        .fieldValues(Lists.immutable.ofAll(version.value()))
                        .build());
            }
            EntityService.get().putEntity(
                    SemanticRecordBuilder.builder(bootstrap).versions(versions.toImmutable()).build());
        }
    }

    static int nidFor(UUID uuid) {
        return PrimitiveData.nid(PublicIds.of(uuid));
    }

    static int nidFor(PublicId publicId) {
        return PrimitiveData.nid(publicId);
    }

    /**
     * Builds a semantic bootstrap record from its full public id — additional UUIDs
     * included — minting the nid pattern-scoped from the pattern's public id the caller
     * already holds. Never reverse-resolves a nid to a public id, so it works on
     * persistent providers even when the pattern chronology is not present yet.
     */
    private static SemanticRecord newSemantic(PublicId semanticId, EntityProxy.Pattern pattern,
                                              int referencedComponentNid,
                                              RecordListBuilder<SemanticVersionRecord> versions) {
        PublicIdentifierRecord identifier = PublicIdentifierRecord.make(semanticId);
        int nid = ScopedValue.where(PrimitiveData.SCOPED_PATTERN_PUBLICID_FOR_NID, pattern.publicId())
                .call(() -> PrimitiveData.nid(semanticId));
        return SemanticRecordBuilder.builder()
                .mostSignificantBits(identifier.mostSignificantBits())
                .leastSignificantBits(identifier.leastSignificantBits())
                .additionalUuidLongs(identifier.additionalUuidLongs())
                .nid(nid)
                .patternNid(nidFor(pattern.publicId()))
                .referencedComponentNid(referencedComponentNid)
                .versions(versions)
                .build();
    }

    // ------------------------------------------------------------------ ledger records

    record VersionEntry<T>(Stamp stamp, T value) {
    }

    /**
     * The version ledger of one generic declared-identity semantic: its established
     * identity, its pattern, the component it references ({@code null} means the owning
     * component), and its versions in declaration order.
     */
    static final class GenericSemanticLedger {
        final PublicId semanticId;
        final EntityProxy.Pattern pattern;
        final PublicId referencedComponent;
        final List<VersionEntry<List<Object>>> versions = new ArrayList<>();

        GenericSemanticLedger(PublicId semanticId, EntityProxy.Pattern pattern, PublicId referencedComponent) {
            this.semanticId = semanticId;
            this.pattern = pattern;
            this.referencedComponent = referencedComponent;
        }
    }

    static final class DescriptionLedger {
        final UUID uuid;
        final EntityProxy.Concept type;
        final ConceptFacade language;
        final List<VersionEntry<String>> versions = new ArrayList<>();
        final List<VersionEntry<EntityProxy.Concept>> dialects = new ArrayList<>();

        DescriptionLedger(UUID uuid, EntityProxy.Concept type, ConceptFacade language) {
            this.uuid = uuid;
            this.type = type;
            this.language = language;
        }

        String currentText() {
            return versions.getLast().value();
        }

        boolean retired() {
            return versions.getLast().stamp() instanceof InactiveStamp;
        }
    }
}
