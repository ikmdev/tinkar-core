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

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.boundary.DataKind;
import dev.ikm.tinkar.entity.boundary.EntityBoundaries;
import dev.ikm.tinkar.entity.boundary.ModuleKinds;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiom;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpressionBuilder;
import dev.ikm.tinkar.entity.scope.KindMove;
import dev.ikm.tinkar.entity.scope.Scope;
import dev.ikm.tinkar.entity.scope.ScopeOperations;
import dev.ikm.tinkar.entity.scope.SharedFolder;
import dev.ikm.tinkar.entity.transaction.StampedWriter;
import dev.ikm.tinkar.importer.registry.ChangeSetLoad;
import dev.ikm.tinkar.importer.registry.Registry;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import dev.ikm.tinkar.terms.State;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A shared document goes from one knowledge base to another through the folder of shared data, and
 * leaves it through a marker alone (IKE-Network/ike-issues#1313; decisions 5, 7 and 8 of
 * {@code design-2026-11-22-knowledge-shared-local} and the questions before step 4).
 */
class SharedDataIT {

    private static final File STORE = TestConstants.createFilePathInTargetFromClassName.apply(SharedDataIT.class);

    @TempDir
    Path folders;

    private final ConceptFacade module = EntityProxy.Concept.make(PublicIds.newRandom());
    private final PublicId pattern = PublicIds.newRandom();
    private final PublicId root = PublicIds.newRandom();
    private final PublicId line = PublicIds.newRandom();
    /** The other knowledge base's folder, and its folder of shared data. */
    private Path other;
    private Path shared;

    @BeforeAll
    static void start() {
        FileUtil.recursiveDelete(STORE);
        TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
    }

    @AfterAll
    static void stop() {
        ModuleKinds.clear();
        EntityBoundaries.clear();
        TestHelper.stopDatabase();
    }

    /**
     * Shares a document, then removes it, so the store is as a knowledge base that has never had it, with
     * the folder the document was shared to.
     */
    @BeforeEach
    void shareAndForget() throws Exception {
        ModuleKinds.writeModule(module, KernelConcept.FILE_MODULE, () -> {
            try (StampedWriter writer = StampedWriter.open("notes/a.adoc", State.ACTIVE, KernelConcept.USER, module, KernelConcept.DEVELOPMENT_PATH)) {
                writer.concept(module.publicId());
                writer.fullyQualifiedName(module.publicId(), "notes/a.adoc");
                LogicalExpressionBuilder leb = new LogicalExpressionBuilder();
                leb.NecessarySet(leb.And(new LogicalAxiom.Atom[] {leb.ConceptAxiom(KernelConcept.FILE_MODULE)}));
                writer.semantic(PublicIds.newRandom(), KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.publicId(), module.publicId(),
                        (DiTreeEntity) leb.build().sourceGraph());
                writer.pattern(pattern, KernelConcept.STRING, KernelConcept.STRING,
                        StampedWriter.field(KernelConcept.STRING, KernelConcept.STRING, KernelConcept.STRING));
                EntityService.get().nidForSemantic(pattern, root);
                writer.semantic(root, pattern, pattern, "the document");
                writer.semantic(line, pattern, root, "a line");
                writer.commit();
            }
        });
        other = Files.createDirectories(folders.resolve("other"));
        shared = Files.createDirectories(SharedFolder.of(other));
        KindMove.Moved moved = KindMove.move(Scope.of(nid(pattern)), KernelConcept.SHARED_MODULE, KernelConcept.USER,
                _ -> dev.ikm.tinkar.common.service.ScopeErasure.ChangeSetRemoval.NONE,
                _ -> Optional.of(shared.resolve("notes-a.adoc [" + uuid() + "] Author 20261123T100000PST abc ike-cs.zip").toFile()));
        assertEquals(1, moved.files().size());
        ScopeOperations.delete(SharedFolder.scopeOf(nid(module.publicId())));
        assertFalse(present(line) || present(module.publicId()), "as if it had never been here");
        ModuleKinds.clear();
        EntityBoundaries.clear();
    }

