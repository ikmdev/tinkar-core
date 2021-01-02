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
package org.hl7.tinkar.lombok.dto;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.lombok.dto.binary.MarshalExceptionUnchecked;
import org.hl7.tinkar.lombok.dto.binary.Marshalable;
import org.hl7.tinkar.lombok.dto.binary.TinkarByteArrayOutput;
import org.hl7.tinkar.lombok.dto.json.JsonMarshalable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author kec
 */
public class ChangeSetTest {


    private void testWriteIOException(Marshalable marshalable) {
        assertThrows(RuntimeException.class, () -> marshalable.marshal(new TinkarFailingOutput()));
    }

    private void testWriteIOExceptionForVersion(Marshalable marshalable) {
        assertThrows(RuntimeException.class, () -> marshalable.marshal(new TinkarFailingOutput()));
    }


    private void testWriteIOExceptionForSemanticVersion(Marshalable marshalable) {
        assertThrows(RuntimeException.class, () -> marshalable.marshal(new TinkarFailingOutput()));
    }

    private void testInvalidMarshalVersion(Class clazz) {
        TinkarByteArrayOutput out = TinkarByteArrayOutput.make(-1); //an invalid version number...
        out.toInput();
        assertThrows(RuntimeException.class, () -> Marshalable.make(clazz, out.toInput()));
    }

    private void testInvalidMarshalVersionForVersion(Class clazz) {
        TinkarByteArrayOutput out = TinkarByteArrayOutput.make(-1); //an invalid version number...
        out.toInput();
        assertThrows(RuntimeException.class, () -> Marshalable.makeVersion(clazz, out.toInput(), TestUtil.makeUuidList()));
    }

    private void testInvalidMarshalVersionForSemanticVersion(Class clazz, int marshalVersion) {
        TinkarByteArrayOutput out = TinkarByteArrayOutput.make(-1); //an invalid version number...
        out.toInput();
        assertThrows(RuntimeException.class, () -> Marshalable.makeSemanticVersion(clazz, out.toInput(),
                TestUtil.makeUuidList(), TestUtil.makeUuidList(), TestUtil.makeUuidList()));
    }

    private void testReadIOException(Class clazz) {
        assertThrows(RuntimeException.class, () -> Marshalable.make(clazz, new TinkarFailingInput()));
    }

    private void testReadSemanticVersionIOException(Class clazz, int marshalVersion) {
        assertThrows(RuntimeException.class, () -> Marshalable.makeSemanticVersion(clazz, new TinkarFailingInput(),
                TestUtil.makeUuidList(), TestUtil.makeUuidList(), TestUtil.makeUuidList()));
    }

    private void testReadVersionIOException(Class clazz) {
        assertThrows(RuntimeException.class, () -> Marshalable.makeVersion(clazz, new TinkarFailingInput(), TestUtil.makeUuidList()));
    }

    @Test
    public void testStampForChangeSet() {
        StampDTO component = TestUtil.makeStampForChangeSet();
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
        assertFalse(component.equals(TestUtil.makeStampForChangeSet()));
        assertFalse(component.equals("will be false"));
        assertTrue(component.status().equals(newestComponent.status()));
        assertTrue(component.status().componentUuids().equals(newestComponent.status().componentUuids()));
        assertTrue(component.time().equals(newestComponent.time()));
        assertTrue(component.author().equals(newestComponent.author()));
        assertTrue(component.module().equals(newestComponent.module()));
        assertTrue(component.path().equals(newestComponent.path()));
        assertEquals(component, StampDTO.make(component));
    }

    @Test
    public void testFieldDefinitionForChangeSet() {
        
        FieldDefinitionDTO component = TestUtil.makeFieldDefinitionForChangeSet();
        
        FieldDefinitionDTO newComponent = JsonMarshalable.make(FieldDefinitionDTO.class, component.toJsonString());
        assertEquals(component, newComponent); 
        
        FieldDefinitionDTO newerComponent = Marshalable.make(FieldDefinitionDTO.class, component.marshal());
        assertEquals(component, newerComponent);

        testInvalidMarshalVersion(FieldDefinitionDTO.class);
        testReadIOException(FieldDefinitionDTO.class);
        testWriteIOException(component);
        assertTrue(component.equals(newerComponent));
        assertTrue(component.hashCode() == newerComponent.hashCode());
        assertFalse(component.equals(TestUtil.makeFieldDefinitionForChangeSet()));
        assertFalse(component.equals("will be false"));
        assertEquals(component, FieldDefinitionDTO.make(component));
    }

