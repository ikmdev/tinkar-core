package dev.ikm.tinkar.entity.transfom;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import dev.ikm.tinkar.common.id.*;
import dev.ikm.tinkar.common.id.PublicIdList;
import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import dev.ikm.tinkar.component.*;
import dev.ikm.tinkar.component.graph.DiGraph;
import dev.ikm.tinkar.component.graph.DiTree;
import dev.ikm.tinkar.component.graph.Vertex;
import dev.ikm.tinkar.component.location.PlanarPoint;
import dev.ikm.tinkar.component.location.SpatialPoint;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.entity.graph.DiGraphEntity;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.schema.*;
import dev.ikm.tinkar.schema.ConceptChronology;
import dev.ikm.tinkar.schema.ConceptVersion;
import dev.ikm.tinkar.schema.Field;
import dev.ikm.tinkar.schema.FieldDefinition;
import dev.ikm.tinkar.schema.PatternChronology;
import dev.ikm.tinkar.schema.PatternVersion;
import dev.ikm.tinkar.schema.PublicId;
import dev.ikm.tinkar.schema.SemanticChronology;
import dev.ikm.tinkar.schema.SemanticVersion;
import dev.ikm.tinkar.schema.StampVersion;
import dev.ikm.tinkar.schema.VertexId;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.map.primitive.ImmutableIntIntMap;
import org.eclipse.collections.api.map.primitive.ImmutableIntObjectMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The entityTransformer class is responsible for transformer a entity of a certain data type to a
 * Protobuf message object.
 */
public class EntityToTinkarSchemaTransformer {

    private final AtomicInteger conceptCount = new AtomicInteger();
    private final AtomicInteger semanticCount = new AtomicInteger();
    private final AtomicInteger patternCount = new AtomicInteger();

    private final Logger LOG = LoggerFactory.getLogger(EntityToTinkarSchemaTransformer.class);
    private static EntityToTinkarSchemaTransformer INSTANCE;
    private List<Flow.Subscriber<? super TinkarMsg>> subscribers = new ArrayList<>();

    private EntityToTinkarSchemaTransformer(){
    }

