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
package dev.ikm.tinkar.terms.test;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelTerm;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Moving a reference from {@code TinkarTerm.X} to {@code KernelTerm.X} changes no identity:
 * every kernel constant is the {@code TinkarTerm} constant of the same name, the same kind of
 * proxy, sharing its UUIDs and losing none, so it resolves to the same component and nid.
 * Holds while
 * {@code TinkarTerm} exists, which is the replacement's duration.
 */
class KernelTermIdentityTest {

    @Test
    void everyKernelConstantIsTheTinkarTermConstantOfTheSameName() throws Exception {
        List<String> differences = new ArrayList<>();
        int compared = 0;
        for (Field kernelField : KernelTerm.class.getFields()) {
            if (!Modifier.isStatic(kernelField.getModifiers()) || !EntityProxy.class.isAssignableFrom(kernelField.getType())) {
                continue;
            }
            EntityProxy kernel = (EntityProxy) kernelField.get(null);
            Field tinkarField;
            try {
                tinkarField = TinkarTerm.class.getField(kernelField.getName());
            } catch (NoSuchFieldException e) {
                differences.add(kernelField.getName() + ": no TinkarTerm constant of the name");
                continue;
            }
            EntityProxy tinkar = (EntityProxy) tinkarField.get(null);
            compared++;
            if (kernel.getClass() != tinkar.getClass()) {
                differences.add(kernelField.getName() + ": a " + kernel.getClass().getSimpleName()
                        + " in the kernel, a " + tinkar.getClass().getSimpleName() + " in TinkarTerm");
            }
            // Public ids match when they share any UUID, in whatever order each lists them. The
            // kernel must also lose none of TinkarTerm's: a store may know a component by any one,
            // and a proxy that lacks it finds another component, or none.
            if (!PublicId.equals(kernel.publicId(), tinkar.publicId())
                    || !Set.of(kernel.publicId().asUuidArray()).containsAll(Set.of(tinkar.publicId().asUuidArray()))) {
                differences.add(kernelField.getName() + ": " + kernel.publicId().idString()
                        + " in the kernel, " + tinkar.publicId().idString() + " in TinkarTerm");
            }
        }
        assertTrue(compared > 100, "the kernel's constants were compared");
        assertEquals(List.of(), differences);
    }
}
