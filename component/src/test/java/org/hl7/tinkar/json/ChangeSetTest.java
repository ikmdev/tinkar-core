/*
 * Copyright 2020 kec.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hl7.tinkar.json;

import java.io.IOException;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.binary.MarshalExceptionUnchecked;
import org.hl7.tinkar.binary.Marshalable;
import org.hl7.tinkar.binary.TinkarByteArrayOutput;
import org.hl7.tinkar.binary.TinkarInput;
import org.hl7.tinkar.dto.*;
import org.hl7.tinkar.dto.ConceptChronologyDTO;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author kec
 */
public class ChangeSetTest {
    
    private static final Random rand = new Random();
    
    private static int getRandomSize(int maxSize) {
        return rand.nextInt(maxSize) + 1;
    }

    protected static ImmutableList<UUID> makeUuidList() {
        return Lists.immutable.of(makeUuidArray());
    }

    private static UUID[] makeUuidArray() {
        int size = getRandomSize(2);
        UUID[] array = new UUID[size];
        for (int i = 0; i < size; i++) {
            array[i] = UUID.randomUUID();
        }
        return array;
    }

    private static StampDTO makeStampForChangeSet() {
        return new StampDTO(makeUuidList(),
                    Instant.now(),
                    makeUuidList(),
                    makeUuidList(),
                    makeUuidList());
    }

    private static FieldDefinitionDTO makeFieldDefinitionForChangeSet() {
        return new FieldDefinitionDTO(makeUuidList(),
                makeUuidList());
    }

    private static ImmutableList<FieldDefinitionDTO> makeFieldDefinitionList() {
        int size = getRandomSize(2);
        FieldDefinitionDTO[] array = new FieldDefinitionDTO[size];
        for (int i = 0; i < size; i++) {
            array[i] = makeFieldDefinitionForChangeSet();
        }
        return Lists.immutable.of(array);
    }

    private static DefinitionForSemanticVersionDTO makeDefinitionForSemanticVersionForChangeSet(ImmutableList<UUID> componentUuidList) {
        
        return new DefinitionForSemanticVersionDTO(componentUuidList, makeStampForChangeSet(), makeUuidList(),
                makeFieldDefinitionList());
    }
    
    private static ImmutableList<DefinitionForSemanticVersionDTO> makeDefinitionForSemanticVersionForChangeSetList(ImmutableList<UUID> componentUuidList) {
        int size = getRandomSize(4);
        DefinitionForSemanticVersionDTO[] array = new DefinitionForSemanticVersionDTO[size];
        for (int i = 0; i < size; i++) {
            array[i] = makeDefinitionForSemanticVersionForChangeSet(componentUuidList);
        }
        return Lists.immutable.of(array);
    }

    private static ConceptVersionDTO[] makeConceptVersionArray(ImmutableList<UUID> conceptUuid) {
        int size = getRandomSize(7);
        ConceptVersionDTO[] array = new ConceptVersionDTO[size];
        for (int i = 0; i < size; i++) {
            array[i] = new ConceptVersionDTO(conceptUuid, makeStampForChangeSet());
        }
        return array;
    }

    private static ImmutableList<ConceptVersionDTO> makeConceptVersionList(ImmutableList<UUID> conceptUuid) {
        return Lists.immutable.of(makeConceptVersionArray(conceptUuid));
    }

