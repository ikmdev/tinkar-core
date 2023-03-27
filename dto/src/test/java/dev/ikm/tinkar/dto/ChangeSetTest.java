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
package dev.ikm.tinkar.dto;

import dev.ikm.tinkar.dto.binary.Marshalable;
import dev.ikm.tinkar.dto.binary.TinkarByteArrayOutput;
import dev.ikm.tinkar.common.id.PublicId;
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
                TestUtil.makePublicId()));
    }

    private void testReadIOException(Class clazz) {
        assertThrows(RuntimeException.class, () -> Marshalable.make(clazz, new TinkarFailingInput()));
    }

    private void testReadSemanticVersionIOException(Class clazz, int marshalVersion) {
        assertThrows(RuntimeException.class, () -> Marshalable.makeSemanticVersion(clazz, new TinkarFailingInput(),
                TestUtil.makePublicId()));
    }

    private void testReadVersionIOException(Class clazz) {
        assertThrows(RuntimeException.class, () -> Marshalable.makeVersion(clazz, new TinkarFailingInput(), TestUtil.makePublicId()));
    }

    @Test
    public void testStampForChangeSet() {
        StampDTO component = TestUtil.makeStampForChangeSet();

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
    public void testPatternVersionForChangeSet() {
        PublicId componentPublicId = TestUtil.makePublicId();
        PatternVersionDTO component = TestUtil.makePatternVersionForChangeSet(componentPublicId);

        PatternVersionDTO newerComponent = Marshalable.makeVersion(PatternVersionDTO.class, component.marshal(), componentPublicId);
        assertEquals(component, newerComponent);

        testInvalidMarshalVersionForVersion(PatternVersionDTO.class);
        testReadVersionIOException(PatternVersionDTO.class);
        testWriteIOExceptionForVersion(component);
        assertTrue(component.equals(newerComponent));
        assertFalse(component.equals(TestUtil.makePatternVersionForChangeSet(componentPublicId)));
        assertFalse(component.equals("will be false"));
        assertTrue(component.publicId().equals(newerComponent.publicId()));
        assertEquals(component, PatternVersionDTO.make(component));
    }

    @Test
    public void testConceptChronologyForChangeSet() {
        ConceptChronologyDTO component = TestUtil.makeConceptChronology();

        ConceptChronologyDTO newerComponent = Marshalable.make(ConceptChronologyDTO.class, component.marshal());
        assertEquals(component, newerComponent);

        testInvalidMarshalVersion(ConceptChronologyDTO.class);
        testReadIOException(ConceptChronologyDTO.class);
        testWriteIOException(component);
        assertTrue(component.equals(newerComponent));
        assertTrue(component.hashCode() == newerComponent.hashCode());
        assertTrue(component.publicId().equals(newerComponent.publicId()));
        PublicId newConceptId = TestUtil.makePublicId();
        assertFalse(component.equals(new ConceptChronologyDTO(newConceptId, TestUtil.makeConceptVersionList(newConceptId))));
        newConceptId = TestUtil.makePublicId();
        assertFalse(component.equals(new ConceptChronologyDTO(newConceptId, TestUtil.makeConceptVersionList(newConceptId))));
        assertFalse(component.equals("will be false"));
        assertEquals(component, ConceptChronologyDTO.make(component));
    }


    @Test
    public void testPatternChronologyForChangeSet() {
         PatternChronologyDTO component = TestUtil.makePatternChronology();

        PatternChronologyDTO newerComponent = Marshalable.make(PatternChronologyDTO.class, component.marshal());
        assertEquals(component, newerComponent);

        testInvalidMarshalVersion(PatternChronologyDTO.class);
        testReadIOException(PatternChronologyDTO.class);
        testWriteIOException(component);
        assertTrue(component.equals(newerComponent));
        assertTrue(component.hashCode() == newerComponent.hashCode());
        assertFalse(component.equals(new PatternChronologyDTO(TestUtil.makePublicId(),
                TestUtil.makePatternVersionForChangeSetList(component.publicId()))));
        assertFalse(component.equals("will be false"));
        assertTrue(component.publicId().equals(newerComponent.publicId()));
        assertEquals(component, PatternChronologyDTO.make(component));

    }
    
    
    @Test
    public void testSemanticVersionForChangeSet() {
        PublicId componentPublicId = TestUtil.makePublicId();
        PublicId patternPublicId = TestUtil.makePublicId();
        PublicId referencedComponentPublicId = TestUtil.makePublicId();

        SemanticVersionDTO component = new SemanticVersionDTO(componentPublicId,
                TestUtil.makeStampForChangeSet(), TestUtil.makeImmutableObjectList());

        SemanticVersionDTO newerComponent = Marshalable.makeSemanticVersion(SemanticVersionDTO.class, component.marshal().toInput(),
                componentPublicId);
        assertEquals(component, newerComponent);

        testInvalidMarshalVersionForSemanticVersion(SemanticVersionDTO.class, Marshalable.marshalVersion);
        testReadSemanticVersionIOException(SemanticVersionDTO.class, Marshalable.marshalVersion);
        testWriteIOExceptionForSemanticVersion(component);

        assertTrue(component.equals(newerComponent));
        assertTrue(component.hashCode() == newerComponent.hashCode());
        assertFalse(component.equals(new SemanticVersionDTO(componentPublicId,
                TestUtil.makeStampForChangeSet(), TestUtil.makeImmutableObjectList())));
        assertFalse(component.equals("will be false"));
        assertTrue(component.publicId().equals(newerComponent.publicId()));
        assertEquals(component, SemanticVersionDTO.make(component));
    }
    
   
    @Test
    public void testSemanticChronologyForChangeSet() {

        SemanticChronologyDTO component = TestUtil.makeSemanticChronology();
        

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
        assertTrue(component.pattern().equals(newerComponent.pattern()));
        assertTrue(component.referencedComponent().equals(newerComponent.referencedComponent()));
        assertEquals(component, SemanticChronologyDTO.make(component));
    }

}
