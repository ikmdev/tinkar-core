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
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.fixtures.StarterDataEphemeralProvider;
import dev.ikm.tinkar.importer.registry.ChangeSetLoad;
import dev.ikm.tinkar.importer.registry.Registry;
import dev.ikm.tinkar.schema.ConceptChronology;
import dev.ikm.tinkar.schema.ConceptVersion;
import dev.ikm.tinkar.schema.Field;
import dev.ikm.tinkar.schema.PublicId;
import dev.ikm.tinkar.schema.SemanticChronology;
import dev.ikm.tinkar.schema.SemanticVersion;
import dev.ikm.tinkar.schema.StampChronology;
import dev.ikm.tinkar.schema.StampVersion;
import dev.ikm.tinkar.schema.TinkarMsg;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A store loads the change sets of its folders that it has not registered, and registers each
 * ({@code dev-ike-dashboard}, decision 20): the knowledge folder then the instance folder, each in
 * name order; a file refused for a component no file before it carried is tried again after the
 * rest; a file registered before is not loaded again; a file refused in both passes is reported
 * and left for the next open. Against the starter set in a memory-only 64-bit store, with change
 * sets written by hand.
 */
@ExtendWith(StarterDataEphemeralProvider.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UnregisteredChangeSetLoadIT {

    /** Deterministic, so a rerun of a stage finds what an earlier stage wrote. */
    private static final UUID STAMP = UUID.nameUUIDFromBytes("unregistered-load stamp".getBytes());
    private static final UUID CONCEPT = UUID.nameUUIDFromBytes("unregistered-load concept".getBytes());
    private static final UUID DESCRIPTION = UUID.nameUUIDFromBytes("unregistered-load description".getBytes());
    private static final UUID INSTANCE_CONCEPT = UUID.nameUUIDFromBytes("unregistered-load instance concept".getBytes());
    private static final UUID NOBODY_CARRIES = UUID.nameUUIDFromBytes("unregistered-load nobody carries".getBytes());
    private static final UUID ORPHAN_DESCRIPTION = UUID.nameUUIDFromBytes("unregistered-load orphan description".getBytes());

    @TempDir
    static Path dir;

    private Path storeRoot;
    private Path describesBeforeConcept;
    private Path carriesConcept;
    private Path instance;
    private final List<String> attempts = new ArrayList<>();
    private final ChangeSetLoad.Progress recording = (index, count, file, retry) ->
            attempts.add((retry ? "retry " : "") + file.getFileName());

    @Test
    @Order(1)
    void theStoreDeclaresTheRegistry() {
        assertTrue(Registry.declared(), "the starter set declares the registry");
        assertFalse(PrimitiveData.get().hasPublicId(PublicIds.of(CONCEPT)), "the concept is not in the store yet");
    }

    @Test
    @Order(2)
    void knowledgeThenInstanceInNameOrderAndARefusedFileIsTriedAgainAfterTheRest() throws Exception {
        storeRoot = dir.resolve("store");
        Path knowledge = Files.createDirectories(storeRoot.resolve(ChangeSetLoad.KNOWLEDGE_FOLDER));
        Path instanceFolder = Files.createDirectories(storeRoot.resolve(ChangeSetLoad.INSTANCE_FOLDER));
        Files.createDirectories(storeRoot.resolve("ephemeralChangeSets"));
        write(storeRoot.resolve("ephemeralChangeSets").resolve("never read ike-cs.zip"), stamp(), concept(NOBODY_CARRIES));
        // "a" describes a concept only "b" carries: refused first, loaded after the rest.
        describesBeforeConcept = write(knowledge.resolve("a describes ike-cs.zip"), stamp(), description(DESCRIPTION, CONCEPT));
        carriesConcept = write(knowledge.resolve("b carries ike-cs.zip"), stamp(), concept(CONCEPT));
        instance = write(instanceFolder.resolve("Journal [module] ike-cs.zip"), stamp(), concept(INSTANCE_CONCEPT));
        Files.writeString(knowledge.resolve("session.zip.spool"), "a journal being written, never chosen");

        ChangeSetLoad.Report report = ChangeSetLoad.loadStore(storeRoot, recording);

        assertEquals(List.of("a describes ike-cs.zip", "b carries ike-cs.zip", "Journal [module] ike-cs.zip",
                "retry a describes ike-cs.zip"), attempts, "knowledge folder in name order, then instance, then the retry");
        assertTrue(report.clean(), report.markdown());
        assertTrue(report.registryDeclared());
        assertEquals(List.of(knowledge, instanceFolder), report.folders());
        assertEquals(ChangeSetLoad.Outcome.LOADED_AFTER_RETRY, outcome(report, describesBeforeConcept));
        assertEquals(ChangeSetLoad.Outcome.LOADED, outcome(report, carriesConcept));
        assertEquals(ChangeSetLoad.Outcome.LOADED, outcome(report, instance));
        assertTrue(result(report, describesBeforeConcept).reason().orElseThrow().contains(CONCEPT.toString()),
                "the first refusal names the component no earlier file carried: " + report.markdown());
        assertTrue(EntityHandle.get(PublicIds.of(CONCEPT)).entity().isPresent(), "the concept is in the store");
        assertTrue(EntityHandle.get(PublicIds.of(DESCRIPTION)).entity().isPresent(), "and its description, after the retry");
        assertTrue(EntityHandle.get(PublicIds.of(INSTANCE_CONCEPT)).entity().isPresent(), "and the instance folder's concept");
        assertFalse(PrimitiveData.get().hasPublicId(PublicIds.of(NOBODY_CARRIES)), "the ephemeral folder is not read");

        Set<String> loaded = Registry.loadedChecksums();
        for (Path file : List.of(describesBeforeConcept, carriesConcept, instance)) {
            assertTrue(loaded.contains(Registry.checksum(file)), "registered: " + file.getFileName());
            assertTrue(result(report, file).registration().isPresent(), "under a Registry stamp: " + file.getFileName());
        }
        assertTrue(report.markdown().contains("| `changeSets/src/main/resources/a describes ike-cs.zip` | loaded after a retry |"),
                report.markdown());
    }

    @Test
    @Order(3)
    void aRegisteredFileIsNotLoadedAgain() throws Exception {
        attempts.clear();
        ChangeSetLoad.Report report = ChangeSetLoad.loadStore(storeRoot, recording);

        assertEquals(List.of(), attempts, "nothing to load");
        assertTrue(report.clean());
        assertEquals(3, report.count(ChangeSetLoad.Outcome.REGISTERED_BEFORE));
        assertEquals(0, report.loaded().size());
        assertTrue(report.summary().startsWith("3 change-set file(s): 0 loaded, 3 registered before, 0 refused"), report.summary());
    }

    @Test
    @Order(4)
    void aFileNoOtherFileCompletesIsRefusedInBothPassesAndLeftUnregistered() throws Exception {
        attempts.clear();
        Path orphan = write(storeRoot.resolve(ChangeSetLoad.KNOWLEDGE_FOLDER).resolve("c orphan ike-cs.zip"), stamp(),
                description(ORPHAN_DESCRIPTION, NOBODY_CARRIES));

        ChangeSetLoad.Report report = ChangeSetLoad.loadStore(storeRoot, recording);

        assertEquals(List.of("c orphan ike-cs.zip", "retry c orphan ike-cs.zip"), attempts);
        assertFalse(report.clean());
        assertEquals(ChangeSetLoad.Outcome.REFUSED, outcome(report, orphan));
        assertTrue(result(report, orphan).reason().orElseThrow().contains(NOBODY_CARRIES.toString()), report.markdown());
        assertEquals(3, report.count(ChangeSetLoad.Outcome.REGISTERED_BEFORE), "the others stay skipped");
        assertFalse(Registry.loadedChecksums().contains(Registry.checksum(orphan)), "a refused file is not registered");
        // The identity pass mints the semantic's nid before its record is refused, so the store knows
        // the public id; what it does not hold is the record.
        assertTrue(EntityHandle.get(PublicIds.of(ORPHAN_DESCRIPTION)).entity().isEmpty(), "and its record is not in the store");

        // The next open tries it again; once the component is there, it loads.
        attempts.clear();
        write(storeRoot.resolve(ChangeSetLoad.KNOWLEDGE_FOLDER).resolve("d carries the orphan's concept ike-cs.zip"),
                stamp(), concept(NOBODY_CARRIES));
        report = ChangeSetLoad.loadStore(storeRoot, recording);
        assertTrue(report.clean(), report.markdown());
        assertEquals(ChangeSetLoad.Outcome.LOADED_AFTER_RETRY, outcome(report, orphan));
        assertTrue(Registry.loadedChecksums().contains(Registry.checksum(orphan)));
    }

    @Test
    @Order(5)
    void aListOfFilesLoadsInTheOrderGiven() throws Exception {
        attempts.clear();
        Path first = storeRoot.resolve(ChangeSetLoad.KNOWLEDGE_FOLDER).resolve("a describes ike-cs.zip");
        Path other = write(dir.resolve("elsewhere ike-cs.zip"), stamp(),
                concept(UUID.nameUUIDFromBytes("unregistered-load elsewhere".getBytes())));
        ChangeSetLoad.Report report = ChangeSetLoad.load(dir, List.of(other, first), recording);
        assertEquals(List.of("elsewhere ike-cs.zip"), attempts, "the registered file is skipped, the other loads");
        assertEquals(List.of(), report.folders());
        assertEquals(ChangeSetLoad.Outcome.LOADED, outcome(report, other));
        assertEquals(ChangeSetLoad.Outcome.REGISTERED_BEFORE, outcome(report, first));
    }

    private static ChangeSetLoad.Outcome outcome(ChangeSetLoad.Report report, Path file) {
        return result(report, file).outcome();
    }

    private static ChangeSetLoad.Result result(ChangeSetLoad.Report report, Path file) {
        return report.results().stream().filter(result -> result.file().equals(file)).findFirst()
                .orElseThrow(() -> new AssertionError(file + " has no result in\n" + report.markdown()));
    }

    // ── Change sets by hand: format 1, which the 64-bit store reads in two passes ─────────────

    private static PublicId id(UUID uuid) {
        return PublicId.newBuilder().addUuids(uuid.toString()).build();
    }

    private static PublicId id(EntityFacade facade) {
        return id(facade.publicId().asUuidArray()[0]);
    }

    /** One stamp every file carries, under the starter set's author, module and path. */
    private static TinkarMsg stamp() {
        return TinkarMsg.newBuilder().setStampChronology(StampChronology.newBuilder()
                .setPublicId(id(STAMP))
                .setFirstStampVersion(StampVersion.newBuilder()
                        .setStatusPublicId(id(KernelConcept.ACTIVE_STATE))
                        .setAuthorPublicId(id(KernelConcept.USER))
                        .setModulePublicId(id(KernelConcept.PRIMORDIAL_MODULE))
                        .setPathPublicId(id(KernelConcept.DEVELOPMENT_PATH))
                        .setTime(1_760_000_000_000L))).build();
    }

    private static TinkarMsg concept(UUID concept) {
        return TinkarMsg.newBuilder().setConceptChronology(ConceptChronology.newBuilder()
                .setPublicId(id(concept))
                .addConceptVersions(ConceptVersion.newBuilder().setStampChronologyPublicId(id(STAMP)))).build();
    }

    /** A description of a concept, in the starter set's description pattern. */
    private static TinkarMsg description(UUID semantic, UUID described) {
        return TinkarMsg.newBuilder().setSemanticChronology(SemanticChronology.newBuilder()
                .setPublicId(id(semantic))
                .setReferencedComponentPublicId(id(described))
                .setPatternForSemanticPublicId(id(KernelPattern.DESCRIPTION_PATTERN))
                .addSemanticVersions(SemanticVersion.newBuilder()
                        .setStampChronologyPublicId(id(STAMP))
                        .addFields(Field.newBuilder().setPublicId(id(KernelConcept.ENGLISH_LANGUAGE)))
                        .addFields(Field.newBuilder().setStringValue("A description written by hand"))
                        .addFields(Field.newBuilder().setPublicId(id(KernelConcept.DESCRIPTION_NOT_CASE_SENSITIVE)))
                        .addFields(Field.newBuilder().setPublicId(id(KernelConcept.REGULAR_NAME_DESCRIPTION_TYPE))))).build();
    }

    private static Path write(Path file, TinkarMsg... records) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
            out.putNextEntry(new ZipEntry("Entities"));
            for (TinkarMsg record : records) {
                record.writeDelimitedTo(out);
            }
            out.closeEntry();
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().putValue("Total-Count", Integer.toString(records.length));
            out.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            manifest.write(out);
            out.closeEntry();
        }
        return file;
    }
}
