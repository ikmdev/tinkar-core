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

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import dev.ikm.tinkar.common.service.DataActivity;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.changeset.ChangeSetFormat;
import dev.ikm.tinkar.entity.changeset.ChangeSetVerification;
import dev.ikm.tinkar.entity.changeset.ComponentTable;
import dev.ikm.tinkar.entity.changeset.SchemaIds;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.provider.changeset.ChangeSetWriterProvider;
import dev.ikm.tinkar.schema.PublicId;
import dev.ikm.tinkar.schema.TinkarMsg;
import dev.ikm.tinkar.schema.VertexUUID;
import dev.ikm.tinkar.terms.EntityBinding;
import dev.ikm.tinkar.terms.KernelTerm;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The incremental writer journals a session in the format-2 layout and compacts the journal
 * into a format-3 change set at close: one record per component, by its last record, with a
 * component table that lists each under its pattern, references by sequence, and a manifest
 * that verifies.
 */
class ChangeSetWriterFormatVersion3IT {

    private static final File DATASTORE_ROOT =
            TestConstants.createFilePathInTargetFromClassName.apply(ChangeSetWriterFormatVersion3IT.class);

    private static File changeSet;
    private static List<TinkarMsg> records;
    private static int written;

    @BeforeAll
    static void writeAChangeSet() throws Exception {
        FileUtil.recursiveDelete(DATASTORE_ROOT);
        TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, DATASTORE_ROOT);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);

        // Active state, its semantics, and the stamps of all their versions; the concept twice,
        // as the writer sees a component again when a later commit changes it.
        List<Entity<?>> entities = new ArrayList<>();
        Entity<?> activeState = EntityHandle.get(KernelTerm.ACTIVE_STATE).expectEntity();
        entities.add(activeState);
        EntityService.get().semanticsForComponent(activeState.nid()).forEach(entities::add);
        List<Entity<?>> stamps = new ArrayList<>();
        for (Entity<?> entity : entities) {
            for (EntityVersion version : entity.versions()) {
                Entity<?> stamp = EntityHandle.get(version.stampNid()).expectEntity();
                if (stamps.stream().noneMatch(listed -> listed.nid() == stamp.nid())) {
                    stamps.add(stamp);
                }
            }
        }
        entities.addAll(stamps);
        entities.add(activeState);

        ChangeSetWriterProvider writer = ChangeSetWriterProvider.provider();
        entities.forEach(entity -> writer.writeToChangeSet(entity, DataActivity.SYNCHRONIZABLE_EDIT));
        written = entities.size();
        // save() completes once everything queued before it is in a closed file.
        writer.save().get(1, TimeUnit.MINUTES);

        Path folder = DATASTORE_ROOT.toPath().resolve(Path.of("changeSets", "src", "main", "resources"));
        try (Stream<Path> files = Files.list(folder)) {
            // The finished change sets: the one save() closed; the next one is an open spool under another name.
            List<Path> changeSets = files.filter(path -> path.getFileName().toString().endsWith("ike-cs.zip")).toList();
            assertEquals(1, changeSets.size(), "Finished change sets written to " + folder + ": " + changeSets);
            changeSet = changeSets.getFirst().toFile();
        }
        records = records(changeSet);
    }

    @AfterAll
    static void stop() {
        TestHelper.stopDatabase();
    }

    @Test
    void theWriterWritesFormatVersion3() throws Exception {
        List<String> textUuids = new ArrayList<>();
        records.forEach(record -> forEachMessage(record, message -> {
            if (message instanceof PublicId publicId && (publicId.getUuidsCount() > 0
                    || (publicId.getUuidBitsCount() == 0 && !publicId.hasSequence()))) {
                textUuids.add(publicId.toString().strip());
            } else if (message instanceof VertexUUID vertex && !vertex.getUuid().isEmpty()) {
                textUuids.add(vertex.toString().strip());
            }
        }));
        Map<UUID, ComponentTable.Component> byUuid = new HashMap<>();
        List<ComponentTable.Component> bySequence = new ArrayList<>();
        bySequence.add(null);
        try (ZipFile zip = new ZipFile(changeSet)) {
            ComponentTable.forEach(zip, component -> {
                bySequence.add(component);
                for (UUID uuid : component.uuids()) {
                    byUuid.put(uuid, component);
                }
            });
        }
        ChangeSetVerification.Verification verified = new ChangeSetVerification(changeSet).call();

        assertAll(
                () -> assertEquals("3", manifest(changeSet).getValue(ChangeSetFormat.VERSION_ATTRIBUTE), "The manifest's format version"),
                () -> assertTrue(verified.ok(), verified.text()),
                () -> assertEquals(written - 1, records.size(), "Records: each component once, the repeated concept by its last record"),
                () -> assertEquals(List.of(), textUuids.stream().limit(5).toList(),
                        textUuids.size() + " ids not written as longs or sequences; the first five"),
                () -> {
                    List<String> wrong = new ArrayList<>();
                    for (TinkarMsg record : records) {
                        UUID first = SchemaIds.uuids(ChangeSetFormat.componentOf(record))[0];
                        ComponentTable.Component component = byUuid.get(first);
                        if (component == null || component.referencedOnly()) {
                            wrong.add(first + " is not listed as carried");
                            continue;
                        }
                        UUID pattern = switch (record.getValueCase()) {
                            case SEMANTIC_CHRONOLOGY -> bySequence.get(record.getSemanticChronology().getPatternForSemanticPublicId().getSequence()).uuids()[0];
                            case CONCEPT_CHRONOLOGY -> EntityBinding.Concept.pattern().publicId().asUuidArray()[0];
                            case STAMP_CHRONOLOGY -> EntityBinding.Stamp.pattern().publicId().asUuidArray()[0];
                            case PATTERN_CHRONOLOGY -> EntityBinding.Pattern.pattern().publicId().asUuidArray()[0];
                            case VALUE_NOT_SET -> null;
                        };
                        UUID listed = bySequence.get(component.patternSequence()).uuids()[0];
                        if (!listed.equals(pattern)) {
                            wrong.add(first + " listed under " + listed + ", not " + pattern);
                        }
                    }
                    assertEquals(List.of(), wrong, "Components the table lists under the wrong pattern, or not at all");
                });
    }

    private static List<TinkarMsg> records(File file) throws IOException {
        List<TinkarMsg> parsed = new ArrayList<>();
        try (ZipFile zip = new ZipFile(file)) {
            Manifest manifest = ChangeSetFormat.manifest(zip).orElseThrow();
            for (ChangeSetFormat.RecordEntry entry : ChangeSetFormat.recordEntries(zip, manifest)) {
                try (InputStream in = ChangeSetFormat.openRecords(zip, entry.entry())) {
                    TinkarMsg record;
                    while ((record = TinkarMsg.parseDelimitedFrom(in)) != null) {
                        parsed.add(record);
                    }
                }
            }
        }
        return parsed;
    }

    private static java.util.jar.Attributes manifest(File file) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            return ChangeSetFormat.manifest(zip).orElseThrow().getMainAttributes();
        }
    }

    /** Visits the message and every message nested in it, at any depth. */
    private static void forEachMessage(Message message, Consumer<Message> visitor) {
        visitor.accept(message);
        for (Map.Entry<FieldDescriptor, Object> field : message.getAllFields().entrySet()) {
            if (field.getKey().getJavaType() != FieldDescriptor.JavaType.MESSAGE) {
                continue;
            }
            if (field.getKey().isRepeated()) {
                for (Object element : (List<?>) field.getValue()) {
                    forEachMessage((Message) element, visitor);
                }
            } else {
                forEachMessage((Message) field.getValue(), visitor);
            }
        }
    }
}
