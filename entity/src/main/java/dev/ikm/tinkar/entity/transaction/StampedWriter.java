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
package dev.ikm.tinkar.entity.transaction;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.ConceptRecord;
import dev.ikm.tinkar.entity.ConceptRecordBuilder;
import dev.ikm.tinkar.entity.ConceptVersionRecord;
import dev.ikm.tinkar.entity.ConceptVersionRecordBuilder;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.FieldDefinitionRecord;
import dev.ikm.tinkar.entity.FieldDefinitionRecordBuilder;
import dev.ikm.tinkar.entity.PatternRecord;
import dev.ikm.tinkar.entity.PatternRecordBuilder;
import dev.ikm.tinkar.entity.PatternVersionRecord;
import dev.ikm.tinkar.entity.PatternVersionRecordBuilder;
import dev.ikm.tinkar.entity.PublicIdentifierRecord;
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.SemanticRecordBuilder;
import dev.ikm.tinkar.entity.SemanticVersionRecord;
import dev.ikm.tinkar.entity.SemanticVersionRecordBuilder;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.KernelTerm;
import dev.ikm.tinkar.terms.State;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;

import java.util.UUID;

/**
 * Writes concepts, patterns, and semantics into the open store under one stamp, in one
 * {@link Transaction}: the runtime write path for code that adds to a live knowledge base,
 * such as a plugin seeding its vocabulary or recording what a user did.
 * <p>
 * Each write is one new version at the writer's current stamp. A write to a component the
 * store already holds adds that version to it, and the store merges it into the existing
 * chronology. Every written component joins the transaction, so {@link #commit()} broadcasts
 * it as changed. {@link #close()} cancels a writer that was neither committed nor cancelled,
 * so a failure inside a try-with-resources block leaves nothing uncommitted behind:
 * <pre>{@code
 * try (StampedWriter writer = StampedWriter.open("narrator seed",
 *         State.ACTIVE, author, module, path)) {
 *     writer.concept(concept);
 *     writer.fullyQualifiedName(concept, "Narration");
 *     writer.commit();
 * }
 * }</pre>
 * <p>
 * Nids are minted in the scope of the pattern the entity instantiates (the concept pattern,
 * the pattern pattern, or the semantic's own pattern), which a store that encodes the pattern
 * into the nid requires. The caller names every identity; the only identities the writer
 * derives are a fully qualified name's and its dialect semantic's, the same way the ledger
 * builders derive them, so a component seeded here and the same component built from a
 * ledger carry one fully qualified name, not two.
 * <p>
 * A batch build from declared content belongs to the ledger builders in
 * {@code dev.ikm.tinkar.entity.builder}, which write committed stamps directly and need no
 * transaction.
 */
public final class StampedWriter implements AutoCloseable {

    /**
     * The language seed of a derived description identity. It must equal the ledger's
     * ({@code ComponentLedger.DESCRIPTION_LANGUAGE_SEED}): both derive a fully qualified name's
     * identity from it, and a component written by both must reach the same description.
     */
    private static final UUID DESCRIPTION_LANGUAGE_SEED = UUID.fromString("02018e5a-46ba-5297-92f1-6931b9f98a12");

    /**
     * One field definition of a pattern version.
     *
     * @param meaning  what the field means
     * @param purpose  what the field is for
     * @param dataType the field's data type, such as {@link KernelTerm#STRING}
     */
    public record Field(ConceptFacade meaning, ConceptFacade purpose, ConceptFacade dataType) {
    }

    /**
     * A field definition, for {@link #pattern(PublicId, ConceptFacade, ConceptFacade, Field...)}.
     *
     * @param meaning  what the field means
     * @param purpose  what the field is for
     * @param dataType the field's data type
     * @return the field definition
     */
    public static Field field(ConceptFacade meaning, ConceptFacade purpose, ConceptFacade dataType) {
        return new Field(meaning, purpose, dataType);
    }

    private final Transaction transaction;
    private StampEntity<?> stamp;
    private boolean finished;

    private StampedWriter(Transaction transaction) {
        this.transaction = transaction;
    }

