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
import dev.ikm.tinkar.common.service.DataActivity;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.boundary.CommitBoundary;
import dev.ikm.tinkar.entity.boundary.DataKind;
import dev.ikm.tinkar.entity.boundary.EntityBoundaries;
import dev.ikm.tinkar.entity.boundary.ModuleKinds;
import dev.ikm.tinkar.entity.changeset.ComponentTable;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiom;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpressionBuilder;
import dev.ikm.tinkar.entity.transaction.StampedWriter;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.provider.changeset.ChangeSetWriterProvider;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import dev.ikm.tinkar.terms.State;
import network.ike.foundation.ike.bindings.IkeConcept;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The change-set writer's three streams (IKE-Network/ike-issues#1313): knowledge to the folder a
 * release packages and Komet's sync adds to git, local data and local data saved in a file each to a folder
 * of its own, one file per module per session, named for the module.
 */
class ChangeSetStreamsIT {

    private static final File DATASTORE_ROOT =
            TestConstants.createFilePathInTargetFromClassName.apply(ChangeSetStreamsIT.class);
    private static final long TIME = 1_760_000_000_000L;

    private static final ConceptFacade JOURNALS = EntityProxy.Concept.make(PublicIds.newRandom());
    private static final ConceptFacade GIT_DOCUMENTS = EntityProxy.Concept.make(PublicIds.newRandom());
    private static long time = TIME;

