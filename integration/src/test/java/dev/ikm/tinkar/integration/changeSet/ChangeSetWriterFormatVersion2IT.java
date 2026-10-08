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
import dev.ikm.tinkar.entity.changeset.SchemaIds;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.provider.changeset.ChangeSetWriterProvider;
import dev.ikm.tinkar.schema.PatternMembers;
import dev.ikm.tinkar.schema.PublicId;
import dev.ikm.tinkar.schema.TinkarMsg;
import dev.ikm.tinkar.schema.VertexUUID;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The changesets Komet writes as it edits are in format version 2: the manifest names it, every
 * UUID is two longs, and the identity index lists each component once — although the writer
 * writes a component again at each commit.
 *
 * <p>The format itself, and loading it into every store, is held by rocks-kb's
 * {@code FormatVersion2IT}; this test holds the changeset writer to it.
 */
class ChangeSetWriterFormatVersion2IT {

    private static final File DATASTORE_ROOT =
            TestConstants.createFilePathInTargetFromClassName.apply(ChangeSetWriterFormatVersion2IT.class);

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
            // The finished changesets: save() also opened the next one, which is empty and open.
            List<Path> changeSets = files.filter(path -> path.getFileName().toString().endsWith("ike-cs.zip"))
                    .filter(ChangeSetWriterFormatVersion2IT::isFinished).toList();
            assertEquals(1, changeSets.size(), "Finished changesets written to " + folder + ": " + changeSets);
            changeSet = changeSets.getFirst().toFile();
        }
        records = records(changeSet);
    }

    @AfterAll
    static void stop() {
        TestHelper.stopDatabase();
    }

    @Test
    void theWriterWritesFormatVersion2() throws IOException {
        List<String> textUuids = new ArrayList<>();
        records.forEach(record -> forEachMessage(record, message -> {
            if (message instanceof PublicId publicId && (publicId.getUuidsCount() > 0 || publicId.getUuidBitsCount() == 0)) {
                textUuids.add(publicId.toString().strip());
            } else if (message instanceof VertexUUID vertex && !vertex.getUuid().isEmpty()) {
                textUuids.add(vertex.toString().strip());
            }
        }));
        Map<List<UUID>, List<UUID>> index = index(changeSet);

        assertAll(
                () -> assertEquals("2", manifest(changeSet).getValue(ChangeSetFormat.VERSION_ATTRIBUTE), "The manifest's format version"),
                () -> assertEquals(written, records.size(), "Records written, each time the writer was given one"),
                () -> assertEquals(List.of(), textUuids.stream().limit(5).toList(),
                        textUuids.size() + " UUIDs not written as longs alone; the first five"),
                () -> assertNotNull(index, "No identity index"),
                () -> {
                    List<String> wrong = new ArrayList<>();
                    for (TinkarMsg record : records) {
                        List<UUID> component = List.of(SchemaIds.uuids(ChangeSetFormat.componentOf(record)));
                        List<UUID> pattern = ChangeSetFormat.patternOf(record).asUuidList().castToList();
                        if (!pattern.equals(index.get(component))) {
                            wrong.add(component + " listed under " + index.get(component) + ", not " + pattern);
                        }
                    }
                    assertEquals(List.of(), wrong, "Components the index lists under the wrong pattern, or not at all");
                    assertEquals(written - 1, index.size(), "Components the index lists: each once, the repeated concept included");
                });
    }

    private static boolean isFinished(Path changeSet) {
        try (ZipFile zip = new ZipFile(changeSet.toFile())) {
            return zip.getEntry(ChangeSetFormat.MANIFEST) != null;
        } catch (IOException notYetAZip) {
            return false;
        }
    }

    private static List<TinkarMsg> records(File file) throws IOException {
        List<TinkarMsg> parsed = new ArrayList<>();
        try (ZipFile zip = new ZipFile(file)) {
            for (ZipEntry entry : zip.stream().toList()) {
                if (ChangeSetFormat.isMetadata(entry.getName())) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(entry)) {
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
            return new Manifest(zip.getInputStream(zip.getEntry(ChangeSetFormat.MANIFEST))).getMainAttributes();
        }
    }

    /** Component to pattern, as the index lists them, or null with no index; fails on a component listed twice. */
    private static Map<List<UUID>, List<UUID>> index(File file) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry entry = zip.getEntry(ChangeSetFormat.IDENTITY_INDEX);
            if (entry == null) {
                return null;
            }
            Map<List<UUID>, List<UUID>> index = new HashMap<>();
            try (InputStream in = zip.getInputStream(entry)) {
                PatternMembers members;
                while ((members = PatternMembers.parseDelimitedFrom(in)) != null) {
                    List<UUID> pattern = Arrays.asList(SchemaIds.uuids(members.getPatternPublicId()));
                    for (PublicId component : members.getComponentPublicIdsList()) {
                        List<UUID> previous = index.put(Arrays.asList(SchemaIds.uuids(component)), pattern);
                        assertTrue(previous == null, "The index lists " + Arrays.toString(SchemaIds.uuids(component)) + " more than once");
                    }
                }
            }
            return index;
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