    @Test
    public void testConceptVersionForChangeSet() {

        ImmutableList<UUID> componentUuidList = TestUtil.makeUuidList();
        ConceptVersionDTO component = new ConceptVersionDTO(componentUuidList, TestUtil.makeStampForChangeSet());

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
        assertFalse(component.equals(new ConceptVersionDTO(component.componentUuids(), TestUtil.makeStampForChangeSet())));
        assertFalse(component.equals("will be false"));
        assertEquals(component, ConceptVersionDTO.make(component));
    }

    @Test
    public void testDefinitionForSemanticVersionForChangeSet() {
        ImmutableList<UUID> componentUuidList = TestUtil.makeUuidList();
        DefinitionForSemanticVersionDTO component = TestUtil.makeDefinitionForSemanticVersionForChangeSet(componentUuidList);
        
        DefinitionForSemanticVersionDTO newComponent = JsonMarshalable.makeVersion(DefinitionForSemanticVersionDTO.class, component.toJsonString(), componentUuidList);
        assertEquals(component, newComponent); 
        
        DefinitionForSemanticVersionDTO newerComponent = Marshalable.makeVersion(DefinitionForSemanticVersionDTO.class, component.marshal(), componentUuidList);
        assertEquals(component, newerComponent);

        testInvalidMarshalVersionForVersion(DefinitionForSemanticVersionDTO.class);
        testReadVersionIOException(DefinitionForSemanticVersionDTO.class);
        testWriteIOExceptionForVersion(component);
        assertTrue(component.equals(newerComponent));
        assertFalse(component.equals(TestUtil.makeDefinitionForSemanticVersionForChangeSet(componentUuidList)));
        assertFalse(component.equals("will be false"));
        assertTrue(component.componentUuids().equals(newerComponent.componentUuids()));
        assertEquals(component, DefinitionForSemanticVersionDTO.make(component));
    }

    @Test
    public void testConceptChronologyForChangeSet() {
        ConceptChronologyDTO component = TestUtil.makeConceptChronology();
        
        ConceptChronologyDTO newComponent = JsonMarshalable.make(ConceptChronologyDTO.class, component.toJsonString());
        assertEquals(component, newComponent); 
        
        ConceptChronologyDTO newerComponent = Marshalable.make(ConceptChronologyDTO.class, component.marshal());
        assertEquals(component, newerComponent);

        testInvalidMarshalVersion(ConceptChronologyDTO.class);
        testReadIOException(ConceptChronologyDTO.class);
        testWriteIOException(component);
        assertTrue(component.equals(newerComponent));
        assertTrue(component.hashCode() == newerComponent.hashCode());
        assertTrue(component.componentUuids().equals(newerComponent.componentUuids()));
        ImmutableList<UUID> newConceptId = TestUtil.makeUuidList();
        assertFalse(component.equals(new ConceptChronologyDTO(newConceptId, TestUtil.makeUuidList(), TestUtil.makeConceptVersionList(newConceptId))));
        newConceptId = TestUtil.makeUuidList();
        assertFalse(component.equals(new ConceptChronologyDTO(newConceptId, TestUtil.makeUuidList(), TestUtil.makeConceptVersionList(newConceptId))));
        assertFalse(component.equals("will be false"));
        assertEquals(component, ConceptChronologyDTO.make(component));
    }


