package org.hl7.tinkar.dto;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;

public class TestUtil {
    private static final Random rand = new Random();

    public static int getRandomSize(int maxSize) {
        return rand.nextInt(maxSize) + 1;
    }

    public static ImmutableList<UUID> makeUuidList() {
        return Lists.immutable.of(makeRandomUuidArray());
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
        return new StampDTO(TestUtil.makeUuidList(),
                TestUtil.makeUuidList(),
                Instant.now(),
                TestUtil.makeUuidList(),
                TestUtil.makeUuidList(),
                TestUtil.makeUuidList());
    }

    public static FieldDefinitionDTO makeFieldDefinitionForChangeSet() {
        return new FieldDefinitionDTO(makeUuidList(),
                makeUuidList(), makeUuidList());
    }

    public static ImmutableList<FieldDefinitionDTO> makeFieldDefinitionList() {
        int size = getRandomSize(2);
        FieldDefinitionDTO[] array = new FieldDefinitionDTO[size];
        for (int i = 0; i < size; i++) {
            array[i] = makeFieldDefinitionForChangeSet();
        }
        return Lists.immutable.of(array);
    }

    public static DefinitionForSemanticVersionDTO makeDefinitionForSemanticVersionForChangeSet(ImmutableList<UUID> componentUuidList) {

        return new DefinitionForSemanticVersionDTO(componentUuidList, makeStampForChangeSet(), makeUuidList(),
                makeFieldDefinitionList());
    }

    public static ImmutableList<DefinitionForSemanticVersionDTO> makeDefinitionForSemanticVersionForChangeSetList(ImmutableList<UUID> componentUuidList) {
        int size = getRandomSize(4);
        DefinitionForSemanticVersionDTO[] array = new DefinitionForSemanticVersionDTO[size];
        for (int i = 0; i < size; i++) {
            array[i] = makeDefinitionForSemanticVersionForChangeSet(componentUuidList);
        }
        return Lists.immutable.of(array);
    }

    public static ConceptVersionDTO[] makeConceptVersionArray(ImmutableList<UUID> conceptUuid) {
        int size = getRandomSize(7);
        ConceptVersionDTO[] array = new ConceptVersionDTO[size];
        for (int i = 0; i < size; i++) {
            array[i] = new ConceptVersionDTO(conceptUuid, makeStampForChangeSet());
        }
        return array;
    }

    public static ImmutableList<ConceptVersionDTO> makeConceptVersionList(ImmutableList<UUID> conceptUuid) {
        return Lists.immutable.of(makeConceptVersionArray(conceptUuid));
    }

    public static ImmutableList<SemanticVersionDTO> makeSemanticVersionForChangeSetList(ImmutableList<UUID> componentUuids,
                                                                                         ImmutableList<UUID> definitionForSemanticUuids,
                                                                                         ImmutableList<UUID> referencedComponentUuids) {
        int size = getRandomSize(7);
        SemanticVersionDTO[] array = new SemanticVersionDTO[size];
        for (int i = 0; i < size; i++) {
            array[i] = new SemanticVersionDTO(componentUuids, definitionForSemanticUuids,
                    referencedComponentUuids, makeStampForChangeSet(), makeImmutableObjectList());
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
                Instant.now(),
                new ConceptDTO(makeUuidList()),
                new DefinitionForSemanticDTO(makeUuidList()),
                new SemanticDTO(makeUuidList(), makeUuidList(), makeUuidList())
        };
        return array;
    }


    public static ImmutableList<Object> makeImmutableObjectList() {
        return Lists.immutable.of(makeObjectArray());
    }

    public static ConceptChronologyDTO makeConceptChronology() {
        ImmutableList<UUID> componentUuidList = makeUuidList();
        return new ConceptChronologyDTO(componentUuidList, makeUuidList(), makeConceptVersionList(componentUuidList));
    }

    public static DefinitionForSemanticChronologyDTO makeDefinitionForSemanticChronology() {
        ImmutableList<UUID> componentUuidList = makeUuidList();
        return new DefinitionForSemanticChronologyDTO(componentUuidList, makeUuidList(), makeDefinitionForSemanticVersionForChangeSetList(componentUuidList));
    }

    public static SemanticChronologyDTO makeSemanticChronology() {
        ImmutableList<UUID> componentUuids = makeUuidList();
        ImmutableList<UUID> definitionForSemanticUuids = makeUuidList();
        ImmutableList<UUID> referencedComponentUuids = makeUuidList();

        return new SemanticChronologyDTO(componentUuids,
                definitionForSemanticUuids, referencedComponentUuids,
                makeSemanticVersionForChangeSetList(componentUuids,
                        definitionForSemanticUuids, referencedComponentUuids));
    }
}
