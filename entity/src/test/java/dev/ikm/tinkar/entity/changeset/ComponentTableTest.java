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
package dev.ikm.tinkar.entity.changeset;

import dev.ikm.tinkar.common.id.PublicIds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The component table numbers the carried components and then the referenced ones, from 1, in file order. */
class ComponentTableTest {

    @TempDir
    Path dir;

    @Test
    void carriedThenReferencedAreNumberedInFileOrder() throws IOException {
        UUID patternPattern = UUID.randomUUID();
        UUID conceptPattern = UUID.randomUUID();
        UUID concept = UUID.randomUUID();
        UUID conceptAlias = UUID.randomUUID();
        UUID referencedStampPattern = UUID.randomUUID();
        UUID referencedStamp = UUID.randomUUID();

        Path changeSet = dir.resolve("table.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(changeSet))) {
            out.putNextEntry(new ZipEntry(ChangeSetFormat.COMPONENT_TABLE));
            try (ComponentTable.Writer table = new ComponentTable.Writer(out)) {
                assertEquals(1, table.add(1, PublicIds.of(patternPattern), false));   // names itself
                assertEquals(2, table.add(1, PublicIds.of(conceptPattern), false));
                assertEquals(3, table.add(2, PublicIds.of(concept, conceptAlias), false));
            }
            out.closeEntry();
            out.putNextEntry(new ZipEntry(ChangeSetFormat.REFERENCE_TABLE));
            try (ComponentTable.Writer table = new ComponentTable.Writer(out)) {
                table.add(1, PublicIds.of(referencedStampPattern), true);
                table.add(4, PublicIds.of(referencedStamp), true);
            }
            out.closeEntry();
        }

        List<ComponentTable.Component> components = new ArrayList<>();
        try (ZipFile zip = new ZipFile(changeSet.toFile())) {
            assertTrue(ChangeSetFormat.hasComponentTable(zip));
            assertEquals(5, ComponentTable.forEach(zip, components::add));
        }
        assertEquals(List.of(1, 2, 3, 4, 5), components.stream().map(ComponentTable.Component::sequence).toList());
        assertTrue(components.get(0).isPatternPattern());
        assertFalse(components.get(1).isPatternPattern());
        assertEquals(2, components.get(2).patternSequence());
        assertArrayEquals(new UUID[]{concept, conceptAlias}, components.get(2).uuids());
        assertFalse(components.get(2).referencedOnly());
        assertTrue(components.get(3).referencedOnly());
        assertEquals(4, components.get(4).patternSequence());
        assertEquals(referencedStamp, components.get(4).uuids()[0]);
    }

    @Test
    void aTableLargerThanOneMessageReadsBack() throws IOException {
        Path changeSet = dir.resolve("large.zip");
        int count = ComponentTable.COMPONENTS_PER_MESSAGE * 2 + 7;
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(changeSet))) {
            out.putNextEntry(new ZipEntry(ChangeSetFormat.COMPONENT_TABLE));
            try (ComponentTable.Writer table = new ComponentTable.Writer(out)) {
                for (int i = 1; i <= count; i++) {
                    table.add(1, PublicIds.of(new UUID(i, i)), false);
                }
                assertEquals(count, table.count());
            }
            out.closeEntry();
        }
        long[] last = {0};
        try (ZipFile zip = new ZipFile(changeSet.toFile())) {
            long streamed = ComponentTable.forEach(zip, component -> {
                assertEquals(last[0] + 1, component.sequence());
                assertEquals(new UUID(component.sequence(), component.sequence()), component.uuids()[0]);
                last[0] = component.sequence();
            });
            assertEquals(count, streamed);
        }
    }
}