    /** The store is one for every test: what a test leaves shared would be missing from the next one's folder. */
    @AfterEach
    void forget() {
        if (present(module.publicId())) {
            ScopeOperations.delete(SharedFolder.scopeOf(nid(module.publicId())));
        }
        ModuleKinds.clear();
        EntityBoundaries.clear();
    }

    @Test
    void aSharedDocumentLoadsWhereItIsSyncedAndIsNotRegisteredInKnowledge() throws Exception {
        int registered = Registry.loadedChecksums().size();
        ChangeSetLoad.Report report = load();
        assertEquals(1, report.loaded().size(), report.markdown());
        assertEquals(DataKind.SHARED, EntityBoundaries.kindOf(nid(line)), "shared here too");
        assertEquals(registered, Registry.loadedChecksums().size(), "the registry is knowledge, and the file is named for the document");
        assertTrue(Files.readString(other.resolve("loadedChangeSets.tsv")).contains("notes-a.adoc"), "remembered here instead");
        assertEquals(0, load().loaded().size(), "loaded once");
        assertEquals(List.of(), SharedFolder.apply(shared, KernelConcept.USER).sentences(), "nothing to say");
    }

    @Test
    void onlyAMarkerRemovesASharedDocument() throws Exception {
        load();
        Path file = SharedFolder.changeSets(shared).get(uuid()).getFirst();
        byte[] kept = Files.readAllBytes(file);
        Files.delete(file);

        SharedFolder.Applied missing = SharedFolder.apply(shared, KernelConcept.USER);
        assertTrue(missing.removed().isEmpty(), "missing files remove nothing");
        assertEquals(List.of("notes/a.adoc"), missing.missing(), "and Komet says so");
        assertTrue(present(line));

        SharedFolder.mark(shared, uuid(), "notes/a.adoc", SharedFolder.Act.DELETED, "Ada Lovelace", Instant.parse("2026-11-23T18:00:00Z"));
        SharedFolder.Applied applied = SharedFolder.apply(shared, KernelConcept.USER);
        assertEquals(List.of("notes/a.adoc was deleted by Ada Lovelace on 2026-11-23"), applied.removed(), applied.sentences().toString());
        assertFalse(present(line), "removed here");
        assertFalse(present(module.publicId()), "with its module");

        Files.write(file, kept);                                                        // its file again, from git's history
        assertEquals(0, load().loaded().size(), "a marked document is not loaded");
        assertFalse(present(line));
        assertEquals(1, SharedFolder.unmark(shared, uuid()));                            // shared again
        assertEquals(1, load().loaded().size(), "the same file loads again, since the knowledge base does not hold it");
        assertTrue(present(line));
        assertEquals(DataKind.SHARED, EntityBoundaries.kindOf(nid(line)));
    }

    @Test
    void whatWasNeverSyncedIsKeptHereAsLocalData() throws Exception {
        load();
        Path pulled = SharedFolder.changeSets(shared).get(uuid()).getFirst();
        Files.delete(pulled);                                                           // the sync took it away
        Path own = shared.resolve("notes-a.adoc [" + uuid() + "] Author 20261123T110000PST own ike-cs.zip");
        Files.write(own, new byte[] {1});                                               // a comment of this person's, not synced
        SharedFolder.mark(shared, uuid(), "notes/a.adoc", SharedFolder.Act.MADE_LOCAL, "Ada Lovelace", Instant.parse("2026-11-23T18:00:00Z"));

        SharedFolder.Applied applied = SharedFolder.apply(shared, KernelConcept.USER);
        assertEquals(1, applied.keptLocal().size(), applied.sentences().toString());
        assertTrue(applied.sentences().getFirst().contains("kept here as local data"), applied.sentences().toString());
        assertTrue(present(line));
        assertEquals(DataKind.LOCAL, EntityBoundaries.kindOf(nid(line)));
        assertFalse(Files.exists(own), "and it is out of the folder");
        assertEquals(List.of(), SharedFolder.apply(shared, KernelConcept.USER).sentences(), "done once");
    }

