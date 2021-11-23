package org.hl7.tinkar.integration.provider.protocolbuffers;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.id.PublicIdList;
import org.hl7.tinkar.common.id.PublicIds;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.common.service.DataUriOption;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.service.ServiceProperties;
import org.hl7.tinkar.common.util.uuid.UuidUtil;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.entity.transfom.ProtocolBuffersToEntityTransform;
import org.hl7.tinkar.integration.TestConstants;
import org.hl7.tinkar.protobuf.*;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Disabled
class TestProtobufToEntityTransform {

    private static final Logger LOG = LoggerFactory.getLogger(TestProtobufToEntityTransform.class);
    private final ProtocolBuffersToEntityTransform transformer = new ProtocolBuffersToEntityTransform();

    @BeforeAll
    static void setupSuite() {
        LOG.info("Clear caches");
        CachingService.clearAll();
        LOG.info("Setup: " + LOG.getName());
        LOG.info(ServiceProperties.jvmUuid());
        PrimitiveData.selectControllerByName(TestConstants.EPHEMERAL_STORE_NAME);
        PrimitiveData.getController().setDataUriOption(
                new DataUriOption(TestConstants.PB_TEST_FILE.getName(), TestConstants.PB_TEST_FILE.toURI()));
        PrimitiveData.start();
    }

    @AfterAll
    static void teardownSuite() {
        LOG.info("Teardown: " + LOG.getName());
        PrimitiveData.stop();
    }

    /**
     * Testing PublicId transformations
     */
    @Test
    @Order(1)
    public void singlePublicId() {
        UUID uuid1 = UUID.randomUUID();
        ByteString byteString = ByteString.copyFrom(UuidUtil.getRawBytes(uuid1));
        PBPublicId pbPublicId = PBPublicId.newBuilder()
                .addId(byteString)
                .build();

        PublicId publicIdOne = PublicIds.of(uuid1);
        PublicId publicIdTwo = transformer.createPublicId(pbPublicId);
        assert PublicId.equals(publicIdOne, publicIdTwo);
    }

    @Test
    public void multiplePublicId() {
        List<ByteString> uuidByteString = new ArrayList<>();
        UUID[] uuids = new UUID[4];
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        UUID uuid3 = UUID.randomUUID();
        UUID uuid4 = UUID.randomUUID();
        uuids[0] = uuid1;
        uuids[1] = uuid2;
        uuids[2] = uuid3;
        uuids[3] = uuid4;
        uuidByteString.add(ByteString.copyFrom(UuidUtil.getRawBytes(uuid1)));
        uuidByteString.add(ByteString.copyFrom(UuidUtil.getRawBytes(uuid2)));
        uuidByteString.add(ByteString.copyFrom(UuidUtil.getRawBytes(uuid3)));
        uuidByteString.add(ByteString.copyFrom(UuidUtil.getRawBytes(uuid4)));
        PBPublicId pbPublicId = PBPublicId.newBuilder()
                .addAllId(uuidByteString)
                .build();
        PublicId publicIdOne = PublicIds.of(uuids);
        PublicId publicIdTwo = transformer.createPublicId(pbPublicId);
        assert PublicId.equals(publicIdOne, publicIdTwo);
    }

    @Test
    public void publicIdList() {
        List<PublicId> publicIds = new ArrayList<>();
        UUID uuid1 = UUID.randomUUID();
        ByteString byteString1 = ByteString.copyFrom(UuidUtil.getRawBytes(uuid1));
        PBPublicId pbPublicId1 = PBPublicId.newBuilder()
                .addId(byteString1)
                .build();
        publicIds.add(PublicIds.of(uuid1));

        UUID uuid2 = UUID.randomUUID();
        ByteString byteString2 = ByteString.copyFrom(UuidUtil.getRawBytes(uuid2));
        PBPublicId pbPublicId2 = PBPublicId.newBuilder()
                .addId(byteString2)
                .build();
        publicIds.add(PublicIds.of(uuid2));

        UUID uuid3 = UUID.randomUUID();
        ByteString byteString3 = ByteString.copyFrom(UuidUtil.getRawBytes(uuid3));
        PBPublicId pbPublicId3 = PBPublicId.newBuilder()
                .addId(byteString3)
                .build();
        publicIds.add(PublicIds.of(uuid3));

        PublicIdList publicIdListOne = PublicIds.list.of(PublicIds.of(uuid1), PublicIds.of(uuid2));
        PBPublicIdList pbPublicIdListTwo = PBPublicIdList.newBuilder()
                .addPublicIds(pbPublicId1)
                .addPublicIds(pbPublicId2)
                .addPublicIds(pbPublicId3)
                .build();
        assert PublicIdList.equals(publicIdListOne, transformer.createPublicIdList(pbPublicIdListTwo));
    }

