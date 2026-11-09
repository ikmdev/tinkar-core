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
package dev.ikm.tinkar.common.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A name stored before its class was renamed resolves to the class it became. */
class RenamedClassesTest {

    /** The class a former name became. */
    static final class Present {
    }

    /** Renames a former name to {@link Present}; found through META-INF/services. */
    public static final class Renames implements RenamedClasses {
        @Override
        public Optional<String> current(String formerName) {
            return formerName.equals("dev.ikm.tinkar.former.Absent$Factory")
                    ? Optional.of(Present.class.getName()) : Optional.empty();
        }
    }

    @Test
    @DisplayName("A name that resolves is never looked up as renamed")
    void presentNameResolves() throws ClassNotFoundException {
        assertEquals(Present.class, PluggableService.forName(Present.class.getName()));
    }

    @Test
    @DisplayName("A former name resolves to the class a provider says it became")
    void formerNameResolves() throws ClassNotFoundException {
        assertEquals(Present.class, PluggableService.forName("dev.ikm.tinkar.former.Absent$Factory"));
    }

    @Test
    @DisplayName("A name no class has, and no provider renamed, is not found")
    void unknownNameIsNotFound() {
        assertThrows(ClassNotFoundException.class, () -> PluggableService.forName("dev.ikm.tinkar.former.Unknown"));
    }
}
