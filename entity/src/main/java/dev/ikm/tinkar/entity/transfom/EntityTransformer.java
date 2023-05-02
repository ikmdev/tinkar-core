package dev.ikm.tinkar.entity.transfom;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import dev.ikm.tinkar.common.id.*;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * The entityTransformer class is responsible for transformer a entity of a certain data type to a
 * Protobuf message object.
 */
public class EntityTransformer{

    private final AtomicInteger conceptCount = new AtomicInteger();
    private final AtomicInteger semanticCount = new AtomicInteger();
    private final AtomicInteger patternCount = new AtomicInteger();

    private final Logger LOG = LoggerFactory.getLogger(EntityTransformer.class);
    private static EntityTransformer INSTANCE;

    private EntityTransformer(){
    }

    public static EntityTransformer getInstance(){
        if(INSTANCE == null){
            synchronized (EntityTransformer.class){
                if (INSTANCE == null){
                    INSTANCE = new EntityTransformer();
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
    public PBTinkarMsg transform(Entity entity){
        return switch (entity.entityDataType()){
            case CONCEPT_CHRONOLOGY -> createPBConceptChronology((ConceptEntity<ConceptEntityVersion>) entity);
            case SEMANTIC_CHRONOLOGY -> createPBSemanticChronology((SemanticEntity<SemanticEntityVersion>) entity);
            case PATTERN_CHRONOLOGY -> createPBPatternChronology((PatternEntity<PatternEntityVersion>) entity);
            default -> throw new IllegalStateException("not expecting" + entity.versionDataType());
        };
    }

    protected PBTinkarMsg createPBConceptChronology(ConceptEntity<ConceptEntityVersion> conceptEntity){
        return PBTinkarMsg.newBuilder()
                .setConceptChronologyValue(PBConceptChronology.newBuilder()
                        .setPublicId(createPBPublicId(conceptEntity.publicId()))
                        .addAllConceptVersions(createPBConceptVersions(conceptEntity.versions()))
                        .build())
                .build();
    }

    protected List<PBConceptVersion> createPBConceptVersions(ImmutableList<ConceptEntityVersion> conceptEntityVersions){
        if(conceptEntityVersions.size() == 0){
            throw new RuntimeException("Exception thrown, ImmutableList contains zero Entity Concept Versions");
        }
        final ArrayList<PBConceptVersion> pbConceptVersions = new ArrayList<>();
        conceptEntityVersions.forEach(conceptEntityVersion -> pbConceptVersions.add(PBConceptVersion.newBuilder()
                .setStamp(createPBStampChronology(conceptEntityVersion.stamp()))
                .build()));
        return pbConceptVersions;
    }

    protected PBTinkarMsg createPBSemanticChronology(SemanticEntity<SemanticEntityVersion> semanticEntity){
        if(semanticEntity.versions().size() == 0){
            throw new RuntimeException("Exception thrown, Semantic Chronology can't contain zero versions");
        }
        if (semanticEntity.referencedComponent() != null) {
            return PBTinkarMsg.newBuilder()
                    .setSemanticChronologyValue(PBSemanticChronology.newBuilder()
                            .setPublicId(createPBPublicId(semanticEntity.publicId()))
                            .setReferencedComponent(createPBPublicId(semanticEntity.referencedComponent().publicId()))
                            .setPatternForSemantic(createPBPublicId(semanticEntity.pattern().publicId()))
                            .addAllVersions(createPBSemanticVersions(semanticEntity.versions()))
                            .build())
                    .build();

        }
        return PBTinkarMsg.newBuilder()
                .setSemanticChronologyValue(PBSemanticChronology.newBuilder()
                        .setPublicId(createPBPublicId(semanticEntity.publicId()))
                        .setReferencedComponent(createPBPublicId(PublicIds.of(0, 0)))
                        .setPatternForSemantic(createPBPublicId(semanticEntity.pattern().publicId()))
                        .addAllVersions(createPBSemanticVersions(semanticEntity.versions()))
                        .build())
                .build();
    }

    protected List<PBSemanticVersion> createPBSemanticVersions(ImmutableList<SemanticEntityVersion> semanticEntityVersions) {
        if(semanticEntityVersions.size() == 0){
            throw new RuntimeException("Exception thrown, ImmutableList contains zero Entity Semantic Versions");
        }
        return semanticEntityVersions.stream()
                .map(semanticEntityVersion -> PBSemanticVersion.newBuilder()
                    .setStamp(createPBStampChronology(semanticEntityVersion.stamp()))
                    .addAllFieldValues(createPBFields(semanticEntityVersion.fieldValues()))
                    .build())
                .toList();
    }

    protected PBTinkarMsg createPBPatternChronology(PatternEntity<PatternEntityVersion> patternEntity){
        return PBTinkarMsg.newBuilder()
                .setPatternChronologyValue(PBPatternChronology.newBuilder()
                        .setPublicId(createPBPublicId(patternEntity.publicId()))
                        .addAllVersions(createPBPatternVersions(patternEntity.versions()))
                        .build())
                .build();
    }

    protected List<PBPatternVersion> createPBPatternVersions(ImmutableList<PatternEntityVersion> patternEntityVersions){
        if(patternEntityVersions.size() == 0){
            throw new RuntimeException("Exception thrown, ImmutableList contains zero Entity Pattern Versions");
        }
        final ArrayList<PBPatternVersion> pbPatternVersions = new ArrayList<>();
        patternEntityVersions.forEach(patternEntityVersion -> pbPatternVersions
                .add(PBPatternVersion.newBuilder()
                .setStamp(createPBStampChronology(patternEntityVersion.stamp()))
                .setReferencedComponentPurpose(createPBPublicId(patternEntityVersion.semanticPurpose().publicId()))
                .setReferencedComponentMeaning(createPBPublicId(patternEntityVersion.semanticMeaning().publicId()))
                .addAllFieldDefinitions(createPBFieldDefinitions((ImmutableList<FieldDefinitionRecord>) patternEntityVersion.fieldDefinitions()))
                .build()));
        return pbPatternVersions;
    }

    protected PBStampChronology createPBStampChronology(StampEntity<StampVersionRecord> stampEntity){
        return PBStampChronology.newBuilder()
                .setPublicId(createPBPublicId(stampEntity.publicId()))
                .addAllStampVersions(createPBStampVersions(stampEntity.versions()))
                .build();
    }
    // TODO: error occurring  here because setStatus method does not exist in PBConceptChronology (ask Andrew).
    protected List<PBStampVersion> createPBStampVersions(ImmutableList<StampVersionRecord> stampVersionRecords){
        return stampVersionRecords
                .stream()
                .map(stampVersionRecord -> PBStampVersion.newBuilder()
                    .setStatus(createPBPublicId(stampVersionRecord.state().publicId()))
                    .setAuthor(createPBPublicId(stampVersionRecord.author().publicId()))
                    .setModule(createPBPublicId(stampVersionRecord.module().publicId()))
                    .setPath(createPBPublicId(stampVersionRecord.path().publicId()))
                    .setTime(createTimestamp(stampVersionRecord.time()))
                    .build())
                .toList();
    }

    protected PBFieldDefinition createPBFieldDefinition(FieldDefinitionRecord fieldDefinitionRecord){
        return PBFieldDefinition.newBuilder()
                .setMeaning(createPBPublicId(fieldDefinitionRecord.meaning().publicId()))
                .setDataType(createPBPublicId(fieldDefinitionRecord.dataType().publicId()))
                .setPurpose(createPBPublicId(fieldDefinitionRecord.purpose().publicId()))
                .build();
    }

    protected List<PBFieldDefinition> createPBFieldDefinitions
            (ImmutableList<FieldDefinitionRecord> fieldDefinitionRecords){
        final ArrayList<PBFieldDefinition> pbFieldDefinitions = new ArrayList<>();
        fieldDefinitionRecords.forEach(fieldDefinitionRecord -> pbFieldDefinitions
                .add(createPBFieldDefinition(fieldDefinitionRecord)));
        return pbFieldDefinitions;
    }

    protected PBField toPBBool(boolean value) {
        return PBField.newBuilder().setBoolValue(value).build();
    }
    protected PBField toPBByte(byte[] value) {
        return PBField.newBuilder().setBytesValue(ByteString.copyFrom(value)).build();
    }
    protected PBField toPBFloat(float value) {
        return PBField.newBuilder().setFloatValue(value).build();
    }
    protected PBField toPBInteger(Integer value) {
        return PBField.newBuilder().setIntValue(value).build();
    }
    protected PBField toPBStamp(Stamp value) {
        return PBField.newBuilder().setStampValue(createPBStampChronology((StampRecord) value)).build();
    }
    protected PBField toPBConcept(Concept value) {
        return PBField.newBuilder().setPublicIdValue(createPBPublicId(value.publicId())).build();
    }
    protected PBField toPBSemantic(Semantic value) {
        return PBField.newBuilder().setPublicIdValue(createPBPublicId(value.publicId())).build();
    }
    protected PBField toPBPattern(Pattern value) {
        return PBField.newBuilder().setPublicIdValue(createPBPublicId(value.publicId())).build();
    }
    protected PBField toPBComponent(Component value) {
        return PBField.newBuilder().setPublicIdValue(createPBPublicId(value.publicId())).build();
    }
    protected PBField toPBPublicId(PublicId value) {
        return PBField.newBuilder().setPublicIdValue(createPBPublicId(value)).build();
    }
    protected PBField toPBPublicIdList(PublicIdList value) {
        return PBField.newBuilder().setPublicIdListValue(createPBPublicIdList(value)).build();
    }
    protected PBField toPBString(String value) {
        return PBField.newBuilder().setStringValue(value).build();
    }
    protected PBField toPBInstant(Instant value) {
        return PBField.newBuilder().setTimeValue(createTimestamp(value.getEpochSecond())).build();
    }
    protected PBField toPBIntIdList(IntIdList value) {
        return PBField.newBuilder().setPublicIdListValue(createPBPublicIdList(value)).build();
    }
    protected PBField toPBIntIdSet(IntIdSet value) {
        return PBField.newBuilder().setPublicIdListValue(createPBPublicIdList(value)).build();
    }
    protected PBField toPBDiTree(DiTree value) {
        return PBField.newBuilder().setDiTreeValue(createPBDiTree((DiTreeEntity) value)).build();
    }
    protected PBField toPBDiGraph(DiGraph value) {
        return PBField.newBuilder().setDiGraphValue(createPBDiGraph((DiGraphEntity<EntityVertex>) value)).build();
    }

    //TODO: Add in Planar/Spatial point into the switch statement.
    protected PBField createPBField(Object obj){
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
            case PublicId publicId -> toPBPublicId(publicId);
            case PublicIdList publicIdList -> toPBPublicIdList(publicIdList);
            case String s -> toPBString(s);
            case Instant instant -> toPBInstant(instant);
            case IntIdList intIdList -> toPBIntIdList(intIdList);
            case IntIdSet intIdSet -> toPBIntIdSet(intIdSet);
            case DiTree diTree -> toPBDiTree(diTree);
            case DiGraph diGraph -> toPBDiGraph(diGraph);
            case null, default -> throw new IllegalStateException("Unknown or null field object for: " + obj);
        };
    }
    protected List<PBField> createPBFields(ImmutableList<Object> objects){
        final ArrayList<PBField> pbFields = new ArrayList<>();
        for(Object obj : objects){
            pbFields.add(createPBField(obj));
        }
        return pbFields;
    }

    //TODO: PBConcept on its own doesnt exist, because PBConcept is its own type of message (like semantic or pattern).
    protected PBConceptChronology createPBConcept(PublicId publicId){
        return PBConceptChronology.newBuilder()
                .setPublicId(createPBPublicId(publicId))
                .build();
    }

    protected Timestamp createTimestamp(long time){
        return Timestamp.newBuilder()
                .setSeconds(time)
                .build();
    }

    protected PBPublicId createPBPublicId(PublicId publicId){
        return PBPublicId.newBuilder()
                .addAllId(publicId.asUuidList().stream()
                        .map(UuidUtil::getRawBytes)
                        .map(ByteString::copyFrom)
                        .collect(Collectors.toList()))
                .build();
    }

    protected PBPublicIdList createPBPublicIdList(PublicIdList publicIdList){
        ArrayList<PBPublicId> pbPublicIds = new ArrayList<>();
        for(PublicId publicId : publicIdList.toIdArray()){
            pbPublicIds.add(createPBPublicId(publicId));
        }
        return PBPublicIdList.newBuilder()
                .addAllPublicIds(pbPublicIds)
                .build();
    }

    protected PBPublicIdList createPBPublicIdList(IntIdList intIdList){
        ArrayList<PBPublicId> pbPublicIds = new ArrayList<>();
        intIdList.forEach(nid -> pbPublicIds.add(createPBPublicId(EntityService.get().getEntityFast(nid).publicId())));
        return PBPublicIdList.newBuilder()
                .addAllPublicIds(pbPublicIds)
                .build();
    }

    protected PBPublicIdList createPBPublicIdList(IntIdSet intIdSet){
        ArrayList<PBPublicId> pbPublicIds = new ArrayList<>();
        intIdSet.forEach(nid -> pbPublicIds.add(createPBPublicId(EntityService.get().getEntityFast(nid).publicId())));
        return PBPublicIdList.newBuilder()
                .addAllPublicIds(pbPublicIds)
                .build();
    }

    protected PBDiGraph createPBDiGraph(DiGraphEntity<EntityVertex> diGraph){
        //List all PBVertex TODO-aks8m: Why is this called a map when it's a list??
        ArrayList<PBVertex> pbVertices = new ArrayList<>();
        diGraph.vertexMap().forEach(vertex -> pbVertices.add(createPBVertex(vertex)));
        //Int Root Sequences TODO-aks8m: Is this a list of root Vertex's (then need to change protobuf)
        ArrayList<Integer> pbRoots = new ArrayList<>();
        diGraph.roots().forEach(root -> pbRoots.add(root.vertexIndex()));
        ArrayList<PBIntToMultipleIntMap> pbSuccessorsMap = new ArrayList<>();
        createPBIntToMultipleIntMaps(diGraph.successorMap().toImmutable());
        ArrayList<PBIntToMultipleIntMap> pbPredecessorsMap = new ArrayList<>();
        createPBIntToMultipleIntMaps(diGraph.predecessorMap().toImmutable());
        return PBDiGraph.newBuilder()
                .addAllVertexMap(pbVertices)
                .addAllRootSequence(pbRoots)
                .addAllSuccessorMap(pbSuccessorsMap)
                .addAllPredecesorMap(pbPredecessorsMap)
                .build();
    }

    protected PBDiTree createPBDiTree(DiTreeEntity diTree){
        ArrayList<PBVertex> pbVertices = new ArrayList<>();
        diTree.vertexMap().forEach(vertex -> pbVertices.add(createPBVertex(vertex)));
        Vertex pbVertexRoot = diTree.root();
        ArrayList<PBIntToIntMap> pbPredecesorMap = new ArrayList<>();
        createPBIntToIntMaps(diTree.predecessorMap().toImmutable());
        ArrayList<PBIntToMultipleIntMap> pbSuccessorMap = new ArrayList<>();
        createPBIntToMultipleIntMaps(diTree.successorMap().toImmutable());
        return PBDiTree.newBuilder()
                .addAllVertexMap(pbVertices)
                .setRoot(pbVertexRoot.vertexIndex())
                .addAllPredecesorMap(pbPredecesorMap)
                .addAllSuccessorMap(pbSuccessorMap)
                .build();
    }

    protected PBVertex createPBVertex(EntityVertex vertex){
        int pbVertexIndex = vertex.vertexIndex();
        RichIterable<Concept> vertexKeys = vertex.propertyKeys();
        ArrayList<PBVertex.Property> pbPropertyList = new ArrayList<>();
        vertexKeys.forEach(concept -> pbPropertyList.add(PBVertex.Property.newBuilder()
                .setPublicId(createPBPublicId(concept.publicId()))
                .setValue(createPBField(vertex.propertyFast(concept)))
                .build()));
        return PBVertex.newBuilder()
                .setVertexId(createPBVertexId(vertex.vertexId()))
                .setVertexIndex(pbVertexIndex)
                .setMeaning(createPBPublicId(EntityService.get().getEntityFast(vertex.getMeaningNid()).publicId()))
                .addAllProperties(pbPropertyList)
                .build();
    }

    protected PBVertexId createPBVertexId(VertexId vertexId){
        return PBVertexId.newBuilder()
                .setId(ByteString.copyFrom(PublicId.idString(vertexId.asUuidArray()), StandardCharsets.UTF_8))
                .build();
    }
    protected List<PBIntToIntMap> createPBIntToIntMaps(ImmutableIntIntMap intToIntMap) {
        ArrayList<PBIntToIntMap> pbIntToIntMaps = new ArrayList<>();
        intToIntMap.forEachKeyValue((source, target) -> pbIntToIntMaps.add(PBIntToIntMap.newBuilder()
                .setSource(source)
                .setTarget(target)
                .build()
        ));
        return pbIntToIntMaps;
    }
    protected List<PBIntToMultipleIntMap> createPBIntToMultipleIntMaps(ImmutableIntObjectMap intToMultipleIntMap){
        List<PBIntToMultipleIntMap> pbIntToMultipleIntMaps = new ArrayList<>();
        intToMultipleIntMap.keySet().forEach(source -> {
            final ArrayList<Integer> targets = new ArrayList<>();
            ((ImmutableIntList) intToMultipleIntMap.get(source)).forEach(target -> targets.add(target));
            pbIntToMultipleIntMaps.add(PBIntToMultipleIntMap.newBuilder()
                    .setSource(source)
                    .addAllTarget(targets)
                    .build()
            );
        });
        return  pbIntToMultipleIntMaps;
    }

    protected PBPlanarPoint createPBPlanaPoint(PlanarPoint planarPoint){
        return PBPlanarPoint.newBuilder()
                .setX(planarPoint.x())
                .setY(planarPoint.y())
                .build();
    }

    protected PBSpatialPoint createPBSpatialPoint(SpatialPoint spatialPoint){
        return PBSpatialPoint.newBuilder()
                .setX(spatialPoint.x())
                .setY(spatialPoint.y())
                .setZ(spatialPoint.z())
                .build();
    }
}