    /**
     * Opens a writer whose stamp carries the given time, which commit preserves: for content
     * whose time is known, or for writes that must be strictly ordered in time.
     *
     * @param name   the transaction's name
     * @param state  the stamp's status
     * @param time   the stamp's time, in epoch milliseconds
     * @param author the stamp's author
     * @param module the stamp's module
     * @param path   the stamp's path
     * @return the open writer
     */
    public static StampedWriter open(String name, State state, long time,
                                     ConceptFacade author, ConceptFacade module, ConceptFacade path) {
        StampedWriter writer = new StampedWriter(Transaction.make(name));
        writer.restamp(state, time, author, module, path);
        return writer;
    }

    /**
     * Opens a writer whose stamp takes its time at commit.
     *
     * @param name   the transaction's name
     * @param state  the stamp's status
     * @param author the stamp's author
     * @param module the stamp's module
     * @param path   the stamp's path
     * @return the open writer
     */
    public static StampedWriter open(String name, State state,
                                     ConceptFacade author, ConceptFacade module, ConceptFacade path) {
        return open(name, state, Long.MAX_VALUE, author, module, path);
    }

    /**
     * Moves later writes to another stamp in the same transaction, so writes by two authors,
     * or at two times, commit or cancel together.
     *
     * @param state  the stamp's status
     * @param time   the stamp's time, or {@link Long#MAX_VALUE} for the commit time
     * @param author the stamp's author
     * @param module the stamp's module
     * @param path   the stamp's path
     */
    public void restamp(State state, long time, ConceptFacade author, ConceptFacade module, ConceptFacade path) {
        requireOpen();
        this.stamp = transaction.getStamp(state, time, author, module, path);
    }

    /**
     * The stamp the next write carries.
     *
     * @return the current stamp
     */
    public StampEntity<?> stamp() {
        return stamp;
    }

    /**
     * Writes a version of a concept.
     *
     * @param conceptId the concept's identity
     * @return the concept's nid
     */
    public long concept(PublicId conceptId) {
        requireOpen();
        long nid = EntityService.get().nidForConcept(conceptId);
        PublicIdentifierRecord identifier = PublicIdentifierRecord.make(conceptId);
        RecordListBuilder<ConceptVersionRecord> versions = RecordListBuilder.make();
        ConceptRecord concept = ConceptRecordBuilder.builder()
                .nid(nid)
                .mostSignificantBits(identifier.mostSignificantBits())
                .leastSignificantBits(identifier.leastSignificantBits())
                .additionalUuidLongs(identifier.additionalUuidLongs())
                .versions(versions)
                .build();
        versions.add(ConceptVersionRecordBuilder.builder()
                .chronology(concept)
                .stampNid(stamp.nid())
                .build());
        transaction.addComponent(nid);
        EntityService.get().putEntity(
                ConceptRecordBuilder.builder(concept).versions(versions.toImmutable()).build());
        return nid;
    }

    /**
     * Writes a version of a pattern.
     *
     * @param patternId the pattern's identity
     * @param meaning   what a semantic of the pattern means
     * @param purpose   what a semantic of the pattern is for
     * @param fields    the field definitions, in field order; none for a membership pattern
     * @return the pattern's nid
     */
    public long pattern(PublicId patternId, ConceptFacade meaning, ConceptFacade purpose, Field... fields) {
        requireOpen();
        long nid = EntityService.get().nidForPattern(patternId);
        PublicIdentifierRecord identifier = PublicIdentifierRecord.make(patternId);
        RecordListBuilder<PatternVersionRecord> versions = RecordListBuilder.make();
        PatternRecord pattern = PatternRecordBuilder.builder()
                .nid(nid)
                .mostSignificantBits(identifier.mostSignificantBits())
                .leastSignificantBits(identifier.leastSignificantBits())
                .additionalUuidLongs(identifier.additionalUuidLongs())
                .versions(versions)
                .build();
        MutableList<FieldDefinitionRecord> fieldDefinitions = Lists.mutable.empty();
        for (int index = 0; index < fields.length; index++) {
            Field field = fields[index];
            fieldDefinitions.add(FieldDefinitionRecordBuilder.builder()
                    .patternNid(nid)
                    .meaningNid(field.meaning().nid())
                    .purposeNid(field.purpose().nid())
                    .dataTypeNid(field.dataType().nid())
                    .indexInPattern(index)
                    .patternVersionStampNid(stamp.nid())
                    .build());
        }
        versions.add(PatternVersionRecordBuilder.builder()
                .chronology(pattern)
                .stampNid(stamp.nid())
                .semanticMeaningNid(meaning.nid())
                .semanticPurposeNid(purpose.nid())
                .fieldDefinitions(fieldDefinitions.toImmutable())
                .build());
        transaction.addComponent(nid);
        EntityService.get().putEntity(
                PatternRecordBuilder.builder(pattern).versions(versions.toImmutable()).build());
        return nid;
    }

