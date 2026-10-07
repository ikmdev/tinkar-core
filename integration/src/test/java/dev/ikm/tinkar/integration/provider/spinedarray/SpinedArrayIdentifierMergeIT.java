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
package dev.ikm.tinkar.integration.provider.spinedarray;

import network.ike.foundation.ike.bindings.IkeTerms;
import dev.ikm.tinkar.terms.KernelTerm;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.coordinate.Coordinates;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.view.ViewCoordinateRecord;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculatorWithCache;
import dev.ikm.tinkar.common.service.EntityCountSummary;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.PatternEntityVersion;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.entity.transaction.StampedWriter;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.State;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpinedArrayIdentifierMergeIT {
    static final Logger LOG = LoggerFactory.getLogger(SpinedArrayIdentifierMergeIT.class);

    @BeforeAll
    static void beforeAll() {
        File datastoreRoot = TestConstants.createFilePathInTargetFromClassName.apply(SpinedArrayIdentifierMergeIT.class);
        FileUtil.recursiveDelete(datastoreRoot);
        TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, datastoreRoot);
        File file = TestConstants.PB_EXAMPLE_DATA_REASONED;
        LoadEntitiesFromProtobufFile loadProto = new LoadEntitiesFromProtobufFile(file);
        EntityCountSummary count = loadProto.compute();
        LOG.info(count + " entitles loaded from file: " + loadProto.summarize() + "\n\n");
    }

    @AfterAll
    static void afterAll() {
        TestHelper.stopDatabase();
    }

    @Test
    public void testMergeIdentifiersCompoundPublicId_ExpectFailure() {
        UUID namespace = UUID.randomUUID();
        EntityProxy.Concept author = EntityProxy.Concept.make("IHTSDO SNOMED CT Starter Data Author",
                UuidT5Generator.get(namespace, "IHTSDO SNOMED CT Starter Data Author"));

        StampedWriter writer = StampedWriter.open("Snomed Starter Data Writer",
                State.ACTIVE, author, KernelTerm.PRIMORDIAL_MODULE, KernelTerm.PRIMORDIAL_PATH);

        initializeAuthor(writer, author);

        EntityProxy.Concept sctId = createIdentifierSemantic(writer, namespace, "SCTID");
        EntityProxy.Concept gmdnTerms = createIdentifierSemantic(writer, namespace, "GMDN Terms");

        //

        // 1. Create first concept
        UUID snomedUuid = createConceptWithIdentifier(writer, namespace, sctId, "725561007");
        // 2. Create second concept
        UUID gmdnUuid = createConceptWithIdentifier(writer, namespace, gmdnTerms, "62567");

        // 3. Create third concept with compound Public ID
        EntityProxy.Concept compoundConcept = EntityProxy.Concept.make(PublicIds.of(snomedUuid, gmdnUuid));
        writer.concept(compoundConcept);

        //

        writer.commit();

        Set<String> identifiers = extractIdentifiers(compoundConcept);

        // TODO remove after fixing root cause
        assertThrows(AssertionError.class, () -> {
            verifyIdentifiers(identifiers);
        });
    }

    @Test
    public void testMergeIdentifiersCompoundPublicId_Workaround() {
        UUID namespace = UUID.randomUUID();
        EntityProxy.Concept author = EntityProxy.Concept.make("IHTSDO SNOMED CT Starter Data Author",
                UuidT5Generator.get(namespace, "IHTSDO SNOMED CT Starter Data Author"));

        StampedWriter writer = StampedWriter.open("Snomed Starter Data Writer",
                State.ACTIVE, author, KernelTerm.PRIMORDIAL_MODULE, KernelTerm.PRIMORDIAL_PATH);

        initializeAuthor(writer, author);

        EntityProxy.Concept sctId = createIdentifierSemantic(writer, namespace, "SCTID");
        EntityProxy.Concept gmdnTerms = createIdentifierSemantic(writer, namespace, "GMDN Terms");

        //

        // 1. Create first concept
        UUID gmdnUuid = createConceptWithIdentifier(writer, namespace, gmdnTerms, "62567");
        // 2. Create third concept with compound Public ID, referencing first and second concepts
        UUID snomedUuid = UuidT5Generator.get(namespace, "725561007");
        EntityProxy.Concept compoundConcept = EntityProxy.Concept.make(PublicIds.of(snomedUuid, gmdnUuid));
        writer.concept(compoundConcept);
        // 3. Create second concept, using previously generated UUID
        createConceptWithIdentifier(writer, namespace, sctId, "725561007", snomedUuid);

        //

        writer.commit();

        Set<String> identifiers = extractIdentifiers(compoundConcept);
        verifyIdentifiers(identifiers);
    }

    private void verifyIdentifiers(Set<String> identifiers) {
        LOG.info("IDENTIFIERS: {}", identifiers);
        assertEquals(2, identifiers.size());
        assertTrue(identifiers.contains("GMDN Terms: 62567"));
        assertTrue(identifiers.contains("SCTID: 725561007"));
    }

    private EntityProxy.Concept createIdentifierSemantic(StampedWriter writer, UUID namespace, String name) {
        UUID snomedIdentifierUuid = UuidT5Generator.get(namespace, name);
        EntityProxy.Concept snomedIdentifier = EntityProxy.Concept.make(name, snomedIdentifierUuid);
        writer.concept(snomedIdentifier);
        writer.fullyQualifiedName(snomedIdentifier, name);
        writeIdentifier(writer, snomedIdentifier, IkeTerms.UNIVERSALLY_UNIQUE_IDENTIFIER, snomedIdentifierUuid.toString());
        return snomedIdentifier;
    }

    private UUID createConceptWithIdentifier(StampedWriter writer, UUID namespace, EntityProxy.Concept identifierSource, String identiferValue) {
        UUID uuid = UuidT5Generator.get(namespace, identiferValue);
        return createConceptWithIdentifier(writer, namespace, identifierSource, identiferValue, uuid);
    }

    private UUID createConceptWithIdentifier(StampedWriter writer, UUID namespace, EntityProxy.Concept identifierSource, String identifierValue, UUID uuid) {
        EntityProxy.Concept concept = EntityProxy.Concept.make(PublicIds.of(uuid));
        writer.concept(concept);
        writeIdentifier(writer, concept, IkeTerms.UNIVERSALLY_UNIQUE_IDENTIFIER, uuid.toString());
        writeIdentifier(writer, concept, identifierSource, identifierValue);
        return uuid;
    }

    private void initializeAuthor(StampedWriter writer, EntityProxy.Concept author) {
        writer.concept(author);
        writer.fullyQualifiedName(author, "IHTSDO SNOMED CT Starter Data Author");
        writeIdentifier(writer, author, IkeTerms.UNIVERSALLY_UNIQUE_IDENTIFIER, author.leastUuid().toString());
    }

    /** Writes an identifier semantic, its identity derived from the component, source, and value. */
    private void writeIdentifier(StampedWriter writer, EntityProxy.Concept component,
                                 EntityProxy.Concept source, String value) {
        PublicId identifier = PublicIds.of(UuidT5Generator.get(component.leastUuid(),
                "identifier|" + source.leastUuid() + "|" + value));
        writer.semantic(identifier, KernelTerm.IDENTIFIER_PATTERN, component, source, value);
    }

    private Set<String> extractIdentifiers(EntityProxy componentInDetailsViewer) {
        ViewCoordinateRecord viewCoord = Coordinates.View.DefaultView();
        ViewCalculatorWithCache viewCalc = ViewCalculatorWithCache.getCalculator(viewCoord);

        Latest<PatternEntityVersion> latestIdPattern = viewCalc.latestPatternEntityVersion(KernelTerm.IDENTIFIER_PATTERN);
        Set<String> identifiers = new HashSet<>();

        EntityService.get().forEachSemanticForComponentOfPattern(componentInDetailsViewer.nid(), KernelTerm.IDENTIFIER_PATTERN.nid(), (semanticEntity) -> {
            viewCalc.latest(semanticEntity).ifPresent((latestSemanticVersion -> {
                EntityProxy identifierSource = latestIdPattern.get().getFieldWithMeaning(KernelTerm.IDENTIFIER_SOURCE, latestSemanticVersion);
                if (!PublicId.equals(identifierSource, IkeTerms.UNIVERSALLY_UNIQUE_IDENTIFIER)) {
                    try {
                        String idSourceName = viewCalc.getPreferredDescriptionTextWithFallbackOrNid(identifierSource);
                        String idValue = latestIdPattern.get().getFieldWithMeaning(KernelTerm.IDENTIFIER_VALUE, latestSemanticVersion);

                        identifiers.add("%s: %s".formatted(idSourceName, idValue));
                    } catch (IndexOutOfBoundsException exception) {
                        //
                    }
                }
            }));
        });

        return identifiers;
    }

}
