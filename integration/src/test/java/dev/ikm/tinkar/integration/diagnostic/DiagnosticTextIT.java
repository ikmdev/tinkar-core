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

import network.ike.foundation.ike.bindings.IkeTerms;
import dev.ikm.tinkar.terms.KernelTerm;
import dev.ikm.tinkar.common.id.LongIds;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.DiagnosticText;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.entity.ConceptRecord;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.DiTreeText;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.State;
import org.eclipse.collections.api.factory.primitive.LongObjectMaps;
import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What {@link DiagnosticText} writes against a store that keeps an index from nid to UUID
 * ({@code IKE-Network/ike-issues#1189}): the description and the UUID, the UUID alone, and the
 * nid only when the store has no public id for it. {@code DiagnosticTextSpinedArrayIT} has the
 * case of a store with no such index.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DiagnosticTextIT {

    /** A concept the test writes with no description. */
    private static final UUID UNDESCRIBED_UUID = UUID.nameUUIDFromBytes(
            "tinkar-core diagnostic text test: undescribed concept".getBytes(StandardCharsets.UTF_8));

    /** The UUID of that concept, as a message writes it. */
    private static final String UNDESCRIBED = UNDESCRIBED_UUID.toString();

    /** A nid the store never assigned. */
    private static final long UNASSIGNED_NID = Integer.MAX_VALUE - 1;

    /** The form {@code PrimitiveData.text} writes for a component with no description. */
    private static final Pattern ANGLE_BRACKET_NID = Pattern.compile("<-?\\d+>");

    /** A nid of the test store in decimal: the store numbers components up from the bottom of the int range. */
    private static final Pattern STORE_NID = Pattern.compile("-2147[34]\\d{5}(?!\\d)");

    private long undescribedNid;

    @BeforeAll
    void startStore() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);

        Transaction transaction = Transaction.make("A concept with no description");
        StampEntity<?> stamp = transaction.getStamp(State.ACTIVE, System.currentTimeMillis(),
                KernelTerm.USER.nid(), KernelTerm.SOLOR_OVERLAY_MODULE.nid(), KernelTerm.DEVELOPMENT_PATH.nid());
        ConceptRecord undescribed = ConceptRecord.build(UNDESCRIBED_UUID, stamp.versions().get(0));
        EntityService.get().putEntity(undescribed);
        transaction.addComponent(undescribed);
        transaction.commit();
        undescribedNid = undescribed.nid();
    }

    @AfterAll
    void stopStore() {
        TestHelper.stopDatabase();
    }

    @Test
    void aDescribedComponentIsItsDescriptionThenItsUuid() {
        long nid = KernelTerm.ENGLISH_LANGUAGE.nid();
        String description = PrimitiveData.textOptional(nid).orElseThrow();

        String text = DiagnosticText.component(nid);

        assertEquals(description + " (" + writtenUuids(PrimitiveData.publicId(nid)) + ")", text);
        assertNamesEveryUuid(KernelTerm.ENGLISH_LANGUAGE, text);
        assertEquals(description, DiagnosticText.name(nid));
        assertNoNid(text);
    }

    @Test
    void aComponentWithNoDescriptionIsItsUuid() {
        // The store's default text for it is the nid in angle brackets.
        assertEquals("<" + undescribedNid + ">", PrimitiveData.text(undescribedNid));

        assertEquals("UUID " + UNDESCRIBED, DiagnosticText.component(undescribedNid));
        assertEquals(UNDESCRIBED, DiagnosticText.name(undescribedNid));
    }

    @Test
    void aSemanticHasNoDescriptionAndIsItsUuid() {
        long semanticNid = EntityService.get().semanticsForComponentOfPattern(
                KernelTerm.ENGLISH_LANGUAGE.nid(), KernelTerm.DESCRIPTION_PATTERN.nid()).findFirst().orElseThrow().nid();

        String text = DiagnosticText.component(semanticNid);

        assertEquals(writtenUuids(PrimitiveData.publicId(semanticNid)), text);
        assertNoNid(text);
    }

    @Test
    void aNidTheStoreHasNoPublicIdForIsWrittenAsANidOfThisStore() {
        assertEquals("nid " + UNASSIGNED_NID + " in this store, which has no public id for it",
                DiagnosticText.component(UNASSIGNED_NID));
        assertEquals("nid " + UNASSIGNED_NID + " in this store", DiagnosticText.name(UNASSIGNED_NID));
    }

    @Test
    void aComponentThatIsReferredToButWasNeverWrittenIsItsUuidInThisStore() {
        // This store keeps an index from nid to UUID beside its entities, so it has the public
        // id of a nid for which no entity was written.
        String referredTo = UUID.randomUUID().toString();
        long nid = PrimitiveData.nid(PublicIds.of(referredTo));

        assertEquals("UUID " + referredTo, DiagnosticText.component(nid));
        assertEquals(referredTo, DiagnosticText.name(nid));
    }

    @Test
    void aPublicIdTheStoreHoldsIsWrittenWithItsDescription() {
        String description = PrimitiveData.textOptional(KernelTerm.ENGLISH_LANGUAGE.nid()).orElseThrow();

        String text = DiagnosticText.component(KernelTerm.ENGLISH_LANGUAGE.publicId());

        assertEquals(description + " (" + writtenUuids(KernelTerm.ENGLISH_LANGUAGE.publicId()) + ")", text);
        assertNamesEveryUuid(KernelTerm.ENGLISH_LANGUAGE, text);
    }

    @Test
    void aPublicIdTheStoreDoesNotHoldIsItsUuidAndIsAssignedNoNid() {
        String unknownUuid = UUID.randomUUID().toString();
        PublicId unknown = PublicIds.of(unknownUuid);

        assertEquals("UUID " + unknownUuid, DiagnosticText.component(unknown));
        assertFalse(PrimitiveData.get().hasPublicId(unknown), "writing the text assigned the public id a nid");
    }

    @Test
    void aTreeForAMessageNamesEachComponentTheSameWay() {
        DiTreeEntity tree = aTreeThatRefersToEveryKindOfComponent();

        String text = DiTreeText.diagnostic(tree);

        assertEquals(DiTreeText.tree(tree, DiagnosticText::name), text);
        assertTrue(text.contains(DiagnosticText.name(KernelTerm.LANGUAGE.nid())), text);
        assertTrue(text.contains(": " + UNDESCRIBED + "\n"), "a concept with no description is its UUID:\n" + text);
        assertTrue(text.contains("nid " + UNASSIGNED_NID + " in this store"),
                "a nid with no public id is written as a nid of this store:\n" + text);
        assertFalse(ANGLE_BRACKET_NID.matcher(text).find(), text);
        assertFalse(STORE_NID.matcher(text).find(), text);

        // The tree's own text writes a nid for each element of an id list, and for the
        // concept with no description.
        assertTrue(tree.toString().contains("<" + undescribedNid + ">"), tree.toString());
    }

    @Test
    void theDiagnosticFormThrowsNothingForWhatCannotBeWritten() {
        assertEquals("no tree", DiTreeText.diagnostic((DiTreeEntity) null));
        assertEquals("no vertex", DiTreeText.diagnostic((EntityVertex) null));
        assertEquals("no vertices", DiTreeText.diagnostic((List<EntityVertex>) null));

        EntityVertex reference = EntityVertex.make(KernelTerm.CONCEPT_REFERENCE);
        setProperty(reference, KernelTerm.CONCEPT_REFERENCE, KernelTerm.LANGUAGE);
        EntityVertex and = EntityVertex.make(KernelTerm.AND);
        assertEquals("[" + DiTreeText.diagnostic(reference) + "; " + DiTreeText.diagnostic(and) + "]",
                DiTreeText.diagnostic(List.of(reference, and)));
        assertEquals(DiagnosticText.name(KernelTerm.CONCEPT_REFERENCE.nid()) + ": "
                + DiagnosticText.name(KernelTerm.LANGUAGE.nid()), DiTreeText.diagnostic(reference));
    }

    /**
     * A definition whose necessary set holds a reference to a described concept, a reference to
     * the concept with no description, and a vertex with a list of both and of a nid the store
     * never assigned.
     */
    private DiTreeEntity aTreeThatRefersToEveryKindOfComponent() {
        EntityVertex root = EntityVertex.make(KernelTerm.DEFINITION_ROOT);
        EntityVertex necessarySet = EntityVertex.make(KernelTerm.NECESSARY_SET);
        EntityVertex and = EntityVertex.make(KernelTerm.AND);

        EntityVertex described = EntityVertex.make(KernelTerm.CONCEPT_REFERENCE);
        setProperty(described, KernelTerm.CONCEPT_REFERENCE, KernelTerm.LANGUAGE);

        EntityVertex undescribed = EntityVertex.make(KernelTerm.CONCEPT_REFERENCE);
        setProperty(undescribed, KernelTerm.CONCEPT_REFERENCE, EntityProxy.Concept.make(PublicIds.of(UNDESCRIBED)));

        EntityVertex propertySet = EntityVertex.make(KernelTerm.PROPERTY_SET);
        setProperty(propertySet, KernelTerm.PROPERTY_SEQUENCE,
                LongIds.list.of(IkeTerms.PART_OF.nid(), undescribedNid, UNASSIGNED_NID));

        DiTreeEntity.Builder builder = DiTreeEntity.builder();
        builder.setRoot(root);
        builder.addEdge(necessarySet, root);
        builder.addEdge(and, necessarySet);
        builder.addEdge(described, and);
        builder.addEdge(undescribed, and);
        builder.addEdge(propertySet, and);
        return builder.build();
    }

    /** Gives a vertex one property. */
    private static void setProperty(EntityVertex vertex, EntityFacade key, Object value) {
        MutableLongObjectMap<Object> properties = LongObjectMaps.mutable.empty();
        properties.put(key.nid(), value);
        vertex.setProperties(properties);
    }

    /** A public id's UUIDs as a message writes them: every one, in the order it lists them. */
    private static String writtenUuids(PublicId publicId) {
        UUID[] uuids = publicId.asUuidArray();
        StringBuilder text = new StringBuilder(uuids.length == 1 ? "UUID " : "UUIDs ");
        for (int i = 0; i < uuids.length; i++) {
            text.append(i == 0 ? "" : ", ").append(uuids[i]);
        }
        return text.toString();
    }

    /** Every UUID of the component appears in the text: none of them is its primordial UUID. */
    private static void assertNamesEveryUuid(EntityFacade component, String text) {
        for (UUID uuid : component.publicId().asUuidArray()) {
            assertTrue(text.contains(uuid.toString()), "the text names " + uuid + ": " + text);
        }
    }

    private static void assertNoNid(String text) {
        assertFalse(ANGLE_BRACKET_NID.matcher(text).find(), text);
        assertFalse(STORE_NID.matcher(text).find(), text);
    }
}