    private static ImmutableList<SemanticVersionDTO> makeSemanticVersionForChangeSetList(ImmutableList<UUID> componentUuids,
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

    private static Object[] makeObjectArray() {
        int size = getRandomSize(7);
        Object[] array = new Object[size];
        for (int i = 0; i < size; i++) {
            array[i] = Integer.toString(i);
        }
        return array;
    }


    private static ImmutableList<Object> makeImmutableObjectList() {
        return Lists.immutable.of(makeObjectArray());
    }


    private void testWriteIOException(Marshalable marshalable) {
        Assertions.assertThrows(RuntimeException.class, () -> marshalable.marshal(new TinkarFailingOutput()));
    }

    private void testWriteIOExceptionForVersion(Marshalable marshalable) {
        Assertions.assertThrows(RuntimeException.class, () -> marshalable.marshal(new TinkarFailingOutput()));
    }


    private void testWriteIOExceptionForSemanticVersion(Marshalable marshalable) {
        Assertions.assertThrows(RuntimeException.class, () -> marshalable.marshal(new TinkarFailingOutput()));
    }

    private void testInvalidMarshalVersion(Class clazz) {
        try {
            TinkarByteArrayOutput out = TinkarByteArrayOutput.make();
            out.writeInt(-1); //an invalid version number...
            out.toInput();
            Assertions.assertThrows(RuntimeException.class, () -> Marshalable.make(clazz, out.toInput()));
        } catch (IOException ex) {
            fail(ex);
        }
    }

    private void testInvalidMarshalVersionForVersion(Class clazz) {
        try {
            TinkarByteArrayOutput out = TinkarByteArrayOutput.make();
            out.writeInt(-1); //an invalid version number...
            out.toInput();
            Assertions.assertThrows(RuntimeException.class, () -> Marshalable.makeVersion(clazz, out.toInput(), makeUuidList()));
        } catch (IOException ex) {
            fail(ex);
        }
    }

    private void testInvalidMarshalVersionForSemanticVersion(Class clazz) {
        try {
            TinkarByteArrayOutput out = TinkarByteArrayOutput.make();
            out.writeInt(-1); //an invalid version number...
            out.toInput();
            Assertions.assertThrows(RuntimeException.class, () -> Marshalable.makeSemanticVersion(clazz, out.toInput(),
                    makeUuidList(), makeUuidList(), makeUuidList()));
        } catch (IOException ex) {
            fail(ex);
        }
    }

    private void testReadIOException(Class clazz) {
        Assertions.assertThrows(RuntimeException.class, () -> Marshalable.make(clazz, new TinkarFailingInput()));
    }

    private void testReadSemanticVersionIOException(Class clazz) {
        Assertions.assertThrows(RuntimeException.class, () -> Marshalable.makeSemanticVersion(clazz, new TinkarFailingInput(),
                makeUuidList(), makeUuidList(), makeUuidList()));
    }

    private void testReadVersionIOException(Class clazz) {
        Assertions.assertThrows(RuntimeException.class, () -> Marshalable.makeVersion(clazz, new TinkarFailingInput(), makeUuidList()));
    }

    @Test
    public void testStampForChangeSet() {
        StampDTO component = makeStampForChangeSet();
        StampDTO newStamp = JsonMarshalable.make(StampDTO.class, component.toJsonString());
        assertEquals(component, newStamp);

        assertThrows(MarshalExceptionUnchecked.class, () -> JsonMarshalable.make(StampDTO.class, "not a good string..."));

        StampDTO newerComponent = Marshalable.make(StampDTO.class, component.marshal());
        assertEquals(component, newerComponent);

        StampDTO newestComponent = Marshalable.make(StampDTO.class, component.marshal().getBytes());
        assertEquals(component, newestComponent);

        testInvalidMarshalVersion(StampDTO.class);
        testReadIOException(StampDTO.class);
        testWriteIOException(component);
        assertTrue(component.equals(newerComponent));
        assertTrue(component.hashCode() == newerComponent.hashCode());
        assertFalse(component.equals(makeStampForChangeSet()));
        assertFalse(component.equals("will be false"));
        assertTrue(component.getStatus().equals(newestComponent.getStatus()));
        assertTrue(component.getStatus().getComponentUuids().equals(newestComponent.getStatus().getComponentUuids()));
        assertTrue(component.getTime().equals(newestComponent.getTime()));
        assertTrue(component.getAuthor().equals(newestComponent.getAuthor()));
        assertTrue(component.getModule().equals(newestComponent.getModule()));
        assertTrue(component.getPath().equals(newestComponent.getPath()));
    }

    @Test
    public void testStampCommentForChangeSet() {
        
        StampCommentDTO component = new StampCommentDTO(makeStampForChangeSet(), "comment");
        StampCommentDTO newStampComment = JsonMarshalable.make(StampCommentDTO.class, component.toJsonString());
        assertEquals(component, newStampComment);
        
        StampCommentDTO newerComponent = Marshalable.make(StampCommentDTO.class, component.marshal());
        assertEquals(component, newerComponent);

        testInvalidMarshalVersion(StampCommentDTO.class);
        testReadIOException(StampCommentDTO.class);
        testWriteIOException(component);
        assertTrue(component.equals(newerComponent));
        assertTrue(component.hashCode() == newerComponent.hashCode());
        assertFalse(component.equals(new StampCommentDTO(makeStampForChangeSet(), "comment")));
        assertFalse(component.equals("will be false"));
        assertTrue(component.getStamp().equals(newerComponent.getStamp()));
        assertTrue(component.getComment().equals(newerComponent.getComment()));
    }
    
    @Test
    public void testFieldDefinitionForChangeSet() {
        
        FieldDefinitionDTO component = makeFieldDefinitionForChangeSet();
        
        FieldDefinitionDTO newComponent = JsonMarshalable.make(FieldDefinitionDTO.class, component.toJsonString());
        assertEquals(component, newComponent); 
        
        FieldDefinitionDTO newerComponent = Marshalable.make(FieldDefinitionDTO.class, component.marshal());
        assertEquals(component, newerComponent);

        testInvalidMarshalVersion(FieldDefinitionDTO.class);
        testReadIOException(FieldDefinitionDTO.class);
        testWriteIOException(component);
        assertTrue(component.equals(newerComponent));
        assertTrue(component.hashCode() == newerComponent.hashCode());
        assertFalse(component.equals(makeFieldDefinitionForChangeSet()));
        assertFalse(component.equals("will be false"));
    }

    @Test
    public void testConceptVersionForChangeSet() {

        ImmutableList<UUID> componentUuidList = makeUuidList();
        ConceptVersionDTO component = new ConceptVersionDTO(componentUuidList, makeStampForChangeSet());

        ConceptVersionDTO newComponent = JsonMarshalable.makeVersion(ConceptVersionDTO.class, component.toJsonString(), componentUuidList);
        assertEquals(component, newComponent);

        assertThrows(MarshalExceptionUnchecked.class, () -> JsonMarshalable.makeVersion(ConceptVersionDTO.class, "not a good string...",
                componentUuidList));

        ConceptVersionDTO newerComponent = Marshalable.makeVersion(ConceptVersionDTO.class, component.marshal(), componentUuidList);
        assertEquals(component, newerComponent);

        testInvalidMarshalVersionForVersion(ConceptVersionDTO.class);
        testReadVersionIOException(ConceptVersionDTO.class);
        testWriteIOExceptionForVersion(component);

        assertTrue(component.equals(newerComponent));
        assertTrue(component.hashCode() == newerComponent.hashCode());
        assertFalse(component.equals(new ConceptVersionDTO(component.getComponentUuids(), makeStampForChangeSet())));
        assertFalse(component.equals("will be false"));
    }

    @Test
    public void testDefinitionForSemanticVersionForChangeSet() {
        ImmutableList<UUID> componentUuidList = makeUuidList();
        DefinitionForSemanticVersionDTO component = makeDefinitionForSemanticVersionForChangeSet(componentUuidList);
        
        DefinitionForSemanticVersionDTO newComponent = JsonMarshalable.makeVersion(DefinitionForSemanticVersionDTO.class, component.toJsonString(), componentUuidList);
        assertEquals(component, newComponent); 
        
        DefinitionForSemanticVersionDTO newerComponent = Marshalable.makeVersion(DefinitionForSemanticVersionDTO.class, component.marshal(), componentUuidList);
        assertEquals(component, newerComponent);

        testInvalidMarshalVersionForVersion(DefinitionForSemanticVersionDTO.class);
        testReadVersionIOException(DefinitionForSemanticVersionDTO.class);
        testWriteIOExceptionForVersion(component);
        assertTrue(component.equals(newerComponent));
        assertFalse(component.equals(makeDefinitionForSemanticVersionForChangeSet(componentUuidList)));
        assertFalse(component.equals("will be false"));
        assertTrue(component.getComponentUuids().equals(newerComponent.getComponentUuids()));
    }

    @Test
    public void testConceptChronologyForChangeSet() {
        ImmutableList<UUID> componentUuidList = makeUuidList();
        ConceptChronologyDTO component = new ConceptChronologyDTO(componentUuidList, makeUuidList(), makeConceptVersionList(componentUuidList));
        
        ConceptChronologyDTO newComponent = JsonMarshalable.make(ConceptChronologyDTO.class, component.toJsonString());
        assertEquals(component, newComponent); 
        
        ConceptChronologyDTO newerComponent = Marshalable.make(ConceptChronologyDTO.class, component.marshal());
        assertEquals(component, newerComponent);

        testInvalidMarshalVersion(ConceptChronologyDTO.class);
        testReadIOException(ConceptChronologyDTO.class);
        testWriteIOException(component);
        assertTrue(component.equals(newerComponent));
        assertTrue(component.hashCode() == newerComponent.hashCode());
        assertTrue(component.getComponentUuids().equals(newerComponent.getComponentUuids()));
        ImmutableList<UUID> newConceptId = makeUuidList();
        assertFalse(component.equals(new ConceptChronologyDTO(newConceptId, makeUuidList(), makeConceptVersionList(newConceptId))));
        newConceptId = makeUuidList();
        assertFalse(component.equals(new ConceptChronologyDTO(newConceptId, makeUuidList(), makeConceptVersionList(newConceptId))));
        assertFalse(component.equals("will be false"));
    }


    @Test
    public void testDefinitionForSemanticChronologyForChangeSet() {
        ImmutableList<UUID> componentUuidList = makeUuidList();
        DefinitionForSemanticChronologyDTO component = new DefinitionForSemanticChronologyDTO(componentUuidList, makeUuidList(), makeDefinitionForSemanticVersionForChangeSetList(componentUuidList));
        
        DefinitionForSemanticChronologyDTO newComponent = JsonMarshalable.make(DefinitionForSemanticChronologyDTO.class, component.toJsonString());
        assertEquals(component, newComponent); 
        
        DefinitionForSemanticChronologyDTO newerComponent = Marshalable.make(DefinitionForSemanticChronologyDTO.class, component.marshal());
        assertEquals(component, newerComponent);

        testInvalidMarshalVersion(DefinitionForSemanticChronologyDTO.class);
        testReadIOException(DefinitionForSemanticChronologyDTO.class);
        testWriteIOException(component);
        assertTrue(component.equals(newerComponent));
        assertTrue(component.hashCode() == newerComponent.hashCode());
        assertFalse(component.equals(new DefinitionForSemanticChronologyDTO(makeUuidList(), makeUuidList(),
                makeDefinitionForSemanticVersionForChangeSetList(componentUuidList))));
        assertFalse(component.equals("will be false"));
        assertTrue(component.getComponentUuids().equals(newerComponent.getComponentUuids()));

    }
    
    
    @Test
    public void testSemanticVersionForChangeSet() {
        ImmutableList<UUID> componentUuids = makeUuidList();
        ImmutableList<UUID> definitionForSemanticUuids = makeUuidList();
        ImmutableList<UUID> referencedComponentUuids = makeUuidList();

        SemanticVersionDTO component = new SemanticVersionDTO(componentUuids,
                definitionForSemanticUuids, referencedComponentUuids, makeStampForChangeSet(), makeImmutableObjectList());
        
        SemanticVersionDTO newComponent = JsonMarshalable.makeSemanticVersion(SemanticVersionDTO.class, component.toJsonString(),
                componentUuids, definitionForSemanticUuids, referencedComponentUuids);
        assertEquals(component, newComponent);

        assertThrows(MarshalExceptionUnchecked.class, () -> JsonMarshalable.makeSemanticVersion(SemanticVersionDTO.class, "not a good string...",
                componentUuids, definitionForSemanticUuids, referencedComponentUuids));
        
        SemanticVersionDTO newerComponent = Marshalable.makeSemanticVersion(SemanticVersionDTO.class, component.marshal().toInput(),
                componentUuids, definitionForSemanticUuids, referencedComponentUuids);
        assertEquals(component, newerComponent);

        testInvalidMarshalVersionForSemanticVersion(SemanticVersionDTO.class);
        testReadSemanticVersionIOException(SemanticVersionDTO.class);
        testWriteIOExceptionForSemanticVersion(component);

        assertTrue(component.equals(newerComponent));
        assertTrue(component.hashCode() == newerComponent.hashCode());
        assertFalse(component.equals(new SemanticVersionDTO(componentUuids,
                definitionForSemanticUuids, referencedComponentUuids, makeStampForChangeSet(), makeImmutableObjectList())));
        assertFalse(component.equals("will be false"));
        assertTrue(component.getComponentUuids().equals(newerComponent.getComponentUuids()));
        assertTrue(component.getDefinitionForSemantic().equals(newerComponent.getDefinitionForSemantic()));
        assertTrue(component.getReferencedComponent().equals(newerComponent.getReferencedComponent()));
        assertTrue(component.getDefinitionForSemantic().getComponentUuids().equals(newerComponent.getDefinitionForSemantic().getComponentUuids()));
        assertTrue(component.getReferencedComponent().getComponentUuids().equals(newerComponent.getReferencedComponent().getComponentUuids()));

    }
    
   
    @Test
    public void testSemanticChronologyForChangeSet() {
        ImmutableList<UUID> componentUuids = makeUuidList();
        ImmutableList<UUID> definitionForSemanticUuids = makeUuidList();
        ImmutableList<UUID> referencedComponentUuids = makeUuidList();

        SemanticChronologyDTO component = new SemanticChronologyDTO(componentUuids,
                definitionForSemanticUuids, referencedComponentUuids,
                makeSemanticVersionForChangeSetList(componentUuids,
                    definitionForSemanticUuids, referencedComponentUuids));
        
        SemanticChronologyDTO newComponent = JsonMarshalable.make(SemanticChronologyDTO.class, component.toJsonString());
        assertEquals(component, newComponent); 
        
        SemanticChronologyDTO newerComponent = Marshalable.make(SemanticChronologyDTO.class, component.marshal());
        assertEquals(component, newerComponent);

        testInvalidMarshalVersion(SemanticChronologyDTO.class);
        testReadIOException(SemanticChronologyDTO.class);
        testWriteIOException(component);
        assertTrue(component.equals(newerComponent));
        assertTrue(component.hashCode() == newerComponent.hashCode());
        assertFalse(component.equals(new SemanticChronologyDTO(makeUuidList(),
                makeUuidList(), makeUuidList(), makeSemanticVersionForChangeSetList(makeUuidList(),
                makeUuidList(), makeUuidList()))));
        assertFalse(component.equals("will be false"));
        assertTrue(component.getComponentUuids().equals(newerComponent.getComponentUuids()));
        assertTrue(component.getDefinitionForSemantic().equals(newerComponent.getDefinitionForSemantic()));
        assertTrue(component.getReferencedComponent().equals(newerComponent.getReferencedComponent()));
    }
        
}