    @Test
    public void testDefinitionForSemanticChronologyForChangeSet() {
         DefinitionForSemanticChronologyDTO component = TestUtil.makeDefinitionForSemanticChronology();
        
        DefinitionForSemanticChronologyDTO newComponent = JsonMarshalable.make(DefinitionForSemanticChronologyDTO.class, component.toJsonString());
        assertEquals(component, newComponent); 
        
        DefinitionForSemanticChronologyDTO newerComponent = Marshalable.make(DefinitionForSemanticChronologyDTO.class, component.marshal());
        assertEquals(component, newerComponent);

        testInvalidMarshalVersion(DefinitionForSemanticChronologyDTO.class);
        testReadIOException(DefinitionForSemanticChronologyDTO.class);
        testWriteIOException(component);
        assertTrue(component.equals(newerComponent));
        assertTrue(component.hashCode() == newerComponent.hashCode());
        assertFalse(component.equals(new DefinitionForSemanticChronologyDTO(TestUtil.makeUuidList(), TestUtil.makeUuidList(),
                TestUtil.makeDefinitionForSemanticVersionForChangeSetList(component.componentUuids()))));
        assertFalse(component.equals("will be false"));
        assertTrue(component.componentUuids().equals(newerComponent.componentUuids()));
        assertEquals(component, DefinitionForSemanticChronologyDTO.make(component));

    }
    
    
    @Test
    public void testSemanticVersionForChangeSet() {
        ImmutableList<UUID> componentUuids = TestUtil.makeUuidList();
        ImmutableList<UUID> definitionForSemanticUuids = TestUtil.makeUuidList();
        ImmutableList<UUID> referencedComponentUuids = TestUtil.makeUuidList();

        SemanticVersionDTO component = new SemanticVersionDTO(componentUuids,
                definitionForSemanticUuids, referencedComponentUuids, TestUtil.makeStampForChangeSet(), TestUtil.makeImmutableObjectList());
        
        SemanticVersionDTO newComponent = JsonMarshalable.makeSemanticVersion(SemanticVersionDTO.class, component.toJsonString(),
                componentUuids, definitionForSemanticUuids, referencedComponentUuids);
        assertEquals(component, newComponent);

        assertThrows(MarshalExceptionUnchecked.class, () -> JsonMarshalable.makeSemanticVersion(SemanticVersionDTO.class, "not a good string...",
                componentUuids, definitionForSemanticUuids, referencedComponentUuids));
        
        SemanticVersionDTO newerComponent = Marshalable.makeSemanticVersion(SemanticVersionDTO.class, component.marshal().toInput(),
                componentUuids, definitionForSemanticUuids, referencedComponentUuids);
        assertEquals(component, newerComponent);

        testInvalidMarshalVersionForSemanticVersion(SemanticVersionDTO.class, Marshalable.marshalVersion);
        testReadSemanticVersionIOException(SemanticVersionDTO.class, Marshalable.marshalVersion);
        testWriteIOExceptionForSemanticVersion(component);

        assertTrue(component.equals(newerComponent));
        assertTrue(component.hashCode() == newerComponent.hashCode());
        assertFalse(component.equals(new SemanticVersionDTO(componentUuids,
                definitionForSemanticUuids, referencedComponentUuids, TestUtil.makeStampForChangeSet(), TestUtil.makeImmutableObjectList())));
        assertFalse(component.equals("will be false"));
        assertTrue(component.componentUuids().equals(newerComponent.componentUuids()));
        assertTrue(component.definitionForSemantic().equals(newerComponent.definitionForSemantic()));
        assertTrue(component.referencedComponent().equals(newerComponent.referencedComponent()));
        assertTrue(component.definitionForSemantic().componentUuids().equals(newerComponent.definitionForSemantic().componentUuids()));
        assertTrue(component.referencedComponent().componentUuids().equals(newerComponent.referencedComponent().componentUuids()));
        assertEquals(component, SemanticVersionDTO.make(component));
    }
    
   
    @Test
    public void testSemanticChronologyForChangeSet() {

        SemanticChronologyDTO component = TestUtil.makeSemanticChronology();
        
        SemanticChronologyDTO newComponent = JsonMarshalable.make(SemanticChronologyDTO.class, component.toJsonString());
        assertEquals(component, newComponent); 
        
        SemanticChronologyDTO newerComponent = Marshalable.make(SemanticChronologyDTO.class, component.marshal());
        assertEquals(component, newerComponent);

        testInvalidMarshalVersion(SemanticChronologyDTO.class);
        testReadIOException(SemanticChronologyDTO.class);
        testWriteIOException(component);
        assertTrue(component.equals(newerComponent));
        assertTrue(component.hashCode() == newerComponent.hashCode());
        assertFalse(component.equals(new SemanticChronologyDTO(TestUtil.makeUuidList(),
                TestUtil.makeUuidList(), TestUtil.makeUuidList(), TestUtil.makeSemanticVersionForChangeSetList(TestUtil.makeUuidList(),
                TestUtil.makeUuidList(), TestUtil.makeUuidList()))));
        assertFalse(component.equals("will be false"));
        assertTrue(component.componentUuids().equals(newerComponent.componentUuids()));
        assertTrue(component.definitionForSemantic().equals(newerComponent.definitionForSemantic()));
        assertTrue(component.referencedComponent().equals(newerComponent.referencedComponent()));
        assertEquals(component, SemanticChronologyDTO.make(component));
    }
        
}
