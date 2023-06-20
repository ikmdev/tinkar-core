package dev.ikm.tinkar.entity.transfom;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.FieldDefinitionRecord;
import dev.ikm.tinkar.schema.FieldDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static dev.ikm.tinkar.entity.transfom.ProtobufToEntityTestHelper.createPBPublicId;
import static dev.ikm.tinkar.entity.transfom.ProtobufToEntityTestHelper.openSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestEntityToProtobufFieldDefinitionTransform {

    @Test
    @DisplayName("Transform a Field Definition Transform With All Fields Present")
    public void testEntityFieldDefinitionTransformWithAllFieldsPresent() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            ConceptEntity dataTypeConcept = mock(ConceptEntity.class);
            PublicId datatypePublicId = PublicIds.newRandom();
            when(dataTypeConcept.publicId()).thenReturn(datatypePublicId);

            ConceptEntity meaningConcept = mock(ConceptEntity.class);
            PublicId meaningPublicId = PublicIds.newRandom();
            when(meaningConcept.publicId()).thenReturn(meaningPublicId);

            ConceptEntity purposeConcept = mock(ConceptEntity.class);
            PublicId purposePublicId = PublicIds.newRandom();
            when(purposeConcept.publicId()).thenReturn(purposePublicId);

            FieldDefinitionRecord mockFieldDef = mock(FieldDefinitionRecord.class);
            when(mockFieldDef.dataType()).thenReturn(dataTypeConcept);
            when(mockFieldDef.meaning()).thenReturn(meaningConcept);
            when(mockFieldDef.purpose()).thenReturn(purposeConcept);

            // When we transform the FieldDef entity to a protobuf message
            FieldDefinition actualTransformedFieldDefinition = EntityToTinkarSchemaTransformer.getInstance().createPBFieldDefinition(mockFieldDef);
            // Then assert that the actual transformed Field def matches that of the expected.
            assertEquals(createPBPublicId(dataTypeConcept.publicId()), actualTransformedFieldDefinition.getDataType(), "Public Id's did not match in Field Definitions Data Type.");
            assertEquals(createPBPublicId(meaningConcept.publicId()), actualTransformedFieldDefinition.getMeaning(), "Public Id's did not match in Field Definitions Meaning.");
            assertEquals(createPBPublicId(purposeConcept.publicId()), actualTransformedFieldDefinition.getPurpose(), "Public Id's did not match in Field Definitions Purpose.");
        });
    }

    @Test
    @DisplayName("Transform a Field Definition Transform With Missing DataType")
    public void testEntityFieldDefinitionTransformWithMissingDataType() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            ConceptEntity meaningConcept = mock(ConceptEntity.class);
            PublicId meaningPublicId = PublicIds.newRandom();
            when(meaningConcept.publicId()).thenReturn(meaningPublicId);

            ConceptEntity purposeConcept = mock(ConceptEntity.class);
            PublicId purposePublicId = PublicIds.newRandom();
            when(purposeConcept.publicId()).thenReturn(purposePublicId);

            FieldDefinitionRecord mockFieldDef = mock(FieldDefinitionRecord.class);
            when(mockFieldDef.meaning()).thenReturn(meaningConcept);
            when(mockFieldDef.purpose()).thenReturn(purposeConcept);

            // When we transform the FieldDef entity to a protobuf message

            // Then throw an exception because missing fields in field definition are not allowed
            assertThrows(Throwable.class, () -> EntityToTinkarSchemaTransformer.getInstance().createPBFieldDefinition(mockFieldDef), "Not allowed to have a missing DataType in field definitions.");
        });
    }

    @Test
    @DisplayName("Transform a Field Definition Transform With Missing Meaning")
    public void testEntityFieldDefinitionTransformWithMissingMeaning() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            ConceptEntity dataTypeConcept = mock(ConceptEntity.class);
            PublicId datatypePublicId = PublicIds.newRandom();
            when(dataTypeConcept.publicId()).thenReturn(datatypePublicId);

            ConceptEntity purposeConcept = mock(ConceptEntity.class);
            PublicId purposePublicId = PublicIds.newRandom();
            when(purposeConcept.publicId()).thenReturn(purposePublicId);

            FieldDefinitionRecord mockFieldDef = mock(FieldDefinitionRecord.class);
            when(mockFieldDef.dataType()).thenReturn(dataTypeConcept);
            when(mockFieldDef.purpose()).thenReturn(purposeConcept);

            // When we transform the FieldDef entity to a protobuf message

            // Then throw an exception because missing fields in field definition are not allowed
            assertThrows(Throwable.class, () -> EntityToTinkarSchemaTransformer.getInstance().createPBFieldDefinition(mockFieldDef), "Not allowed to have a missing Meaning in field definitions.");
        });

    }

    @Test
    @DisplayName("Transform a Field Definition Transform With Missing Purpose")
    public void testEntityFieldDefinitionTransformWithMissingPurpose() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            ConceptEntity dataTypeConcept = mock(ConceptEntity.class);
            PublicId datatypePublicId = PublicIds.newRandom();
            when(dataTypeConcept.publicId()).thenReturn(datatypePublicId);

            ConceptEntity meaningConcept = mock(ConceptEntity.class);
            PublicId meaningPublicId = PublicIds.newRandom();
            when(meaningConcept.publicId()).thenReturn(meaningPublicId);

            FieldDefinitionRecord mockFieldDef = mock(FieldDefinitionRecord.class);
            when(mockFieldDef.dataType()).thenReturn(dataTypeConcept);
            when(mockFieldDef.meaning()).thenReturn(meaningConcept);

            // When we transform the FieldDef entity to a protobuf message

            // Then throw an exception because missing fields in field definition are not allowed
            assertThrows(Throwable.class, () -> EntityToTinkarSchemaTransformer.getInstance().createPBFieldDefinition(mockFieldDef), "Not allowed to have a missing Purpose in field definitions.");
        });
    }
}
