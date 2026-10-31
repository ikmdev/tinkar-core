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
package dev.ikm.tinkar.integration.changeSet;

import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.IdentityAdvisories;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.KnowledgeSet;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.entity.changeset.ChangeSetFormat;
import dev.ikm.tinkar.entity.changeset.ComponentTable;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.EntityBinding;
import dev.ikm.tinkar.terms.KernelConcept;
import network.ike.foundation.ike.bindings.IkeConcept;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A change set's reference table names a component it refers to but does not carry by every UUID
 * its source holds, and loading it adds the UUIDs the store's record lacks, as merging a carried
 * record does: a concept RxNorm merges with SNOMED CT is carried only when RxNorm gives it a
 * version, and referenced under both UUIDs otherwise.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReferencedUuidsChangeSetIT {

    private static final UUID HELD = UUID.fromString("7b2e3c4d-5e6f-5a71-8b8c-9d0e1f2a3b41");
    private static final UUID REFERENCED = UUID.fromString("7b2e3c4d-5e6f-5a71-8b8c-9d0e1f2a3b42");

    @TempDir
    Path dir;

    @BeforeAll
    void start() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
    }

    @AfterAll
    void stop() {
        TestHelper.stopDatabase();
    }

    @Test
    @DisplayName("A component referenced under a UUID its record lacks gains it, and keeps it on export")
    void referencedComponentGainsTheUuidsItIsReferencedBy() throws IOException {
        ActiveStamp stamp = Stamp.active("2020-01-01T00:00:00Z", KernelConcept.USER,
                IkeConcept.DEVELOPMENT_MODULE, KernelConcept.DEVELOPMENT_PATH);
        KnowledgeSet base = KnowledgeSet.of("7b2e3c4d-5e6f-5a71-8b8c-9d0e1f2a3b00");
        base.concept("Referenced concept (Test)", PublicIds.of(HELD)).at(stamp).isA(IkeConcept.MODEL_CONCEPT);
        base.write();
        long nid = PrimitiveData.nid(PublicIds.of(HELD));

        File changeSet = referencing(HELD, REFERENCED);
        long added = IdentityAdvisories.uuidsAddedCount();
        new LoadEntitiesFromProtobufFile(changeSet).compute();

        assertEquals(Set.of(HELD, REFERENCED),
                Set.of(EntityHandle.get(nid).expectConcept().publicId().asUuidArray()),
                "the record holds the UUID the reference named it by");
        assertEquals(added + 1, IdentityAdvisories.uuidsAddedCount(), "one component gained UUIDs");

        new LoadEntitiesFromProtobufFile(changeSet).compute();
        assertEquals(added + 1, IdentityAdvisories.uuidsAddedCount(), "loading again adds nothing");
    }

    /** A format-3 change set carrying nothing, whose reference table names a concept by {@code uuids}. */
    private File referencing(UUID... uuids) throws IOException {
        Path file = dir.resolve("referencing.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
            out.putNextEntry(new ZipEntry(ChangeSetFormat.COMPONENT_TABLE));
            new ComponentTable.Writer(out).close();
            out.closeEntry();
            out.putNextEntry(new ZipEntry(ChangeSetFormat.REFERENCE_TABLE));
            try (ComponentTable.Writer table = new ComponentTable.Writer(out)) {
                table.add(1, EntityBinding.Pattern.pattern().publicId(), true);   // names itself
                table.add(1, EntityBinding.Concept.pattern().publicId(), true);
                table.add(2, PublicIds.of(uuids), true);
            }
            out.closeEntry();
            Manifest manifest = new Manifest();
            Attributes main = manifest.getMainAttributes();
            main.put(Attributes.Name.MANIFEST_VERSION, "1.0");
            main.putValue(ChangeSetFormat.VERSION_ATTRIBUTE, Integer.toString(ChangeSetFormat.CURRENT_VERSION));
            main.putValue(ChangeSetFormat.RECORD_ENTRIES_ATTRIBUTE, "0");
            main.putValue(ChangeSetFormat.COMPONENT_COUNT_ATTRIBUTE, "3");
            main.putValue("Total-Count", "0");
            out.putNextEntry(new ZipEntry(ChangeSetFormat.MANIFEST));
            manifest.write(out);
            out.closeEntry();
        }
        return file.toFile();
    }
}
