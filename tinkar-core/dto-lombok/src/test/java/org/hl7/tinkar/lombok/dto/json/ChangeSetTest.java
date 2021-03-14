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
package org.hl7.tinkar.lombok.dto.json;

import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.lombok.dto.*;
import org.hl7.tinkar.lombok.dto.binary.MarshalExceptionUnchecked;
import org.hl7.tinkar.lombok.dto.binary.Marshalable;
import org.hl7.tinkar.lombok.dto.binary.TinkarByteArrayOutput;
import org.junit.jupiter.api.Test;

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
        assertThrows(RuntimeException.class, () -> Marshalable.makeVersion(clazz, out.toInput(), TestUtil.makePublicId()));
    }

    private void testInvalidMarshalVersionForSemanticVersion(Class clazz, int marshalVersion) {
        TinkarByteArrayOutput out = TinkarByteArrayOutput.make(-1); //an invalid version number...
        out.toInput();
        assertThrows(RuntimeException.class, () -> Marshalable.makeSemanticVersion(clazz, out.toInput(),
                TestUtil.makePublicId(), TestUtil.makePublicId(), TestUtil.makePublicId()));
    }

    private void testReadIOException(Class clazz) {
        assertThrows(RuntimeException.class, () -> Marshalable.make(clazz, new TinkarFailingInput()));
    }

    private void testReadSemanticVersionIOException(Class clazz, int marshalVersion) {
        assertThrows(RuntimeException.class, () -> Marshalable.makeSemanticVersion(clazz, new TinkarFailingInput(),
                TestUtil.makePublicId(), TestUtil.makePublicId(), TestUtil.makePublicId()));
    }

    private void testReadVersionIOException(Class clazz) {
        assertThrows(RuntimeException.class, () -> Marshalable.makeVersion(clazz, new TinkarFailingInput(), TestUtil.makePublicId()));
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
        assertTrue(component.state().equals(newestComponent.state()));
        assertTrue(component.state().publicId().equals(newestComponent.state().publicId()));
        assertTrue(component.time() == newestComponent.time());
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

        PublicId componentPublicId = TestUtil.makePublicId();
        ConceptVersionDTO component = new ConceptVersionDTO(componentPublicId, TestUtil.makeStampForChangeSet());

        ConceptVersionDTO newComponent = JsonMarshalable.makeVersion(ConceptVersionDTO.class, component.toJsonString(), componentPublicId);
        assertEquals(component, newComponent);

        assertThrows(MarshalExceptionUnchecked.class, () -> JsonMarshalable.makeVersion(ConceptVersionDTO.class, "not a good string...",
                componentPublicId));

        ConceptVersionDTO newerComponent = Marshalable.makeVersion(ConceptVersionDTO.class, component.marshal(), componentPublicId);
        assertEquals(component, newerComponent);

        testInvalidMarshalVersionForVersion(ConceptVersionDTO.class);
        testReadVersionIOException(ConceptVersionDTO.class);
        testWriteIOExceptionForVersion(component);

        assertTrue(component.equals(newerComponent));
        assertTrue(component.hashCode() == newerComponent.hashCode());
        assertFalse(component.equals(new ConceptVersionDTO(component.publicId(), TestUtil.makeStampForChangeSet())));
        assertFalse(component.equals("will be false"));
        assertEquals(component, ConceptVersionDTO.make(component));
    }

    @Test
    public void testDefinitionForSemanticVersionForChangeSet() {
        PublicId componentPublicId = TestUtil.makePublicId();
        PatternForSemanticVersionDTO component = TestUtil.makeDefinitionForSemanticVersionForChangeSet(componentPublicId);
        
        PatternForSemanticVersionDTO newComponent = JsonMarshalable.makeVersion(PatternForSemanticVersionDTO.class, component.toJsonString(), componentPublicId);
        assertEquals(component, newComponent); 
        
        PatternForSemanticVersionDTO newerComponent = Marshalable.makeVersion(PatternForSemanticVersionDTO.class, component.marshal(), componentPublicId);
        assertEquals(component, newerComponent);

        testInvalidMarshalVersionForVersion(PatternForSemanticVersionDTO.class);
        testReadVersionIOException(PatternForSemanticVersionDTO.class);
        testWriteIOExceptionForVersion(component);
        assertTrue(component.equals(newerComponent));
        assertFalse(component.equals(TestUtil.makeDefinitionForSemanticVersionForChangeSet(componentPublicId)));
        assertFalse(component.equals("will be false"));
        assertTrue(component.publicId().equals(newerComponent.publicId()));
        assertEquals(component, PatternForSemanticVersionDTO.make(component));
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
        assertTrue(component.publicId().equals(newerComponent.publicId()));
        PublicId newConceptId = TestUtil.makePublicId();
        assertFalse(component.equals(new ConceptChronologyDTO(newConceptId, TestUtil.makePublicId(), TestUtil.makeConceptVersionList(newConceptId))));
        newConceptId = TestUtil.makePublicId();
        assertFalse(component.equals(new ConceptChronologyDTO(newConceptId, TestUtil.makePublicId(), TestUtil.makeConceptVersionList(newConceptId))));
        assertFalse(component.equals("will be false"));
        assertEquals(component, ConceptChronologyDTO.make(component));
    }


    @Test
    public void testDefinitionForSemanticChronologyForChangeSet() {
         PatternForSemanticChronologyDTO component = TestUtil.makeDefinitionForSemanticChronology();
        
        PatternForSemanticChronologyDTO newComponent = JsonMarshalable.make(PatternForSemanticChronologyDTO.class, component.toJsonString());
        assertEquals(component, newComponent); 
        
        PatternForSemanticChronologyDTO newerComponent = Marshalable.make(PatternForSemanticChronologyDTO.class, component.marshal());
        assertEquals(component, newerComponent);

        testInvalidMarshalVersion(PatternForSemanticChronologyDTO.class);
        testReadIOException(PatternForSemanticChronologyDTO.class);
        testWriteIOException(component);
        assertTrue(component.equals(newerComponent));
        assertTrue(component.hashCode() == newerComponent.hashCode());
        assertFalse(component.equals(new PatternForSemanticChronologyDTO(TestUtil.makePublicId(), TestUtil.makePublicId(),
                TestUtil.makeDefinitionForSemanticVersionForChangeSetList(component.publicId()))));
        assertFalse(component.equals("will be false"));
        assertTrue(component.publicId().equals(newerComponent.publicId()));
        assertEquals(component, PatternForSemanticChronologyDTO.make(component));

    }
    
    
    @Test
    public void testSemanticVersionForChangeSet() {
        PublicId componentPublicId = TestUtil.makePublicId();
        PublicId patternForSemanticPublicId = TestUtil.makePublicId();
        PublicId referencedComponentPublicId = TestUtil.makePublicId();

        SemanticVersionDTO component = new SemanticVersionDTO(componentPublicId,
                patternForSemanticPublicId, referencedComponentPublicId, TestUtil.makeStampForChangeSet(), TestUtil.makeImmutableObjectList());
        
        SemanticVersionDTO newComponent = JsonMarshalable.makeSemanticVersion(SemanticVersionDTO.class, component.toJsonString(),
                componentPublicId, patternForSemanticPublicId, referencedComponentPublicId);
        assertEquals(component, newComponent);

        assertThrows(MarshalExceptionUnchecked.class, () -> JsonMarshalable.makeSemanticVersion(SemanticVersionDTO.class, "not a good string...",
                componentPublicId, patternForSemanticPublicId, referencedComponentPublicId));
        
        SemanticVersionDTO newerComponent = Marshalable.makeSemanticVersion(SemanticVersionDTO.class, component.marshal().toInput(),
                componentPublicId, patternForSemanticPublicId, referencedComponentPublicId);
        assertEquals(component, newerComponent);

        testInvalidMarshalVersionForSemanticVersion(SemanticVersionDTO.class, Marshalable.marshalVersion);
        testReadSemanticVersionIOException(SemanticVersionDTO.class, Marshalable.marshalVersion);
        testWriteIOExceptionForSemanticVersion(component);

        assertTrue(component.equals(newerComponent));
        assertTrue(component.hashCode() == newerComponent.hashCode());
        assertFalse(component.equals(new SemanticVersionDTO(componentPublicId,
                patternForSemanticPublicId, referencedComponentPublicId, TestUtil.makeStampForChangeSet(), TestUtil.makeImmutableObjectList())));
        assertFalse(component.equals("will be false"));
        assertTrue(component.publicId().equals(newerComponent.publicId()));
        assertTrue(component.patternForSemantic().equals(newerComponent.patternForSemantic()));
        assertTrue(component.referencedComponent().equals(newerComponent.referencedComponent()));
        assertTrue(component.patternForSemantic().publicId().equals(newerComponent.patternForSemantic().publicId()));
        assertTrue(component.referencedComponent().publicId().equals(newerComponent.referencedComponent().publicId()));
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
        assertFalse(component.equals(new SemanticChronologyDTO(TestUtil.makePublicId(),
                TestUtil.makePublicId(), TestUtil.makePublicId(), TestUtil.makeSemanticVersionForChangeSetList(TestUtil.makePublicId(),
                TestUtil.makePublicId(), TestUtil.makePublicId()))));
        assertFalse(component.equals("will be false"));
        assertTrue(component.publicId().equals(newerComponent.publicId()));
        assertTrue(component.patternForSemantic().equals(newerComponent.patternForSemantic()));
        assertTrue(component.referencedComponent().equals(newerComponent.referencedComponent()));
        assertEquals(component, SemanticChronologyDTO.make(component));
    }

}