    @Test
    void aCopyKeptBeforeIsKeptAgainWhenTheDocumentIsSharedAgainAndDeleted() throws Exception {
        load();
        Files.delete(SharedFolder.changeSets(shared).get(uuid()).getFirst());           // the sync took it away
        Path kept = Files.createDirectories(other.resolve("localChangeSets"));          // and what was said of the copy kept here
        Files.write(kept.resolve("notes-a.adoc [" + uuid() + "] Author 20261123T110000PST own ike-cs.zip"), new byte[] {1});
        SharedFolder.mark(shared, uuid(), "notes/a.adoc", SharedFolder.Act.DELETED, "Ada Lovelace", Instant.parse("2026-11-23T18:00:00Z"));

        SharedFolder.Applied applied = SharedFolder.apply(shared, KernelConcept.USER);
        assertEquals(List.of(), applied.removed(), applied.sentences().toString());
        assertEquals(1, applied.keptLocal().size(), applied.sentences().toString());
        assertTrue(present(line));
        assertEquals(DataKind.LOCAL, EntityBoundaries.kindOf(nid(line)));
    }

    @Test
    void whatReachesTheFolderAfterADeletionIsClearedWhereTheDocumentIsGone() throws Exception {
        assertFalse(present(line), "deleted here, and the marker with it");
        SharedFolder.mark(shared, uuid(), "notes/a.adoc", SharedFolder.Act.DELETED, "Ada Lovelace", Instant.parse("2026-11-23T18:00:00Z"));
        Files.write(shared.resolve("notes-a.adoc [" + uuid() + "] Author 20261123T120000PST bob ike-cs.zip"), new byte[] {1});
        SharedFolder.apply(shared, KernelConcept.USER);
        assertTrue(SharedFolder.changeSets(shared).isEmpty(), "or a third knowledge base would take it for changes of its own");
        assertEquals(1, SharedFolder.removals(shared).size(), "the marker stays");
    }

    @Test
    void aMarkerThatCannotBeReadRemovesNothing() throws Exception {
        load();
        Files.writeString(shared.resolve("notes-a.adoc [" + uuid() + "] broken" + SharedFolder.MARKER_SUFFIX), "module=not an id\n");
        assertTrue(SharedFolder.removals(shared).isEmpty());
        assertTrue(SharedFolder.apply(shared, KernelConcept.USER).removed().isEmpty());
        assertTrue(present(line));
    }

    /** Writes a document's own module: its concept, its name and the parent it states. */
    static void writeModule(ConceptFacade module, String name, ConceptFacade parent) {
        ModuleKinds.writeModule(module, parent, () -> {
            try (StampedWriter writer = StampedWriter.open(name, State.ACTIVE, KernelConcept.USER, module, KernelConcept.DEVELOPMENT_PATH)) {
                writer.concept(module.publicId());
                writer.fullyQualifiedName(module.publicId(), name);
                LogicalExpressionBuilder leb = new LogicalExpressionBuilder();
                leb.NecessarySet(leb.And(new LogicalAxiom.Atom[] {leb.ConceptAxiom(parent)}));
                writer.semantic(PublicIds.newRandom(), KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.publicId(), module.publicId(),
                        (DiTreeEntity) leb.build().sourceGraph());
                writer.commit();
            }
        });
    }

    private ChangeSetLoad.Report load() throws Exception {
        return ChangeSetLoad.loadStore(other, ChangeSetLoad.Progress.NONE);
    }

    private static long nid(PublicId id) {
        return EntityHandle.get(id).expectEntity().nid();
    }

    private static boolean present(PublicId id) {
        return PrimitiveData.get().hasPublicId(id) && EntityHandle.get(id).entity().isPresent();
    }

    private UUID uuid() {
        return module.publicId().asUuidArray()[0];
    }
}
