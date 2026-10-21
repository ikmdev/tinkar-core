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
 * release packages and Komet's sync adds to git, instance data and ephemeral data each to a folder
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
            module(writer, KernelConcept.EPHEMERAL_MODULE, "Ephemeral module", KernelConcept.SOLOR_MODULE);
            module(writer, KernelConcept.INSTANCE_MODULE, "Instance module", KernelConcept.SOLOR_MODULE);
            module(writer, JOURNALS, "Test journal: module/one", KernelConcept.INSTANCE_MODULE);
            module(writer, GIT_DOCUMENTS, "Test git documents", KernelConcept.EPHEMERAL_MODULE);
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
        Set<PublicId> instanceCarried = carried(root.resolve(ChangeSetWriterProvider.INSTANCE_FOLDER));
        Set<PublicId> ephemeralCarried = carried(root.resolve(ChangeSetWriterProvider.EPHEMERAL_FOLDER));

        assertTrue(knowledgeCarried.contains(knowledge), "knowledge stays in the knowledge folder");
        assertFalse(knowledgeCarried.contains(instance), "no instance data in the knowledge folder");
        assertFalse(knowledgeCarried.contains(ephemeral), "no ephemeral data in the knowledge folder");
        assertTrue(instanceCarried.contains(instance), "the journal line is in the instance folder");
        assertFalse(instanceCarried.contains(ephemeral) || instanceCarried.contains(knowledge), "and only instance data");
        assertTrue(ephemeralCarried.contains(ephemeral), "the git line is in the ephemeral folder");
        assertFalse(ephemeralCarried.contains(instance) || ephemeralCarried.contains(knowledge), "and only ephemeral data");

        List<String> instanceFiles = names(root.resolve(ChangeSetWriterProvider.INSTANCE_FOLDER));
        assertEquals(1, instanceFiles.size(), "one module, one file: " + instanceFiles);
        String expected = "Test journal꞉ module-one [" + JOURNALS.publicId().asUuidArray()[0] + "] ";
        assertTrue(instanceFiles.getFirst().startsWith(expected), "named for the module, made safe: " + instanceFiles.getFirst());
        assertTrue(instanceFiles.getFirst().endsWith(" ike-cs.zip"), instanceFiles.getFirst());
        List<String> ephemeralFiles = names(root.resolve(ChangeSetWriterProvider.EPHEMERAL_FOLDER));
        assertTrue(ephemeralFiles.getFirst().startsWith("Test git documents [" + GIT_DOCUMENTS.publicId().asUuidArray()[0] + "] "),
                ephemeralFiles.getFirst());
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

        Path folder = DATASTORE_ROOT.toPath().resolve(ChangeSetWriterProvider.EPHEMERAL_FOLDER);
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
        LogicalExpressionBuilder leb = new LogicalExpressionBuilder();
        leb.NecessarySet(leb.And(new LogicalAxiom.Atom[] {leb.ConceptAxiom(parent)}));
        writer.semantic(PublicIds.newRandom(), KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.publicId(), module.publicId(),
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
