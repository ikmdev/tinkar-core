/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.dto;

import dev.ikm.tinkar.dto.binary.*;
import org.eclipse.collections.api.factory.Lists;
import dev.ikm.tinkar.dto.binary.*;
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
        Assertions.assertThrows(UncheckedIOException.class, () -> failingInput.readConceptVersionList(TestUtil.makePublicId()));
        Assertions.assertThrows(UncheckedIOException.class, () -> failingInput.readPatternVersionList(TestUtil.makePublicId()));
        Assertions.assertThrows(UncheckedIOException.class, () -> failingInput.readSemanticVersionList(TestUtil.makePublicId(), TestUtil.makePublicId(), TestUtil.makePublicId()));
        Assertions.assertThrows(UncheckedIOException.class, () -> failingInput.readObjectArray());
        Assertions.assertThrows(UncheckedIOException.class, () -> SemanticVersionDTO.make(failingInput,
                TestUtil.makePublicId())
        );
    }
    @Test
    public void tinkarOutputTests() {
        TinkarFailingOutput tinkarFailingOutput = new TinkarFailingOutput();
        Assertions.assertThrows(UncheckedIOException.class, () -> tinkarFailingOutput.writeUuidArray(new UUID[0]));
        Assertions.assertThrows(UncheckedIOException.class, () -> tinkarFailingOutput.writeInstant(Instant.now()));
        Assertions.assertThrows(UncheckedIOException.class, () -> tinkarFailingOutput.writeFieldDefinitionList(Lists.immutable.empty()));
        Assertions.assertThrows(UncheckedIOException.class, () -> tinkarFailingOutput.writeConceptVersionList(Lists.immutable.empty()));
        Assertions.assertThrows(UncheckedIOException.class, () -> tinkarFailingOutput.writePatternVersionList(Lists.immutable.empty()));
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
                new ComponentDTO(TestUtil.makePublicId()),
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
