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
package dev.ikm.tinkar.integration.diagnostic;

import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.DiagnosticText;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.entity.ConceptRecord;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.FieldHandle;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampEntityVersion;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.DiTreeText;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiomSemantic;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.reasoner.elksnomed.ElkSnomedUtil;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.State;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.factory.Lists;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The messages of exceptions, provoked through the code that composes them
 * ({@code IKE-Network/ike-issues#1189}). Each identifies a component as {@link DiagnosticText}
 * does: by its description and UUID, and by its nid only when the store has no public id for
 * it. Each case here failed before the change, with a nid in the message or with no
 * identification at all.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExceptionMessageIT {

    /** A nid the store never assigned. */
    private static final int UNASSIGNED_NID = Integer.MAX_VALUE - 1;

    /** The form {@code PrimitiveData.text} writes for a component with no description. */
    private static final Pattern ANGLE_BRACKET_NID = Pattern.compile("<-?\\d+>");

    /** A nid of the test store in decimal: the store numbers components up from the bottom of the int range. */
    private static final Pattern STORE_NID = Pattern.compile("-2147[34]\\d{5}(?!\\d)");

    private ViewCalculator view;

    /** A concept with no description and no definition. */
    private int undescribedNid;

    /** A concept with no description whose stated definition is malformed. */
    private int malformedNid;

    /** The version of the semantic that holds the malformed definition. */
    private SemanticEntityVersion malformedDefinition;

    @BeforeAll
    void startStoreAndWriteTheComponentsUnderTest() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
        view = Calculators.View.Default();

        Transaction transaction = Transaction.make("Components for the exception message tests");
        StampEntity<?> stamp = transaction.getStamp(State.ACTIVE, System.currentTimeMillis(),
                TinkarTerm.USER.nid(), TinkarTerm.SOLOR_OVERLAY_MODULE.nid(), TinkarTerm.DEVELOPMENT_PATH.nid());
        StampEntityVersion version = stamp.versions().get(0);

        undescribedNid = put(transaction, ConceptRecord.build(fixtureUuid("undescribed concept"), version));
        malformedNid = put(transaction, ConceptRecord.build(fixtureUuid("concept with a malformed definition"), version));
        SemanticRecord definition = SemanticRecord.build(fixtureUuid("malformed definition"),
                TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid(), malformedNid, version,
                Lists.immutable.of(aNecessarySetWithTwoChildren()));
        put(transaction, definition);
        transaction.commit();
        malformedDefinition = (SemanticEntityVersion) definition.versions().get(0);
    }

    @AfterAll
    void stopStore() {
        TestHelper.stopDatabase();
    }

    // ── An entity handle ─────────────────────────────────────────────────────

    @Test
    void aHandleAskedForTheWrongKindNamesTheComponentByDescriptionAndUuid() {
        int conceptNid = TinkarTerm.ENGLISH_LANGUAGE.nid();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> EntityHandle.get(conceptNid).expectSemantic());
        assertEquals("Expected SemanticEntity but was ConceptRecord: " + DiagnosticText.component(conceptNid),
                failure.getMessage());
        assertNoNid(failure.getMessage());

        IllegalStateException withPrefix = assertThrows(IllegalStateException.class,
                () -> EntityHandle.get(conceptNid).expectPattern("The pattern of a semantic. "));
        assertEquals("The pattern of a semantic. Expected PatternEntity but was ConceptRecord: "
                + DiagnosticText.component(conceptNid), withPrefix.getMessage());
        assertNoNid(withPrefix.getMessage());

        IllegalStateException throughTheStaticForm = assertThrows(IllegalStateException.class,
                () -> EntityHandle.getStampOrThrow(conceptNid));
        assertEquals("Expected StampEntity but was ConceptRecord: " + DiagnosticText.component(conceptNid),
                throughTheStaticForm.getMessage());
    }

    @Test
    void aHandleForAnAbsentEntitySaysWhichOneWasAskedFor() {
        IllegalStateException byNid = assertThrows(IllegalStateException.class,
                () -> EntityHandle.get(UNASSIGNED_NID).expectEntity());
        assertEquals("Expected entity to be present but entity was absent: nid " + UNASSIGNED_NID
                + " in this store, which has no public id for it", byNid.getMessage());

        IllegalStateException asAConcept = assertThrows(IllegalStateException.class,
                () -> EntityHandle.getConceptOrThrow(UNASSIGNED_NID));
        assertEquals("Expected ConceptEntity but entity was absent: nid " + UNASSIGNED_NID
                + " in this store, which has no public id for it", asAConcept.getMessage());

        String unknown = UUID.randomUUID().toString();
        IllegalStateException byPublicId = assertThrows(IllegalStateException.class,
                () -> EntityHandle.get(PublicIds.of(unknown)).expectSemantic());
        assertEquals("Expected SemanticEntity but entity was absent: UUID " + unknown, byPublicId.getMessage());
        assertNoNid(byPublicId.getMessage());
    }

    @Test
    void aHandleThatWasAskedForNothingIdentifiesNothing() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> EntityHandle.absent().expectEntity());
        assertEquals("Expected entity to be present but entity was absent", failure.getMessage());
    }

    // ── A field of a semantic ────────────────────────────────────────────────

    @Test
    void aFieldThatAPatternDoesNotHaveIsNamedByDescriptionAndUuid() {
        SemanticEntityVersion description = aDescriptionOf(TinkarTerm.ENGLISH_LANGUAGE);
        StampCalculator stamps = view.stampCalculator();
        String pattern = DiagnosticText.component(TinkarTerm.DESCRIPTION_PATTERN.nid());
        String roleType = DiagnosticText.component(TinkarTerm.ROLE_TYPE.nid());

        IllegalArgumentException meaningByNid = assertThrows(IllegalArgumentException.class,
                () -> FieldHandle.ofMeaning(description, TinkarTerm.ROLE_TYPE.nid(), stamps));
        assertEquals("No field with meaning " + roleType + " found in pattern " + pattern, meaningByNid.getMessage());
        assertNoNid(meaningByNid.getMessage());

        IllegalArgumentException meaningByFacade = assertThrows(IllegalArgumentException.class,
                () -> FieldHandle.of(description, TinkarTerm.ROLE_TYPE, stamps));
        assertEquals("No field with meaning " + roleType + " found in pattern " + pattern,
                meaningByFacade.getMessage());

        IllegalArgumentException purposeByNid = assertThrows(IllegalArgumentException.class,
                () -> FieldHandle.ofPurpose(description, TinkarTerm.ROLE_TYPE.nid(), stamps));
        assertEquals("No field with purpose " + roleType + " found in pattern " + pattern, purposeByNid.getMessage());

        IllegalArgumentException purposeByFacade = assertThrows(IllegalArgumentException.class,
                () -> FieldHandle.ofPurpose(description, TinkarTerm.ROLE_TYPE, stamps));
        assertEquals("No field with purpose " + roleType + " found in pattern " + pattern,
                purposeByFacade.getMessage());
    }

    @Test
    void aFieldMeaningWithNoDescriptionIsNamedByItsUuid() {
        SemanticEntityVersion description = aDescriptionOf(TinkarTerm.ENGLISH_LANGUAGE);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> FieldHandle.ofMeaning(description, undescribedNid, view.stampCalculator()));

        assertEquals("No field with meaning UUID " + fixtureUuid("undescribed concept") + " found in pattern "
                + DiagnosticText.component(TinkarTerm.DESCRIPTION_PATTERN.nid()), failure.getMessage());
        assertNoNid(failure.getMessage());
    }

    // ── A logical definition ─────────────────────────────────────────────────

    @Test
    void aConceptThatIsNotAnAxiomMeaningIsNamed() {
        int nid = TinkarTerm.ENGLISH_LANGUAGE.nid();

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> LogicalAxiomSemantic.get(nid));

        assertEquals("No meaning for: " + DiagnosticText.component(nid), failure.getMessage());
        assertNoNid(failure.getMessage());
    }

    @Test
    void aConceptWithNoStatedDefinitionIsNamed() {
        IllegalStateException undescribed = assertThrows(IllegalStateException.class,
                () -> ElkSnomedUtil.getStatedSemantic(view, undescribedNid));
        assertEquals("No stated form for concept: UUID " + fixtureUuid("undescribed concept"),
                undescribed.getMessage());
        assertNoNid(undescribed.getMessage());

        int patternNid = TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid();
        IllegalStateException noSemantic = assertThrows(IllegalStateException.class,
                () -> ElkSnomedUtil.getLatestSemantic(view, patternNid, undescribedNid));
        assertEquals("No semantic of pattern " + DiagnosticText.component(patternNid) + " for component: UUID "
                + fixtureUuid("undescribed concept"), noSemantic.getMessage());
        assertNoNid(noSemantic.getMessage());
    }

    @Test
    void theSnomedBuilderNamesTheConceptAndWritesTheDefinitionWithNames() {
        DiTreeEntity definition = (DiTreeEntity) malformedDefinition.fieldValues().get(0);
        EntityVertex necessarySet = definition.successors(definition.root()).get(0);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ElkSnomedUtil.buildConcept(malformedDefinition));

        assertEquals(DiTreeText.diagnostic(necessarySet) + " can only have one child. Concept: UUID "
                + fixtureUuid("concept with a malformed definition") + " Definition:\n"
                + DiTreeText.diagnostic(definition), failure.getMessage());
        assertNoNid(failure.getMessage());
    }

    // ── What the tests write and read ────────────────────────────────────────

    /** A definition whose necessary set has two children where one is allowed. */
    private static DiTreeEntity aNecessarySetWithTwoChildren() {
        EntityVertex root = EntityVertex.make(TinkarTerm.DEFINITION_ROOT);
        EntityVertex necessarySet = EntityVertex.make(TinkarTerm.NECESSARY_SET);
        DiTreeEntity.Builder builder = DiTreeEntity.builder();
        builder.setRoot(root);
        builder.addEdge(necessarySet, root);
        builder.addEdge(EntityVertex.make(TinkarTerm.AND), necessarySet);
        builder.addEdge(EntityVertex.make(TinkarTerm.AND), necessarySet);
        return builder.build();
    }

    /** The latest version of a description of a concept. */
    private SemanticEntityVersion aDescriptionOf(EntityFacade concept) {
        SemanticEntity<SemanticEntityVersion> semantic = EntityService.get().semanticsForComponentOfPattern(
                concept.nid(), TinkarTerm.DESCRIPTION_PATTERN.nid()).findFirst().orElseThrow();
        Latest<SemanticEntityVersion> latest = view.stampCalculator().latest(semantic);
        return latest.get();
    }

    /** Writes an entity into the store and the transaction. */
    private static int put(Transaction transaction, Entity<?> entity) {
        EntityService.get().putEntity(entity);
        transaction.addComponent(entity);
        return entity.nid();
    }

    /** A UUID for a component the tests write: the same in every run. */
    private static UUID fixtureUuid(String what) {
        UUID uuid = UUID.nameUUIDFromBytes(
                ("tinkar-core exception message test: " + what).getBytes(StandardCharsets.UTF_8));
        assertTrue(!STORE_NID.matcher(uuid.toString()).find(), "a UUID written by the test looks like a nid: " + uuid);
        return uuid;
    }

    /** Fails when the text holds a nid in either of the forms one is written in. */
    private static void assertNoNid(String text) {
        for (Pattern form : new Pattern[]{ANGLE_BRACKET_NID, STORE_NID}) {
            Matcher matcher = form.matcher(text);
            if (matcher.find()) {
                fail("the message holds a nid: \"" + matcher.group() + "\" in:\n" + text);
            }
        }
    }
}
