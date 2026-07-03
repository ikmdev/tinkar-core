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
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.FieldDefinitionRecord;
import dev.ikm.tinkar.entity.FieldDefinitionRecordBuilder;
import dev.ikm.tinkar.entity.PatternRecord;
import dev.ikm.tinkar.entity.PatternRecordBuilder;
import dev.ikm.tinkar.entity.PatternVersionRecord;
import dev.ikm.tinkar.entity.PatternVersionRecordBuilder;
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.builder.ComponentLedger.VersionEntry;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ledger-form authoring of a pattern — its meaning, purpose, field definitions, and
 * descriptions — as one coherent, replayable declaration. Shares the grammar and identity
 * discipline of {@link ConceptBuilder}: identity is {@code T5(setUuid, birthFqn)},
 * {@link #at(ActiveStamp)} opens a version scope, descriptions use the add/revise/retire
 * verbs, and retirement scopes ({@link #at(InactiveStamp)}) expose only retirement verbs.
 *
 * <h2>Pattern versions restate as a whole</h2>
 * A pattern version is the tuple (meaning, purpose, field definitions), and it is
 * singleton-keyed: each scope that declares any of {@link ActiveScope#meaning},
 * {@link ActiveScope#purpose}, or {@link ActiveScope#field} produces one new pattern
 * version, and must restate meaning and purpose in full — matching how pattern versions
 * are stored (the field-definition list versions as a whole, not per field). Field order
 * is declaration order within the scope; a pattern with no {@code field} declarations is
 * a membership pattern, whose meaning and purpose alone specify the semantic.
 *
 * <pre>{@code
 * RICH_SURFACE.pattern("Journal manifest pattern (RichSurfaceTerms)")
 *     .at(W1)
 *         .meaning(JOURNAL_MANIFEST).purpose(ELEMENT_ORDER)
 *         .field(JOURNAL_ELEMENTS, ELEMENT_ORDER, TinkarTerm.COMPONENT_ID_LIST_FIELD)
 *         .synonym("Journal manifest")
 *     .build();
 * }</pre>
 */
public final class PatternBuilder {

    private final ComponentLedger ledger;
    private final List<VersionEntry<PatternContent>> patternVersions = new ArrayList<>();

    private ConceptFacade pendingMeaning;
    private ConceptFacade pendingPurpose;
    private final List<FieldDeclaration> pendingFields = new ArrayList<>();
    private ActiveStamp pendingStamp;

    PatternBuilder(KnowledgeSet knowledgeSet, String birthFqn) {
        this.ledger = new ComponentLedger(knowledgeSet.uuidFor(birthFqn), birthFqn);
    }

    /**
     * The identity this declaration derives: {@code T5(setUuid, birthFqn)}.
     *
     * @return the pattern's public id
     */
    public PublicId publicId() {
        return PublicIds.of(ledger.componentUuid);
    }

    /**
     * Opens a content scope at the given active stamp. The first active scope is the
     * birth scope, which must declare the pattern's meaning and purpose; it also creates
     * the fully qualified name description from the birth FQN.
     *
     * @param stamp the declared active stamp this scope's changes are bound to
     * @return the content scope
     * @throws IllegalArgumentException if the stamp's time precedes a previously scoped
     *                                  stamp's time — the ledger must be chronological
     * @throws IllegalStateException    if a prior scope declared an incomplete pattern
     *                                  version, or if {@code build()} has already run
     */
    public ActiveScope at(ActiveStamp stamp) {
        ledger.checkChronology(stamp);
        flushPendingVersion();
        if (!ledger.born()) {
            ledger.birth(stamp);
        }
        pendingStamp = stamp;
        return new ActiveScope(stamp);
    }

    /**
     * Opens a retirement scope at the given inactive stamp. Only retirement verbs are
     * available.
     *
     * @param stamp the declared inactive stamp this scope's retirements are bound to
     * @return the retirement scope
     * @throws IllegalArgumentException if the stamp's time precedes a previously scoped
     *                                  stamp's time — the ledger must be chronological
     * @throws IllegalStateException    if the pattern has no birth scope yet, or if
     *                                  {@code build()} has already run
     */
    public RetireScope at(InactiveStamp stamp) {
        ledger.requireBornForRetirement();
        ledger.checkChronology(stamp);
        flushPendingVersion();
        return new RetireScope(stamp);
    }

    /**
     * Replays the accumulated declarations into the open datastore: the stamps used, the
     * pattern chronology with its versions and field definitions, and every description
     * with its dialect-acceptability semantic. Invoked by {@link KnowledgeSet#write()};
     * repeatable — identities and stamps are derived, so re-writing merges to the same
     * state.
     */
    void writeInto() {
        flushPendingVersion();
        ledger.requireBornForWrite();
        if (patternVersions.isEmpty()) {
            throw new IllegalStateException(
                    "A pattern declaration requires meaning and purpose in its birth scope: " + ledger.birthFqn);
        }
        int patternNid = ledger.componentNid();
        writePattern(patternNid);
        ledger.writeDescriptions(patternNid);
    }

    private void flushPendingVersion() {
        if (pendingMeaning == null && pendingPurpose == null && pendingFields.isEmpty()) {
            return;
        }
        if (pendingMeaning == null || pendingPurpose == null) {
            throw new IllegalStateException(
                    "A pattern version restates as a whole: meaning and purpose are both required in a scope"
                            + " that declares any of meaning, purpose, or field — " + ledger.birthFqn);
        }
        Set<Integer> meaningNids = new HashSet<>();
        for (FieldDeclaration field : pendingFields) {
            if (!meaningNids.add(field.meaning().nid())) {
                throw new IllegalStateException(
                        "Field meanings must be unique within a pattern — meaning is the field's"
                                + " knowledge-level address (getFieldWithMeaning): duplicate \""
                                + field.meaning().description() + "\" on " + ledger.birthFqn);
            }
        }
        patternVersions.add(new VersionEntry<>(pendingStamp,
                new PatternContent(pendingMeaning, pendingPurpose, List.copyOf(pendingFields))));
        pendingMeaning = null;
        pendingPurpose = null;
        pendingFields.clear();
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
         * Declares the pattern version's meaning — what a semantic of this pattern means.
         *
         * @param meaning the meaning concept
         * @return this scope, for chaining
         */
        public ActiveScope meaning(ConceptFacade meaning) {
            pendingMeaning = meaning;
            return this;
        }

        /**
         * Declares the pattern version's purpose — what a semantic of this pattern is for.
         *
         * @param purpose the purpose concept
         * @return this scope, for chaining
         */
        public ActiveScope purpose(ConceptFacade purpose) {
            pendingPurpose = purpose;
            return this;
        }

        /**
         * Declares the next field definition of this pattern version. Field index is
         * declaration order within the scope. A scope that declares fields must also
         * restate meaning and purpose.
         *
         * @param fieldMeaning  what this field means
         * @param fieldPurpose  what this field is for
         * @param fieldDataType the field's datatype concept, for example
         *                      {@code TinkarTerm.COMPONENT_ID_LIST_FIELD} or {@code TinkarTerm.STRING}
         * @return this scope, for chaining
         */
        public ActiveScope field(ConceptFacade fieldMeaning, ConceptFacade fieldPurpose, ConceptFacade fieldDataType) {
            pendingFields.add(new FieldDeclaration(fieldMeaning, fieldPurpose, fieldDataType));
            return this;
        }

        /**
         * Adds a new synonym (regular-name description) in English, with the default
         * US-dialect acceptability of {@code PREFERRED}.
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
         * with the new text, referenced by its current text.
         *
         * @param currentText the text the synonym carries before this revision
         * @param newText     the text after this revision
         * @return this scope, for chaining
         * @throws IllegalArgumentException if no live synonym carries {@code currentText},
         *                                  or if more than one does (ambiguous reference)
         */
        public ActiveScope reviseSynonym(String currentText, String newText) {
            ledger.resolveLive(TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE, currentText, "synonym")
                    .versions.add(new VersionEntry<>(stamp, newText));
            return this;
        }

        /**
         * Revises the fully qualified name: a new version of the FQN description. The
         * pattern's identity is unaffected — identity was seeded by the FQN at birth.
         *
         * @param newText the fully qualified name after this revision
         * @return this scope, for chaining
         */
        public ActiveScope reviseFullyQualifiedName(String newText) {
            ledger.fqnLedger().versions.add(new VersionEntry<>(stamp, newText));
            return this;
        }

        /**
         * Opens the next content scope. Delegates to {@link PatternBuilder#at(ActiveStamp)}.
         *
         * @param nextStamp the declared active stamp for the next scope
         * @return the next content scope
         */
        public ActiveScope at(ActiveStamp nextStamp) {
            return PatternBuilder.this.at(nextStamp);
        }

        /**
         * Opens a retirement scope. Delegates to {@link PatternBuilder#at(InactiveStamp)}.
         *
         * @param nextStamp the declared inactive stamp for the retirement scope
         * @return the retirement scope
         */
        public RetireScope at(InactiveStamp nextStamp) {
            return PatternBuilder.this.at(nextStamp);
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
         * Retires the pattern: a new pattern version carrying the prior version's
         * meaning, purpose, and field definitions, bound to this scope's inactive stamp.
         *
         * @return this scope, for chaining
         * @throws IllegalStateException if no pattern version has been declared yet
         */
        public RetireScope retire() {
            if (patternVersions.isEmpty()) {
                throw new IllegalStateException(
                        "Cannot retire a pattern before its birth scope declared meaning and purpose: "
                                + ledger.birthFqn);
            }
            patternVersions.add(new VersionEntry<>(stamp, patternVersions.getLast().value()));
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
            ledger.resolveLive(TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE, currentText, "synonym")
                    .versions.add(new VersionEntry<>(stamp, currentText));
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
            ledger.resolveLive(TinkarTerm.DEFINITION_DESCRIPTION_TYPE, currentText, "definition")
                    .versions.add(new VersionEntry<>(stamp, currentText));
            return this;
        }

        /**
         * Opens the next content scope. Delegates to {@link PatternBuilder#at(ActiveStamp)}.
         *
         * @param nextStamp the declared active stamp for the next scope
         * @return the next content scope
         */
        public ActiveScope at(ActiveStamp nextStamp) {
            return PatternBuilder.this.at(nextStamp);
        }

        /**
         * Opens another retirement scope. Delegates to {@link PatternBuilder#at(InactiveStamp)}.
         *
         * @param nextStamp the declared inactive stamp for the next retirement scope
         * @return the retirement scope
         */
        public RetireScope at(InactiveStamp nextStamp) {
            return PatternBuilder.this.at(nextStamp);
        }

    }

    // ------------------------------------------------------------------ replay

    private void writePattern(int patternNid) {
        RecordListBuilder<PatternVersionRecord> versions = RecordListBuilder.make();
        PatternRecord bootstrap = PatternRecordBuilder.builder()
                .nid(patternNid)
                .mostSignificantBits(ledger.componentUuid.getMostSignificantBits())
                .leastSignificantBits(ledger.componentUuid.getLeastSignificantBits())
                .versions(versions)
                .build();
        for (VersionEntry<PatternContent> version : patternVersions) {
            int stampNid = ledger.writeStamp(version.stamp());
            MutableList<FieldDefinitionRecord> fieldDefinitions = Lists.mutable.empty();
            List<FieldDeclaration> fields = version.value().fields();
            for (int index = 0; index < fields.size(); index++) {
                FieldDeclaration field = fields.get(index);
                fieldDefinitions.add(FieldDefinitionRecordBuilder.builder()
                        .patternNid(patternNid)
                        .meaningNid(field.meaning().nid())
                        .purposeNid(field.purpose().nid())
                        .dataTypeNid(field.dataType().nid())
                        .indexInPattern(index)
                        .patternVersionStampNid(stampNid)
                        .build());
            }
            versions.add(PatternVersionRecordBuilder.builder()
                    .chronology(bootstrap)
                    .stampNid(stampNid)
                    .semanticMeaningNid(version.value().meaning().nid())
                    .semanticPurposeNid(version.value().purpose().nid())
                    .fieldDefinitions(fieldDefinitions.toImmutable())
                    .build());
        }
        EntityService.get().putEntity(
                PatternRecordBuilder.builder(bootstrap).versions(versions.toImmutable()).build());
    }

    // ------------------------------------------------------------------ ledger records

    private record PatternContent(ConceptFacade meaning, ConceptFacade purpose,
                                  List<FieldDeclaration> fields) {
    }

    private record FieldDeclaration(ConceptFacade meaning, ConceptFacade purpose,
                                    ConceptFacade dataType) {
    }
}
