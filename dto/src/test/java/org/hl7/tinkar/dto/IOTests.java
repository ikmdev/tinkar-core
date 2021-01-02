package org.hl7.tinkar.dto;

import org.eclipse.collections.api.factory.Lists;
import org.hl7.tinkar.dto.binary.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;


public class IOTests {

    @Test
    public void tinkarInputTests() {
        TinkarFailingInput failingInput = new TinkarFailingInput();
        Assertions.assertThrows(UncheckedIOException.class, () -> failingInput.readUuidArray());
        Assertions.assertThrows(UncheckedIOException.class, () -> failingInput.readInstant());
        Assertions.assertThrows(UncheckedIOException.class, () -> failingInput.readFieldDefinitionList());
        Assertions.assertThrows(UncheckedIOException.class, () -> failingInput.readConceptVersionList(TestUtil.makeUuidList()));
        Assertions.assertThrows(UncheckedIOException.class, () -> failingInput.readDefinitionForSemanticVersionList(TestUtil.makeUuidList()));
        Assertions.assertThrows(UncheckedIOException.class, () -> failingInput.readSemanticVersionList(TestUtil.makeUuidList(), TestUtil.makeUuidList(), TestUtil.makeUuidList()));
        Assertions.assertThrows(UncheckedIOException.class, () -> failingInput.readObjectArray());
        Assertions.assertThrows(UncheckedIOException.class, () -> SemanticVersionDTO.make(failingInput,
                TestUtil.makeUuidList(), TestUtil.makeUuidList(), TestUtil.makeUuidList())
        );
    }
    @Test
    public void tinkarOutputTests() {
        TinkarFailingOutput tinkarFailingOutput = new TinkarFailingOutput();
        Assertions.assertThrows(UncheckedIOException.class, () -> tinkarFailingOutput.writeUuidArray(new UUID[0]));
        Assertions.assertThrows(UncheckedIOException.class, () -> tinkarFailingOutput.writeInstant(Instant.now()));
        Assertions.assertThrows(UncheckedIOException.class, () -> tinkarFailingOutput.writeFieldDefinitionList(Lists.immutable.empty()));
        Assertions.assertThrows(UncheckedIOException.class, () -> tinkarFailingOutput.writeConceptVersionList(Lists.immutable.empty()));
        Assertions.assertThrows(UncheckedIOException.class, () -> tinkarFailingOutput.writeDefinitionForSemanticVersionList(Lists.immutable.empty()));
        Assertions.assertThrows(UncheckedIOException.class, () -> tinkarFailingOutput.writeSemanticVersionList(Lists.immutable.empty()));
        Assertions.assertThrows(UncheckedIOException.class, () -> tinkarFailingOutput.writeObjectArray(new Object[0]));
    }

    @Test
    public void marshalAnnotationTests() {
        Assertions.assertThrows(MarshalExceptionUnchecked.class, () -> Marshalable.make(String.class, new byte[0], Marshalable.marshalVersion));
        Assertions.assertThrows(MarshalExceptionUnchecked.class, () -> Marshalable.make(NonStaticAnnotationClass.class, new byte[0], Marshalable.marshalVersion));
        Assertions.assertThrows(MarshalExceptionUnchecked.class, () -> Marshalable.make(MultipleAnnotationClass.class, new byte[0], Marshalable.marshalVersion));
    }

    @Test
    public void tinakrInputOutputTests() {
        TinkarByteArrayOutput output = TinkarByteArrayOutput.make();
        Object[] objectArray = new Object[] {"String",
                Boolean.TRUE, new byte[] {}, (float) 2.0, 10, (float) 3.0,
                new ComponentDTO(Lists.immutable.of(UUID.randomUUID())),
                new Object[] {"one", "two"}
        };
        output.writeObjectArray(objectArray);
        TinkarInput input = TinkarInput.make(output);
        Object[] objectArray2 = input.readObjectArray();
        Assertions.assertTrue(Arrays.deepEquals(objectArray, objectArray2));
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