    /**
     * Testing chronologies (e.g., Concept, Semantic, Pattern) transformations
     */
    @Test
    public void conceptChronology() {
        UUID conceptUUID = UUID.randomUUID();
        UUID versionUUID = UUID.randomUUID();
        UUID stampUUID = UUID.randomUUID();
        UUID statusUUID = UUID.randomUUID();
        UUID authorUUID = UUID.randomUUID();
        UUID moduleUUID = UUID.randomUUID();
        UUID pathUUID = UUID.randomUUID();
        ByteString conceptByteString = ByteString.copyFrom(UuidUtil.getRawBytes(conceptUUID));
        ByteString versionByteString = ByteString.copyFrom(UuidUtil.getRawBytes(versionUUID));
        ByteString stampByteString = ByteString.copyFrom(UuidUtil.getRawBytes(stampUUID));
        ByteString statusByteString = ByteString.copyFrom(UuidUtil.getRawBytes(statusUUID));
        ByteString authorByteString = ByteString.copyFrom(UuidUtil.getRawBytes(authorUUID));
        ByteString moduleByteString = ByteString.copyFrom(UuidUtil.getRawBytes(moduleUUID));
        ByteString pathByteString = ByteString.copyFrom(UuidUtil.getRawBytes(pathUUID));
        Instant instant = Instant.now();

        PBConceptVersion pbConceptVersion = PBConceptVersion.newBuilder()
                .setPublicId(PBPublicId.newBuilder()
                        .addId(versionByteString)
                        .build())
                .setStamp(PBStamp.newBuilder()
                        .setPublicId(PBPublicId.newBuilder().addId(stampByteString).build())
                        .setStatus(PBConcept.newBuilder().setPublicId(PBPublicId.newBuilder().addId(statusByteString).build()).build())
                        .setTime(Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build())
                        .setAuthor(PBConcept.newBuilder().setPublicId(PBPublicId.newBuilder().addId(authorByteString).build()).build())
                        .setModule(PBConcept.newBuilder().setPublicId(PBPublicId.newBuilder().addId(moduleByteString).build()).build())
                        .setPath(PBConcept.newBuilder().setPublicId(PBPublicId.newBuilder().addId(pathByteString).build()).build())
                        .build())
                .build();

        PBConceptChronology pbConceptChronology = PBConceptChronology.newBuilder()
                .setPublicId(PBPublicId.newBuilder().addId(conceptByteString).build())
                .addConceptVersions(pbConceptVersion)
                .build();

        ConceptRecord conceptChronology = ConceptRecordBuilder.builder()
                .leastSignificantBits(conceptUUID.getLeastSignificantBits())
                .mostSignificantBits(conceptUUID.getMostSignificantBits())
                .nid(EntityService.get().nidForUuids(conceptUUID))
                .build();

        MutableList<ConceptEntityVersion> versions = Lists.mutable.ofInitialCapacity(1);
        versions.add(ConceptVersionRecordBuilder.builder()
                .chronology(conceptChronology)
                .stampNid(EntityService.get().nidForUuids(stampUUID))
                .build());
        ConceptRecord conceptChronologyOne = ConceptRecordBuilder.builder(conceptChronology).versionRecords(versions).build();

        ConceptEntity<ConceptEntityVersion> conceptChronologyTwo = transformer.makeConceptChronology(pbConceptChronology);
        assert conceptChronologyOne.deepEquals(conceptChronologyTwo);
    }

