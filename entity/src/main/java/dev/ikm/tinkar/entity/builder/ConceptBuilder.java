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
import dev.ikm.tinkar.entity.ConceptRecord;
import dev.ikm.tinkar.entity.ConceptRecordBuilder;
import dev.ikm.tinkar.entity.ConceptVersionRecord;
import dev.ikm.tinkar.entity.ConceptVersionRecordBuilder;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiom;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpressionBuilder;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.TinkarTerm;

import java.util.List;
import java.util.function.Consumer;

/**
 * Ledger-form authoring of a concept and its attached semantics — descriptions, dialect
 * acceptability, and stated axioms — as one coherent, replayable declaration.
 * <p>
 * The source form is an append-only version ledger: {@link #at(ActiveStamp)} opens a
 * version scope bound to a declared {@link Stamp}, the verbs inside it say what changed at
 * that stamp, and {@link KnowledgeSet#write()} replays the accumulated declarations into
 * the open datastore. There is no transaction: declared stamps are committed facts, writes go
 * directly through {@link EntityService#putEntity}, and replaying the same ledger yields
 * identical entities (all identities are derived, none random).
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
 * The concept's identity is {@code T5(setUuid, birthFqn)} (see {@link KnowledgeSet}).
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
 *         .reviseSynonym("Journal element", "Journal atom"); // revise — new version of the first
 * }</pre>
 */
public final class ConceptBuilder {

    private final ComponentLedger ledger;

    ConceptBuilder(PublicId componentId, String birthFqn, SessionRegistry registry) {
        this.ledger = new ComponentLedger(componentId, birthFqn, registry);
    }

    /**
     * The identity of this declaration: {@code T5(setUuid, birthFqn)} when derived, or
     * the declared identity when the ledger adopted an established one (see
     * {@link KnowledgeSet#concept(String, java.util.UUID)}) — in full, including any
     * additional UUIDs the established identity carries.
     *
     * @return the concept's public id
     */
    public PublicId publicId() {
        return ledger.componentId;
    }

