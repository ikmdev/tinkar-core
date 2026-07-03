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

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.ConceptRecord;
import dev.ikm.tinkar.entity.ConceptRecordBuilder;
import dev.ikm.tinkar.entity.ConceptVersionRecord;
import dev.ikm.tinkar.entity.ConceptVersionRecordBuilder;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.SemanticRecordBuilder;
import dev.ikm.tinkar.entity.SemanticVersionRecord;
import dev.ikm.tinkar.entity.SemanticVersionRecordBuilder;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpressionBuilder;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.factory.Lists;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Ledger-form authoring of a concept and its attached semantics — descriptions, dialect
 * acceptability, and stated axioms — as one coherent, replayable declaration.
 * <p>
 * The source form is an append-only version ledger: {@link #at(ActiveStamp)} opens a
 * version scope bound to a declared {@link Stamp}, the verbs inside it say what changed at
 * that stamp, and {@code build()} replays the accumulated declarations into the open
 * datastore. There is no transaction: declared stamps are committed facts, writes go
 * directly through {@link EntityService#putEntity}, and replaying the same ledger yields
 * byte-identical entities (all identities are derived, none random).
 *
 * <h2>Grammar</h2>
 * <ul>
 *   <li><b>Singleton-keyed semantics revise by restatement.</b> The fully qualified name
 *       and the stated-axiom set have one semantic per concept (per keying dimension), so
 *       stating them in a later scope is a revision — a new version of the same semantic.</li>
 *   <li><b>Open-multiplicity semantics use explicit verbs.</b> {@link ActiveScope#synonym}
 *       and {@link ActiveScope#definition} always <em>add</em>;
 *       {@link ActiveScope#reviseSynonym(String, String)} and
 *       {@link RetireScope#retireSynonym(String)} reference an existing one by its current
 *       text, resolved to its declaration-ordinal identity (stable because the ledger is
 *       append-only).</li>
 *   <li><b>Status is compile-visible.</b> {@link #at(ActiveStamp)} yields content verbs;
 *       {@link #at(InactiveStamp)} yields only retirement verbs.</li>
 * </ul>
 *
 * <h2>Identity</h2>
 * The concept's identity is {@code T5(namespace, birthFqn)} (see {@link Namespace}).
 * Attached semantics derive their identity from authoring context, never content:
 * the FQN description from {@code (concept, type, language)}; synonyms and definitions
 * from {@code (concept, type, language, declaration ordinal)}; dialect semantics from
 * their description; the stated-axiom semantic from the concept. Axiom graphs are
 * constructed by {@link LogicalExpressionBuilder} — this class adds no graph assembly.
 * <p>
 * Description semantics are written in the canonical description-pattern field order
 * (language, text, case significance, type). When the pattern chronology is present in
 * the store (a starter-data-seeded build), the release verifier — not this class — is
 * responsible for validating that convention against the pattern's field definitions.
 *
 * <pre>{@code
 * RICH_SURFACE.concept("Journal element (RichSurfaceTerms)")
 *     .at(W1)
 *         .synonym("Journal element")
 *         .statedAxioms(leb -> leb.NecessarySet(leb.And(leb.ConceptAxiom(RICH_SURFACE_ROOT))))
 *     .at(W2)
 *         .synonym("Journal block")                          // add — a second synonym
 *         .reviseSynonym("Journal element", "Journal atom")  // revise — new version of the first
 *     .build();
 * }</pre>
 */
public final class ConceptBuilder {

    private final Namespace namespace;
    private final String birthFqn;
    private final UUID conceptUuid;

    private final List<Stamp> conceptStamps = new ArrayList<>();
    private final List<DescriptionLedger> descriptions = new ArrayList<>();
    private final List<VersionEntry<DiTreeEntity>> axiomVersions = new ArrayList<>();

    private long lastStampTime = Long.MIN_VALUE;
    private boolean born = false;
    private boolean built = false;

    ConceptBuilder(Namespace namespace, String birthFqn) {
        this.namespace = namespace;
        this.birthFqn = birthFqn;
        this.conceptUuid = namespace.uuidFor(birthFqn);
    }

    /**
     * The identity this declaration derives: {@code T5(namespace, birthFqn)}.
     *
     * @return the concept's public id
     */
    public PublicId publicId() {
        return PublicIds.of(conceptUuid);
    }

    /**
     * Opens a content scope at the given active stamp. The first active scope is the
     * birth scope: it records the concept's first version and creates the fully
     * qualified name description from the birth FQN.
     *
     * @param stamp the declared active stamp this scope's changes are bound to
     * @return the content scope
     * @throws IllegalArgumentException if the stamp's time precedes a previously scoped
     *                                  stamp's time — the ledger must be chronological
     * @throws IllegalStateException    if {@code build()} has already run
     */
    public ActiveScope at(ActiveStamp stamp) {
        checkChronology(stamp);
        if (!born) {
            born = true;
            conceptStamps.add(stamp);
            DescriptionLedger fqn = new DescriptionLedger(
                    fqnUuid(), TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE, TinkarTerm.ENGLISH_LANGUAGE);
            fqn.versions.add(new VersionEntry<>(stamp, birthFqn));
            fqn.dialects.add(new VersionEntry<>(stamp, TinkarTerm.PREFERRED));
            descriptions.add(fqn);
        }
        return new ActiveScope(stamp);
    }

    /**
     * Opens a retirement scope at the given inactive stamp. Only retirement verbs are
     * available: retirement is always a new version bound to a new inactive stamp, never
     * a mutation, matching the append-only stamp discipline.
     *
     * @param stamp the declared inactive stamp this scope's retirements are bound to
     * @return the retirement scope
     * @throws IllegalArgumentException if the stamp's time precedes a previously scoped
     *                                  stamp's time — the ledger must be chronological
     * @throws IllegalStateException    if the concept has no birth scope yet, or if
     *                                  {@code build()} has already run
     */
    public RetireScope at(InactiveStamp stamp) {
        if (!born) {
            throw new IllegalStateException(
                    "Cannot open a retirement scope before the birth scope: " + birthFqn);
        }
        checkChronology(stamp);
        return new RetireScope(stamp);
    }

    private void checkChronology(Stamp stamp) {
        if (built) {
            throw new IllegalStateException("This declaration has already been built: " + birthFqn);
        }
        if (stamp.time() < lastStampTime) {
            throw new IllegalArgumentException(
                    "Ledger scopes must be chronological: stamp time " + stamp.time()
                            + " precedes prior scope time " + lastStampTime + " for " + birthFqn);
        }
        lastStampTime = stamp.time();
    }

    /**
     * Replays the accumulated declarations into the open datastore: the stamps used, the
     * concept chronology, every description with its dialect-acceptability semantic, and
     * the stated-axiom semantic. Writes go directly through
     * {@link EntityService#putEntity}; provider-level merge reconciles with any existing
     * versions of the same chronologies.
     *
     * @return a proxy for the built concept, carrying its birth FQN and derived identity
     * @throws IllegalStateException if no birth scope was declared, or on repeat invocation
     */
    public EntityProxy.Concept build() {
        if (!born) {
            throw new IllegalStateException("A concept declaration requires a birth scope: " + birthFqn);
        }
        if (built) {
            throw new IllegalStateException("This declaration has already been built: " + birthFqn);
        }
        built = true;

        Set<UUID> writtenStamps = new HashSet<>();
        int conceptNid = nidFor(conceptUuid);

        writeConcept(conceptNid, writtenStamps);
        for (DescriptionLedger description : descriptions) {
            writeDescription(description, conceptNid, writtenStamps);
        }
        writeAxioms(conceptNid, writtenStamps);

        return EntityProxy.Concept.make(birthFqn, PublicIds.of(conceptUuid));
    }

    // ------------------------------------------------------------------ scopes

    /**
     * The content verbs available under an {@link ActiveStamp}. Every verb records a
     * change bound to this scope's stamp and returns the scope for chaining.
     */
    public final class ActiveScope {
        private final ActiveStamp stamp;

        private ActiveScope(ActiveStamp stamp) {
            this.stamp = stamp;
        }

        /**
         * Adds a new synonym (regular-name description) in English, with the default
         * US-dialect acceptability of {@code PREFERRED}. Always an addition — to change
         * an existing synonym use {@link #reviseSynonym(String, String)}.
         *
         * @param text the synonym text
         * @return this scope, for chaining
         */
        public ActiveScope synonym(String text) {
            addDescription(TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE, "synonym", text, stamp);
            return this;
        }

        /**
         * Adds a new definition description in English, with the default US-dialect
         * acceptability of {@code PREFERRED}.
         *
         * @param text the definition text
         * @return this scope, for chaining
         */
        public ActiveScope definition(String text) {
            addDescription(TinkarTerm.DEFINITION_DESCRIPTION_TYPE, "definition", text, stamp);
            return this;
        }

        /**
         * Revises an existing synonym: a new version of the same description semantic
         * with the new text, bound to this scope's stamp. The synonym is referenced by
         * its current text; identity is unchanged by revision.
         *
         * @param currentText the text the synonym carries before this revision
         * @param newText     the text after this revision
         * @return this scope, for chaining
         * @throws IllegalArgumentException if no live synonym carries {@code currentText},
         *                                  or if more than one does (ambiguous reference)
         */
        public ActiveScope reviseSynonym(String currentText, String newText) {
            resolveLive(TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE, currentText, "synonym")
                    .versions.add(new VersionEntry<>(stamp, newText));
            return this;
        }

        /**
         * Revises the fully qualified name: a new version of the FQN description with the
         * new text. The concept's identity is unaffected — identity was seeded by the FQN
         * <em>at birth</em>, which the append-only ledger records permanently.
         *
         * @param newText the fully qualified name after this revision
         * @return this scope, for chaining
         */
        public ActiveScope reviseFullyQualifiedName(String newText) {
            descriptions.getFirst().versions.add(new VersionEntry<>(stamp, newText));
            return this;
        }

        /**
         * States the concept's axioms: a version of the singleton stated-axiom semantic,
         * whose value is the {@link DiTreeEntity} the given consumer composes on a fresh
         * {@link LogicalExpressionBuilder}. Restating axioms in a later scope revises the
         * same semantic — the whole expression is restated, matching how axiom versions
         * are stored.
         *
         * @param axioms composes the logical expression, for example
         *               {@code leb -> leb.NecessarySet(leb.And(leb.ConceptAxiom(parent)))}
         * @return this scope, for chaining
         */
        public ActiveScope statedAxioms(Consumer<LogicalExpressionBuilder> axioms) {
            LogicalExpressionBuilder leb = new LogicalExpressionBuilder();
            axioms.accept(leb);
            axiomVersions.add(new VersionEntry<>(stamp, (DiTreeEntity) leb.build().sourceGraph()));
            return this;
        }

        /**
         * Opens the next content scope. Delegates to {@link ConceptBuilder#at(ActiveStamp)}.
         *
         * @param nextStamp the declared active stamp for the next scope
         * @return the next content scope
         */
        public ActiveScope at(ActiveStamp nextStamp) {
            return ConceptBuilder.this.at(nextStamp);
        }

        /**
         * Opens a retirement scope. Delegates to {@link ConceptBuilder#at(InactiveStamp)}.
         *
         * @param nextStamp the declared inactive stamp for the retirement scope
         * @return the retirement scope
         */
        public RetireScope at(InactiveStamp nextStamp) {
            return ConceptBuilder.this.at(nextStamp);
        }

        /**
         * Terminal: replays the ledger into the datastore.
         * Delegates to {@link ConceptBuilder#build()}.
         *
         * @return a proxy for the built concept
         */
        public EntityProxy.Concept build() {
            return ConceptBuilder.this.build();
        }
    }

    /**
     * The retirement verbs available under an {@link InactiveStamp}. Content verbs are
     * deliberately absent: authoring content under a retirement stamp does not compile.
     */
    public final class RetireScope {
        private final InactiveStamp stamp;

        private RetireScope(InactiveStamp stamp) {
            this.stamp = stamp;
        }

        /**
         * Retires the concept itself: a new concept version bound to this scope's
         * inactive stamp. Attached semantics are not retired implicitly.
         *
         * @return this scope, for chaining
         */
        public RetireScope retire() {
            conceptStamps.add(stamp);
            return this;
        }

        /**
         * Retires a synonym: a new version of the referenced description, same text,
         * bound to this scope's inactive stamp.
         *
         * @param currentText the text the synonym carries — the reference key
         * @return this scope, for chaining
         * @throws IllegalArgumentException if no live synonym carries {@code currentText},
         *                                  or if more than one does (ambiguous reference)
         */
        public RetireScope retireSynonym(String currentText) {
            DescriptionLedger target = resolveLive(TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE, currentText, "synonym");
            target.versions.add(new VersionEntry<>(stamp, currentText));
            return this;
        }

        /**
         * Retires a definition: a new version of the referenced description, same text,
         * bound to this scope's inactive stamp.
         *
         * @param currentText the text the definition carries — the reference key
         * @return this scope, for chaining
         * @throws IllegalArgumentException if no live definition carries {@code currentText},
         *                                  or if more than one does (ambiguous reference)
         */
        public RetireScope retireDefinition(String currentText) {
            DescriptionLedger target = resolveLive(TinkarTerm.DEFINITION_DESCRIPTION_TYPE, currentText, "definition");
            target.versions.add(new VersionEntry<>(stamp, currentText));
            return this;
        }

        /**
         * Opens the next content scope. Delegates to {@link ConceptBuilder#at(ActiveStamp)}.
         *
         * @param nextStamp the declared active stamp for the next scope
         * @return the next content scope
         */
        public ActiveScope at(ActiveStamp nextStamp) {
            return ConceptBuilder.this.at(nextStamp);
        }

        /**
         * Opens another retirement scope. Delegates to {@link ConceptBuilder#at(InactiveStamp)}.
         *
         * @param nextStamp the declared inactive stamp for the next retirement scope
         * @return the retirement scope
         */
        public RetireScope at(InactiveStamp nextStamp) {
            return ConceptBuilder.this.at(nextStamp);
        }

        /**
         * Terminal: replays the ledger into the datastore.
         * Delegates to {@link ConceptBuilder#build()}.
         *
         * @return a proxy for the built concept
         */
        public EntityProxy.Concept build() {
            return ConceptBuilder.this.build();
        }
    }

    // ------------------------------------------------------------------ accumulation

    private void addDescription(EntityProxy.Concept type, String kindKey, String text, Stamp stamp) {
        long ordinal = descriptions.stream().filter(d -> d.type.equals(type)).count();
        UUID descriptionUuid = UuidT5Generator.get(conceptUuid,
                kindKey + "|" + TinkarTerm.ENGLISH_LANGUAGE.publicId().asUuidArray()[0] + "|" + ordinal);
        DescriptionLedger description = new DescriptionLedger(descriptionUuid, type, TinkarTerm.ENGLISH_LANGUAGE);
        description.versions.add(new VersionEntry<>(stamp, text));
        description.dialects.add(new VersionEntry<>(stamp, TinkarTerm.PREFERRED));
        descriptions.add(description);
    }

    private DescriptionLedger resolveLive(EntityProxy.Concept type, String currentText, String kindLabel) {
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

    private UUID fqnUuid() {
        return UuidT5Generator.get(conceptUuid,
                "fully-qualified-name|" + TinkarTerm.ENGLISH_LANGUAGE.publicId().asUuidArray()[0]);
    }

    // ------------------------------------------------------------------ replay

    private int writeStamp(Stamp stamp, Set<UUID> written) {
        UUID stampUuid = stamp.publicId().asUuidArray()[0];
        int stampNid = nidFor(stampUuid);
        if (written.add(stampUuid)) {
            StampRecord record = StampRecord.make(stampUuid, stamp.state(), stamp.time(),
                    stamp.author().publicId(), stamp.module().publicId(), stamp.path().publicId());
            EntityService.get().putEntity(record);
        }
        return stampNid;
    }

    private void writeConcept(int conceptNid, Set<UUID> writtenStamps) {
        RecordListBuilder<ConceptVersionRecord> versions = RecordListBuilder.make();
        ConceptRecord bootstrap = ConceptRecordBuilder.builder()
                .nid(conceptNid)
                .mostSignificantBits(conceptUuid.getMostSignificantBits())
                .leastSignificantBits(conceptUuid.getLeastSignificantBits())
                .versions(versions)
                .build();
        for (Stamp stamp : conceptStamps) {
            versions.add(ConceptVersionRecordBuilder.builder()
                    .chronology(bootstrap)
                    .stampNid(writeStamp(stamp, writtenStamps))
                    .build());
        }
        EntityService.get().putEntity(
                ConceptRecordBuilder.builder(bootstrap).versions(versions.toImmutable()).build());
    }

    private void writeDescription(DescriptionLedger description, int conceptNid, Set<UUID> writtenStamps) {
        int descriptionNid = nidFor(description.uuid);
        RecordListBuilder<SemanticVersionRecord> versions = RecordListBuilder.make();
        SemanticRecord bootstrap = SemanticRecordBuilder.builder()
                .nid(descriptionNid)
                .mostSignificantBits(description.uuid.getMostSignificantBits())
                .leastSignificantBits(description.uuid.getLeastSignificantBits())
                .patternNid(TinkarTerm.DESCRIPTION_PATTERN.nid())
                .referencedComponentNid(conceptNid)
                .versions(versions)
                .build();
        for (VersionEntry<String> version : description.versions) {
            versions.add(SemanticVersionRecordBuilder.builder()
                    .chronology(bootstrap)
                    .stampNid(writeStamp(version.stamp, writtenStamps))
                    .fieldValues(Lists.immutable.of(
                            description.language, version.value,
                            TinkarTerm.DESCRIPTION_NOT_CASE_SENSITIVE, description.type))
                    .build());
        }
        EntityService.get().putEntity(
                SemanticRecordBuilder.builder(bootstrap).versions(versions.toImmutable()).build());

        writeDialect(description, descriptionNid, writtenStamps);
    }

    private void writeDialect(DescriptionLedger description, int descriptionNid, Set<UUID> writtenStamps) {
        UUID dialectUuid = UuidT5Generator.get(description.uuid, "us-dialect");
        RecordListBuilder<SemanticVersionRecord> versions = RecordListBuilder.make();
        SemanticRecord bootstrap = SemanticRecordBuilder.builder()
                .nid(nidFor(dialectUuid))
                .mostSignificantBits(dialectUuid.getMostSignificantBits())
                .leastSignificantBits(dialectUuid.getLeastSignificantBits())
                .patternNid(TinkarTerm.US_DIALECT_PATTERN.nid())
                .referencedComponentNid(descriptionNid)
                .versions(versions)
                .build();
        for (VersionEntry<EntityProxy.Concept> dialect : description.dialects) {
            versions.add(SemanticVersionRecordBuilder.builder()
                    .chronology(bootstrap)
                    .stampNid(writeStamp(dialect.stamp, writtenStamps))
                    .fieldValues(Lists.immutable.of(dialect.value))
                    .build());
        }
        EntityService.get().putEntity(
                SemanticRecordBuilder.builder(bootstrap).versions(versions.toImmutable()).build());
    }

    private void writeAxioms(int conceptNid, Set<UUID> writtenStamps) {
        if (axiomVersions.isEmpty()) {
            return;
        }
        UUID axiomUuid = UuidT5Generator.get(conceptUuid, "el-plus-plus-stated-axioms");
        RecordListBuilder<SemanticVersionRecord> versions = RecordListBuilder.make();
        SemanticRecord bootstrap = SemanticRecordBuilder.builder()
                .nid(nidFor(axiomUuid))
                .mostSignificantBits(axiomUuid.getMostSignificantBits())
                .leastSignificantBits(axiomUuid.getLeastSignificantBits())
                .patternNid(TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid())
                .referencedComponentNid(conceptNid)
                .versions(versions)
                .build();
        for (VersionEntry<DiTreeEntity> axiom : axiomVersions) {
            versions.add(SemanticVersionRecordBuilder.builder()
                    .chronology(bootstrap)
                    .stampNid(writeStamp(axiom.stamp, writtenStamps))
                    .fieldValues(Lists.immutable.of(axiom.value))
                    .build());
        }
        EntityService.get().putEntity(
                SemanticRecordBuilder.builder(bootstrap).versions(versions.toImmutable()).build());
    }

    private static int nidFor(UUID uuid) {
        return PrimitiveData.nid(PublicIds.of(uuid));
    }

    // ------------------------------------------------------------------ ledger state

    private record VersionEntry<T>(Stamp stamp, T value) {
    }

    private static final class DescriptionLedger {
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