    @Test
    public void semanticChronology() {
        UUID semanticUUID = UUID.randomUUID();
        UUID referenceUUID = UUID.randomUUID();
        UUID patternUUID = UUID.randomUUID();
        UUID versionUUID = UUID.randomUUID();
        UUID stampUUID = UUID.randomUUID();
        UUID statusUUID = UUID.randomUUID();
        UUID authorUUID = UUID.randomUUID();
        UUID moduleUUID = UUID.randomUUID();
        UUID pathUUID = UUID.randomUUID();
        ByteString semanticByteString = ByteString.copyFrom(UuidUtil.getRawBytes(semanticUUID));
        ByteString referenceByteString = ByteString.copyFrom(UuidUtil.getRawBytes(referenceUUID));
        ByteString patternByteString = ByteString.copyFrom(UuidUtil.getRawBytes(patternUUID));
        ByteString versionByteString = ByteString.copyFrom(UuidUtil.getRawBytes(versionUUID));
        ByteString stampByteString = ByteString.copyFrom(UuidUtil.getRawBytes(stampUUID));
        ByteString statusByteString = ByteString.copyFrom(UuidUtil.getRawBytes(statusUUID));
        ByteString authorByteString = ByteString.copyFrom(UuidUtil.getRawBytes(authorUUID));
        ByteString moduleByteString = ByteString.copyFrom(UuidUtil.getRawBytes(moduleUUID));
        ByteString pathByteString = ByteString.copyFrom(UuidUtil.getRawBytes(pathUUID));
        Instant instant = Instant.now();
        String fieldValue = "Test Field Value";

        PBSemanticVersion pbSemanticVersion = PBSemanticVersion.newBuilder()
                .setPublicId(PBPublicId.newBuilder().addId(versionByteString).build())
                .setStamp(PBStamp.newBuilder()
                        .setPublicId(PBPublicId.newBuilder().addId(stampByteString).build())
                        .setStatus(PBConcept.newBuilder().setPublicId(PBPublicId.newBuilder().addId(statusByteString).build()).build())
                        .setTime(Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build())
                        .setAuthor(PBConcept.newBuilder().setPublicId(PBPublicId.newBuilder().addId(authorByteString).build()).build())
                        .setModule(PBConcept.newBuilder().setPublicId(PBPublicId.newBuilder().addId(moduleByteString).build()).build())
                        .setPath(PBConcept.newBuilder().setPublicId(PBPublicId.newBuilder().addId(pathByteString).build()).build())
                        .build())
                .addFieldValues(PBField.newBuilder().setStringValue(fieldValue).build())
                .build();

        PBSemanticChronology pbSemanticChronology = PBSemanticChronology.newBuilder()
                .setPublicId(PBPublicId.newBuilder().addId(semanticByteString).build())
                .setPatternForSemantic(PBPublicId.newBuilder().addId(patternByteString).build())
                .setReferencedComponent(PBPublicId.newBuilder().addId(referenceByteString).build())
                .addVersions(pbSemanticVersion)
                .build();

        MutableList<Object> fieldsList = Lists.mutable.ofInitialCapacity(1);
        fieldsList.add(fieldValue);

        SemanticRecord semanticChronology = SemanticRecordBuilder.builder()
                .leastSignificantBits(semanticUUID.getLeastSignificantBits())
                .mostSignificantBits(semanticUUID.getMostSignificantBits())
                .nid(EntityService.get().nidForUuids(semanticUUID))
                .referencedComponentNid(EntityService.get().nidForUuids(referenceUUID))
                .patternNid(EntityService.get().nidForUuids(patternUUID))
                .build();
        MutableList<SemanticEntityVersion> versions = Lists.mutable.ofInitialCapacity(1);
        versions.add(SemanticVersionRecordBuilder.builder()
                .chronology(semanticChronology)
                .stampNid(EntityService.get().nidForUuids(stampUUID))
                .fieldValues(fieldsList.toImmutable())
                .build());

        SemanticRecord semanticChronologyOne = SemanticRecordBuilder.builder(semanticChronology).versionRecords(versions).build();

        SemanticEntity<SemanticEntityVersion> semanticTwo = transformer.makeSemanticChronology(pbSemanticChronology);
        assert semanticChronologyOne.deepEquals(semanticTwo);
    }