    public static EntityToTinkarSchemaTransformer getInstance(){
        if(INSTANCE == null){
            synchronized (EntityToTinkarSchemaTransformer.class){
                if (INSTANCE == null){
                    INSTANCE = new EntityToTinkarSchemaTransformer();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * This method takes in an entity and is matched on its entity type based on the type of message. It is then transformed into a PB message.
     * @param entity
     * @return a Protobuf message of entity data type.
     */
    public TinkarMsg transform(Entity entity){
        return switch (entity.entityDataType()){
            case CONCEPT_CHRONOLOGY -> createPBConceptChronology((ConceptEntity<ConceptEntityVersion>) entity);
            case SEMANTIC_CHRONOLOGY -> createPBSemanticChronology((SemanticEntity<SemanticEntityVersion>) entity);
            case PATTERN_CHRONOLOGY -> createPBPatternChronology((PatternEntity<PatternEntityVersion>) entity);
            default -> throw new IllegalStateException("not expecting" + entity.versionDataType());
        };
    }

    protected TinkarMsg createPBConceptChronology(ConceptEntity<ConceptEntityVersion> conceptEntity){
        return TinkarMsg.newBuilder()
                .setConceptChronology(ConceptChronology.newBuilder()
                        .setPublicId(createPBPublicId(conceptEntity.publicId()))
                        .addAllVersions(createPBConceptVersions(conceptEntity.versions()))
                        .build())
                .build();
    }

    protected List<ConceptVersion> createPBConceptVersions(ImmutableList<ConceptEntityVersion> conceptEntityVersions){
        if(conceptEntityVersions.size() == 0){
            throw new RuntimeException("Exception thrown, ImmutableList contains zero Entity Concept Versions");
        }
        final ArrayList<ConceptVersion> pbConceptVersions = new ArrayList<>();
        conceptEntityVersions.forEach(conceptEntityVersion -> pbConceptVersions.add(ConceptVersion.newBuilder()
                .setStamp(createPBStampChronology(conceptEntityVersion.stamp()))
                .build()));
        return pbConceptVersions;
    }

    protected TinkarMsg createPBSemanticChronology(SemanticEntity<SemanticEntityVersion> semanticEntity){
        if(semanticEntity.versions().size() == 0){
            throw new RuntimeException("Exception thrown, Semantic Chronology can't contain zero versions");
        }
        if (semanticEntity.referencedComponent() != null) {
            return TinkarMsg.newBuilder()
                    .setSemanticChronology(SemanticChronology.newBuilder()
                            .setPublicId(createPBPublicId(semanticEntity.publicId()))
                            .setReferencedComponent(createPBPublicId(semanticEntity.referencedComponent().publicId()))
                            .setPatternForSemantic(createPBPublicId(semanticEntity.pattern().publicId()))
                            .addAllVersions(createPBSemanticVersions(semanticEntity.versions()))
                            .build())
                    .build();

        }
        return TinkarMsg.newBuilder()
                .setSemanticChronology(SemanticChronology.newBuilder()
                        .setPublicId(createPBPublicId(semanticEntity.publicId()))
                        .setReferencedComponent(createPBPublicId(PublicIds.of(0, 0)))
                        .setPatternForSemantic(createPBPublicId(semanticEntity.pattern().publicId()))
                        .addAllVersions(createPBSemanticVersions(semanticEntity.versions()))
                        .build())
                .build();
    }

    protected List<SemanticVersion> createPBSemanticVersions(ImmutableList<SemanticEntityVersion> semanticEntityVersions) {
        if(semanticEntityVersions.size() == 0){
            throw new RuntimeException("Exception thrown, ImmutableList contains zero Entity Semantic Versions");
        }
        return semanticEntityVersions.stream()
                .map(semanticEntityVersion -> SemanticVersion.newBuilder()
                    .setStamp(createPBStampChronology(semanticEntityVersion.stamp()))
                    .addAllFields(createPBFields(semanticEntityVersion.fieldValues()))
                    .build())
                .toList();
    }
        protected TinkarMsg createPBPatternChronology(PatternEntity<PatternEntityVersion> patternEntity){
        return TinkarMsg.newBuilder()
                .setPatternChronology(PatternChronology.newBuilder()
                        .setPublicId(createPBPublicId(patternEntity.publicId()))
                        .addAllVersions(createPBPatternVersions(patternEntity.versions()))
                        .build())
                .build();
    }

    protected List<PatternVersion> createPBPatternVersions(ImmutableList<PatternEntityVersion> patternEntityVersions){
        if(patternEntityVersions.size() == 0){
            throw new RuntimeException("Exception thrown, ImmutableList contains zero Entity Pattern Versions");
        }
        final ArrayList<PatternVersion> pbPatternVersions = new ArrayList<>();
        patternEntityVersions.forEach(patternEntityVersion -> pbPatternVersions
                .add(PatternVersion.newBuilder()
                .setStamp(createPBStampChronology(patternEntityVersion.stamp()))
                .setReferencedComponentPurpose(createPBPublicId(patternEntityVersion.semanticPurpose().publicId()))
                .setReferencedComponentMeaning(createPBPublicId(patternEntityVersion.semanticMeaning().publicId()))
                .addAllFieldDefinitions(createPBFieldDefinitions((ImmutableList<FieldDefinitionRecord>) patternEntityVersion.fieldDefinitions()))
                .build()));
        return pbPatternVersions;
    }

    protected StampChronology createPBStampChronology(StampEntity<StampVersionRecord> stampEntity){
        return StampChronology.newBuilder()
                .setPublicId(createPBPublicId(stampEntity.publicId()))
                .addAllVersions(createPBStampVersions(stampEntity.versions()))
                .build();
    }
    // TODO: error occurring  here because setStatus method does not exist in PBConceptChronology (ask Andrew).
    protected List<StampVersion> createPBStampVersions(ImmutableList<StampVersionRecord> stampVersionRecords){
        return stampVersionRecords
                .stream()
                .map(stampVersionRecord -> StampVersion.newBuilder()
                    .setStatus(createPBPublicId(stampVersionRecord.state().publicId()))
                    .setAuthor(createPBPublicId(stampVersionRecord.author().publicId()))
                    .setModule(createPBPublicId(stampVersionRecord.module().publicId()))
                    .setPath(createPBPublicId(stampVersionRecord.path().publicId()))
                    .setTime(createTimestamp(stampVersionRecord.time()))
                    .build())
                .toList();
    }

    protected FieldDefinition createPBFieldDefinition(FieldDefinitionRecord fieldDefinitionRecord){
        return FieldDefinition.newBuilder()
                .setMeaning(createPBPublicId(fieldDefinitionRecord.meaning().publicId()))
                .setDataType(createPBPublicId(fieldDefinitionRecord.dataType().publicId()))
                .setPurpose(createPBPublicId(fieldDefinitionRecord.purpose().publicId()))
                .build();
    }

    protected List<FieldDefinition> createPBFieldDefinitions
            (ImmutableList<FieldDefinitionRecord> fieldDefinitionRecords){
        final ArrayList<FieldDefinition> pbFieldDefinitions = new ArrayList<>();
        fieldDefinitionRecords.forEach(fieldDefinitionRecord -> pbFieldDefinitions
                .add(createPBFieldDefinition(fieldDefinitionRecord)));
        return pbFieldDefinitions;
    }

    //TODO: Add in Planar/Spatial point into the switch statement.
    protected Field createPBField(Object obj){
        return switch (obj){
            case Boolean bool -> toPBBool(bool);
            case byte[] bytes -> toPBByte(bytes);
            case Float f -> toPBFloat(f);
            case Integer i -> toPBInteger(i);
            case Stamp stamp -> toPBStamp(stamp);
            case Concept concept -> toPBConcept(concept);
            case Semantic semantic -> toPBSemantic(semantic);
            case Pattern pattern -> toPBPattern(pattern);
            case Component component -> toPBComponent(component);
            case dev.ikm.tinkar.common.id.PublicId publicId -> toPBPublicId(publicId);
            case PublicIdList publicIdList -> toPBPublicIdList(publicIdList);
            case String s -> toPBString(s);
            case Instant instant -> toPBInstant(instant);
            case IntIdList intIdList -> toPBPublicIdList(intIdList);
            case IntIdSet intIdSet -> toPBPublicIdList(intIdSet);
            case DiTree diTree -> toPBDiTree(diTree);
            case DiGraph diGraph -> toPBDiGraph(diGraph);
            case null, default -> throw new IllegalStateException("Unknown or null field object for: " + obj);
        };
    }

    protected Field toPBBool(boolean value) {
        return Field.newBuilder().setBool(value).build();
    }
    protected Field toPBByte(byte[] value) {
        return Field.newBuilder().setBytes(ByteString.copyFrom(value)).build();
    }
    protected Field toPBFloat(float value) {
        return Field.newBuilder().setFloat(value).build();
    }
    protected Field toPBInteger(Integer value) {
        return Field.newBuilder().setInt(value).build();
    }
    protected Field toPBStamp(Stamp value) {
        return Field.newBuilder().setStamp(createPBStampChronology((StampRecord) value)).build();
    }
    protected Field toPBConcept(Concept value) {
        return Field.newBuilder().setPublicId(createPBPublicId(value.publicId())).build();
    }
    protected Field toPBSemantic(Semantic value) {
        return Field.newBuilder().setPublicId(createPBPublicId(value.publicId())).build();
    }
    protected Field toPBPattern(Pattern value) {
        return Field.newBuilder().setPublicId(createPBPublicId(value.publicId())).build();
    }
    protected Field toPBComponent(Component value) {
        return Field.newBuilder().setPublicId(createPBPublicId(value.publicId())).build();
    }
    protected Field toPBPublicId(dev.ikm.tinkar.common.id.PublicId value) {
        return Field.newBuilder().setPublicId(createPBPublicId(value)).build();
    }
    protected Field toPBPublicIdList(PublicIdList value) {
        return Field.newBuilder().setPublicIdList(createPBPublicIdList(value)).build();
    }
    protected Field toPBString(String value) {
        return Field.newBuilder().setString(value).build();
    }
    protected Field toPBInstant(Instant value) {
        return Field.newBuilder().setTime(createTimestamp(value.getEpochSecond())).build();
    }
    protected Field toPBPublicIdList(IntIdList value) {
        //TODO: Figure out what are the Int ID's getting written
        return Field.newBuilder().setPublicIdList(createPBPublicIdList(value)).build();
    }
    protected Field toPBPublicIdList(IntIdSet value) {
        return Field.newBuilder().setPublicIdList(createPBPublicIdList(value)).build();
    }
    protected Field toPBDiTree(DiTree value) {
        return Field.newBuilder().setDiTree(createPBDiTree((DiTreeEntity) value)).build();
    }
    protected Field toPBDiGraph(DiGraph value) {
        return Field.newBuilder().setDiGraph(createPBDiGraph((DiGraphEntity<EntityVertex>) value)).build();
    }

    protected List<Field> createPBFields(ImmutableList<Object> objects){
        final ArrayList<Field> pbFields = new ArrayList<>();
        for(Object obj : objects){
            pbFields.add(createPBField(obj));
        }
        return pbFields;
    }

    //TODO: PBConcept on its own doesnt exist, because PBConcept is its own type of message (like semantic or pattern).
    protected ConceptChronology createPBConcept(dev.ikm.tinkar.common.id.PublicId publicId){
        return ConceptChronology.newBuilder()
                .setPublicId(createPBPublicId(publicId))
                .build();
    }

    protected Timestamp createTimestamp(long time){
        return Timestamp.newBuilder()
                .setSeconds(time)
                .build();
    }

    protected PublicId createPBPublicId(dev.ikm.tinkar.common.id.PublicId publicId){
        if (publicId.uuidCount() == 0){
            throw new RuntimeException("Exception thrown, empty Public ID is present [entity transformer].");
        }
        return PublicId.newBuilder()
                .addAllId(publicId.asUuidList().stream()
                        .map(UuidUtil::getRawBytes)
                        .map(ByteString::copyFrom)
                        .toList())
                .build();
    }

    protected dev.ikm.tinkar.schema.PublicIdList createPBPublicIdList(PublicIdList publicIdList){
        ArrayList<PublicId> pbPublicIds = new ArrayList<>();
        for(dev.ikm.tinkar.common.id.PublicId publicId : publicIdList.toIdArray()){
            pbPublicIds.add(createPBPublicId(publicId));
        }
        return dev.ikm.tinkar.schema.PublicIdList.newBuilder()
                .addAllPublicIds(pbPublicIds)
                .build();
    }

    protected dev.ikm.tinkar.schema.PublicIdList createPBPublicIdList(IntIdList intIdList){
        List<PublicId> pbPublicIds = new ArrayList<>();
        intIdList.forEach(nid -> pbPublicIds.add(createPBPublicId(EntityService.get().getEntityFast(nid).publicId())));
        return dev.ikm.tinkar.schema.PublicIdList.newBuilder()
                .addAllPublicIds(pbPublicIds)
                .build();
    }

    protected dev.ikm.tinkar.schema.PublicIdList createPBPublicIdList(IntIdSet intIdSet){
        ArrayList<PublicId> pbPublicIds = new ArrayList<>();
        intIdSet.forEach(nid -> pbPublicIds.add(createPBPublicId(EntityService.get().getEntityFast(nid).publicId())));
        return dev.ikm.tinkar.schema.PublicIdList.newBuilder()
                .addAllPublicIds(pbPublicIds)
                .build();
    }

    protected dev.ikm.tinkar.schema.DiGraph createPBDiGraph(DiGraphEntity<EntityVertex> diGraph){
        //List all PBVertex TODO-aks8m: Why is this called a map when it's a list??
        ArrayList<dev.ikm.tinkar.schema.Vertex> pbVertices = new ArrayList<>();
        diGraph.vertexMap().forEach(vertex -> pbVertices.add(createPBVertex(vertex)));
        //Int Root Sequences TODO-aks8m: Is this a list of root Vertex's (then need to change protobuf)
        ArrayList<Integer> pbRoots = new ArrayList<>();
        diGraph.roots().forEach(root -> pbRoots.add(root.vertexIndex()));
        ArrayList<IntToMultipleIntMap> pbSuccessorsMap = new ArrayList<>();
        createPBIntToMultipleIntMaps(diGraph.successorMap().toImmutable());
        ArrayList<IntToMultipleIntMap> pbPredecessorsMap = new ArrayList<>();
        createPBIntToMultipleIntMaps(diGraph.predecessorMap().toImmutable());
        return dev.ikm.tinkar.schema.DiGraph.newBuilder()
                .addAllVertexMap(pbVertices)
                .addAllRootSequence(pbRoots)
                .addAllSuccessorMap(pbSuccessorsMap)
                .addAllPredecesorMap(pbPredecessorsMap)
                .build();
    }

    protected dev.ikm.tinkar.schema.DiTree createPBDiTree(DiTreeEntity diTree){
        ArrayList<dev.ikm.tinkar.schema.Vertex> pbVertices = new ArrayList<>();
        diTree.vertexMap().forEach(vertex -> pbVertices.add(createPBVertex(vertex)));
        Vertex pbVertexRoot = diTree.root();
        ArrayList<IntToIntMap> pbPredecesorMap = new ArrayList<>();
        createPBIntToIntMaps(diTree.predecessorMap().toImmutable());
        ArrayList<IntToMultipleIntMap> pbSuccessorMap = new ArrayList<>();
        createPBIntToMultipleIntMaps(diTree.successorMap().toImmutable());
        return dev.ikm.tinkar.schema.DiTree.newBuilder()
                .addAllVertexMap(pbVertices)
                .setRoot(pbVertexRoot.vertexIndex())
                .addAllPredecesorMap(pbPredecesorMap)
                .addAllSuccessorMap(pbSuccessorMap)
                .build();
    }

    protected dev.ikm.tinkar.schema.Vertex createPBVertex(EntityVertex vertex){
        int pbVertexIndex = vertex.vertexIndex();
        RichIterable<Concept> vertexKeys = vertex.propertyKeys();
        ArrayList<dev.ikm.tinkar.schema.Vertex.Property> pbPropertyList = new ArrayList<>();
        vertexKeys.forEach(concept -> pbPropertyList.add(dev.ikm.tinkar.schema.Vertex.Property.newBuilder()
                .setPublicId(createPBPublicId(concept.publicId()))
                .setField(createPBField(vertex.propertyFast(concept)))
                .build()));
        return dev.ikm.tinkar.schema.Vertex.newBuilder()
                .setVertexId(createPBVertexId(vertex.vertexId()))
                .setVertexIndex(pbVertexIndex)
                .setMeaning(createPBPublicId(EntityService.get().getEntityFast(vertex.getMeaningNid()).publicId()))
                .addAllProperties(pbPropertyList)
                .build();
    }

    protected VertexId createPBVertexId(dev.ikm.tinkar.common.id.VertexId vertexId){
        return VertexId.newBuilder()
                .setId(ByteString.copyFrom(dev.ikm.tinkar.common.id.PublicId.idString(vertexId.asUuidArray()), StandardCharsets.UTF_8))
                .build();
    }
    protected List<IntToIntMap> createPBIntToIntMaps(ImmutableIntIntMap intToIntMap) {
        ArrayList<IntToIntMap> pbIntToIntMaps = new ArrayList<>();
        intToIntMap.forEachKeyValue((source, target) -> pbIntToIntMaps.add(IntToIntMap.newBuilder()
                .setSource(source)
                .setTarget(target)
                .build()
        ));
        return pbIntToIntMaps;
    }
    protected List<IntToMultipleIntMap> createPBIntToMultipleIntMaps(ImmutableIntObjectMap intToMultipleIntMap){
        List<IntToMultipleIntMap> pbIntToMultipleIntMaps = new ArrayList<>();
        intToMultipleIntMap.keySet().forEach(source -> {
            final ArrayList<Integer> targets = new ArrayList<>();
            ((ImmutableIntList) intToMultipleIntMap.get(source)).forEach(target -> targets.add(target));
            pbIntToMultipleIntMaps.add(IntToMultipleIntMap.newBuilder()
                    .setSource(source)
                    .addAllTarget(targets)
                    .build()
            );
        });
        return  pbIntToMultipleIntMaps;
    }

    protected dev.ikm.tinkar.schema.PlanarPoint createPBPlanaPoint(PlanarPoint planarPoint){
        return dev.ikm.tinkar.schema.PlanarPoint.newBuilder()
                .setX(planarPoint.x())
                .setY(planarPoint.y())
                .build();
    }

    protected dev.ikm.tinkar.schema.SpatialPoint createPBSpatialPoint(SpatialPoint spatialPoint){
        return dev.ikm.tinkar.schema.SpatialPoint.newBuilder()
                .setX(spatialPoint.x())
                .setY(spatialPoint.y())
                .setZ(spatialPoint.z())
                .build();
    }

}
