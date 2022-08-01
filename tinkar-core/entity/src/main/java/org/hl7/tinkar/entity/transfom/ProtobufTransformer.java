package org.hl7.tinkar.entity.transfom;

import com.google.protobuf.ByteString;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.id.PublicIdList;
import org.hl7.tinkar.common.id.PublicIds;
import org.hl7.tinkar.common.id.impl.PublicId1;
import org.hl7.tinkar.common.util.time.DateTimeUtil;
import org.hl7.tinkar.common.util.uuid.UuidUtil;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.protobuf.*;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ProtobufTransformer {

    private final List<StampEntity<? extends StampEntityVersion>> stampEntities = new ArrayList<>();

    public Entity<? extends EntityVersion> transform(PBTinkarMsg pbTinkarMsg) {
        return switch (pbTinkarMsg.getValueCase()) {
            case CONCEPTCHRONOLOGYVALUE -> createConceptChronology(pbTinkarMsg.getConceptChronologyValue());
            case SEMANTICCHRONOLOGYVALUE -> createSemanticChronology(pbTinkarMsg.getSemanticChronologyValue());
            case PATTERNCHRONOLOGYVALUE -> createPatternChronology(pbTinkarMsg.getPatternChronologyValue());
            case VALUE_NOT_SET -> throw new IllegalStateException("Tinkar message value not set");
            default -> throw new IllegalStateException("Unexpected value: " + pbTinkarMsg.getValueCase());
        };
    }

    public List<StampEntity<? extends StampEntityVersion>> getStampEntities() {
        return stampEntities;
    }

    //Concept Transformation
    private ConceptEntity<? extends ConceptEntityVersion> createConceptChronology(
            PBConceptChronology pbConceptChronology) {
        ConceptRecord conceptRecord = createConcept(pbConceptChronology.getPublicId());
        RecordListBuilder<ConceptVersionRecord> conceptVersions = RecordListBuilder.make();
        for (PBConceptVersion pbConceptVersion : pbConceptChronology.getConceptVersionsList()) {
            conceptVersions.add(createConceptVersion(pbConceptVersion, conceptRecord));
        }
        return ConceptRecordBuilder.builder(conceptRecord).versions(conceptVersions).build();
    }

    private ConceptRecord createConcept(PBConcept pbConcept){
        return createConcept(pbConcept.getPublicId());
    }

    private ConceptRecord createConcept(PBPublicId pbPublicId) throws IllegalStateException {
        PublicId conceptPublicId = createPublicId(pbPublicId);
        if (conceptPublicId.uuidCount() > 0) {
            int conceptNid = Entity.nid(conceptPublicId);
            if (conceptPublicId.uuidCount() > 1) {
                return ConceptRecordBuilder.builder()
                        .leastSignificantBits(conceptPublicId.asUuidArray()[0].getLeastSignificantBits())
                        .mostSignificantBits(conceptPublicId.asUuidArray()[0].getMostSignificantBits())
                        .additionalUuidLongs(UuidUtil.asArray(Arrays.copyOfRange(conceptPublicId.asUuidArray(),
                                1, conceptPublicId.uuidCount())))
                        .nid(conceptNid)
                        .build();
            } else {
                return ConceptRecordBuilder.builder()
                        .leastSignificantBits(conceptPublicId.asUuidArray()[0].getLeastSignificantBits())
                        .mostSignificantBits(conceptPublicId.asUuidArray()[0].getMostSignificantBits())
                        .nid(conceptNid)
                        .build();
            }
        } else {
            throw new IllegalStateException("missing primordial UUID");
        }
    }

    private ConceptVersionRecord createConceptVersion(PBConceptVersion pbConceptVersion, ConceptRecord concept) {
        return ConceptVersionRecordBuilder.builder()
                .chronology(concept)
                .stampNid(createStampChronology(pbConceptVersion.getStamp()).nid())
                .build();
    }

    //Semantic Transformation
    private SemanticEntity<? extends SemanticEntityVersion> createSemanticChronology(
            PBSemanticChronology pbSemanticChronology){
        SemanticRecord semanticRecord = createSemantic
                (pbSemanticChronology.getPublicId(), pbSemanticChronology.getPatternForSemantic(),
                        pbSemanticChronology.getReferencedComponent());
        RecordListBuilder<SemanticVersionRecord> semanticVersions = RecordListBuilder.make();
        for(PBSemanticVersion pbSemanticVersion : pbSemanticChronology.getVersionsList()){
            semanticVersions.add(createSemanticVersion(pbSemanticVersion, semanticRecord));
        }
        return SemanticRecordBuilder.builder(semanticRecord).versions(semanticVersions).build();
    }

    private SemanticRecord createSemantic(PBPublicId semanticId,
                                          PBPublicId patternId,
                                          PBPublicId referencedComponentId){
        PublicId semanticPublicId = createPublicId(semanticId);
        PublicId patternPublicId = createPublicId(patternId);
        PublicId referencedComponentPublicId = createPublicId(referencedComponentId);
        int patternNid = Entity.nid(patternPublicId);
        int referencedComponentNid = Entity.nid(referencedComponentPublicId);
        if (semanticPublicId.uuidCount() > 0) {
            int semanticNid = Entity.nid(semanticPublicId);
            if (semanticPublicId.uuidCount() > 1) {
                return SemanticRecordBuilder.builder()
                        .leastSignificantBits(semanticPublicId.asUuidArray()[0].getLeastSignificantBits())
                        .mostSignificantBits(semanticPublicId.asUuidArray()[0].getMostSignificantBits())
                        .additionalUuidLongs(UuidUtil.asArray(Arrays.copyOfRange(semanticPublicId.asUuidArray(),
                                1, semanticPublicId.uuidCount())))
                        .nid(semanticNid)
                        .patternNid(patternNid)
                        .referencedComponentNid(referencedComponentNid)
                        .build();
            } else {
                return SemanticRecordBuilder.builder()
                        .leastSignificantBits(semanticPublicId.asUuidArray()[0].getLeastSignificantBits())
                        .mostSignificantBits(semanticPublicId.asUuidArray()[0].getMostSignificantBits())
                        .nid(semanticNid)
                        .patternNid(patternNid)
                        .referencedComponentNid(referencedComponentNid)
                        .build();
            }
        } else {
            throw new IllegalStateException("missing primordial UUID");
        }
    }

    private SemanticVersionRecord createSemanticVersion(PBSemanticVersion pbSemanticVersion, SemanticRecord semantic){
        MutableList<Object> fieldValues = Lists.mutable.ofInitialCapacity(pbSemanticVersion.getFieldValuesCount());
        for(PBField pbField : pbSemanticVersion.getFieldValuesList()){
            fieldValues.add(createField(pbField));
        }
        return SemanticVersionRecordBuilder.builder()
                .chronology(semantic)
                .stampNid(createStampChronology(pbSemanticVersion.getStamp()).nid())
                .fieldValues(fieldValues.toImmutable())
                .build();
    }

    //Pattern Transformation
    private PatternEntity<? extends PatternEntityVersion> createPatternChronology(
            PBPatternChronology pbPatternChronology){
        PatternRecord patternRecord = createPattern(pbPatternChronology.getPublicId());
        RecordListBuilder<PatternVersionRecord> patternVersions = RecordListBuilder.make();
        for(PBPatternVersion pbPatternVersion : pbPatternChronology.getVersionsList()){
            patternVersions.add(createPatternVersion(pbPatternVersion, patternRecord));
        }
        return PatternRecordBuilder.builder(patternRecord).versions(patternVersions).build();
    }

    private PatternRecord createPattern(PBPublicId pbPublicId){
        PublicId patternPublicId = createPublicId(pbPublicId);
        int patternNid = Entity.nid(patternPublicId);
        if (patternPublicId.uuidCount() > 0) {
            if (patternPublicId.uuidCount() > 1) {
                return PatternRecordBuilder.builder()
                        .leastSignificantBits(patternPublicId.asUuidArray()[0].getLeastSignificantBits())
                        .mostSignificantBits(patternPublicId.asUuidArray()[0].getMostSignificantBits())
                        .nid(patternNid)
                        .additionalUuidLongs(UuidUtil.asArray(Arrays.copyOfRange(patternPublicId.asUuidArray(),
                                1, patternPublicId.uuidCount())))
                        .build();
            } else {
                return PatternRecordBuilder.builder()
                        .leastSignificantBits(patternPublicId.asUuidArray()[0].getLeastSignificantBits())
                        .mostSignificantBits(patternPublicId.asUuidArray()[0].getMostSignificantBits())
                        .nid(patternNid)
                        .build();
            }
        } else {
            throw new IllegalStateException("missing primordial UUID");
        }
    }

    private PatternVersionRecord createPatternVersion(PBPatternVersion pbPatternVersion, PatternRecord pattern){
        MutableList<FieldDefinitionRecord> fieldDefinition = Lists.mutable
                .withInitialCapacity(pbPatternVersion.getFieldDefinitionsCount());
        int patternStampNid = createStampChronology(pbPatternVersion.getStamp()).nid();
        int semanticPurposeNid = Entity.nid(createPublicId(pbPatternVersion.getReferencedComponentPurpose()));
        int semanticMeaningNid = Entity.nid(createPublicId(pbPatternVersion.getReferencedComponentMeaning()));
        for(PBFieldDefinition pbFieldDefinition : pbPatternVersion.getFieldDefinitionsList()){
            fieldDefinition.add(createFieldDefinitionRecord(pbFieldDefinition, patternStampNid));
        }
        return PatternVersionRecordBuilder.builder()
                .chronology(pattern)
                .stampNid(patternStampNid)
                .semanticPurposeNid(semanticPurposeNid)
                .semanticMeaningNid(semanticMeaningNid)
                .fieldDefinitions(fieldDefinition.toImmutable())
                .build();
    }

    //STAMP Transformation
    private StampEntity<? extends StampEntityVersion> createStampChronology(PBStampChronology pbStampChronology){
        StampRecord stampRecord = createStamp(pbStampChronology.getPublicId());
        RecordListBuilder<StampVersionRecord> stampVersions = RecordListBuilder.make();
        for(PBStampVersion pbStampVersion : pbStampChronology.getStampVersionsList()){
            stampVersions.add(createStampVersion(pbStampVersion, stampRecord));
        }
        StampEntity<? extends StampEntityVersion> stampEntity = StampRecordBuilder.builder(stampRecord)
                .versions(stampVersions).build();
        this.stampEntities.add(stampEntity);
        return stampEntity;
    }

    private StampRecord createStamp(PBPublicId pbPublicId){
        PublicId conceptPublicId = createPublicId(pbPublicId);
        if (conceptPublicId.uuidCount() > 0) {
            int conceptNid = Entity.nid(conceptPublicId);
            if (conceptPublicId.uuidCount() > 1) {
                return StampRecordBuilder.builder()
                        .leastSignificantBits(conceptPublicId.asUuidArray()[0].getLeastSignificantBits())
                        .mostSignificantBits(conceptPublicId.asUuidArray()[0].getMostSignificantBits())
                        .additionalUuidLongs(UuidUtil.asArray(Arrays.copyOfRange(conceptPublicId.asUuidArray(),
                                1, conceptPublicId.uuidCount())))
                        .nid(conceptNid)
                        .build();
            } else {
                return StampRecordBuilder.builder()
                        .leastSignificantBits(conceptPublicId.asUuidArray()[0].getLeastSignificantBits())
                        .mostSignificantBits(conceptPublicId.asUuidArray()[0].getMostSignificantBits())
                        .nid(conceptNid)
                        .build();
            }
        } else {
            throw new IllegalStateException("missing primordial UUID");
        }
    }

    private StampVersionRecord createStampVersion(PBStampVersion pbStampVersion, StampRecord stampRecord){
        return StampVersionRecordBuilder.builder()
                .chronology(stampRecord)
                .stateNid(Entity.nid(createPublicId(pbStampVersion.getStatus().getPublicId())))
                .time(Instant.ofEpochSecond(pbStampVersion.getTime().getSeconds()).getEpochSecond())
                .authorNid(Entity.nid(createPublicId(pbStampVersion.getAuthor().getPublicId())))
                .moduleNid(Entity.nid(createPublicId(pbStampVersion.getModule().getPublicId())))
                .pathNid(Entity.nid(createPublicId(pbStampVersion.getPath().getPublicId())))
                .build();
    }

    //Field Definition Transformation
    private FieldDefinitionRecord createFieldDefinitionRecord(PBFieldDefinition pbFieldDefinition,
                                                              int patternVersionStampNid) {
        int meaningNid = Entity.nid(createPublicId(pbFieldDefinition.getMeaning()));
        int purposeNid = Entity.nid(createPublicId(pbFieldDefinition.getPurpose()));
        int dataTypeNid = Entity.nid(createPublicId(pbFieldDefinition.getDataType()));

        return FieldDefinitionRecordBuilder.builder()
                .meaningNid(meaningNid)
                .purposeNid(purposeNid)
                .dataTypeNid(dataTypeNid)
                .patternVersionStampNid(patternVersionStampNid)
                .build();
    }

    //Field Transformation
    private Object createField(PBField pbField){
        return switch (pbField.getValueCase()){
            case BOOLVALUE -> pbField.getBoolValue();
            case BYTESVALUE -> pbField.getBytesValue().toByteArray();
            case CONCEPTVALUE -> createConcept(pbField.getConceptValue()); //TODO-aks8m: Do we need this? Just use PublicId
            case DIGRAPHVALUE -> throw new UnsupportedOperationException("createDiGraphEntity not implemented");
            case DITREEVALUE -> throw new UnsupportedOperationException("createDiTreeEntity not implemented");
            case FLOATVALUE -> pbField.getFloatValue();
            case GRAPHVALUE -> throw new UnsupportedOperationException("createGraphEntity not implemented");
            case INTVALUE -> pbField.getIntValue();
            case PUBLICIDVALUE -> createPublicId(pbField.getPublicIdValue());
            case PUBLICIDLISTVALUE -> createPublicIdList(pbField.getPublicIdListValue());
            case STAMPVALUE -> createStampChronology(pbField.getStampValue());
            case STRINGVALUE -> pbField.getStringValue();
            case TIMEVALUE -> DateTimeUtil.epochMsToInstant(pbField.getTimeValue().getSeconds());
            case VALUE_NOT_SET -> throw new IllegalStateException("PBField value not set");
            case VERTEXIDVALUE -> throw new UnsupportedOperationException("createVertexIdEntity not implemented");
            case VERTEXVALUE -> throw new UnsupportedOperationException("createVertexEntity not implemented");
        };
    }

    private PublicId createPublicId(PBPublicId pbPublicId){
        return PublicIds.of(pbPublicId.getIdList().stream()
                .map(ByteString::toByteArray)
                .map(ByteBuffer::wrap)
                .map(byteBuffer -> new UUID(byteBuffer.getLong(), byteBuffer.getLong()))
                .collect(Collectors.toList()));
    }

    //Todo: Review
    private PublicIdList createPublicIdList(PBPublicIdList pbPublicIdList) {
        final PublicId[] publicIds = new PublicId[pbPublicIdList.getPublicIdsCount()];
        for (int i = 0; i < pbPublicIdList.getPublicIdsCount(); i++) {
            publicIds[i] = createPublicId(pbPublicIdList.getPublicIds(i));
        }
        return PublicIds.list.of(publicIds);
    }

    //Todo: Review
    private PublicId1 createVertexId(PBVertexId pbVertexId) {
        return new PublicId1(UUID.nameUUIDFromBytes(pbVertexId.getId().toByteArray()));
    }

    /*private static DiGraphEntity<EntityVertex> createDigraphDTO(PBDiGraph pbDiGraph){
        pbDiGraph.get

        return new DiGraphDTO(
                parseRootSequences(pbDiGraph),
                parsePredecessorAndSuccessorMaps(pbDiGraph.getPredecesorMapList(), pbDiGraph.getPredecesorMapCount()),
                parseVertices(pbDiGraph.getVertexMapList(), pbDiGraph.getVertexMapCount()),
                parsePredecessorAndSuccessorMaps(pbDiGraph.getSuccessorMapList(), pbDiGraph.getSuccessorMapCount()));
    }

    private static GraphDTO createGraphDTO(PBGraph pbGraph){
        return new GraphDTO(
                parseVertices(pbGraph.getVertexMapList(), pbGraph.getVertexMapCount()),
                parsePredecessorAndSuccessorMaps(pbGraph.getSuccessorMapList(), pbGraph.getSuccessorMapCount()));
    }

    private DiTreeEntity createDiTreeEntity(PBDiTree pbDiTree) {
        return new DiTreeEntity<>(
                EntityVertex.make(createVertexDTO(pbDiTree.getRoot())),
                parseVertices(pbDiTree.getVertexMapList(), pbDiTree.getVertexMapCount()),
                parseSuccessors(pbDiTree.getSuccessorMapList()),
                parsePredecessors(pbDiTree.getPredecesorMapList()));
    }

    private ImmutableIntList parseRootSequences(PBDiGraph pbDiGraph) {
        MutableIntList rootSequences = new IntArrayList(pbDiGraph.getRootSequenceCount());
        pbDiGraph.getRootSequenceList().forEach(rootSequences::add);
        return rootSequences.toImmutable();
    }

    private ImmutableIntObjectMap<ImmutableIntList> parseSuccessors(List<PBIntToMultipleIntMap> successorMapList) {
        MutableIntObjectMap<ImmutableIntList> mutableIntObjectMap = new IntObjectHashMap<>();
        successorMapList.forEach(pbIntToMultipleIntMap -> {
            MutableIntList mutableIntList = new IntArrayList();
            pbIntToMultipleIntMap.getTargetList().forEach(mutableIntList::add);
            mutableIntObjectMap.put(pbIntToMultipleIntMap.getSource(), mutableIntList.toImmutable());
        });
        return mutableIntObjectMap.toImmutable();
    }

    private ImmutableIntIntMap parsePredecessors(List<PBIntToIntMap> predecesorMapList) {
        MutableIntIntMap mutableIntIntMap = new IntIntHashMap();
        predecesorMapList.forEach(pbIntToIntMap -> mutableIntIntMap.put(pbIntToIntMap.getSource(), pbIntToIntMap.getTarget()));
        return mutableIntIntMap.toImmutable();
    }

    private ImmutableList<EntityVertex> parseVertices(List<PBVertex> pbVertices, int pbVertexCount) {
        MutableList<EntityVertex> vertexMap = Lists.mutable.ofInitialCapacity(pbVertexCount);
        pbVertices.forEach(pbVertex -> vertexMap.add(EntityVertex.make(createVertexDTO(pbVertex))));
        return vertexMap.toImmutable();
    }

    private VertexDTO createVertexDTO(PBVertex pbVertex) {
        PublicId1 vertexID = processPBVertexID(pbVertex.getVertexId());
        MutableMap<ConceptDTO, Object> properties = Maps.mutable.ofInitialCapacity(pbVertex.getPropertiesCount());
        pbVertex.getPropertiesList().forEach(property -> properties.put(
                ConceptDTOBuilder.builder()
                        .publicId(createPublicId(pbVertex.getMeaning().getPublicId()))
                        .build(), createFieldObject(property.getValue())));
        return VertexDTOBuilder.builder()
                .vertexIdLsb(vertexID.leastSignificantBits())
                .vertexIdMsb(vertexID.mostSignificantBits())
                .vertexIndex(pbVertex.getVertexIndex())
                .meaning(ConceptDTOBuilder.builder()
                        .publicId(createPublicId(pbVertex.getMeaning().getPublicId()))
                        .build())
                .properties(properties.toImmutable())
                .build();
    }*/

}