    ComponentLedger ledger() {
        return ledger;
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
     */
    public ActiveScope at(ActiveStamp stamp) {
        ledger.checkChronology(stamp);
        if (!ledger.born()) {
            ledger.birth(stamp);
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
     * @throws IllegalStateException    if the concept has no birth scope yet
     */
    public RetireScope at(InactiveStamp stamp) {
        ledger.requireBornForRetirement();
        ledger.checkChronology(stamp);
        return new RetireScope(stamp);
    }

    /**
     * Replays the accumulated declarations into the open datastore: the stamps used, the
     * concept chronology, every description with its dialect-acceptability semantic, and
     * the stated-axiom semantic. Invoked by {@link KnowledgeSet#write()}; repeatable —
     * identities and stamps are derived, so re-writing merges to the same state.
     */
    void writeInto() {
        ledger.requireBornForWrite();
        int conceptNid = ledger.componentNid();
        writeConcept();
        ledger.writeDescriptions(conceptNid);
        ledger.writeAxioms(conceptNid);
        ledger.writeGenericSemantics(conceptNid);
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
            ledger.addDescription(TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE, "synonym", text, stamp);
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
            ledger.addDescription(TinkarTerm.DEFINITION_DESCRIPTION_TYPE, "definition", text, stamp);
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
            ledger.appendDescriptionVersion(
                    ledger.resolveLive(TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE, currentText, "synonym"),
                    stamp, newText);
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
            ledger.appendDescriptionVersion(ledger.fqnLedger(), stamp, newText);
            return this;
        }

        /**
         * Records a new active version of the concept itself at this scope's stamp —
         * the concept-level revision an ingested multi-version chronology restates.
         * The birth scope already records the first version; one version per stamp.
         *
         * @return this scope, for chaining
         * @throws IllegalArgumentException if the concept chronology already has a
         *                                  version at this scope's stamp
         */
        public ActiveScope revise() {
            ledger.addComponentStamp(stamp);
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
            // Materialized at write() — composing stays store-free (graph construction
            // mints nids, which requires a started PrimitiveData store).
            ledger.addAxiomVersion(stamp, axioms);
            return this;
        }

        /**
         * States the concept's axioms under a <em>declared</em> identity: the identity
         * the stated-axiom semantic already carries — content ingested from an existing
         * starter set, or axioms authored interactively and lifted back into the ledger.
         * The identity must be declared at the semantic's first statement; restating
         * axioms in a later scope — with the same declared identity or plainly —
         * continues the same singleton semantic.
         *
         * @param declaredIdentity the established identity of the stated-axiom semantic
         * @param axioms           composes the logical expression, as in
         *                         {@link #statedAxioms(Consumer)}
         * @return this scope, for chaining
         * @throws IllegalArgumentException if {@code declaredIdentity} is null or empty,
         *                                  disagrees with an earlier declaration, or
         *                                  arrives after the semantic already versioned
         *                                  under its derived identity
         */
        public ActiveScope statedAxioms(PublicId declaredIdentity, Consumer<LogicalExpressionBuilder> axioms) {
            ledger.addAxiomVersion(declaredIdentity, stamp, axioms);
            return this;
        }

        /**
         * Declares a version of a <em>generic</em> declared-identity semantic on this
         * concept — the ingest workhorse: any pattern, the semantic's established
         * identity, and its field values verbatim. Where the rich verbs derive identity
         * from authoring context and add conveniences (a synonym brings its US-dialect
         * acceptability), the generic verb adds nothing: one semantic version, exactly
         * as stated — which is what identity-exact ingest requires. Restating the same
         * declared identity in a later scope appends a version of the same semantic.
         * <p>
         * Field values are carried in identity form and resolved at write: components as
         * {@link EntityProxy} handles (or {@link PublicId}s), component lists and sets as
         * {@code PublicIdList}/{@code PublicIdSet}, plus String, Boolean, Integer, Long,
         * Float, BigDecimal, Instant, and byte[]. A membership semantic declares no
         * fields. Field-count and datatype fit against the pattern's field definitions
         * are the release verifier's concern — at compose time the pattern chronology
         * need not be present.
         *
         * @param pattern          the semantic's pattern
         * @param declaredIdentity the established identity the semantic adopts
         * @param fieldValues      the version's field values, in the pattern's field order
         * @return this scope, for chaining
         * @throws IllegalArgumentException if the pattern or identity is missing, a
         *                                  resumed declaration disagrees on identity,
         *                                  pattern, or referenced component, a field
         *                                  value's type is unsupported, or this stamp
         *                                  already carries a version of the semantic
         */
        public ActiveScope semantic(EntityProxy.Pattern pattern, PublicId declaredIdentity, Object... fieldValues) {
            ledger.addGenericVersion(pattern, declaredIdentity, null, stamp, fieldValues);
            return this;
        }

        /**
         * Declares a version of a generic declared-identity semantic on <em>another</em>
         * component — one whose identity is established, typically a semantic declared
         * earlier in this ledger: a dialect-acceptability semantic references its
         * description, for example. See
         * {@link #semantic(EntityProxy.Pattern, PublicId, Object...)} for the generic
         * verb's semantics; the semantic is still written with this concept's ledger.
         *
         * @param referencedComponent the established identity of the component the
         *                            semantic references
         * @param pattern             the semantic's pattern
         * @param declaredIdentity    the established identity the semantic adopts
         * @param fieldValues         the version's field values, in the pattern's field order
         * @return this scope, for chaining
         * @throws IllegalArgumentException as
         *                                  {@link #semantic(EntityProxy.Pattern, PublicId, Object...)},
         *                                  or if {@code referencedComponent} is null or empty
         */
        public ActiveScope semanticOn(PublicId referencedComponent, EntityProxy.Pattern pattern,
                                      PublicId declaredIdentity, Object... fieldValues) {
            if (referencedComponent == null || referencedComponent.uuidCount() == 0) {
                throw new IllegalArgumentException(
                        "semanticOn requires the referenced component's established identity —"
                                + " use semantic(pattern, identity, fields) for a semantic on this concept");
            }
            ledger.addGenericVersion(pattern, declaredIdentity, referencedComponent, stamp, fieldValues);
            return this;
        }

        /**
         * States the concept's supertypes: composes a stated-axiom version of the form
         * {@code NecessarySet(And(ConceptAxiom(parent), ...))} — the common is-a
         * taxonomy case. Restating {@code isA} in a later scope revises the singleton
         * stated-axiom semantic. The taxonomy is read back from the stored axioms (for
         * example by the glossary extractor over a loaded store), not from the builder.
         *
         * @param parents the supertype concepts; at least one
         * @return this scope, for chaining
         * @throws IllegalArgumentException if no parent is given
         */
        public ActiveScope isA(ConceptFacade... parents) {
            if (parents.length == 0) {
                throw new IllegalArgumentException("isA requires at least one parent");
            }
            List<ConceptFacade> parentList = List.of(parents);
            ledger.addAxiomVersion(stamp, leb -> {
                LogicalAxiom.Atom[] atoms = parentList.stream()
                        .map(parent -> leb.ConceptAxiom(parent))
                        .toArray(LogicalAxiom.Atom[]::new);
                leb.NecessarySet(leb.And(atoms));
            });
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
            ledger.addComponentStamp(stamp);
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
            ledger.appendDescriptionVersion(
                    ledger.resolveLive(TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE, currentText, "synonym"),
                    stamp, currentText);
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
            ledger.appendDescriptionVersion(
                    ledger.resolveLive(TinkarTerm.DEFINITION_DESCRIPTION_TYPE, currentText, "definition"),
                    stamp, currentText);
            return this;
        }

        /**
         * Retires a generic declared-identity semantic: a new version bound to this
         * scope's inactive stamp — the prior version's fields restated when
         * {@code fieldValues} is empty (a pure status change), or the given payload
         * verbatim, since retired versions carry field values too.
         *
         * @param pattern          the semantic's pattern — restated for agreement
         * @param declaredIdentity the established identity of the semantic to retire
         * @param fieldValues      the retired version's field values, or empty to restate
         *                         the prior version's
         * @return this scope, for chaining
         * @throws IllegalArgumentException if no semantic with the declared identity was
         *                                  declared, the pattern disagrees, a field
         *                                  value's type is unsupported, or this stamp
         *                                  already carries a version of the semantic
         */
        public RetireScope retireSemantic(EntityProxy.Pattern pattern, PublicId declaredIdentity,
                                          Object... fieldValues) {
            ledger.retireGenericVersion(pattern, declaredIdentity, stamp, fieldValues);
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

    }

    // ------------------------------------------------------------------ replay

    private void writeConcept() {
        RecordListBuilder<ConceptVersionRecord> versions = RecordListBuilder.make();
        ConceptRecord bootstrap = ConceptRecord.makeNew(ledger.componentId, versions);
        for (Stamp stamp : ledger.componentStamps) {
            versions.add(ConceptVersionRecordBuilder.builder()
                    .chronology(bootstrap)
                    .stampNid(ledger.writeStamp(stamp))
                    .build());
        }
        EntityService.get().putEntity(
                ConceptRecordBuilder.builder(bootstrap).versions(versions.toImmutable()).build());
    }
}
