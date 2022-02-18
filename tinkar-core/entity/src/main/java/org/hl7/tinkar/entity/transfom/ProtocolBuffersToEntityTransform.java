package org.hl7.tinkar.entity.transfom;
/*
import com.google.protobuf.ByteString;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.map.primitive.ImmutableIntIntMap;
import org.eclipse.collections.api.map.primitive.ImmutableIntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntIntMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import org.eclipse.collections.impl.map.mutable.primitive.IntIntHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.id.PublicIdList;
import org.hl7.tinkar.common.id.PublicIds;
import org.hl7.tinkar.common.id.impl.IntIdListArray;
import org.hl7.tinkar.common.id.impl.PublicId1;
import org.hl7.tinkar.common.util.uuid.UuidUtil;
import org.hl7.tinkar.dto.ConceptDTO;
import org.hl7.tinkar.dto.ConceptDTOBuilder;
import org.hl7.tinkar.dto.graph.VertexDTO;
import org.hl7.tinkar.dto.graph.VertexDTOBuilder;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.entity.graph.DiTreeEntity;
import org.hl7.tinkar.entity.graph.EntityVertex;
import org.hl7.tinkar.protobuf.*;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

 */

public class ProtocolBuffersToEntityTransform {

}
/*
public class ProtocolBuffersToEntityTransform implements EntityTransform<PBTinkarMsg, Entity> {
    protected static final Logger LOG = Logger.getLogger(ProtocolBuffersToEntityTransform.class.getName());

    public Entity transform(PBTinkarMsg pbTinkarMsg) {
        return switch (pbTinkarMsg.getValueCase()) {
            case CONCEPTCHRONOLOGYVALUE -> makeConceptChronology(pbTinkarMsg.getConceptChronologyValue());
            case SEMANTICCHRONOLOGYVALUE -> makeSemanticChronology(pbTinkarMsg.getSemanticChronologyValue());
            case PATTERNCHRONOLOGYVALUE -> makePatternChronology(pbTinkarMsg.getPatternChronologyValue());
            default -> throw new IllegalStateException("not expecting " + pbTinkarMsg.getValueCase());
        };
    }

    public ConceptRecord makeConceptChronology(PBConceptChronology pbConceptChronology) {
        ConceptRecord conceptEntity = createConceptEntity(pbConceptChronology.getPublicId());
        RecordListBuilder<ConceptVersionRecord> conceptEntityVersions = RecordListBuilder.make();
        createVersionEntities(pbConceptChronology, conceptEntity, conceptEntityVersions);
        return ConceptRecordBuilder.builder((ConceptRecord) conceptEntity).versions(conceptEntityVersions).build();
    }

    public SemanticRecord makeSemanticChronology(PBSemanticChronology pbSemanticChronology) {
        SemanticRecord semanticEntity = createSemanticEntity(pbSemanticChronology);
        RecordListBuilder<SemanticVersionRecord> semanticEntityVersions = RecordListBuilder.make();
        createVersionEntities(pbSemanticChronology, semanticEntity, semanticEntityVersions);
        return SemanticRecordBuilder.builder((SemanticRecord) semanticEntity).versions(semanticEntityVersions).build();
    }

    public PatternRecord makePatternChronology(PBPatternChronology pbPatternChronology) {
        PatternRecord patternRecord = createPatternEntity(pbPatternChronology);
        RecordListBuilder<PatternVersionRecord> patternEntityVersions = RecordListBuilder.make();
        createVersionEntities(pbPatternChronology, patternRecord, patternEntityVersions);
        return PatternRecordBuilder.builder((PatternRecord) patternRecord).versions(patternEntityVersions).build();
    }

    public ConceptRecord createConceptEntity(PBPublicId pbPublicId) throws IllegalStateException {
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

    public void createVersionEntities(PBConceptChronology pbConceptChronology,
                                      ConceptRecord conceptEntity,
                                      RecordListBuilder<ConceptVersionRecord> versions) {
        for (PBConceptVersion pbConceptVersion : pbConceptChronology.getConceptVersionsList()) {
            versions.add(createConceptVersionEntity(pbConceptVersion, conceptEntity));
        }
        versions.build();
    }

    public ConceptVersionRecord createConceptVersionEntity(PBConceptVersion pbConceptVersion,
                                                           ConceptRecord conceptRecord) {
        StampEntityVersion stampEntityVersion = createStampEntityVersion(pbConceptVersion.getStamp());
        return ConceptVersionRecordBuilder.builder()
                .chronology((ConceptRecord) conceptRecord)
                .stampNid(stampEntityVersion.stampNid())
                .build();
    }

    public StampEntityVersion createStampEntityVersion(PBStamp pbStamp) {
        StampRecord stampEntity = createStampEntity(pbStamp);
        int stateNid = Entity.nid(createPublicId(pbStamp.getStatus().getPublicId()));
        long time = Instant.ofEpochSecond(pbStamp.getTime().getSeconds(), pbStamp.getTime().getNanos()).getEpochSecond();
        int authorNid = Entity.nid(createPublicId(pbStamp.getAuthor().getPublicId()));
        int moduleNid = Entity.nid(createPublicId(pbStamp.getModule().getPublicId()));
        int pathNid = Entity.nid(createPublicId(pbStamp.getPath().getPublicId()));
        return StampVersionRecordBuilder.builder()
                .stateNid(stateNid)
                .time(time)
                .authorNid(authorNid)
                .moduleNid(moduleNid)
                .pathNid(pathNid)
                .chronology(stampEntity)
                .build();
    }

    public StampRecord createStampEntity(PBStamp pbStamp) {
        PublicId stampPublicId = createPublicId(pbStamp.getPublicId());
        if (stampPublicId.uuidCount() > 0) {
            int stampNid = Entity.nid(stampPublicId);
            if (stampPublicId.uuidCount() > 1) {
                return StampRecordBuilder.builder()
                        .leastSignificantBits(stampPublicId.asUuidArray()[0].getLeastSignificantBits())
                        .mostSignificantBits(stampPublicId.asUuidArray()[0].getMostSignificantBits())
                        .nid(stampNid)
                        .additionalUuidLongs(UuidUtil.asArray(Arrays.copyOfRange(stampPublicId.asUuidArray(),
                                1, stampPublicId.uuidCount())))
                        .build();
            } else {
                return StampRecordBuilder.builder()
                        .leastSignificantBits(stampPublicId.asUuidArray()[0].getLeastSignificantBits())
                        .mostSignificantBits(stampPublicId.asUuidArray()[0].getMostSignificantBits())
                        .nid(stampNid)
                        .build();
            }
        } else {
            throw new IllegalStateException("missing primordial UUID");
        }
    }

    public SemanticRecord createSemanticEntity(PBSemanticChronology pbSemanticChronology) {
        PublicId semanticPublicId = createPublicId(pbSemanticChronology.getPublicId());
        PublicId patternPublicId = createPublicId(pbSemanticChronology.getPatternForSemantic());
        PublicId referencedComponentPublicId = createPublicId(pbSemanticChronology.getReferencedComponent());
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

    public PatternRecord createPatternEntity(PBPatternChronology pbPatternChronology) {
        PublicId patternPublicId = createPublicId(pbPatternChronology.getPublicId());
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

    public void createVersionEntities(PBPatternChronology pbPatternChronology,
                                      PatternRecord patternRecord,
                                      RecordListBuilder<PatternVersionRecord> versions) {
        for (PBPatternVersion pbPatternVersion : pbPatternChronology.getVersionsList()) {
            versions.add(createPatternVersionEntity(pbPatternVersion, patternRecord));
        }
        versions.build();
    }

    public PatternVersionRecord createPatternVersionEntity(PBPatternVersion pbPatternVersion,
                                                           PatternRecord patternRecord) {
        StampEntityVersion stampEntityVersion = createStampEntityVersion(pbPatternVersion.getStamp());
        int semanticPurposeNid = Entity.nid(createPublicId(pbPatternVersion.getReferencedComponentPurpose()));
        int semanticMeaningNid = Entity.nid(createPublicId(pbPatternVersion.getReferencedComponentMeaning()));

        MutableList<FieldDefinitionRecord> fieldDefinitionForEntities = Lists.mutable
                .ofInitialCapacity(pbPatternVersion.getFieldDefinitionsCount());
        createFieldDefinitionEntity(stampEntityVersion.stampNid(), pbPatternVersion.getFieldDefinitionsList(), fieldDefinitionForEntities);

        PatternVersionRecord patternVersionRecord = PatternVersionRecordBuilder.builder()
                .chronology(patternRecord)
                .stampNid(stampEntityVersion.stampNid())
                .semanticPurposeNid(semanticPurposeNid)
                .semanticMeaningNid(semanticMeaningNid)
                .fieldDefinitions(fieldDefinitionForEntities.toImmutable())
                .build();

        return patternVersionRecord;
    }

    public void createFieldDefinitionEntity(int versionStampNid, List<PBFieldDefinition> pbFieldDefinitions,
                                            MutableList<FieldDefinitionRecord> fieldDefinitionForEntityMutableList) {
        for (PBFieldDefinition pbFieldDefinition : pbFieldDefinitions) {
            int meaningNid = Entity.nid(createPublicId(pbFieldDefinition.getMeaning()));
            int purposeNid = Entity.nid(createPublicId(pbFieldDefinition.getPurpose()));
            int dataTypeNid = Entity.nid(createPublicId(pbFieldDefinition.getDataType()));

            FieldDefinitionRecord fieldDefinitionRecord = FieldDefinitionRecordBuilder.builder()
                    .meaningNid(meaningNid)
                    .purposeNid(purposeNid)
                    .dataTypeNid(dataTypeNid)
                    .patternVersionStampNid(versionStampNid)
                    .build();

            fieldDefinitionForEntityMutableList.add(fieldDefinitionRecord);
        }
    }

    public IntIdList createPublicIDHashList(List<PBPublicId> pbPublicIdList) {
        int[] hashArray = new int[pbPublicIdList.size()];
        for (int i = 0; i < pbPublicIdList.size(); i++) {
            hashArray[i] = createPublicId(pbPublicIdList.get(i)).hashCode();
        }
        return new IntIdListArray(hashArray);
    }

    public PublicIdList createPublicIdList(PBPublicIdList pbPublicIdList) {
        final PublicId[] publicIds = new PublicId[pbPublicIdList.getPublicIdsCount()];
        for (int i = 0; i < pbPublicIdList.getPublicIdsCount(); i++) {
            publicIds[i] = createPublicId(pbPublicIdList.getPublicIds(i));
        }
        return PublicIds.list.of(publicIds);
    }

    public PublicId1 processPBVertexID(PBVertexId pbVertexId) {
        return new PublicId1(UUID.nameUUIDFromBytes(pbVertexId.getId().toByteArray()));
    }

    public ConceptRecord createConceptEntity(PBConcept pbConcept) {
        return createConceptEntity(pbConcept.getPublicId());
    }

    public SemanticEntity<SemanticVersionRecord> createSemanticEntity(PBSemantic pbSemantic) {
        PublicId semanticPublicId = createPublicId(pbSemantic.getPublicId());
        PublicId patternPublicId = createPublicId(pbSemantic.getPatternForSemantic());
        PublicId referencedComponentPublicId = createPublicId(pbSemantic.getReferencedComponent());
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

    public PublicId createPublicId(PBPublicId pbPublicId) {
        return PublicIds.of(pbPublicId.getIdList().stream()
                .map(ByteString::toByteArray)
                .map(ByteBuffer::wrap)
                .map(byteBuffer -> new UUID(byteBuffer.getLong(), byteBuffer.getLong()))
                .collect(Collectors.toList()));
    }

    public PatternEntity<PatternVersionRecord> createPatternEntity(PBPattern pbPattern) {
        PublicId patternPublicId = createPublicId(pbPattern.getPublicId());
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

    public void createVersionEntities(PBSemanticChronology pbSemanticChronology,
                                      SemanticRecord semanticRecord,
                                      RecordListBuilder<SemanticVersionRecord> versions) {
        for (PBSemanticVersion pbSemanticVersion : pbSemanticChronology.getVersionsList()) {
            versions.add(createSemanticVersionEntity(pbSemanticVersion, semanticRecord));
        }
        RecordListBuilder.make();
    }

    public SemanticVersionRecord createSemanticVersionEntity(PBSemanticVersion pbSemanticVersion,
                                                             SemanticRecord semanticEntity) {
        StampEntityVersion stampEntityVersion = createStampEntityVersion(pbSemanticVersion.getStamp());
        MutableList<Object> fieldValues = Lists.mutable.ofInitialCapacity(pbSemanticVersion.getFieldValuesCount());
        for (PBField pbField : pbSemanticVersion.getFieldValuesList()) {

            Object o = createFieldObject(pbField);
            fieldValues.add(o);
        }
        return SemanticVersionRecordBuilder.builder()
                .chronology(semanticEntity)
                .stampNid(stampEntityVersion.stampNid())
                .fieldValues(fieldValues.toImmutable())
                .build();
    }

    public Object createFieldObject(PBField pbField) {
        return switch (pbField.getValueCase()) {
            case BYTESVALUE -> pbField.getBytesValue();
            case INTVALUE -> pbField.getIntValue();
            case FLOATVALUE -> pbField.getFloatValue();
            case BOOLVALUE -> pbField.getBoolValue();
            case STRINGVALUE -> pbField.getStringValue();
            case PUBLICIDVALUE -> createPublicId(pbField.getPublicIdValue());
            case TIMEVALUE -> Instant.ofEpochSecond(pbField.getTimeValue().getSeconds(), pbField.getTimeValue().getNanos());
            case CONCEPTVALUE -> createConceptEntity(pbField.getConceptValue());
            case STAMPVALUE -> createStampEntityVersion(pbField.getStampValue());
//            case DIGRAPHVALUE -> createDigraphDTO(pbField.getDiGraphValue());
//            case GRAPHVALUE -> createGraphDTO(pbField.getGraphValue());
            case VERTEXIDVALUE -> processPBVertexID(pbField.getVertexIdValue());
            case DITREEVALUE -> createDiTreeEntity(pbField.getDiTreeValue());
            case PUBLICIDLISTVALUE -> createPublicIdList(pbField.getPublicIdListValue());
            case VERTEXVALUE -> createVertexDTO(pbField.getVertexValue());
            case PUBLICIDHASHVALUE -> createPublicIDHashList(pbField.getPublicIdListValue().getPublicIdsList());
            case null -> throw new IllegalStateException("PBField value set to null");
            case VALUE_NOT_SET -> throw new IllegalStateException("PBField value not set");
            case default -> throw new IllegalStateException("unknown field type found");
        };
    }

//    private static DiGraphEntity<EntityVertex> createDigraphDTO(PBDiGraph pbDiGraph){
//        pbDiGraph.get
//
//        return new DiGraphDTO(
//                parseRootSequences(pbDiGraph),
//                parsePredecessorAndSuccessorMaps(pbDiGraph.getPredecesorMapList(), pbDiGraph.getPredecesorMapCount()),
//                parseVertices(pbDiGraph.getVertexMapList(), pbDiGraph.getVertexMapCount()),
//                parsePredecessorAndSuccessorMaps(pbDiGraph.getSuccessorMapList(), pbDiGraph.getSuccessorMapCount()));
//    }
//
//    private static GraphDTO createGraphDTO(PBGraph pbGraph){
//        return new GraphDTO(
//                parseVertices(pbGraph.getVertexMapList(), pbGraph.getVertexMapCount()),
//                parsePredecessorAndSuccessorMaps(pbGraph.getSuccessorMapList(), pbGraph.getSuccessorMapCount()));
//    }

    public DiTreeEntity<EntityVertex> createDiTreeEntity(PBDiTree pbDiTree) {
        return new DiTreeEntity<>(
                EntityVertex.make(createVertexDTO(pbDiTree.getRoot())),
                parseVertices(pbDiTree.getVertexMapList(), pbDiTree.getVertexMapCount()),
                parseSuccessors(pbDiTree.getSuccessorMapList()),
                parsePredecessors(pbDiTree.getPredecesorMapList()));
    }

    public ImmutableIntList parseRootSequences(PBDiGraph pbDiGraph) {
        MutableIntList rootSequences = new IntArrayList(pbDiGraph.getRootSequenceCount());
        pbDiGraph.getRootSequenceList().forEach(rootSequences::add);
        return rootSequences.toImmutable();
    }

    public ImmutableIntObjectMap<ImmutableIntList> parseSuccessors(List<PBIntToMultipleIntMap> successorMapList) {
        MutableIntObjectMap<ImmutableIntList> mutableIntObjectMap = new IntObjectHashMap<>();
        successorMapList.forEach(pbIntToMultipleIntMap -> {
            MutableIntList mutableIntList = new IntArrayList();
            pbIntToMultipleIntMap.getTargetList().forEach(mutableIntList::add);
            mutableIntObjectMap.put(pbIntToMultipleIntMap.getSource(), mutableIntList.toImmutable());
        });
        return mutableIntObjectMap.toImmutable();
    }

    public ImmutableIntIntMap parsePredecessors(List<PBIntToIntMap> predecesorMapList) {
        MutableIntIntMap mutableIntIntMap = new IntIntHashMap();
        predecesorMapList.forEach(pbIntToIntMap -> mutableIntIntMap.put(pbIntToIntMap.getSource(), pbIntToIntMap.getTarget()));
        return mutableIntIntMap.toImmutable();
    }

    public ImmutableList<EntityVertex> parseVertices(List<PBVertex> pbVertices, int pbVertexCount) {
        MutableList<EntityVertex> vertexMap = Lists.mutable.ofInitialCapacity(pbVertexCount);
        pbVertices.forEach(pbVertex -> vertexMap.add(EntityVertex.make(createVertexDTO(pbVertex))));
        return vertexMap.toImmutable();
    }

    public VertexDTO createVertexDTO(PBVertex pbVertex) {
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
    }
    }
 */

