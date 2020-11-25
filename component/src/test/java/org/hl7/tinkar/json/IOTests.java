package org.hl7.tinkar.json;

import org.eclipse.collections.api.factory.Lists;
import org.hl7.tinkar.binary.MarshalExceptionUnchecked;
import org.hl7.tinkar.binary.Marshalable;
import org.hl7.tinkar.binary.Unmarshaler;
import org.hl7.tinkar.dto.SemanticVersionDTO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

public class IOTests {

    @Test
    public void tinkarInputTests() {
        TinkarFailingInput failingInput = new TinkarFailingInput();
        Assertions.assertThrows(RuntimeException.class, () -> failingInput.readUuidArray());
        Assertions.assertThrows(RuntimeException.class, () -> failingInput.readInstant());
        Assertions.assertThrows(RuntimeException.class, () -> failingInput.readFieldDefinitionList());
        Assertions.assertThrows(RuntimeException.class, () -> failingInput.readConceptVersionList(ChangeSetTest.makeUuidList()));
        Assertions.assertThrows(RuntimeException.class, () -> failingInput.readDefinitionForSemanticVersionList(ChangeSetTest.makeUuidList()));
        Assertions.assertThrows(RuntimeException.class, () -> failingInput.readSemanticVersionList(ChangeSetTest.makeUuidList(), ChangeSetTest.makeUuidList(), ChangeSetTest.makeUuidList()));
        Assertions.assertThrows(RuntimeException.class, () -> failingInput.readObjectArray());
        Assertions.assertThrows(RuntimeException.class, () -> SemanticVersionDTO.make(failingInput,
                ChangeSetTest.makeUuidList(), ChangeSetTest.makeUuidList(), ChangeSetTest.makeUuidList())
        );
    }
    @Test
    public void tinkarOutputTests() {
        TinkarFailingOutput tinkarFailingOutput = new TinkarFailingOutput();
        Assertions.assertThrows(RuntimeException.class, () -> tinkarFailingOutput.writeUuidArray(new UUID[0]));
        Assertions.assertThrows(RuntimeException.class, () -> tinkarFailingOutput.writeInstant(Instant.now()));
        Assertions.assertThrows(RuntimeException.class, () -> tinkarFailingOutput.writeFieldDefinitionList(Lists.immutable.empty()));
        Assertions.assertThrows(RuntimeException.class, () -> tinkarFailingOutput.writeConceptVersionList(Lists.immutable.empty()));
        Assertions.assertThrows(RuntimeException.class, () -> tinkarFailingOutput.writeDefinitionForSemanticVersionList(Lists.immutable.empty()));
        Assertions.assertThrows(RuntimeException.class, () -> tinkarFailingOutput.writeSemanticVersionList(Lists.immutable.empty()));
        Assertions.assertThrows(RuntimeException.class, () -> tinkarFailingOutput.writeObjectArray(new Object[0]));
    }

    @Test
    public void marshalAnnotationTests() {
        Assertions.assertThrows(MarshalExceptionUnchecked.class, () -> Marshalable.make(String.class, new byte[0]));
        Assertions.assertThrows(MarshalExceptionUnchecked.class, () -> Marshalable.make(NonStaticAnnotationClass.class, new byte[0]));
        Assertions.assertThrows(MarshalExceptionUnchecked.class, () -> Marshalable.make(MultipleAnnotationClass.class, new byte[0]));
    }

    private static class NonStaticAnnotationClass {

        @Unmarshaler
        public void unmarshaler() {}

    }
    private static class MultipleAnnotationClass {

        @Unmarshaler
        public static void unmarshaler() {}

        @Unmarshaler
        public static void unmarshaler2() {}

    }
}
