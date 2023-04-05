package dev.ikm.tinkar.entity.transfom;

import com.google.protobuf.ByteString;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIdList;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.id.impl.PublicId1;
import dev.ikm.tinkar.common.util.time.DateTimeUtil;
import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import dev.ikm.tinkar.component.Component;
import dev.ikm.tinkar.component.location.PlanarPoint;
import dev.ikm.tinkar.component.location.SpatialPoint;
import dev.ikm.tinkar.dto.ConceptDTO;
import dev.ikm.tinkar.dto.ConceptDTOBuilder;
import dev.ikm.tinkar.dto.graph.VertexDTO;
import dev.ikm.tinkar.dto.graph.VertexDTOBuilder;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.entity.graph.DiGraphEntity;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.schema.*;
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

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class ProtobufTransformer {

    private final List<StampEntity<? extends StampEntityVersion>> stampEntities = new ArrayList<>(); //TODO need to look at his load service

    protected static Entity<? extends EntityVersion> transform(PBTinkarMsg pbTinkarMsg) {
        return switch (pbTinkarMsg.getValueCase()) {
            case CONCEPTCHRONOLOGYVALUE -> transformConceptChronology(pbTinkarMsg.getConceptChronologyValue());
            case SEMANTICCHRONOLOGYVALUE -> transformSemanticChronology(pbTinkarMsg.getSemanticChronologyValue());
            case PATTERNCHRONOLOGYVALUE -> transformPatternChronology(pbTinkarMsg.getPatternChronologyValue());
            case VALUE_NOT_SET -> throw new IllegalStateException("Tinkar message value not set");
            default -> throw new IllegalStateException("Unexpected value: " + pbTinkarMsg.getValueCase());
        };
    }

    public List<StampEntity<? extends StampEntityVersion>> getStampEntities() {
        return stampEntities;
    }

    protected static ConceptEntity<? extends ConceptEntityVersion> transformConceptChronology(PBConceptChronology pbConceptChronology) {
        if(pbConceptChronology.getConceptVersionsCount() == 0){ //TODO - change proto file to say just "Versions"
            throw new RuntimeException("Exception thrown, STAMP Chronology can't contain zero versions");
        }
        RecordListBuilder<ConceptVersionRecord> conceptVersions = RecordListBuilder.make();
        PublicId conceptPublicId = transformPublicId(pbConceptChronology.getPublicId());
        ConceptRecord conceptRecord;

        if (conceptPublicId.uuidCount() > 0) {
            int conceptNid = Entity.nid(conceptPublicId);
            if (conceptPublicId.uuidCount() > 1) {
                conceptRecord = ConceptRecordBuilder.builder()
                        .leastSignificantBits(conceptPublicId.asUuidArray()[0].getLeastSignificantBits())
                        .mostSignificantBits(conceptPublicId.asUuidArray()[0].getMostSignificantBits())
                        .additionalUuidLongs(UuidUtil.asArray(Arrays.copyOfRange(conceptPublicId.asUuidArray(),
                                1, conceptPublicId.uuidCount())))
                        .nid(conceptNid)
                        .versions(conceptVersions)
                        .build();
            } else {
                conceptRecord = ConceptRecordBuilder.builder()
                        .leastSignificantBits(conceptPublicId.asUuidArray()[0].getLeastSignificantBits())
                        .mostSignificantBits(conceptPublicId.asUuidArray()[0].getMostSignificantBits())
                        .nid(conceptNid)
                        .versions(conceptVersions)
                        .build();
            }
        } else {
            throw new IllegalStateException("missing primordial UUID");
        }

        for (PBConceptVersion pbConceptVersion : pbConceptChronology.getConceptVersionsList()) {
            conceptVersions.add(transformConceptVersion(pbConceptVersion, conceptRecord));
        }

        return ConceptRecordBuilder.builder(conceptRecord).versions(conceptVersions).build();
    }

    protected static ConceptVersionRecord transformConceptVersion(PBConceptVersion pbConceptVersion, ConceptRecord concept) {
        return ConceptVersionRecordBuilder.builder()
                .chronology(concept)
                .stampNid(transformStampChronology(pbConceptVersion.getStamp()).nid())
                .build();
    }

    protected static SemanticEntity<? extends SemanticEntityVersion> transformSemanticChronology(PBSemanticChronology pbSemanticChronology){
        if(pbSemanticChronology.getVersionsCount() == 0){
            throw new RuntimeException("Exception thrown, STAMP Chronology can't contain zero versions");
        }

        RecordListBuilder<SemanticVersionRecord> semanticVersions = RecordListBuilder.make();
        PublicId semanticPublicId = transformPublicId(pbSemanticChronology.getPublicId());
        PublicId patternPublicId = transformPublicId(pbSemanticChronology.getPatternForSemantic());
        PublicId referencedComponentPublicId = transformPublicId(pbSemanticChronology.getReferencedComponent());
        SemanticRecord semanticRecord;

        int patternNid = Entity.nid(patternPublicId);
        int referencedComponentNid = Entity.nid(referencedComponentPublicId);
        if (semanticPublicId.uuidCount() > 0) {
            int semanticNid = Entity.nid(semanticPublicId);
            if (semanticPublicId.uuidCount() > 1) {
                semanticRecord = SemanticRecordBuilder.builder()
                        .leastSignificantBits(semanticPublicId.asUuidArray()[0].getLeastSignificantBits())
                        .mostSignificantBits(semanticPublicId.asUuidArray()[0].getMostSignificantBits())
                        .additionalUuidLongs(UuidUtil.asArray(Arrays.copyOfRange(semanticPublicId.asUuidArray(),
                                1, semanticPublicId.uuidCount())))
                        .nid(semanticNid)
                        .patternNid(patternNid)
                        .referencedComponentNid(referencedComponentNid)
                        .versions(semanticVersions)
                        .build();
            } else {
                semanticRecord = SemanticRecordBuilder.builder()
                        .leastSignificantBits(semanticPublicId.asUuidArray()[0].getLeastSignificantBits())
                        .mostSignificantBits(semanticPublicId.asUuidArray()[0].getMostSignificantBits())
                        .nid(semanticNid)
                        .patternNid(patternNid)
                        .referencedComponentNid(referencedComponentNid)
                        .versions(semanticVersions)
                        .build();
            }
        } else {
            throw new IllegalStateException("missing primordial UUID");
        }

        for(PBSemanticVersion pbSemanticVersion : pbSemanticChronology.getVersionsList()){
            semanticVersions.add(transformSemanticVersion(pbSemanticVersion, semanticRecord));
        }

        return SemanticRecordBuilder.builder(semanticRecord).versions(semanticVersions).build();
    }

    //This is creating PBSemanticVersion (line 314 Tinkar.proto)
    protected static SemanticVersionRecord transformSemanticVersion(PBSemanticVersion pbSemanticVersion, SemanticRecord semantic){
        MutableList<Object> fieldValues = Lists.mutable.ofInitialCapacity(pbSemanticVersion.getFieldValuesCount());
        for(PBField pbField : pbSemanticVersion.getFieldValuesList()){
            fieldValues.add(transformField(pbField));
        }
        return SemanticVersionRecordBuilder.builder()
                .chronology(semantic)
                .stampNid(transformStampChronology(pbSemanticVersion.getStamp()).nid())
                .fieldValues(fieldValues.toImmutable())
                .build();
    }

    //Pattern Transformation
    //This is creating a PatternChronology (line 270 Tinkar.proto)
    protected static PatternEntity<? extends PatternEntityVersion> transformPatternChronology(PBPatternChronology pbPatternChronology){
        if(pbPatternChronology.getVersionsCount() == 0){
            throw new RuntimeException("Exception thrown, STAMP Chronology can't contain zero versions");
        }

        RecordListBuilder<PatternVersionRecord> patternVersions = RecordListBuilder.make();
        PublicId patternPublicId = transformPublicId(pbPatternChronology.getPublicId());
        PatternRecord patternRecord;

        if (patternPublicId.uuidCount() > 0) {
            if (patternPublicId.uuidCount() > 1) {
                patternRecord = PatternRecordBuilder.builder()
                        .leastSignificantBits(patternPublicId.asUuidArray()[0].getLeastSignificantBits())
                        .mostSignificantBits(patternPublicId.asUuidArray()[0].getMostSignificantBits())
                        .nid(Entity.nid(patternPublicId))
                        .additionalUuidLongs(UuidUtil.asArray(Arrays.copyOfRange(patternPublicId.asUuidArray(),
                                1, patternPublicId.uuidCount())))
                        .versions(patternVersions)
                        .build();
            } else {
                patternRecord = PatternRecordBuilder.builder()
                        .leastSignificantBits(patternPublicId.asUuidArray()[0].getLeastSignificantBits())
                        .mostSignificantBits(patternPublicId.asUuidArray()[0].getMostSignificantBits())
                        .nid(Entity.nid(patternPublicId))
                        .versions(patternVersions)
                        .build();
            }
        } else {
            throw new IllegalStateException("missing primordial UUID");
        }

        for(PBPatternVersion pbPatternVersion : pbPatternChronology.getVersionsList()){
            patternVersions.add(transformPatternVersion(pbPatternVersion, patternRecord));
        }

        return PatternRecordBuilder.builder(patternRecord).versions(patternVersions).build();
    }

    protected static PatternVersionRecord transformPatternVersion(PBPatternVersion pbPatternVersion, PatternRecord pattern){
        MutableList<FieldDefinitionRecord> fieldDefinition = Lists.mutable
                .withInitialCapacity(pbPatternVersion.getFieldDefinitionsCount());
        //TODO: Is this a proper way to grab NID?
        int patternNid = pattern.nid();
        int patternStampNid = transformStampChronology(pbPatternVersion.getStamp()).nid();
        int semanticPurposeNid = Entity.nid(transformPublicId(pbPatternVersion.getReferencedComponentPurpose()));
        int semanticMeaningNid = Entity.nid(transformPublicId(pbPatternVersion.getReferencedComponentMeaning()));
        for(PBFieldDefinition pbFieldDefinition : pbPatternVersion.getFieldDefinitionsList()){
            fieldDefinition.add(transformFieldDefinitionRecord(pbFieldDefinition, patternStampNid, patternNid));
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
    //This is creating PBStampChronology (line 209 Tinkar.proto)
    public static StampRecord transformStampChronology(PBStampChronology pbStampChronology){
        if(pbStampChronology.getStampVersionsList().size() == 0){ //TODO - change proto file to say just "Versions"
            throw new RuntimeException("Exception thrown, STAMP Chronology can't contain zero versions");
        }

        RecordListBuilder<StampVersionRecord> stampVersions = RecordListBuilder.make();
        PublicId stampPublicId = transformPublicId(pbStampChronology.getPublicId());
        StampRecord stampRecord;

        if (stampPublicId.uuidCount() > 0) {
            int conceptNid = Entity.nid(stampPublicId);
            if (stampPublicId.uuidCount() > 1) {
                stampRecord = StampRecordBuilder.builder()
                        .leastSignificantBits(stampPublicId.asUuidArray()[0].getLeastSignificantBits())
                        .mostSignificantBits(stampPublicId.asUuidArray()[0].getMostSignificantBits())
                        .additionalUuidLongs(UuidUtil.asArray(Arrays.copyOfRange(stampPublicId.asUuidArray(),
                                1, stampPublicId.uuidCount())))
                        .nid(conceptNid)
                        .versions(stampVersions)
                        .build();
            } else {
                stampRecord = StampRecordBuilder.builder()
                        .leastSignificantBits(stampPublicId.asUuidArray()[0].getLeastSignificantBits())
                        .mostSignificantBits(stampPublicId.asUuidArray()[0].getMostSignificantBits())
                        .nid(conceptNid)
                        .versions(stampVersions)
                        .build();
            }
        } else {
            throw new IllegalStateException("missing primordial UUID");
        }

        for(PBStampVersion pbStampVersion : pbStampChronology.getStampVersionsList()){
            stampVersions.add(transformStampVersion(pbStampVersion, stampRecord));
        }

        //      stampEntities.add(stampEntity); TODO: remove this.stampEntities call
        return StampRecordBuilder.builder(stampRecord).versions(stampVersions).build();
    }

    protected static StampVersionRecord transformStampVersion(PBStampVersion pbStampVersion, StampRecord stampRecord){
        return StampVersionRecordBuilder.builder()
                .chronology(stampRecord)
                .stateNid(Entity.nid(transformPublicId(pbStampVersion.getStatus())))
                .time(Instant.ofEpochSecond(pbStampVersion.getTime().getSeconds()).getEpochSecond())
                .authorNid(Entity.nid(transformPublicId(pbStampVersion.getAuthor())))
                .moduleNid(Entity.nid(transformPublicId(pbStampVersion.getModule())))
                .pathNid(Entity.nid(transformPublicId(pbStampVersion.getPath())))
                .build();
    }

    //Field Definition Transformation
    //This creates PBFieldDefinition (line 256 in Tinkar.proto)
    protected static FieldDefinitionRecord transformFieldDefinitionRecord(PBFieldDefinition pbFieldDefinition,
                                                                          int patternVersionStampNid, int patternNid) {
        int meaningNid = Entity.nid(transformPublicId(pbFieldDefinition.getMeaning()));
        int purposeNid = Entity.nid(transformPublicId(pbFieldDefinition.getPurpose()));
        int dataTypeNid = Entity.nid(transformPublicId(pbFieldDefinition.getDataType()));

        return FieldDefinitionRecordBuilder.builder()
                .meaningNid(meaningNid)
                .purposeNid(purposeNid)
                .dataTypeNid(dataTypeNid)
                .patternVersionStampNid(patternVersionStampNid)
                .patternNid(patternNid)
                .build();
    }

    //Field Transformation
    //TODO: Use generics in transformField class rather than returning an object
    protected static Object transformField(PBField pbField){
        return switch (pbField.getValueCase()){
            case BOOLVALUE -> pbField.getBoolValue();
            case BYTESVALUE -> pbField.getBytesValue().toByteArray();
            //TODO is the transformDigraphDTO still a DTO?
            case DIGRAPHVALUE -> transformDigraph(pbField.getDiGraphValue());
            case DITREEVALUE -> transformDiTreeEntity(pbField.getDiTreeValue());
            case FLOATVALUE -> pbField.getFloatValue();
            //TODO: A Graph Entity needs to be created here
            case GRAPHVALUE -> throw new UnsupportedOperationException("createGraphEntity not implemented");
            case INTVALUE -> pbField.getIntValue();
            case PUBLICIDVALUE -> transformPublicId(pbField.getPublicIdValue());
            case PUBLICIDLISTVALUE -> transformPublicIdList(pbField.getPublicIdListValue());
            case STAMPVALUE -> transformStampChronology(pbField.getStampValue());
            case STRINGVALUE -> pbField.getStringValue();
            case TIMEVALUE -> DateTimeUtil.epochMsToInstant(pbField.getTimeValue().getSeconds());
            case PLANARPOINT -> transformPlanarPoint(pbField.getPlanarPoint());
            case SPATIALPOINT -> transformSpatialPoint(pbField.getSpatialPoint());
            case PBINTTOINTMAP -> parsePredecessors(Collections.singletonList(pbField.getPBIntToIntMap()));
            case PBINTTOMULTIPLEINTMAP -> parseSuccessors(Collections.singletonList(pbField.getPBIntToMultipleIntMap()));
            case VALUE_NOT_SET -> throw new IllegalStateException("PBField value not set");
            case VERTEXIDVALUE -> transformVertexId(pbField.getVertexIdValue());
            case VERTEXVALUE -> transformVertexEntity(pbField.getVertexValue());
        };
    }

    protected static PublicId transformPublicId(PBPublicId pbPublicId){
        if (pbPublicId.getIdCount() == 0){
            throw new RuntimeException("Exception thrown, empty Public ID is present.");
        }
        return PublicIds.of(pbPublicId.getIdList().stream()
                .map(ByteString::toByteArray)
                .map(ByteBuffer::wrap)
                .map(byteBuffer -> new UUID(byteBuffer.getLong(), byteBuffer.getLong()))
                .collect(Collectors.toList()));
    }

    protected static PublicIdList transformPublicIdList(PBPublicIdList pbPublicIdList) {
        final PublicId[] publicIds = new PublicId[pbPublicIdList.getPublicIdsCount()];
        for (int i = 0; i < pbPublicIdList.getPublicIdsCount(); i++) {
            publicIds[i] = transformPublicId(pbPublicIdList.getPublicIds(i));
        }
        return PublicIds.list.of(publicIds);
    }

    protected static PublicId1 transformVertexId(PBVertexId pbVertexId) {
        //TODO: Verify this is correct.
        return new PublicId1(UUID.nameUUIDFromBytes(pbVertexId.getId().toByteArray()));
    }

    protected static DiGraphEntity<EntityVertex> transformDigraph(PBDiGraph pbDiGraph){
        //pbDiGraph.get
        List<PBIntToMultipleIntMap> PredecessorMapList = pbDiGraph.getPredecesorMapList();
        List<PBIntToMultipleIntMap> SuccessorMapList = pbDiGraph.getPredecesorMapList();

        return new DiGraphEntity<EntityVertex>(
                (ImmutableList<EntityVertex>) parseRootSequences(pbDiGraph),
                parseVertices(pbDiGraph.getVertexMapList(), pbDiGraph.getVertexMapCount()),
                parsePredecessorAndSuccessorMaps(SuccessorMapList, pbDiGraph.getSuccessorMapCount()),
                parsePredecessorAndSuccessorMaps(PredecessorMapList, pbDiGraph.getPredecesorMapCount()));
    }

    //TODO: Created and need to get more context to finish. This the same as Successor parse atm.
    protected static ImmutableIntObjectMap<ImmutableIntList> parsePredecessorAndSuccessorMaps(List<PBIntToMultipleIntMap> predecessorSuccessorMapList, int predecesorSuccessorMapCount) {
        MutableIntObjectMap<ImmutableIntList> mutableIntObjectMap = new IntObjectHashMap<>();
        predecessorSuccessorMapList.forEach(pbIntToMultipleIntMap -> {
            MutableIntList mutableIntList = new IntArrayList();
            pbIntToMultipleIntMap.getTargetList().forEach(mutableIntList::add);
            mutableIntObjectMap.put(pbIntToMultipleIntMap.getSource(), mutableIntList.toImmutable());
        });
        return mutableIntObjectMap.toImmutable();
    }
    //TODO: Graph Entity does not exist.
    //    private GraphDTO createGraphDTO(PBGraph pbGraph){
    //        return new GraphDTO(
    //                parseVertices(pbGraph.getVertexMapList(), pbGraph.getVertexMapCount()),
    //                parsePredecessorAndSuccessorMaps(pbGraph.getSuccessorMapList(), pbGraph.getSuccessorMapCount()));
    //    }

    protected static DiTreeEntity transformDiTreeEntity(PBDiTree pbDiTree) {
        return new DiTreeEntity(
                //                EntityVertex.make(createVertexDTO(pbDiTree.getRoot())),
                EntityVertex.make(transformVertexEntity(pbDiTree.getVertexMap(pbDiTree.getRoot()))),
                parseVertices(pbDiTree.getVertexMapList(), pbDiTree.getVertexMapCount()),
                parseSuccessors(pbDiTree.getSuccessorMapList()),
                parsePredecessors(pbDiTree.getPredecesorMapList()));
    }

    protected static ImmutableIntList parseRootSequences(PBDiGraph pbDiGraph) {
        MutableIntList rootSequences = new IntArrayList(pbDiGraph.getRootSequenceCount());
        pbDiGraph.getRootSequenceList().forEach(rootSequences::add);
        return rootSequences.toImmutable();
    }

    protected static ImmutableIntObjectMap<ImmutableIntList> parseSuccessors(List<PBIntToMultipleIntMap> successorMapList) {
        MutableIntObjectMap<ImmutableIntList> mutableIntObjectMap = new IntObjectHashMap<>();
        successorMapList.forEach(pbIntToMultipleIntMap -> {
            MutableIntList mutableIntList = new IntArrayList();
            pbIntToMultipleIntMap.getTargetList().forEach(mutableIntList::add);
            mutableIntObjectMap.put(pbIntToMultipleIntMap.getSource(), mutableIntList.toImmutable());
        });
        return mutableIntObjectMap.toImmutable();
    }

    protected static ImmutableIntIntMap parsePredecessors(List<PBIntToIntMap> predecesorMapList) {
        MutableIntIntMap mutableIntIntMap = new IntIntHashMap();
        predecesorMapList.forEach(pbIntToIntMap -> mutableIntIntMap.put(pbIntToIntMap.getSource(), pbIntToIntMap.getTarget()));
        return mutableIntIntMap.toImmutable();
    }

    protected static ImmutableList<EntityVertex> parseVertices(List<PBVertex> pbVertices, int pbVertexCount) {
        MutableList<EntityVertex> vertexMap = Lists.mutable.ofInitialCapacity(pbVertexCount);
        pbVertices.forEach(pbVertex -> vertexMap.add(EntityVertex.make(transformVertexEntity(pbVertex))));
        return vertexMap.toImmutable();
    }

    //    private VertexDTO createVertexDTO(PBVertex pbVertex) {
    //        PublicId1 vertexID = processPBVertexID(pbVertex.getVertexId());
    //        MutableMap<ConceptDTO, Object> properties = Maps.mutable.ofInitialCapacity(pbVertex.getPropertiesCount());
    //        pbVertex.getPropertiesList().forEach(property -> properties.put(
    //                ConceptDTOBuilder.builder()
    //                        .publicId(transformPublicId(pbVertex.getMeaning().getDefaultInstanceForType()))
    ////                        .build(), createFieldObject(property.getValue())));
    //                        .build(), transformField(property.getValue())));
    //        return VertexDTOBuilder.builder()
    //                .vertexIdLsb(vertexID.leastSignificantBits())
    //                .vertexIdMsb(vertexID.mostSignificantBits())
    //                .vertexIndex(pbVertex.getVertexIndex())
    //                .meaning(ConceptDTOBuilder.builder()
    //                        .publicId(transformPublicId(pbVertex.getMeaning().getDefaultInstanceForType()))
    //                        .build())
    //                .properties(properties.toImmutable())
    //                .build();
    //    }

    protected static EntityVertex transformVertexEntity(PBVertex pbVertex){
        PublicId1 vertexID = processPBVertexID(pbVertex.getVertexId());
        MutableMap<ConceptDTO, Object> properties = Maps.mutable.ofInitialCapacity(pbVertex.getPropertiesCount());
        pbVertex.getPropertiesList().forEach(property -> properties.put(
                ConceptDTOBuilder.builder()
                        .publicId(transformPublicId(pbVertex.getMeaning().getDefaultInstanceForType()))
                        .build(), transformField(property.getValue())));
        var storedVertexDTO = VertexDTOBuilder.builder()
                .vertexIdLsb(vertexID.leastSignificantBits())
                .vertexIdMsb(vertexID.mostSignificantBits())
                .vertexIndex(pbVertex.getVertexIndex())
                .meaning(ConceptDTOBuilder.builder()
                        //Took out .getPublicId()
                        .publicId(transformPublicId(pbVertex.getMeaning().getDefaultInstanceForType()))
                        .build())
                .properties(properties.toImmutable())
                .build();
        var genEntityVertex = EntityVertex.make(storedVertexDTO);
        return genEntityVertex;
    }

    protected static PublicId1 processPBVertexID(PBVertexId vertexId) {
        return new PublicId1(UUID.nameUUIDFromBytes(vertexId.toByteArray()));
    }

    protected int testMockEntityService(Component component){
        return Entity.nid(component);
    }
    protected static PlanarPoint transformPlanarPoint(PBPlanarPoint planarPoint){
        return new PlanarPoint(
                planarPoint.getX(),
                planarPoint.getY()
        );
    }

    protected static SpatialPoint transformSpatialPoint(PBSpatialPoint spatialPoint){
        return new SpatialPoint(
                spatialPoint.getX(),
                spatialPoint.getY(),
                spatialPoint.getZ()
        );
    }

}