    @Test
    public void patternChronology() {
        UUID patternUUID = UUID.randomUUID();
        UUID purposeUUID = UUID.randomUUID();
        UUID meaningUUID = UUID.randomUUID();
        UUID versionUUID = UUID.randomUUID();
        UUID stampUUID = UUID.randomUUID();
        UUID statusUUID = UUID.randomUUID();
        UUID authorUUID = UUID.randomUUID();
        UUID moduleUUID = UUID.randomUUID();
        UUID pathUUID = UUID.randomUUID();
        UUID fieldMeaningUUID = UUID.randomUUID();
        UUID fieldPurposeUUID = UUID.randomUUID();
        UUID fieldDataType = UUID.randomUUID();
        ByteString patternByteString = ByteString.copyFrom(UuidUtil.getRawBytes(patternUUID));
        ByteString purposeByteString = ByteString.copyFrom(UuidUtil.getRawBytes(purposeUUID));
        ByteString meaningByteString = ByteString.copyFrom(UuidUtil.getRawBytes(meaningUUID));
        ByteString versionByteString = ByteString.copyFrom(UuidUtil.getRawBytes(versionUUID));
        ByteString stampByteString = ByteString.copyFrom(UuidUtil.getRawBytes(stampUUID));
        ByteString statusByteString = ByteString.copyFrom(UuidUtil.getRawBytes(statusUUID));
        ByteString authorByteString = ByteString.copyFrom(UuidUtil.getRawBytes(authorUUID));
        ByteString moduleByteString = ByteString.copyFrom(UuidUtil.getRawBytes(moduleUUID));
        ByteString pathByteString = ByteString.copyFrom(UuidUtil.getRawBytes(pathUUID));
        ByteString fieldMeaningByteString = ByteString.copyFrom(UuidUtil.getRawBytes(fieldMeaningUUID));
        ByteString fieldPurposeByteString = ByteString.copyFrom(UuidUtil.getRawBytes(fieldPurposeUUID));
        ByteString fieldDataTypeByteString = ByteString.copyFrom(UuidUtil.getRawBytes(fieldDataType));
        Instant instant = Instant.now();

        PBPatternVersion pbPatternVersion = PBPatternVersion.newBuilder()
                .setPublicId(PBPublicId.newBuilder().addId(versionByteString).build())
                .setStamp(PBStamp.newBuilder()
                        .setPublicId(PBPublicId.newBuilder().addId(stampByteString).build())
                        .setStatus(PBConcept.newBuilder().setPublicId(PBPublicId.newBuilder().addId(statusByteString).build()).build())
                        .setTime(Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build())
                        .setAuthor(PBConcept.newBuilder().setPublicId(PBPublicId.newBuilder().addId(authorByteString).build()).build())
                        .setModule(PBConcept.newBuilder().setPublicId(PBPublicId.newBuilder().addId(moduleByteString).build()).build())
                        .setPath(PBConcept.newBuilder().setPublicId(PBPublicId.newBuilder().addId(pathByteString).build()).build())
                        .build())
                .setReferencedComponentPurpose(PBPublicId.newBuilder().addId(purposeByteString).build())
                .setReferencedComponentMeaning(PBPublicId.newBuilder().addId(meaningByteString).build())
                .addFieldDefinitions(PBFieldDefinition.newBuilder()
                        .setMeaning(PBPublicId.newBuilder().addId(fieldMeaningByteString).build())
                        .setPurpose(PBPublicId.newBuilder().addId(fieldPurposeByteString).build())
                        .setDataType(PBPublicId.newBuilder().addId(fieldDataTypeByteString).build()))
                .build();

        PBPatternChronology pbPatternChronology = PBPatternChronology.newBuilder()
                .setPublicId(PBPublicId.newBuilder().addId(patternByteString).build())
                .addVersions(pbPatternVersion)
                .build();

        PatternRecord patternChronology = PatternRecordBuilder.builder()
                .leastSignificantBits(patternUUID.getLeastSignificantBits())
                .mostSignificantBits(patternUUID.getMostSignificantBits())
                .nid(EntityService.get().nidForUuids(patternUUID))
                .build();

        MutableList<FieldDefinitionRecord> fieldDefinitionList = Lists.mutable.ofInitialCapacity(1);
        MutableList<PatternVersionRecord> versions = Lists.mutable.ofInitialCapacity(1);
        versions.add(PatternVersionRecordBuilder.builder()
                .chronology(patternChronology)
                .semanticMeaningNid(EntityService.get().nidForUuids(meaningUUID))
                .semanticPurposeNid(EntityService.get().nidForUuids(purposeUUID))
                .stampNid(EntityService.get().nidForUuids(stampUUID))
                .fieldDefinitionMutableList(fieldDefinitionList)
                .build());

        fieldDefinitionList.add(FieldDefinitionRecordBuilder.builder()
                .meaningNid(EntityService.get().nidForUuids(fieldMeaningUUID))
                .purposeNid(EntityService.get().nidForUuids(fieldPurposeUUID))
                .dataTypeNid(EntityService.get().nidForUuids(fieldDataType))
                .patternVersionStampNid(EntityService.get().nidForUuids(stampUUID)).build());

        PatternRecord patternOne = PatternRecordBuilder.builder(patternChronology).versionRecords(versions).build();

        PatternRecord patternTwo = transformer.makePatternChronology(pbPatternChronology);
        assert patternOne.deepEquals(patternTwo);
    }
}
