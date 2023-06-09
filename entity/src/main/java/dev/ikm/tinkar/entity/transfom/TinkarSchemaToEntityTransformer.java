package dev.ikm.tinkar.entity.transfom;

import com.google.protobuf.ByteString;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIdList;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.id.impl.PublicId1;
import dev.ikm.tinkar.common.util.time.DateTimeUtil;
import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import dev.ikm.tinkar.component.Component;
import dev.ikm.tinkar.component.Concept;
import dev.ikm.tinkar.component.location.PlanarPoint;
import dev.ikm.tinkar.component.location.SpatialPoint;
import dev.ikm.tinkar.dto.ConceptDTO;
import dev.ikm.tinkar.dto.ConceptDTOBuilder;
import dev.ikm.tinkar.dto.graph.VertexDTOBuilder;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.entity.graph.DiGraphEntity;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.schema.*;
import dev.ikm.tinkar.schema.Field;
import dev.ikm.tinkar.schema.StampVersion;
import dev.ikm.tinkar.terms.EntityProxy;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TinkarSchemaToEntityTransformer {

    private static TinkarSchemaToEntityTransformer INSTANCE;

    private TinkarSchemaToEntityTransformer() {
    }

    public static TinkarSchemaToEntityTransformer getInstance() {
        if (INSTANCE == null) {
            synchronized (TinkarSchemaToEntityTransformer.class) {
                if (INSTANCE == null) {
                    INSTANCE = new TinkarSchemaToEntityTransformer();
                }
            }
        }
        return INSTANCE;
    }

    public void transform(TinkarMsg pbTinkarMsg, Consumer<Entity<? extends EntityVersion>> entityConsumer, Consumer<StampEntity<StampEntityVersion>> stampEntityConsumer) {
        Entity entity = switch (pbTinkarMsg.getValueCase()) {
            case CONCEPT_CHRONOLOGY -> transformConceptChronology(pbTinkarMsg.getConceptChronology(), stampEntityConsumer);
            case SEMANTIC_CHRONOLOGY -> transformSemanticChronology(pbTinkarMsg.getSemanticChronology(), stampEntityConsumer);
            case PATTERN_CHRONOLOGY -> transformPatternChronology(pbTinkarMsg.getPatternChronology(), stampEntityConsumer);
            case VALUE_NOT_SET -> throw new IllegalStateException("Tinkar message value not set");
            default -> throw new IllegalStateException("Unexpected value: " + pbTinkarMsg.getValueCase());
        };
        if(entityConsumer != null) {
            entityConsumer.accept(entity);
        }
    }

        protected ConceptEntity<? extends ConceptEntityVersion> transformConceptChronology(ConceptChronology pbConceptChronology) {
            return transformConceptChronology(pbConceptChronology, null);
        }
        protected ConceptEntity<? extends ConceptEntityVersion> transformConceptChronology(ConceptChronology pbConceptChronology, Consumer<StampEntity<StampEntityVersion>> stampEntityConsumer) {
        if(pbConceptChronology.getVersionsCount() == 0){
            throw new RuntimeException("Exception thrown, Concept Chronology can't contain zero versions");
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

        for (ConceptVersion pbConceptVersion : pbConceptChronology.getVersionsList()) {
            conceptVersions.add(transformConceptVersion(pbConceptVersion, conceptRecord, stampEntityConsumer));
        }

        return ConceptRecordBuilder.builder(conceptRecord).versions(conceptVersions).build();
    }

    protected ConceptVersionRecord transformConceptVersion(ConceptVersion pbConceptVersion, ConceptRecord concept, Consumer<StampEntity<StampEntityVersion>> stampEntityConsumer) {
        return ConceptVersionRecordBuilder.builder()
                .chronology(concept)
                .stampNid(transformStampChronology(pbConceptVersion.getStamp(), stampEntityConsumer).nid())
                .build();
    }

    protected SemanticEntity<? extends SemanticEntityVersion> transformSemanticChronology(SemanticChronology pbSemanticChronology){
        return transformSemanticChronology(pbSemanticChronology, null);
    }

    protected SemanticEntity<? extends SemanticEntityVersion> transformSemanticChronology(SemanticChronology pbSemanticChronology, Consumer<StampEntity<StampEntityVersion>> stampEntityConsumer){
        if(pbSemanticChronology.getVersionsCount() == 0){
            throw new RuntimeException("Exception thrown, Semantic Chronology can't contain zero versions");
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

        for(SemanticVersion pbSemanticVersion : pbSemanticChronology.getVersionsList()){
            semanticVersions.add(transformSemanticVersion(pbSemanticVersion, semanticRecord, stampEntityConsumer));
        }

        return SemanticRecordBuilder.builder(semanticRecord).versions(semanticVersions).build();
    }

    //This is creating PBSemanticVersion (line 314 Tinkar.proto)
    protected SemanticVersionRecord transformSemanticVersion(SemanticVersion pbSemanticVersion, SemanticRecord semantic, Consumer<StampEntity<StampEntityVersion>> stampEntityConsumer){
        MutableList<Object> fieldValues = Lists.mutable.ofInitialCapacity(pbSemanticVersion.getFieldsCount());
        for(Field pbField : pbSemanticVersion.getFieldsList()){
            Object transformedObject = null;
            if(pbField.hasPublicId()){
                Concept concept = EntityProxy.Concept.make((PublicId) transformField(pbField, stampEntityConsumer));
                transformedObject = EntityRecordFactory.externalToInternalObject(concept);
            } else{
                transformedObject = EntityRecordFactory.externalToInternalObject(transformField(pbField, stampEntityConsumer));
            }
            fieldValues.add(transformedObject);
        }
        return SemanticVersionRecordBuilder.builder()
                .chronology(semantic)
                //TODO: Need to add the stamp consumer here
                .stampNid(transformStampChronology(pbSemanticVersion.getStamp(), stampEntityConsumer).nid())
                .fieldValues(fieldValues.toImmutable())
                .build();
    }

    protected PatternEntity<? extends PatternEntityVersion> transformPatternChronology(PatternChronology pbPatternChronology) {
        return transformPatternChronology(pbPatternChronology,null);
    }

        //Pattern Transformation
    //This is creating a PatternChronology (line 270 Tinkar.proto)
    protected PatternEntity<? extends PatternEntityVersion> transformPatternChronology(PatternChronology pbPatternChronology, Consumer<StampEntity<StampEntityVersion>> stampEntityConsumer){
        if(pbPatternChronology.getVersionsCount() == 0){
            throw new RuntimeException("Exception thrown, Pattern Chronology can't contain zero versions");
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

        for(PatternVersion pbPatternVersion : pbPatternChronology.getVersionsList()){
            patternVersions.add(transformPatternVersion(pbPatternVersion, patternRecord, stampEntityConsumer));
        }

        return PatternRecordBuilder.builder(patternRecord).versions(patternVersions).build();
    }

    protected PatternVersionRecord transformPatternVersion(PatternVersion pbPatternVersion, PatternRecord pattern, Consumer<StampEntity<StampEntityVersion>> stampEntityConsumer){
        MutableList<FieldDefinitionRecord> fieldDefinition = Lists.mutable
                .withInitialCapacity(pbPatternVersion.getFieldDefinitionsCount());
        //TODO: Is this a proper way to grab NID?
        int patternNid = pattern.nid();
        int patternStampNid = transformStampChronology(pbPatternVersion.getStamp(), stampEntityConsumer).nid();
        int semanticPurposeNid = Entity.nid(transformPublicId(pbPatternVersion.getReferencedComponentPurpose()));
        int semanticMeaningNid = Entity.nid(transformPublicId(pbPatternVersion.getReferencedComponentMeaning()));
        for(FieldDefinition pbFieldDefinition : pbPatternVersion.getFieldDefinitionsList()){
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
    public StampRecord transformStampChronology(StampChronology pbStampChronology,Consumer<StampEntity<StampEntityVersion>> stampEntityConsumer){
        if(pbStampChronology.getVersionsList().size() == 0){
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

        for(StampVersion pbStampVersion : pbStampChronology.getVersionsList()){
            stampVersions.add(transformStampVersion(pbStampVersion, stampRecord));
        }

        //      stampEntities.add(stampEntity); TODO: remove this.stampEntities call
        StampEntity<? extends StampEntityVersion> stampEntity = StampRecordBuilder.builder(stampRecord).versions(stampVersions).build();
        if(stampEntityConsumer != null){
            stampEntityConsumer.accept((StampEntity<StampEntityVersion>) stampEntity);
        }
        return (StampRecord) stampEntity;
    }

    protected StampVersionRecord transformStampVersion(StampVersion pbStampVersion, StampRecord stampRecord){
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
    protected FieldDefinitionRecord transformFieldDefinitionRecord(FieldDefinition pbFieldDefinition,
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

    protected Object transformField(Field pbField){
        return transformField(pbField, null);
    }
    //Field Transformation
    //TODO: Use generics in transformField class rather than returning an object
    protected Object transformField(Field pbField, Consumer<StampEntity<StampEntityVersion>> stampEntityConsumer){
        return switch (pbField.getValueCase()){
            case BOOL -> pbField.getBool();
            case BYTES -> pbField.getBytes().toByteArray();
            case FLOAT -> pbField.getFloat();
            case INT -> pbField.getInt();
            case TIME -> DateTimeUtil.epochMsToInstant(pbField.getTime().getSeconds());
            case STRING -> pbField.getString();
            case PLANAR_POINT -> transformPlanarPoint(pbField.getPlanarPoint());
            case SPATIAL_POINT -> transformSpatialPoint(pbField.getSpatialPoint());
            case DI_GRAPH -> transformDigraph(pbField.getDiGraph(), stampEntityConsumer);
            case DI_TREE -> transformDiTreeEntity(pbField.getDiTree(), stampEntityConsumer);
            //TODO: A Graph Entity needs to be created here
            case GRAPH -> throw new UnsupportedOperationException("createGraphEntity not implemented");
            case PUBLIC_ID -> transformPublicId(pbField.getPublicId());
            case PUBLIC_ID_LIST -> transformPublicIdList(pbField.getPublicIdList());
            case STAMP -> transformStampChronology(pbField.getStamp(), stampEntityConsumer);
            case INT_TO_INT_MAP -> parsePredecessors(Collections.singletonList(pbField.getIntToIntMap()));
            case INT_TO_MULTIPLE_INT_MAP -> parseSuccessors(Collections.singletonList(pbField.getIntToMultipleIntMap()));
            case VALUE_NOT_SET -> throw new IllegalStateException("PBField value not set");
            case VERTEX_ID -> transformVertexId(pbField.getVertexId());
            case VERTEX  -> transformVertexEntity(pbField.getVertex(), stampEntityConsumer);
        };
    }

    protected PublicId transformPublicId(dev.ikm.tinkar.schema.PublicId pbPublicId){
        if (pbPublicId.getIdCount() == 0){
            throw new RuntimeException("Exception thrown, empty Public ID is present.");
        }
        return PublicIds.of(pbPublicId.getIdList().stream()
                .map(ByteString::toByteArray)
                .map(ByteBuffer::wrap)
                .map(byteBuffer -> new UUID(byteBuffer.getLong(), byteBuffer.getLong()))
                .collect(Collectors.toList()));
    }

    protected PublicIdList transformPublicIdList(dev.ikm.tinkar.schema.PublicIdList pbPublicIdList) {
        PublicId[] publicIds = new PublicId[pbPublicIdList.getPublicIdsCount()];
        for(int i = 0; i < pbPublicIdList.getPublicIdsCount(); i++) {
            publicIds[i] = transformPublicId(pbPublicIdList.getPublicIds(i));
        }
        return PublicIds.list.of(publicIds);
    }

    protected PublicId1 transformVertexId(VertexId pbVertexId) {
        return new PublicId1(UUID.nameUUIDFromBytes(pbVertexId.getId().toByteArray()));
    }

    protected DiGraphEntity<EntityVertex> transformDigraph(DiGraph pbDiGraph, Consumer<StampEntity<StampEntityVersion>> stampEntityConsumer){
        //pbDiGraph.get
        List<IntToMultipleIntMap> PredecessorMapList = pbDiGraph.getPredecesorMapList();
        List<IntToMultipleIntMap> SuccessorMapList = pbDiGraph.getPredecesorMapList();

        return new DiGraphEntity<EntityVertex>(
                (ImmutableList<EntityVertex>) parseRootSequences(pbDiGraph),
                parseVertices(pbDiGraph.getVertexMapList(), pbDiGraph.getVertexMapCount(), stampEntityConsumer),
                parsePredecessorAndSuccessorMaps(SuccessorMapList, pbDiGraph.getSuccessorMapCount()),
                parsePredecessorAndSuccessorMaps(PredecessorMapList, pbDiGraph.getPredecesorMapCount()));
    }

    //TODO: Created and need to get more context to finish. This the same as Successor parse atm.
    protected ImmutableIntObjectMap<ImmutableIntList> parsePredecessorAndSuccessorMaps(List<IntToMultipleIntMap> predecessorSuccessorMapList, int predecesorSuccessorMapCount) {
        MutableIntObjectMap<ImmutableIntList> mutableIntObjectMap = new IntObjectHashMap<>();
        predecessorSuccessorMapList.forEach(pbIntToMultipleIntMap -> {
            MutableIntList mutableIntList = new IntArrayList();
            pbIntToMultipleIntMap.getTargetList().forEach(mutableIntList::add);
            mutableIntObjectMap.put(pbIntToMultipleIntMap.getSource(), mutableIntList.toImmutable());
        });
        return mutableIntObjectMap.toImmutable();
    }

    protected DiTreeEntity transformDiTreeEntity(DiTree pbDiTree, Consumer<StampEntity<StampEntityVersion>> stampEntityConsumer) {
        return new DiTreeEntity(
                EntityVertex.make(transformVertexEntity(pbDiTree.getVertexMap(pbDiTree.getRoot()), stampEntityConsumer)),
                parseVertices(pbDiTree.getVertexMapList(), pbDiTree.getVertexMapCount(), stampEntityConsumer),
                parseSuccessors(pbDiTree.getSuccessorMapList()),
                parsePredecessors(pbDiTree.getPredecesorMapList()));
    }

    protected ImmutableIntList parseRootSequences(DiGraph pbDiGraph) {
        MutableIntList rootSequences = new IntArrayList(pbDiGraph.getRootSequenceCount());
        pbDiGraph.getRootSequenceList().forEach(rootSequences::add);
        return rootSequences.toImmutable();
    }

    protected ImmutableIntObjectMap<ImmutableIntList> parseSuccessors(List<IntToMultipleIntMap> successorMapList) {
        MutableIntObjectMap<ImmutableIntList> mutableIntObjectMap = new IntObjectHashMap<>();
        successorMapList.forEach(pbIntToMultipleIntMap -> {
            MutableIntList mutableIntList = new IntArrayList();
            pbIntToMultipleIntMap.getTargetList().forEach(mutableIntList::add);
            mutableIntObjectMap.put(pbIntToMultipleIntMap.getSource(), mutableIntList.toImmutable());
        });
        return mutableIntObjectMap.toImmutable();
    }

    protected static ImmutableIntIntMap parsePredecessors(List<IntToIntMap> predecesorMapList) {
        MutableIntIntMap mutableIntIntMap = new IntIntHashMap();
        predecesorMapList.forEach(pbIntToIntMap -> mutableIntIntMap.put(pbIntToIntMap.getSource(), pbIntToIntMap.getTarget()));
        return mutableIntIntMap.toImmutable();
    }

    protected ImmutableList<EntityVertex> parseVertices(List<Vertex> pbVertices, int pbVertexCount, Consumer<StampEntity<StampEntityVersion>> stampEntityConsumer) {
        MutableList<EntityVertex> vertexMap = Lists.mutable.ofInitialCapacity(pbVertexCount);
        pbVertices.forEach(pbVertex -> vertexMap.add(EntityVertex.make(transformVertexEntity(pbVertex, stampEntityConsumer))));
        return vertexMap.toImmutable();
    }

    protected EntityVertex transformVertexEntity(Vertex pbVertex, Consumer<StampEntity<StampEntityVersion>> stampEntityConsumer){
        PublicId1 vertexID = processPBVertexID(pbVertex.getVertexId());
        MutableMap<ConceptDTO, Object> properties = Maps.mutable.ofInitialCapacity(pbVertex.getPropertiesCount());
        pbVertex.getPropertiesList().forEach(property -> properties.put(
                ConceptDTOBuilder.builder()
                        .publicId(transformPublicId(pbVertex.getMeaning()))
                        .build(), transformField(property.getField(), stampEntityConsumer)));
        var storedVertexDTO = VertexDTOBuilder.builder()
                .vertexIdLsb(vertexID.leastSignificantBits())
                .vertexIdMsb(vertexID.mostSignificantBits())
                .vertexIndex(pbVertex.getVertexIndex())
                .meaning(ConceptDTOBuilder.builder()
                        .publicId(transformPublicId(pbVertex.getMeaning()))
                        .build())
                .properties(properties.toImmutable())
                .build();
        var genEntityVertex = EntityVertex.make(storedVertexDTO);
        return genEntityVertex;
    }

    protected PublicId1 processPBVertexID(VertexId vertexId) {
        return new PublicId1(UUID.nameUUIDFromBytes(vertexId.toByteArray()));
    }

    protected int testMockEntityService(Component component){
        return Entity.nid(component);
    }
    protected PlanarPoint transformPlanarPoint(dev.ikm.tinkar.schema.PlanarPoint planarPoint){
        return new PlanarPoint(
                planarPoint.getX(),
                planarPoint.getY()
        );
    }

    protected SpatialPoint transformSpatialPoint(dev.ikm.tinkar.schema.SpatialPoint spatialPoint){
        return new SpatialPoint(
                spatialPoint.getX(),
                spatialPoint.getY(),
                spatialPoint.getZ()
        );
    }
}