    @BeforeAll
    static void start() throws Exception {
        FileUtil.recursiveDelete(DATASTORE_ROOT);
        System.setProperty(CommitBoundary.MODE_PROPERTY, "fail");
        TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, DATASTORE_ROOT);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
        try (StampedWriter writer = writer(IkeConcept.DEVELOPMENT_MODULE, "modules")) {
            module(writer, KernelConcept.FILE_MODULE, "File module", KernelConcept.SOLOR_MODULE);
            module(writer, KernelConcept.LOCAL_MODULE, "Local module", KernelConcept.SOLOR_MODULE);
            module(writer, JOURNALS, "Test journal: module/one", KernelConcept.LOCAL_MODULE);
            module(writer, GIT_DOCUMENTS, "Test git documents", KernelConcept.FILE_MODULE);
            writer.commit();
        }
    }

    @AfterAll
    static void stop() {
        System.clearProperty(CommitBoundary.MODE_PROPERTY);
        ModuleKinds.clear();
        EntityBoundaries.clear();
        TestHelper.stopDatabase();
    }

    @Test
    void eachKindGoesToItsOwnFolderAndEachModuleToItsOwnFile() throws Exception {
        PublicId knowledge = PublicIds.newRandom();
        PublicId instance = PublicIds.newRandom();
        PublicId ephemeral = PublicIds.newRandom();
        comment(IkeConcept.DEVELOPMENT_MODULE, knowledge, "knowledge");
        comment(JOURNALS, instance, "a journal line");
        comment(GIT_DOCUMENTS, ephemeral, "a line of a document in git");
        ChangeSetWriterProvider.provider().save().get(1, TimeUnit.MINUTES);

        Path root = DATASTORE_ROOT.toPath();
        Set<PublicId> knowledgeCarried = carried(root.resolve(Path.of("changeSets", "src", "main", "resources")));
        Set<PublicId> instanceCarried = carried(root.resolve(ChangeSetWriterProvider.LOCAL_FOLDER));
        Set<PublicId> ephemeralCarried = carried(root.resolve(ChangeSetWriterProvider.FILE_FOLDER));

        assertTrue(knowledgeCarried.contains(knowledge), "knowledge stays in the knowledge folder");
        assertFalse(knowledgeCarried.contains(instance), "no local data in the knowledge folder");
        assertFalse(knowledgeCarried.contains(ephemeral), "no file data in the knowledge folder");
        assertTrue(instanceCarried.contains(instance), "the journal line is in the instance folder");
        assertFalse(instanceCarried.contains(ephemeral) || instanceCarried.contains(knowledge), "and only local data");
        assertTrue(ephemeralCarried.contains(ephemeral), "the git line is in the ephemeral folder");
        assertFalse(ephemeralCarried.contains(instance) || ephemeralCarried.contains(knowledge), "and only file data");

        List<String> instanceFiles = names(root.resolve(ChangeSetWriterProvider.LOCAL_FOLDER));
        assertEquals(1, instanceFiles.size(), "one module, one file: " + instanceFiles);
        String expected = "Test journal꞉ module-one [" + JOURNALS.publicId().asUuidArray()[0] + "] ";
        assertTrue(instanceFiles.getFirst().startsWith(expected), "named for the module, made safe: " + instanceFiles.getFirst());
        assertTrue(instanceFiles.getFirst().endsWith(" ike-cs.zip"), instanceFiles.getFirst());
        List<String> ephemeralFiles = names(root.resolve(ChangeSetWriterProvider.FILE_FOLDER));
        assertTrue(ephemeralFiles.getFirst().startsWith("Test git documents [" + GIT_DOCUMENTS.publicId().asUuidArray()[0] + "] "),
                ephemeralFiles.getFirst());
    }

    /**
     * A module written under itself, as a document's own module is, reaches the folder of its parent's kind
     * from its first record, the STAMP, on: nothing of it may reach the knowledge folder, which is synced,
     * and which the next opening loads, bringing back a module that was dropped (IKE-Network/ike-issues#1313).
     * The change sets are closed after each record, as the writer may take each one up before the next is written.
     */
    @Test
    void aModuleWrittenUnderItselfNeverReachesTheKnowledgeFolder() throws Exception {
        ConceptFacade own = EntityProxy.Concept.make(PublicIds.newRandom());
        PublicId[] stamp = new PublicId[1];
        ModuleKinds.writeModule(own, KernelConcept.FILE_MODULE, () -> {
            try (StampedWriter writer = writer(own, "a document's own module")) {
                stamp[0] = writer.stamp().publicId();
                written(writer.stamp().nid());
                assertEquals(DataKind.FILE, ModuleKinds.resolve(own.nid()).kind(), "before anything states its parent");
                written(writer.concept(own.publicId()));
                assertEquals(DataKind.FILE, EntityBoundaries.kindOf(own.nid()), "its concept is file data at once");
                written(writer.fullyQualifiedName(own.publicId(), "conversations/stamp.adoc"));
                written(stateParent(writer, own, KernelConcept.FILE_MODULE));
                writer.commit();
            }
        });
        assertEquals(DataKind.FILE, ModuleKinds.resolve(own.nid()).kind(), "and afterwards, from its stated parent");

        Path root = DATASTORE_ROOT.toPath();
        Set<PublicId> knowledge = carried(root.resolve(Path.of("changeSets", "src", "main", "resources")));
        Set<PublicId> file = carried(root.resolve(ChangeSetWriterProvider.FILE_FOLDER));
        for (PublicId id : new PublicId[] {stamp[0], own.publicId()}) {
            assertFalse(knowledge.contains(id), "nothing of the module in the knowledge folder: " + id.idString());
            assertTrue(file.contains(id), "it is in the file folder: " + id.idString());
        }
    }

    /**
     * A change set is named for its module though it was begun by a record stored before the module had a
     * name, as a module's own STAMP is: it takes its name as it is closed (IKE-Network/ike-issues#1313).
     */
    @Test
    void aChangeSetBegunBeforeItsModuleIsNamedTakesTheNameAsItIsClosed() throws Exception {
        ConceptFacade own = EntityProxy.Concept.make(PublicIds.newRandom());
        String id = "[" + own.publicId().asUuidArray()[0] + "]";
        Path folder = DATASTORE_ROOT.toPath().resolve(ChangeSetWriterProvider.FILE_FOLDER);
        ModuleKinds.writeModule(own, KernelConcept.FILE_MODULE, () -> {
            try (StampedWriter writer = writer(own, "a document's own module")) {
                put(writer.stamp().nid());
                awaitBegun(folder, id);                                   // the file is open, and the module has no name
                put(writer.concept(own.publicId()));
                put(writer.fullyQualifiedName(own.publicId(), "conversations/named.adoc"));
                put(stateParent(writer, own, KernelConcept.FILE_MODULE));
                writer.commit();
            }
        });
        ChangeSetWriterProvider.provider().save().get(1, TimeUnit.MINUTES);
        List<String> files = names(folder).stream().filter(name -> name.contains(id)).toList();
        assertEquals(1, files.size(), files.toString());
        assertTrue(files.getFirst().startsWith("conversations-named.adoc " + id + " "), files.getFirst());
    }

    /**
     * Sharing a document moves its module to the Shared module and everything under it goes to the shared
     * folder, under the same identities; making it local brings it back, and what may not move is refused
     * with nothing changed (IKE-Network/ike-issues#1313, decision 5).
     */
    @Test
    void aDocumentMovesToTheSharedFolderAndBack() throws Exception {
        ownDocuments();
        Own a = own("shared-a.adoc", null);
        Own b = own("shared-b.adoc", a);                                    // b refers to a line of a
        Path root = DATASTORE_ROOT.toPath();
        Path file = root.resolve(ChangeSetWriterProvider.FILE_FOLDER);
        Path shared = root.resolve(ChangeSetWriterProvider.SHARED_FOLDER);
        ChangeSetWriterProvider.provider().save().get(1, TimeUnit.MINUTES);
        assertTrue(carried(file).containsAll(Set.of(a.module.publicId(), a.line)), "file data first");

        dev.ikm.tinkar.entity.scope.KindMove.Refused alone = org.junit.jupiter.api.Assertions.assertThrows(
                dev.ikm.tinkar.entity.scope.KindMove.Refused.class, () -> move(KernelConcept.SHARED_MODULE, b));
        assertTrue(alone.getMessage().contains("refers to file data outside it"), alone.getMessage());
        assertEquals(DataKind.FILE, ModuleKinds.resolve(b.module.nid()).kind(), "refused with nothing changed");
        assertTrue(carried(file).contains(b.line), "its records are still there");

        dev.ikm.tinkar.entity.scope.KindMove.Moved moved = move(KernelConcept.SHARED_MODULE, a, b);
        assertEquals(DataKind.SHARED, moved.to());
        assertEquals(2, moved.files().size(), "a file per module");
        for (Own own : List.of(a, b)) {
            assertEquals(DataKind.SHARED, ModuleKinds.resolve(own.module.nid()).kind());
            assertEquals(DataKind.SHARED, EntityBoundaries.kindOf(EntityHandle.get(own.line).expectEntity().nid()), "its lines move with it");
            assertTrue(carried(shared).containsAll(Set.of(own.module.publicId(), own.pattern, own.root, own.line)), "in the shared folder");
            assertFalse(carried(file).contains(own.line), "and in the file folder no more");
        }
        assertTrue(names(shared).stream().anyMatch(name -> name.startsWith("shared-a.adoc [" + a.module.publicId().asUuidArray()[0] + "] ")), names(shared).toString());
        assertFalse(carried(root.resolve(Path.of("changeSets", "src", "main", "resources"))).contains(a.line), "never in the knowledge folder");
        for (File written : moved.files()) {
            dev.ikm.tinkar.entity.changeset.ChangeSetVerification.Verification verified =
                    new dev.ikm.tinkar.entity.changeset.ChangeSetVerification(written).call();
            assertTrue(verified.ok(), verified.text());
        }
        PublicId later = PublicIds.newRandom();
        try (StampedWriter writer = writer(a.module, "a comment after sharing")) {
            put(writer.stamp().nid());
            put(writer.semantic(later, KernelPattern.COMMENT_PATTERN.publicId(), a.line, "said of a shared document"));
            writer.commit();
        }
        ChangeSetWriterProvider.provider().save().get(1, TimeUnit.MINUTES);
        assertTrue(carried(shared).contains(later), "what is written under it afterwards is shared too");

        dev.ikm.tinkar.entity.scope.KindMove.Refused held = org.junit.jupiter.api.Assertions.assertThrows(
                dev.ikm.tinkar.entity.scope.KindMove.Refused.class, () -> move(KernelConcept.FILE_MODULE, a));
        assertTrue(held.getMessage().contains("refers to it"), "shared data still refers to a: " + held.getMessage());
        move(KernelConcept.FILE_MODULE, a, b);
        assertEquals(DataKind.FILE, EntityBoundaries.kindOf(EntityHandle.get(a.line).expectEntity().nid()));
        assertFalse(carried(shared).contains(a.line) || carried(shared).contains(later), "nothing of it in the shared folder");
    }

    @Test
    void anEditNotCommittedIsNotMoved() {
        ownDocuments();
        Own a = own("pending.adoc", null);
        try (StampedWriter writer = StampedWriter.open("pending", State.ACTIVE, KernelConcept.USER, a.module, KernelConcept.DEVELOPMENT_PATH)) {
            writer.semantic(a.line, a.pattern, a.root, "an edit not committed");
            dev.ikm.tinkar.entity.scope.KindMove.Refused refused = org.junit.jupiter.api.Assertions.assertThrows(
                    dev.ikm.tinkar.entity.scope.KindMove.Refused.class, () -> move(KernelConcept.SHARED_MODULE, a));
            assertTrue(refused.getMessage().contains("not yet committed"), refused.getMessage());
        }
    }

    /**
     * A record handed to the writer just before its commit is in a file, whichever side of the commit the
     * writer takes it up on. Asked twice whether it was committed, the writer wrote about one in five
     * nowhere (IKE-Network/ike-issues#1313).
     */
    @Test
    void aRecordTakenUpAsItsCommitLandsIsWritten() throws Exception {
        ownDocuments();
        Own a = own("commit-lands.adoc", null);
        Path file = DATASTORE_ROOT.toPath().resolve(ChangeSetWriterProvider.FILE_FOLDER);
        List<Integer> lost = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            PublicId said = PublicIds.newRandom();
            try (StampedWriter writer = writer(a.module, "comment " + i)) {
                put(writer.stamp().nid());
                put(writer.semantic(said, KernelPattern.COMMENT_PATTERN.publicId(), a.line, "said " + i));
                writer.commit();
            }
            ChangeSetWriterProvider.provider().save().get(1, TimeUnit.MINUTES);
            if (!carried(file).contains(said)) {
                lost.add(i);
            }
        }
        assertEquals(List.of(), lost, "comments in no change set");
    }

    /** A document under a module of its own. */
    private record Own(ConceptFacade module, PublicId pattern, PublicId root, PublicId line) { }

    private static void ownDocuments() {
        if (!dev.ikm.tinkar.common.service.PrimitiveData.get().hasPublicId(KernelConcept.SHARED_MODULE.publicId())
                || EntityHandle.get(KernelConcept.SHARED_MODULE.nid()).entity().isEmpty()) {
            try (StampedWriter writer = writer(IkeConcept.DEVELOPMENT_MODULE, "the Shared module")) {
                module(writer, KernelConcept.SHARED_MODULE, "Shared module", KernelConcept.SOLOR_MODULE);
                writer.commit();
            }
            ModuleKinds.clear();
        }
    }

    private static Own own(String name, Own cites) {
        Own own = new Own(EntityProxy.Concept.make(PublicIds.newRandom()), PublicIds.newRandom(), PublicIds.newRandom(), PublicIds.newRandom());
        ModuleKinds.writeModule(own.module, KernelConcept.FILE_MODULE, () -> {
            try (StampedWriter writer = writer(own.module, name)) {
                put(writer.stamp().nid());
                put(writer.concept(own.module.publicId()));
                put(writer.fullyQualifiedName(own.module.publicId(), name));
                put(stateParent(writer, own.module, KernelConcept.FILE_MODULE));
                put(writer.pattern(own.pattern, KernelConcept.STRING, KernelConcept.STRING,
                        StampedWriter.field(KernelConcept.STRING, KernelConcept.STRING, KernelConcept.STRING)));
                dev.ikm.tinkar.entity.EntityService.get().nidForSemantic(own.pattern, own.root);
                put(writer.semantic(own.root, own.pattern, own.pattern, "root of " + name));
                put(writer.semantic(own.line, own.pattern, cites == null ? own.root : cites.line, "a line of " + name));
                writer.commit();
            }
        });
        return own;
    }

    private static dev.ikm.tinkar.entity.scope.KindMove.Moved move(ConceptFacade parent, Own... documents) {
        long[] roots = java.util.Arrays.stream(documents).mapToLong(own -> EntityHandle.get(own.pattern).expectEntity().nid()).toArray();
        return dev.ikm.tinkar.entity.scope.KindMove.move(dev.ikm.tinkar.entity.scope.Scope.of(roots), parent, KernelConcept.USER,
                ChangeSetWriterProvider.provider()::removeComponents, ChangeSetWriterProvider.provider()::newChangeSet);
    }

    private static void put(long nid) {
        ChangeSetWriterProvider.provider().writeToChangeSet(EntityHandle.get(nid).expectEntity(), DataActivity.SYNCHRONIZABLE_EDIT);
    }

    /** Waits until the writer has begun a file for a module, by the module's id in its name. */
    private static void awaitBegun(Path folder, String id) {
        long until = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < until) {
            File[] begun = folder.toFile().listFiles((dir, name) -> name.contains(id));
            if (begun != null && begun.length > 0) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("no change set was begun for " + id);
    }

    /** Hands a record to the writer as a store does when it is put, and has the writer take it up before the next. */
    private static void written(long nid) {
        put(nid);
        try {
            ChangeSetWriterProvider.provider().save().get(1, TimeUnit.MINUTES);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A deleted scope leaves the ephemeral change sets (IKE-Network/ike-issues#1313): every file
     * holding its records is written again without them, a file holding nothing else is deleted,
     * and a STAMP another document is written under stays. The rewritten files conform to format 3.
     */
    @Test
    void aDeletedScopeLeavesTheChangeSets() throws Exception {
        dev.ikm.tinkar.integration.scope.ScopeFixture fixture = new dev.ikm.tinkar.integration.scope.ScopeFixture();
        fixture.seed();
        dev.ikm.tinkar.integration.scope.ScopeFixture.Doc[] docs = fixture.documents("Ac", "Bc");
        dev.ikm.tinkar.integration.scope.ScopeFixture.Doc a = docs[0];
        dev.ikm.tinkar.integration.scope.ScopeFixture.Doc b = docs[1];
        feed(dev.ikm.tinkar.entity.scope.Scope.of(EntityHandle.get(a.pattern()).expectEntity().nid()));
        feed(dev.ikm.tinkar.entity.scope.Scope.of(EntityHandle.get(b.pattern()).expectEntity().nid()));
        ChangeSetWriterProvider.provider().save().get(1, TimeUnit.MINUTES);
        fixture.revise(a, "Ac revised");
        Entity<? extends EntityVersion> comment = EntityHandle.get(a.comment()).expectEntity();
        EntityVersion revision = comment.versions().stream().map(v -> (EntityVersion) v).max(java.util.Comparator.comparingLong(EntityVersion::time)).orElseThrow();
        EntityVersion shared = comment.versions().stream().map(v -> (EntityVersion) v).min(java.util.Comparator.comparingLong(EntityVersion::time)).orElseThrow();
        PublicId revisionStamp = EntityHandle.get(revision.stampNid()).expectEntity().publicId();
        PublicId sharedStamp = EntityHandle.get(shared.stampNid()).expectEntity().publicId();
        ChangeSetWriterProvider.provider().writeToChangeSet(comment, DataActivity.SYNCHRONIZABLE_EDIT);
        ChangeSetWriterProvider.provider().writeToChangeSet(EntityHandle.get(revision.stampNid()).expectEntity(), DataActivity.SYNCHRONIZABLE_EDIT);
        ChangeSetWriterProvider.provider().save().get(1, TimeUnit.MINUTES);

        Path folder = DATASTORE_ROOT.toPath().resolve(ChangeSetWriterProvider.FILE_FOLDER);
        int filesBefore = names(folder).size();
        Set<PublicId> before = carried(folder);
        assertTrue(before.contains(a.root()) && before.contains(a.comment()) && before.contains(revisionStamp), "A's records are there");

        dev.ikm.tinkar.entity.scope.ScopeOperations.Deleted deleted = dev.ikm.tinkar.entity.scope.ScopeOperations.delete(
                dev.ikm.tinkar.entity.scope.Scope.of(EntityHandle.get(a.pattern()).expectEntity().nid()), ChangeSetWriterProvider.provider()::removeComponents);

        assertTrue(deleted.changeSets().records() > 0, String.valueOf(deleted.changeSets()));
        assertEquals(1, deleted.changeSets().deleted(), "the file holding only A's revision is deleted");
        assertTrue(deleted.changeSets().rewritten() >= 1, "the file holding A and B is written again");
        assertEquals(filesBefore - 1, names(folder).size());
        Set<PublicId> after = carried(folder);
        for (PublicId id : new PublicId[] {a.pattern(), a.root(), a.line(), a.cited(), a.comment(), revisionStamp}) {
            assertFalse(after.contains(id), "no record of " + id.idString() + " is left");
        }
        assertTrue(after.contains(b.root()) && after.contains(b.comment()), "B's records stay");
        assertTrue(after.contains(sharedStamp), "the STAMP B is written under stays");
        for (String name : names(folder)) {
            dev.ikm.tinkar.entity.changeset.ChangeSetVerification.Verification verification =
                    new dev.ikm.tinkar.entity.changeset.ChangeSetVerification(folder.resolve(name).toFile()).call();
            assertTrue(verification.ok(), verification.text());
        }
    }

    /** Hands a scope's components and STAMPs to the writer, as a store with the writer loaded does. */
    private static void feed(dev.ikm.tinkar.entity.scope.Scope scope) {
        for (long nid : scope.stamps().toArray()) {
            ChangeSetWriterProvider.provider().writeToChangeSet(EntityHandle.get(nid).expectEntity(), DataActivity.SYNCHRONIZABLE_EDIT);
        }
        for (long nid : scope.members().toArray()) {
            ChangeSetWriterProvider.provider().writeToChangeSet(EntityHandle.get(nid).expectEntity(), DataActivity.SYNCHRONIZABLE_EDIT);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static StampedWriter writer(ConceptFacade module, String name) {
        return StampedWriter.open(name, State.ACTIVE, ++time, KernelConcept.USER, module, KernelConcept.DEVELOPMENT_PATH);
    }

    /**
     * Commits a comment under a module, then hands the committed semantic and its STAMP to the
     * writer, as a store with the writer loaded does on every put (the test harness loads none).
     */
    private static void comment(ConceptFacade module, PublicId semantic, String text) {
        try (StampedWriter writer = writer(module, text)) {
            writer.semantic(semantic, KernelPattern.COMMENT_PATTERN.publicId(), KernelConcept.USER.publicId(), text);
            writer.commit();
        }
        Entity<? extends EntityVersion> written = EntityHandle.get(semantic).expectEntity();
        ChangeSetWriterProvider.provider().writeToChangeSet(written, DataActivity.SYNCHRONIZABLE_EDIT);
        for (EntityVersion version : written.versions()) {
            ChangeSetWriterProvider.provider().writeToChangeSet(EntityHandle.get(version.stampNid()).expectEntity(),
                    DataActivity.SYNCHRONIZABLE_EDIT);
        }
    }

    private static void module(StampedWriter writer, ConceptFacade module, String name, ConceptFacade parent) {
        writer.concept(module.publicId());
        writer.fullyQualifiedName(module.publicId(), name);
        stateParent(writer, module, parent);
    }

    private static long stateParent(StampedWriter writer, ConceptFacade module, ConceptFacade parent) {
        LogicalExpressionBuilder leb = new LogicalExpressionBuilder();
        leb.NecessarySet(leb.And(new LogicalAxiom.Atom[] {leb.ConceptAxiom(parent)}));
        return writer.semantic(PublicIds.newRandom(), KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.publicId(), module.publicId(),
                (DiTreeEntity) leb.build().sourceGraph());
    }

    /** The components the finished change sets in a folder carry, not those they only reference. */
    private static Set<PublicId> carried(Path folder) throws IOException {
        Set<PublicId> carried = new HashSet<>();
        if (!Files.isDirectory(folder)) {
            return carried;
        }
        for (String name : names(folder)) {
            try (ZipFile zip = new ZipFile(folder.resolve(name).toFile())) {
                ComponentTable.forEach(zip, component -> {
                    if (!component.referencedOnly()) {
                        carried.add(component.publicId());
                    }
                });
            }
        }
        return carried;
    }

    private static List<String> names(Path folder) throws IOException {
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.map(path -> path.getFileName().toString()).filter(name -> name.endsWith("ike-cs.zip")).sorted().forEach(names::add);
        }
        return names;
    }
}
