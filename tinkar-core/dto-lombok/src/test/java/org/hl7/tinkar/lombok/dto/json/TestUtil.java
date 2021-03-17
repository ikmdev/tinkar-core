package org.hl7.tinkar.lombok.dto.json;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.id.PublicIds;
import org.hl7.tinkar.common.id.VertexId;
import org.hl7.tinkar.common.id.VertexIds;
import org.hl7.tinkar.lombok.dto.graph.VertexDTO;
import org.hl7.tinkar.lombok.dto.*;
import org.hl7.tinkar.lombok.dto.graph.VertexDTOBuilder;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;

public class TestUtil {
    private static final Random rand = new Random();


    public static VertexDTO emptyVertex() {
        VertexId vertexId = VertexIds.newRandom();
        return VertexDTOBuilder.builder()
                .vertexIdMsb(vertexId.mostSignificantBits())
                .vertexIdLsb(vertexId.leastSignificantBits())
                .meaning(VertexDTO.abstractObject(ConceptDTOBuilder.builder().publicId(TestUtil.makePublicId()).build()))
                .properties(Maps.immutable.empty()).build();
    }

    public static VertexDTO vertexWithProperties(MutableMap<ConceptDTO, Object> propertyMap) {
        VertexId vertexId = VertexIds.newRandom();
        return VertexDTOBuilder.builder()
                .vertexIdMsb(vertexId.mostSignificantBits())
                .vertexIdLsb(vertexId.leastSignificantBits())
                .meaning(VertexDTO.abstractObject(ConceptDTOBuilder.builder().publicId(TestUtil.makePublicId()).build()))
                .properties(propertyMap.toImmutable()).build();
    }

    public static int getRandomSize(int maxSize) {
        return rand.nextInt(maxSize) + 1;
    }

    public static ImmutableList<UUID> makeUuidList() {
        return Lists.immutable.of(makeRandomUuidArray());
    }

    public static PublicId makePublicId() {
        return PublicIds.of(makeRandomUuidArray());
    }

    public static UUID[] makeRandomUuidArray() {
        int size = getRandomSize(2);
        UUID[] array = new UUID[size];
        for (int i = 0; i < size; i++) {
            array[i] = UUID.randomUUID();
        }
        return array;
    }

    public static StampDTO makeStampForChangeSet() {
        return new StampDTO(TestUtil.makePublicId(),
                TestUtil.makePublicId(),
                Instant.now().toEpochMilli(),
                TestUtil.makePublicId(),
                TestUtil.makePublicId(),
                TestUtil.makePublicId());
    }

    public static FieldDefinitionDTO makeFieldDefinitionForChangeSet() {
        return new FieldDefinitionDTO(makePublicId(),
                makePublicId(), makePublicId());
    }

    public static ImmutableList<FieldDefinitionDTO> makeFieldDefinitionList() {
        int size = getRandomSize(2);
        FieldDefinitionDTO[] array = new FieldDefinitionDTO[size];
        for (int i = 0; i < size; i++) {
            array[i] = makeFieldDefinitionForChangeSet();
        }
        return Lists.immutable.of(array);
    }

    public static TypePatternVersionDTO makeTypePatternVersionForChangeSet(PublicId componentPublicId) {

        return new TypePatternVersionDTO(componentPublicId, makeStampForChangeSet(), makePublicId(),
                makePublicId(), makeFieldDefinitionList());
    }

    public static ImmutableList<TypePatternVersionDTO> makeTypePatternVersionForChangeSetList(PublicId componentPublicId) {
        int size = getRandomSize(4);
        TypePatternVersionDTO[] array = new TypePatternVersionDTO[size];
        for (int i = 0; i < size; i++) {
            array[i] = makeTypePatternVersionForChangeSet(componentPublicId);
        }
        return Lists.immutable.of(array);
    }

    public static ConceptVersionDTO[] makeConceptVersionArray(PublicId conceptPublicId) {
        int size = getRandomSize(7);
        ConceptVersionDTO[] array = new ConceptVersionDTO[size];
        for (int i = 0; i < size; i++) {
            array[i] = new ConceptVersionDTO(conceptPublicId, makeStampForChangeSet());
        }
        return array;
    }

    public static ImmutableList<ConceptVersionDTO> makeConceptVersionList(PublicId conceptPublicId) {
        return Lists.immutable.of(makeConceptVersionArray(conceptPublicId));
    }

    public static ImmutableList<SemanticVersionDTO> makeSemanticVersionForChangeSetList(PublicId componentPublicId,
                                                                                        PublicId typePatternPublicId,
                                                                                        PublicId referencedComponentPublicId) {
        int size = getRandomSize(7);
        SemanticVersionDTO[] array = new SemanticVersionDTO[size];
        for (int i = 0; i < size; i++) {
            array[i] = new SemanticVersionDTO(componentPublicId, makeStampForChangeSet(), makeImmutableObjectList());
        }
        return Lists.immutable.of(array);
    }

    public static Object[] makeObjectArrayOld() {
        int size = getRandomSize(7);
        Object[] array = new Object[size];
        for (int i = 0; i < size; i++) {
            array[i] = Integer.toString(i);
        }
        return array;
    }

    public static Object[] makeObjectArray() {
        Object[] array = new Object[] {
                1,
                (float) 2.0,
                "a string",
                Instant.ofEpochMilli(Instant.now().toEpochMilli()),
                new ConceptDTO(makePublicId()),
                new TypePatternDTO(makePublicId()),
                new SemanticDTO(makePublicId())
        };
        return array;
    }


    public static ImmutableList<Object> makeImmutableObjectList() {
        return Lists.immutable.of(makeObjectArray());
    }

    public static ConceptChronologyDTO makeConceptChronology() {
        PublicId componentPublicId = makePublicId();
        return new ConceptChronologyDTO(componentPublicId, makeConceptVersionList(componentPublicId));
    }

    public static TypePatternChronologyDTO makeTypePatternChronology() {
        PublicId componentPublicId = makePublicId();
        return new TypePatternChronologyDTO(componentPublicId, makeTypePatternVersionForChangeSetList(componentPublicId));
    }

    public static SemanticChronologyDTO makeSemanticChronology() {
        PublicId componentPublicId = makePublicId();
        PublicId typePatternPublicId = makePublicId();
        PublicId referencedComponentPublicId = makePublicId();

        return new SemanticChronologyDTO(componentPublicId,
                typePatternPublicId, referencedComponentPublicId,
                makeSemanticVersionForChangeSetList(componentPublicId,
                        typePatternPublicId, referencedComponentPublicId));
    }
}