    /**
     * Writes a version of a semantic.
     *
     * @param semanticId          the semantic's identity
     * @param patternId           the pattern the semantic instantiates
     * @param referencedComponent the component the semantic is about, which must already have a nid
     * @param fieldValues         the field values, in the pattern's field order
     * @return the semantic's nid
     */
    public long semantic(PublicId semanticId, PublicId patternId, PublicId referencedComponent,
                        Object... fieldValues) {
        requireOpen();
        long nid = EntityService.get().nidForSemantic(patternId, semanticId);
        PublicIdentifierRecord identifier = PublicIdentifierRecord.make(semanticId);
        RecordListBuilder<SemanticVersionRecord> versions = RecordListBuilder.make();
        SemanticRecord semantic = SemanticRecordBuilder.builder()
                .nid(nid)
                .mostSignificantBits(identifier.mostSignificantBits())
                .leastSignificantBits(identifier.leastSignificantBits())
                .additionalUuidLongs(identifier.additionalUuidLongs())
                .patternNid(EntityService.get().nidForPattern(patternId))
                .referencedComponentNid(EntityService.get().nidForPublicId(referencedComponent))
                .versions(versions)
                .build();
        ImmutableList<Object> values = Lists.immutable.of(fieldValues);
        versions.add(SemanticVersionRecordBuilder.builder()
                .chronology(semantic)
                .stampNid(stamp.nid())
                .fieldValues(values)
                .build());
        transaction.addComponent(nid);
        EntityService.get().putEntity(
                SemanticRecordBuilder.builder(semantic).versions(versions.toImmutable()).build());
        return nid;
    }

    /**
     * Writes a version of a component's English fully qualified name, not case sensitive,
     * and of its US-dialect acceptability, preferred. Both identities are derived from the
     * component, so writing a name again revises it rather than adding a second one.
     *
     * @param component the named component, which must already have a nid
     * @param text      the fully qualified name
     * @return the description semantic's nid
     */
    public long fullyQualifiedName(PublicId component, String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("A fully qualified name needs text: " + component.idString());
        }
        PublicId description = PublicIds.of(fullyQualifiedNameUuid(component));
        long nid = semantic(description, KernelTerm.DESCRIPTION_PATTERN, component,
                KernelTerm.ENGLISH_LANGUAGE, text,
                KernelTerm.DESCRIPTION_NOT_CASE_SENSITIVE, KernelTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE);
        semantic(PublicIds.of(UuidT5Generator.get(description.leastUuid(), "us-dialect")),
                KernelTerm.US_DIALECT_PATTERN, description, KernelTerm.PREFERRED);
        return nid;
    }

    /**
     * The identity of a component's derived English fully qualified name: the identity
     * {@link #fullyQualifiedName(PublicId, String)} writes, and the ledger builders seed.
     *
     * @param component the named component
     * @return the fully qualified name's UUID
     */
    public static UUID fullyQualifiedNameUuid(PublicId component) {
        return UuidT5Generator.get(component.leastUuid(), "fully-qualified-name|" + DESCRIPTION_LANGUAGE_SEED);
    }

    /**
     * Commits every stamp the writer used, and with them what it wrote.
     *
     * @throws IllegalStateException if the writer was already committed or cancelled
     */
    public void commit() {
        requireOpen();
        finished = true;
        transaction.commit();
    }

    /**
     * Cancels every stamp the writer used, so nothing it wrote is current on any coordinate.
     *
     * @throws IllegalStateException if the writer was already committed or cancelled
     */
    public void cancel() {
        requireOpen();
        finished = true;
        transaction.cancel();
    }

    /** Cancels the writer unless it was committed or cancelled. */
    @Override
    public void close() {
        if (!finished) {
            cancel();
        }
    }

    private void requireOpen() {
        if (finished) {
            throw new IllegalStateException("This writer was already committed or cancelled");
        }
    }
}
