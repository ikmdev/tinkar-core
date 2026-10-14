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
package dev.ikm.tinkar.integration.transaction;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.entity.PatternEntityVersion;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.transaction.StampedWriter;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.*;
import network.ike.foundation.ike.bindings.IkeConcept;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static dev.ikm.tinkar.entity.transaction.StampedWriter.field;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StampedWriter} against the IKE starter set: what it writes, at which stamp, how a
 * second write versions a component, and that an unfinished writer leaves nothing current.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StampedWriterIT {

    private static final long TIME = 1_760_000_000_000L;

    @BeforeAll
    void beforeAll() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
    }

    @AfterAll
    void afterAll() {
        TestHelper.stopDatabase();
    }

    private static StampedWriter open(String name, long time) {
        return StampedWriter.open(name, State.ACTIVE, time,
                KernelConcept.USER, IkeConcept.DEVELOPMENT_MODULE, KernelConcept.DEVELOPMENT_PATH);
    }

    @Test
    void writesAConceptItsNameAPatternAndASemanticAtTheStamp() {
        ConceptFacade concept = EntityProxy.Concept.make(PublicIds.newRandom());
        PatternFacade pattern = EntityProxy.Pattern.make(PublicIds.newRandom());
        EntityProxy.Semantic semantic = EntityProxy.Semantic.make(PublicIds.newRandom());

        try (StampedWriter writer = open("writes", TIME)) {
            writer.concept(concept.publicId());
            writer.fullyQualifiedName(concept.publicId(), "Stamped writer test concept");
            writer.pattern(pattern.publicId(), concept, IkeConcept.PURPOSE,
                    field(concept, IkeConcept.PURPOSE, KernelConcept.STRING));
            writer.semantic(semantic, pattern.publicId(), concept.publicId(), "a field value");
            writer.commit();
        }

        ConceptEntity<?> conceptEntity = EntityHandle.get(concept).expectConcept();
        assertStampedOnce(conceptEntity.versions());

        PatternEntity<PatternEntityVersion> patternEntity = EntityHandle.get(pattern).expectPattern();
        PatternEntityVersion patternVersion = patternEntity.versions().getOnly();
        assertEquals(concept.nid(), patternVersion.semanticMeaningNid());
        assertEquals(1, patternVersion.fieldDefinitions().size());
        assertEquals(KernelConcept.STRING.nid(), patternVersion.fieldDefinitions().getFirst().dataTypeNid());
        assertStampedOnce(patternEntity.versions());

        SemanticEntity<SemanticEntityVersion> semanticEntity = EntityHandle.get(semantic).expectSemantic();
        assertEquals(pattern.nid(), semanticEntity.patternNid());
        assertEquals(concept.nid(), semanticEntity.referencedComponentNid());
        assertEquals(List.of("a field value"), semanticEntity.versions().getOnly().fieldValues().castToList());
        assertStampedOnce(semanticEntity.versions());

        SemanticEntity<SemanticEntityVersion> name = EntityHandle.get(
                PublicIds.of(StampedWriter.fullyQualifiedNameUuid(concept.publicId()))).expectSemantic();
        assertEquals(KernelPattern.DESCRIPTION_PATTERN.nid(), name.patternNid());
        assertEquals(concept.nid(), name.referencedComponentNid());
        assertEquals("Stamped writer test concept", name.versions().getOnly().fieldValues().get(1));
    }

    private static void assertStampedOnce(Iterable<? extends EntityVersion> versions) {
        List<EntityVersion> list = new java.util.ArrayList<>();
        versions.forEach(list::add);
        assertEquals(1, list.size(), "one version");
        assertEquals(TIME, list.getFirst().time(), "the writer's stamp time");
        assertEquals(KernelConcept.USER.nid(), list.getFirst().authorNid(), "the writer's stamp author");
    }

    @Test
    void writingAComponentAgainAddsAVersion() {
        ConceptFacade concept = EntityProxy.Concept.make(PublicIds.newRandom());
        EntityProxy.Semantic semantic = EntityProxy.Semantic.make(PublicIds.newRandom());
        try (StampedWriter writer = open("first", TIME)) {
            writer.concept(concept.publicId());
            writer.semantic(semantic, KernelPattern.COMMENT_PATTERN.publicId(), concept.publicId(), "first");
            writer.commit();
        }
        try (StampedWriter writer = open("second", TIME + 1)) {
            writer.semantic(semantic, KernelPattern.COMMENT_PATTERN.publicId(), concept.publicId(), "second");
            writer.commit();
        }

        SemanticEntity<SemanticEntityVersion> entity = EntityHandle.get(semantic).expectSemantic();
        Set<Object> values = entity.versions().stream()
                .map(version -> version.fieldValues().getFirst())
                .collect(Collectors.toSet());
        assertEquals(Set.of("first", "second"), values);
    }

    @Test
    void aGivenTimeMakesTheStampATupleSharedAcrossTransactions() {
        ConceptFacade concept = EntityProxy.Concept.make(PublicIds.newRandom());
        EntityProxy.Semantic semantic = EntityProxy.Semantic.make(PublicIds.newRandom());
        long time = TIME + 1_000;
        try (StampedWriter writer = open("first declaration", time)) {
            writer.concept(concept.publicId());
            writer.semantic(semantic, KernelPattern.COMMENT_PATTERN.publicId(), concept.publicId(), "same");
            writer.commit();
        }
        try (StampedWriter writer = open("second declaration", time)) {
            writer.semantic(semantic, KernelPattern.COMMENT_PATTERN.publicId(), concept.publicId(), "same");
            writer.commit();
        }

        SemanticEntity<SemanticEntityVersion> entity = EntityHandle.get(semantic).expectSemantic();
        SemanticEntityVersion only = entity.versions().getOnly();
        assertEquals("same", only.fieldValues().getFirst(), "an identical version written twice under one tuple is one version");
        StampEntity<?> stamp = Entity.getStamp(only.stampNid());
        assertEquals(1, stamp.versions().size(), "a stamp minted with its time has its single, committed version");
        assertEquals(time, stamp.lastVersion().time());
    }

    @Test
    void aDifferentVersionUnderOneTupleIsTheNewerOne() {
        ConceptFacade concept = EntityProxy.Concept.make(PublicIds.newRandom());
        EntityProxy.Semantic semantic = EntityProxy.Semantic.make(PublicIds.newRandom());
        long time = TIME + 2_000;
        try (StampedWriter writer = open("first", time)) {
            writer.concept(concept.publicId());
            writer.semantic(semantic, KernelPattern.COMMENT_PATTERN.publicId(), concept.publicId(), "first");
            writer.commit();
        }
        try (StampedWriter writer = open("second", time)) {
            writer.semantic(semantic, KernelPattern.COMMENT_PATTERN.publicId(), concept.publicId(), "second");
            writer.commit();
        }

        SemanticEntity<SemanticEntityVersion> entity = EntityHandle.get(semantic).expectSemantic();
        assertEquals("second", entity.versions().getOnly().fieldValues().getFirst(),
                "one tuple is one STAMP, so the version under it is the one written last");
    }

    @Test
    void cancellingAWriterWithAGivenTimeLeavesItsStampAndWritesFinal() {
        ConceptFacade concept = EntityProxy.Concept.make(PublicIds.newRandom());
        EntityProxy.Semantic semantic = EntityProxy.Semantic.make(PublicIds.newRandom());
        long time = TIME + 3_000;
        StampEntity<?> stamp;
        try (StampedWriter writer = open("final at the put", time)) {
            writer.concept(concept.publicId());
            writer.semantic(semantic, KernelPattern.COMMENT_PATTERN.publicId(), concept.publicId(), "written");
            stamp = writer.stamp();
            writer.cancel();
        }

        StampEntity<?> after = EntityHandle.get(stamp.nid()).expectStamp();
        assertEquals(1, after.versions().size(), "cancel never adds a version to a committed stamp");
        assertEquals(time, after.lastVersion().time(), "the stamp keeps its committed time");
        SemanticEntity<SemanticEntityVersion> entity = EntityHandle.get(semantic).expectSemantic();
        assertTrue(!entity.canceled(), "a write under a committed stamp is final at the put");
        assertEquals("written", entity.versions().getOnly().fieldValues().getFirst());
    }

    @Test
    void cancellingAnUncommittedWriterCancelsItsStamp() {
        ConceptFacade concept = EntityProxy.Concept.make(PublicIds.newRandom());
        EntityProxy.Semantic semantic = EntityProxy.Semantic.make(PublicIds.newRandom());
        StampEntity<?> stamp;
        try (StampedWriter writer = StampedWriter.open("uncommitted", State.ACTIVE,
                KernelConcept.USER, IkeConcept.DEVELOPMENT_MODULE, KernelConcept.DEVELOPMENT_PATH)) {
            writer.concept(concept.publicId());
            writer.semantic(semantic, KernelPattern.COMMENT_PATTERN.publicId(), concept.publicId(), "draft");
            stamp = writer.stamp();
            writer.cancel();
        }

        StampEntity<?> after = EntityHandle.get(stamp.nid()).expectStamp();
        assertEquals(2, after.versions().size(), "uncommitted, then canceled");
        assertEquals(Long.MIN_VALUE, after.lastVersion().time());
        assertTrue(EntityHandle.get(semantic).expectSemantic().canceled());
    }

    @Test
    void restampCommitsBothStampsTogether() {
        ConceptFacade concept = EntityProxy.Concept.make(PublicIds.newRandom());
        EntityProxy.Semantic byUser = EntityProxy.Semantic.make(PublicIds.newRandom());
        EntityProxy.Semantic byAnother = EntityProxy.Semantic.make(PublicIds.newRandom());
        try (StampedWriter writer = open("two authors", TIME)) {
            writer.concept(concept.publicId());
            writer.semantic(byUser, KernelPattern.COMMENT_PATTERN.publicId(), concept.publicId(), "user");
            writer.restamp(State.ACTIVE, TIME + 1, KernelConcept.AUTHOR_FOR_VERSION,
                    IkeConcept.DEVELOPMENT_MODULE, KernelConcept.DEVELOPMENT_PATH);
            writer.semantic(byAnother, KernelPattern.COMMENT_PATTERN.publicId(), concept.publicId(), "another");
            writer.commit();
        }

        SemanticEntity<SemanticEntityVersion> userSemantic = EntityHandle.get(byUser).expectSemantic();
        SemanticEntity<SemanticEntityVersion> anotherSemantic = EntityHandle.get(byAnother).expectSemantic();
        EntityVersion userVersion = userSemantic.versions().getOnly();
        EntityVersion anotherVersion = anotherSemantic.versions().getOnly();
        assertEquals(KernelConcept.USER.nid(), userVersion.authorNid());
        assertEquals(KernelConcept.AUTHOR_FOR_VERSION.nid(), anotherVersion.authorNid());
        assertEquals(TIME + 1, anotherVersion.time());
    }

    @Test
    void closingAnUnfinishedWriterCancelsIt() {
        ConceptFacade concept = EntityProxy.Concept.make(PublicIds.newRandom());
        StampEntity<?> stamp;
        try (StampedWriter writer = open("abandoned", Long.MAX_VALUE)) {
            writer.concept(concept.publicId());
            stamp = writer.stamp();
        }

        assertEquals(Long.MIN_VALUE, EntityHandle.get(stamp.nid()).expectStamp().lastVersion().time(),
                "a cancelled stamp's time");
        assertTrue(EntityHandle.get(concept).expectConcept().canceled());
    }

    @Test
    void aFinishedWriterRefusesFurtherWrites() {
        StampedWriter writer = open("finished", TIME);
        writer.commit();
        assertThrows(IllegalStateException.class, () -> writer.concept(PublicIds.newRandom()));
        assertThrows(IllegalStateException.class, writer::cancel);
    }

    /**
     * The fully qualified name a writer derives for a component is the one the ledger
     * builders seeded for it. Prose element is authored by the IKE starter set's
     * {@code ProseElementSet} ledger, which seeds its fully qualified name, and is also
     * seeded at runtime by the conversation journal: both must reach one name.
     */
    @Test
    void aFullyQualifiedNameIsTheOneTheLedgerSeeded() {
        PublicId component = IkeConcept.PROSE_ELEMENT.publicId();
        UUID derived = StampedWriter.fullyQualifiedNameUuid(component);
        assertTrue(PrimitiveData.get().hasUuid(derived),
                "the IKE starter set holds " + derived + " as a fully qualified name of " + component.idString());
        SemanticEntity<SemanticEntityVersion> name = EntityHandle.get(PublicIds.of(derived)).expectSemantic();
        assertEquals(EntityHandle.get(component).expectEntity().nid(), name.referencedComponentNid());
        SemanticEntityVersion version = name.versions().getLastOptional().orElseThrow();
        assertEquals(KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid(),
                ((EntityProxy) version.fieldValues().get(3)).nid());
    }
}
