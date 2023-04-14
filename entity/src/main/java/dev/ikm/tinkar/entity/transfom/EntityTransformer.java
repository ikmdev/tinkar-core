package dev.ikm.tinkar.entity.transfom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;


public class EntityTransformer{

    private final AtomicInteger conceptCount = new AtomicInteger();
    private final AtomicInteger semanticCount = new AtomicInteger();
    private final AtomicInteger patternCount = new AtomicInteger();

    private final Logger LOG = LoggerFactory.getLogger(EntityTransformer.class);

//    public PBTinkarMsg transform(Entity entity){
//        return switch (entity.entityDataType()){
//            case CONCEPT_CHRONOLOGY -> createPBConceptChronology((ConceptEntity<ConceptEntityVersion>) entity);
//            case SEMANTIC_CHRONOLOGY -> createPBSemanticChronology((SemanticEntity<SemanticEntityVersion>) entity);
//            case PATTERN_CHRONOLOGY -> createPBPatternChronology((PatternEntity<PatternEntityVersion>) entity);
//            default -> throw new IllegalStateException("not expecting" + entity.versionDataType());
//        };
//    }
//
//    private PBTinkarMsg createPBConceptChronology(ConceptEntity<ConceptEntityVersion> conceptEntity){
//        return PBTinkarMsg.newBuilder()
//                .setConceptChronologyValue(PBConceptChronology.newBuilder()
//                .setPublicId(createPBPublicId(conceptEntity.publicId()))
//                .addAllConceptVersions(createPBConceptVersions(conceptEntity.versions()))
//                .build())
//                .build();
//    }
//
//    private List<PBConceptVersion> createPBConceptVersions(ImmutableList<ConceptEntityVersion> conceptEntityVersions){
//        final ArrayList<PBConceptVersion> pbConceptVersions = new ArrayList<>();
//        conceptEntityVersions.forEach(conceptEntityVersion -> pbConceptVersions.add(PBConceptVersion.newBuilder()
//                //TODO: setPublicId method no longer exists in PBConceptVersion.class
////                .setPublicId(createPBPublicId(conceptEntityVersion.publicId()))
//                .setStamp(createPBStampChronology(conceptEntityVersion.stamp()))
//                .build()));
//        return pbConceptVersions;
//    }
//
//    private PBTinkarMsg createPBSemanticChronology(SemanticEntity<SemanticEntityVersion> semanticEntity){
//        if (semanticEntity.referencedComponent() != null) {
//            return PBTinkarMsg.newBuilder()
//                    .setSemanticChronologyValue(PBSemanticChronology.newBuilder()
//                            .setPublicId(createPBPublicId(semanticEntity.publicId()))
//                            .setReferencedComponent(createPBPublicId(semanticEntity.referencedComponent().publicId()))
//                            .setPatternForSemantic(createPBPublicId(semanticEntity.pattern().publicId()))
//                            .addAllVersions(createPBSemanticVersions(semanticEntity.versions()))
//                            .build())
//                    .build();
//
//        }
//        // TODO: Remove after finished debugging
//        LOG.error("Entity with no referenced component: " + semanticEntity.publicId());
//        return PBTinkarMsg.newBuilder()
//                .setSemanticChronologyValue(PBSemanticChronology.newBuilder()
//                        .setPublicId(createPBPublicId(semanticEntity.publicId()))
//                        .setReferencedComponent(createPBPublicId(PublicIds.of(0, 0)))
//                        .setPatternForSemantic(createPBPublicId(semanticEntity.pattern().publicId()))
//                        .addAllVersions(createPBSemanticVersions(semanticEntity.versions()))
//                        .build())
//                .build();
//
//    }
//
//    private List<PBSemanticVersion> createPBSemanticVersions
//            (ImmutableList<SemanticEntityVersion> semanticEntityVersions){
//        final ArrayList<PBSemanticVersion> pbSemanticVersions = new ArrayList<>();
//        semanticEntityVersions.forEach(semanticEntityVersion -> pbSemanticVersions.add(PBSemanticVersion.newBuilder()
//                //TODO: setPublicId no longer exists in PBSemanticVersion.class
////                .setPublicId(createPBPublicId(semanticEntityVersion.publicId()))
//                .setStamp(createPBStampChronology(semanticEntityVersion.stamp()))
//                .addAllFieldValues(createPBFields(semanticEntityVersion.fieldValues()))
//                .build()));
//        return  pbSemanticVersions;
//    }
//
//    private PBTinkarMsg createPBPatternChronology(PatternEntity<PatternEntityVersion> patternEntity){
//        return PBTinkarMsg.newBuilder()
//                .setPatternChronologyValue(PBPatternChronology.newBuilder()
//                .setPublicId(createPBPublicId(patternEntity.publicId()))
//                .addAllVersions(createPBPatternVersions(patternEntity.versions()))
//                .build())
//                .build();
//    }
//
//    private List<PBPatternVersion> createPBPatternVersions(ImmutableList<PatternEntityVersion> patternEntityVersions){
//        final ArrayList<PBPatternVersion> pbPatternVersions = new ArrayList<>();
//        patternEntityVersions.forEach(patternEntityVersion -> pbPatternVersions.add(PBPatternVersion.newBuilder()
//                //TODO: setPublicId no longer exists in PBPatternVersion.class
////                .setPublicId(createPBPublicId(patternEntityVersion.publicId()))
//                .setStamp(createPBStampChronology(patternEntityVersion.stamp()))
//                .setReferencedComponentPurpose(createPBPublicId(patternEntityVersion.semanticPurpose()))
//                .setReferencedComponentMeaning(createPBPublicId(patternEntityVersion.semanticMeaning()))
//                .addAllFieldDefinitions(createPBFieldDefinitions(
//                        (ImmutableList<FieldDefinitionRecord>) patternEntityVersion.fieldDefinitions()))
//                .build()));
//        return pbPatternVersions;
//    }
//
//    private PBStampChronology createPBStampChronology(StampEntity<StampVersionRecord> stampEntity){
//        return PBStampChronology.newBuilder()
//                .setPublicId(createPBPublicId(stampEntity.publicId()))
//                .addAllStampVersions(createPBStampVersions(stampEntity.versions()))
//                .build();
//    }
//// TODO: error occurring  here because setStatus method does not exist in PBConceptChronology (ask Andrew).
//    private List<PBStampVersion> createPBStampVersions(ImmutableList<StampVersionRecord> stampVersionRecords){
//        final ArrayList<PBStampVersion> pbStampVersions = new ArrayList<>();
//        stampVersionRecords.forEach(stampVersionRecord -> PBStampVersion.newBuilder()
//                .setStatus(createPBPublicId(stampVersionRecord.state().publicId()))
//                .setAuthor(createPBPublicId(stampVersionRecord.author().publicId()))
//                .setModule(createPBPublicId(stampVersionRecord.module().publicId()))
//                .setPath(createPBPublicId(stampVersionRecord.path().publicId()))
//                .setTime(createTimestamp(stampVersionRecord.time()))
//                .build());
//        return pbStampVersions;
//    }
//
//    private PBFieldDefinition createPBFieldDefinition(FieldDefinitionRecord fieldDefinitionRecord){
//        return PBFieldDefinition.newBuilder()
//                .setMeaning(createPBPublicId(fieldDefinitionRecord.meaning().publicId()))
//                .setDataType(createPBPublicId(fieldDefinitionRecord.dataType().publicId()))
//                .setPurpose(createPBPublicId(fieldDefinitionRecord.purpose().publicId()))
//                .build();
//    }
//
//    private List<PBFieldDefinition> createPBFieldDefinitions
//            (ImmutableList<FieldDefinitionRecord> fieldDefinitionRecords){
//        final ArrayList<PBFieldDefinition> pbFieldDefinitions = new ArrayList<>();
//        fieldDefinitionRecords.forEach(fieldDefinitionRecord -> pbFieldDefinitions
//                .add(createPBFieldDefinition(fieldDefinitionRecord)));
//        return pbFieldDefinitions;
//    }
//
//    private PBField createPBField(Object obj){
//        return switch (obj){
//            case Boolean bool -> PBField.newBuilder().setBoolValue(bool).build();
//            case byte[] bytes -> PBField.newBuilder().setBytesValue(ByteString.copyFrom(bytes)).build();
////            case ConceptRecord conceptRecord -> PBField.newBuilder()
////                    .setConceptValue(createPBConcept(conceptRecord.publicId())).build();
//            case Float f -> PBField.newBuilder().setFloatValue(f).build();
//            case Integer i -> PBField.newBuilder().setIntValue(i).build();
//            case Stamp stamp -> PBField.newBuilder()
//                    .setStampValue(createPBStampChronology((StampRecord) stamp)).build();
//            case Concept concept -> PBField.newBuilder().setPublicIdValue(createPBPublicId(concept.publicId())).build();
//            case Semantic semantic -> PBField.newBuilder()
//                    .setPublicIdValue(createPBPublicId(semantic.publicId())).build();
//            case Pattern pattern -> PBField.newBuilder().setPublicIdValue(createPBPublicId(pattern.publicId())).build();
//            case Component component -> PBField.newBuilder()
//                    .setPublicIdValue(createPBPublicId(component.publicId())).build();
//            case PublicId publicId -> PBField.newBuilder().setPublicIdValue(createPBPublicId(publicId)).build();
//            case PublicIdList publicIdList -> PBField.newBuilder()
//                    .setPublicIdListValue(createPBPublicIdList(publicIdList)).build();
//            case String s -> PBField.newBuilder().setStringValue(s).build();
//            case Instant instant -> PBField.newBuilder()
//                    .setTimeValue(createTimestamp(instant.getEpochSecond())).build();
//            case IntIdList intIdList -> PBField.newBuilder()
//                    .setPublicIdListValue(createPBPublicIdList(intIdList)).build();
//            case IntIdSet intIdSet -> PBField.newBuilder()
//                    .setPublicIdListValue(createPBPublicIdList(intIdSet)).build();
//            case DiTree diTree -> PBField.newBuilder().setDiTreeValue(createPBDiTree((DiTreeEntity) diTree)).build();
//            case DiGraph diGraph -> PBField.newBuilder().setDiGraphValue(createPBDiGraph((DiGraphEntity<EntityVertex>) diGraph)).build();
////            case PlanarPoint planarPoint -> createPBPlanaPoint(planarPoint); TODO-aks8m: need to add to protobuf
////            case SpatialPoint spatialPoint -> createPBSpatialPoint(spatialPoint); TODO-aks8m: need to add to protobuf
//            case null, default -> throw new IllegalStateException("Unknown or null field object for: " + obj);
//        };
//    }
//    private List<PBField> createPBFields(ImmutableList<Object> objects){
//        final ArrayList<PBField> pbFields = new ArrayList<>();
//        for(Object obj : objects){
//            pbFields.add(createPBField(obj));
//        }
//        return pbFields;
//    }
//
//    //TODO: PBConcept on its own doesnt exist, because PBConcept is its own type of message (like semantic or pattern).
//    private PBConceptChronology createPBConcept(PublicId publicId){
//        return PBConceptChronology.newBuilder()
//                .setPublicId(createPBPublicId(publicId))
//                .build();
//    }
//
//    private Timestamp createTimestamp(long time){
//        return Timestamp.newBuilder()
//                .setSeconds(time)
//                .build();
//    }
//
//    private PBPublicId createPBPublicId(PublicId publicId){
//        return PBPublicId.newBuilder()
//                .addAllId(publicId.asUuidList().stream()
//                        .map(UuidUtil::getRawBytes)
//                        .map(ByteString::copyFrom)
//                        .collect(Collectors.toList()))
//                .build();
//    }
//
//    private PBPublicIdList createPBPublicIdList(PublicIdList publicIdList){
//        ArrayList<PBPublicId> pbPublicIds = new ArrayList<>();
//        for(PublicId publicId : publicIdList.toIdArray()){
//            pbPublicIds.add(createPBPublicId(publicId));
//        }
//        return PBPublicIdList.newBuilder()
//                .addAllPublicIds(pbPublicIds)
//                .build();
//    }
//
//    private PBPublicIdList createPBPublicIdList(IntIdList intIdList){
//        ArrayList<PBPublicId> pbPublicIds = new ArrayList<>();
//        intIdList.forEach(nid -> pbPublicIds.add(createPBPublicId(EntityService.get().getEntityFast(nid).publicId())));
//        return PBPublicIdList.newBuilder()
//                .addAllPublicIds(pbPublicIds)
//                .build();
//    }
//
//    private PBPublicIdList createPBPublicIdList(IntIdSet intIdSet){
//        ArrayList<PBPublicId> pbPublicIds = new ArrayList<>();
//        intIdSet.forEach(nid -> pbPublicIds.add(createPBPublicId(EntityService.get().getEntityFast(nid).publicId())));
//        return PBPublicIdList.newBuilder()
//                .addAllPublicIds(pbPublicIds)
//                .build();
//    }
//
//    private PBDiGraph createPBDiGraph(DiGraphEntity<EntityVertex> diGraph){
//        //List all PBVertex TODO-aks8m: Why is this called a map when it's a list??
//        ArrayList<PBVertex> pbVertices = new ArrayList<>();
//        diGraph.vertexMap().forEach(vertex -> pbVertices.add(createPBVertex(vertex)));
//        //Int Root Sequences TODO-aks8m: Is this a list of root Vertex's (then need to change protobuf)
//        ArrayList<Integer> pbRoots = new ArrayList<>();
//        diGraph.roots().forEach(root -> pbRoots.add(root.vertexIndex()));
//        ArrayList<PBIntToMultipleIntMap> pbSuccessorsMap = new ArrayList<>();
//        createPBIntToMultipleIntMaps(diGraph.successorMap().toImmutable());
//        ArrayList<PBIntToMultipleIntMap> pbPredecessorsMap = new ArrayList<>();
//        createPBIntToMultipleIntMaps(diGraph.predecessorMap().toImmutable());
//        return PBDiGraph.newBuilder()
//                .addAllVertexMap(pbVertices)
//                .addAllRootSequence(pbRoots)
//                .addAllSuccessorMap(pbSuccessorsMap)
//                .addAllPredecesorMap(pbPredecessorsMap)
//                .build();
//    }
//
//    private PBDiTree createPBDiTree(DiTreeEntity diTree){
//        ArrayList<PBVertex> pbVertices = new ArrayList<>();
//        diTree.vertexMap().forEach(vertex -> pbVertices.add(createPBVertex(vertex)));
//        Vertex pbVertexRoot = diTree.root();
//        ArrayList<PBIntToIntMap> pbPredecesorMap = new ArrayList<>();
//        createPBIntToIntMaps(diTree.predecessorMap().toImmutable());
//        ArrayList<PBIntToMultipleIntMap> pbSuccessorMap = new ArrayList<>();
//        createPBIntToMultipleIntMaps(diTree.successorMap().toImmutable());
//        return PBDiTree.newBuilder()
//                .addAllVertexMap(pbVertices)
//                .setRoot(pbVertexRoot.vertexIndex())
//                .addAllPredecesorMap(pbPredecesorMap)
//                .addAllSuccessorMap(pbSuccessorMap)
//                .build();
//    }
//
//    private PBVertex createPBVertex(EntityVertex vertex){
//        int pbVertexIndex = vertex.vertexIndex();
//        RichIterable<Concept> vertexKeys = vertex.propertyKeys();
//        ArrayList<PBVertex.Property> pbPropertyList = new ArrayList<>();
//        vertexKeys.forEach(concept -> pbPropertyList.add(PBVertex.Property.newBuilder()
//                .setPublicId(createPBPublicId(concept.publicId()))
//                .setValue(createPBField(vertex.propertyFast(concept)))
//                .build()));
//        return PBVertex.newBuilder()
//                .setVertexId(createPBVertexId(vertex.vertexId()))
//                .setVertexIndex(pbVertexIndex)
//                .setMeaning(createPBPublicId(EntityService.get().getEntityFast(vertex.getMeaningNid()).publicId()))
//                .addAllProperties(pbPropertyList)
//                .build();
//    }
//
//    private PBVertexId createPBVertexId(VertexId vertexId){
//        return PBVertexId.newBuilder()
//                .setId(ByteString.copyFrom(PublicId.idString(vertexId.asUuidArray()), StandardCharsets.UTF_8))
//                .build();
//    }
//    private List<PBIntToIntMap> createPBIntToIntMaps(ImmutableIntIntMap intToIntMap) {
//        ArrayList<PBIntToIntMap> pbIntToIntMaps = new ArrayList<>();
//        intToIntMap.forEachKeyValue((source, target) -> pbIntToIntMaps.add(PBIntToIntMap.newBuilder()
//                .setSource(source)
//                .setTarget(target)
//                .build()
//        ));
//        return pbIntToIntMaps;
//    }
//    private List<PBIntToMultipleIntMap> createPBIntToMultipleIntMaps(ImmutableIntObjectMap intToMultipleIntMap){
//        List<PBIntToMultipleIntMap> pbIntToMultipleIntMaps = new ArrayList<>();
//        intToMultipleIntMap.keySet().forEach(source -> {
//            final ArrayList<Integer> targets = new ArrayList<>();
//            ((ImmutableIntList) intToMultipleIntMap.get(source)).forEach(target -> targets.add(target));
//            pbIntToMultipleIntMaps.add(PBIntToMultipleIntMap.newBuilder()
//                    .setSource(source)
//                    .addAllTarget(targets)
//                    .build()
//            );
//        });
//        return  pbIntToMultipleIntMaps;
//    }
//
//    private PBPlanarPoint createPBPlanaPoint(PlanarPoint planarPoint){
//        return PBPlanarPoint.newBuilder()
//                .setX(planarPoint.x())
//                .setY(planarPoint.y())
//                .build();
//    }
//
//    private PBSpatialPoint createPBSpatialPoint(SpatialPoint spatialPoint){
//        return PBSpatialPoint.newBuilder()
//                .setX(spatialPoint.x())
//                .setY(spatialPoint.y())
//                .setZ(spatialPoint.z())
//                .build();
//    }
}
