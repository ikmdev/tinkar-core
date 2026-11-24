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
package dev.ikm.tinkar.integration.scope;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.PrimitiveDataSearchResult;
import dev.ikm.tinkar.common.service.internal.EntityStore;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.boundary.DataKind;
import dev.ikm.tinkar.entity.boundary.EntityBoundaries;
import dev.ikm.tinkar.entity.boundary.ModuleKinds;
import dev.ikm.tinkar.entity.scope.DataInventory;
import dev.ikm.tinkar.entity.scope.Scope;
import dev.ikm.tinkar.entity.scope.ScopeOperations;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.integration.scope.ScopeFixture.Doc;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import org.eclipse.collections.api.factory.primitive.LongLists;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scope operations (IKE-Network/ike-issues#1313): a document's scope is its pattern and what cites
 * it, a reference from another scope's pattern blocks its delete, delete erases it and collects the
 * STAMPs only it is written under, export then import brings it back whole, and an import is
 * refused before anything is loaded when the store lacks knowledge the scope references.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScopeOperationsIT {

    private static final File WORK = TestConstants.createFilePathInTargetFromClassName.apply(ScopeOperationsIT.class);

    private final ScopeFixture fixture = new ScopeFixture();

    @BeforeAll
    void beforeAll() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
        fixture.seed();
        FileUtil.recursiveDelete(WORK);
        assertTrue(WORK.mkdirs());
    }

    @AfterAll
    void afterAll() {
        TestHelper.stopDatabase();
    }

    @AfterEach
    void forget() {
        ModuleKinds.clear();
        EntityBoundaries.clear();
    }

    @Test
    void aDocumentsScopeIsItsPatternAndWhatCitesIt() {
        Doc[] docs = fixture.documents("A1", "B1");
        Scope scope = Scope.of(nid(docs[0].pattern()));
        assertEquals(DataKind.FILE, scope.kind());
        assertTrue(scope.closed(), scope.incoming() + " " + scope.outgoing() + " " + scope.dangling());
        for (PublicId id : new PublicId[] {docs[0].pattern(), docs[0].root(), docs[0].line(), docs[0].cited(), docs[0].comment()}) {
            assertTrue(scope.contains(nid(id)), "the scope holds " + id.idString());
        }
        assertTrue(scope.members().size() > 5, "and the names on its pattern");
        assertFalse(scope.contains(nid(docs[1].root())), "not the other document");
        assertTrue(scope.knowledge().contains(fixture.term.nid()), "the knowledge it references: the term");
        assertTrue(scope.knowledge().contains(KernelPattern.COMMENT_PATTERN.nid()), "the shared pattern its comment is in");
        assertTrue(scope.knowledge().contains(fixture.documents.nid()), "its STAMPs' module");
    }

    @Test
    void knowledgeIsNeverAScope() {
        assertThrows(IllegalArgumentException.class, () -> Scope.of(fixture.term.nid()));
    }

    @Test
    void aCitationFromAnotherScopesPatternBlocksTheDelete() {
        Doc[] docs = fixture.documents("A2", "B2");
        fixture.cite(docs[1], docs[0]);
        Scope scope = Scope.of(nid(docs[0].pattern()));
        assertFalse(scope.closed(), "B2's own pattern cites a line of A2");
        assertThrows(IllegalStateException.class, () -> ScopeOperations.delete(scope));
        assertTrue(PrimitiveData.get().hasPublicId(docs[0].root()), "a refused delete erases nothing");
    }

    /**
     * A reference into a scope carried in a field, which the referencing index does not hold,
     * refuses its delete; the linking scope references out, so it cannot be exported, but can be
     * deleted, and then the first one can be too.
     */
    @Test
    void aFieldReferenceIntoTheScopeRefusesItsDelete() {
        Doc target = fixture.documents("T6")[0];
        PublicId linking = fixture.link(target);
        Scope scope = Scope.of(nid(target.pattern()));
        assertTrue(scope.closed(), "resolution cannot see a reference in another scope's field");
        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> ScopeOperations.delete(scope));
        assertTrue(refused.getMessage().contains("lead into it"), refused.getMessage());
        assertTrue(PrimitiveData.get().hasPublicId(target.root()), "a refused delete erases nothing");

        Scope link = Scope.of(nid(linking));
        assertFalse(link.exportable(), "the linking scope references out of itself");
        assertThrows(IllegalStateException.class, () -> ScopeOperations.export(link, new File(WORK, "link.zip")));
        assertEquals(1, ScopeOperations.delete(link).outgoing(), "its reference out goes with it");
        ScopeOperations.delete(Scope.of(nid(target.pattern())));
        assertFalse(PrimitiveData.get().hasPublicId(target.root()), "nothing leads into it now");
    }

    /** Two documents linked by a field reference are deleted together as one scope. */
    @Test
    void linkedDocumentsAreDeletedTogether() {
        Doc target = fixture.documents("U7")[0];
        PublicId linking = fixture.link(target);
        Scope both = Scope.of(nid(target.pattern()), nid(linking));
        assertTrue(both.closed(), String.valueOf(both.outgoing()));
        ScopeOperations.delete(both);
        assertFalse(PrimitiveData.get().hasPublicId(target.root()));
        assertFalse(PrimitiveData.get().hasPublicId(linking));
    }

    @Test
    void deleteErasesTheScopeAndCollectsTheStampsOnlyItIsWrittenUnder() throws Exception {
        Doc[] docs = fixture.documents("A3", "B3");
        Doc a = docs[0];
        Doc b = docs[1];
        fixture.revise(a, "zebracornA3 revised");
        long revision = latest(a.comment()).stampNid();
        long shared = EntityHandle.get(nid(a.root())).expectEntity().versions().getFirst() instanceof EntityVersion v ? v.stampNid() : 0;
        PublicId revisionId = PrimitiveData.publicId(revision);
        long aCited = nid(a.cited());
        long aComment = nid(a.comment());
        assertTrue(searchFinds("zebracornA3", aComment), "the comment's text is in the search index");

        ScopeOperations.Deleted deleted = ScopeOperations.delete(Scope.of(nid(a.pattern())));

        assertEquals(1, deleted.stamps(), "the revision's STAMP, written under by A3 alone, is collected");
        assertEquals(1, deleted.stampsKept(), "the STAMP A3 shares with B3 is kept");
        for (PublicId id : new PublicId[] {a.pattern(), a.root(), a.line(), a.cited(), a.comment(), revisionId}) {
            assertFalse(PrimitiveData.get().hasPublicId(id), "erased: " + id.idString());
        }
        assertTrue(EntityHandle.get(shared).entity().isPresent(), "the shared STAMP is still there");
        assertTrue(PrimitiveData.get().hasPublicId(b.root()) && EntityHandle.get(nid(b.comment())).entity().isPresent(), "B3 is untouched");
        long[] citingTerm = EntityStore.current().semanticNidsForComponent(fixture.term.nid());
        assertFalse(LongLists.immutable.of(citingTerm).contains(aCited), "the term no longer lists A3's line among its citations");
        assertTrue(LongLists.immutable.of(citingTerm).contains(nid(b.cited())), "but still lists B3's");
        assertFalse(LongLists.immutable.of(EntityStore.current().semanticNidsOfPattern(KernelPattern.COMMENT_PATTERN.nid())).contains(aComment),
                "the comment pattern no longer lists A3's comment");
        assertFalse(searchFinds("zebracornA3", aComment), "the comment's text has left the search index");
        assertTrue(searchFinds("zebracornB3", nid(b.comment())), "B3's has not");
    }

    @Test
    void exportThenImportBringsTheScopeBack() {
        Doc c = fixture.documents("C4")[0];
        fixture.revise(c, "zebracornC4 revised");
        Scope scope = Scope.of(nid(c.pattern()));
        File file = new File(WORK, "c4-scope.zip");
        ScopeOperations.Exported exported = ScopeOperations.export(scope, file);
        assertEquals(scope.knowledge().size(), exported.references());
        ScopeOperations.delete(scope);
        assertFalse(PrimitiveData.get().hasPublicId(c.root()));

        ScopeOperations.Imported imported = ScopeOperations.importScope(file);
        assertEquals(DataKind.FILE, imported.kind());
        assertEquals(1, imported.roots().size());
        assertTrue(PrimitiveData.get().hasPublicId(c.root()), "the root is back, under its public id");
        assertEquals("zebracornC4 revised", latest(c.comment()) instanceof dev.ikm.tinkar.entity.SemanticEntityVersion v
                ? v.fieldValues().get(0) : null, "with every version");
        Scope again = Scope.of(nid(c.pattern()));
        assertEquals(scope.members().size(), again.members().size(), "the same scope");
        assertEquals(scope.stamps().size(), again.stamps().size());
        assertTrue(again.closed());
    }

    @Test
    void anImportIsRefusedWhenTheStoreLacksReferencedKnowledge() throws Exception {
        Doc d = fixture.documents("D5")[0];
        Scope scope = Scope.of(nid(d.pattern()));
        File file = new File(WORK, "d5-scope.zip");
        ScopeOperations.export(scope, file);
        ScopeOperations.delete(scope);
        UUID unknown = UUID.randomUUID();
        try (FileSystem zip = FileSystems.newFileSystem(URI.create("jar:" + file.toURI()), Map.of())) {
            Files.writeString(zip.getPath(ScopeOperations.SCOPE_MANIFEST), "Reference: " + unknown + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND);
        }
        ScopeOperations.MissingKnowledgeException refused = assertThrows(ScopeOperations.MissingKnowledgeException.class,
                () -> ScopeOperations.importScope(file));
        assertEquals(PublicIds.of(unknown), refused.missing().getFirst());
        assertFalse(PrimitiveData.get().hasPublicId(d.root()), "nothing was loaded");
    }

    // ── import, checked (step 2 of plan-2026-11-22-remove-export-import-data) ──

    /** What was exported beside the components comes back with the manifest, and an import writes the store's own change set. */
    @Test
    void anImportWritesTheStoresOwnChangeSetAndGivesTheEntriesBack() throws Exception {
        Doc j = fixture.journals("J8")[0];
        File file = new File(WORK, "j8-scope.zip");
        ScopeOperations.export(Scope.of(nid(j.pattern())), file, java.util.Map.of("conversation.json", "{}".getBytes(StandardCharsets.UTF_8)));
        assertTrue(ScopeOperations.exportedScope(file));
        ScopeOperations.Manifest manifest = ScopeOperations.manifest(file);
        assertEquals(DataKind.LOCAL, manifest.kind());
        assertEquals(java.util.List.of(fixture.journals.publicId()), manifest.modules());
        assertEquals("{}", new String(manifest.entries().get("conversation.json"), StandardCharsets.UTF_8));

        ScopeOperations.delete(Scope.of(nid(j.pattern())));
        File written = new File(WORK, "j8-local ike-cs.zip");
        java.util.List<Long> modules = new java.util.ArrayList<>();
        ScopeOperations.Imported imported = ScopeOperations.importScope(file, false, _ -> dev.ikm.tinkar.common.service.ScopeErasure.ChangeSetRemoval.NONE,
                module -> {
                    modules.add(module);
                    return java.util.Optional.of(written);
                });
        assertEquals(java.util.List.of(written), imported.changeSets());
        assertEquals(java.util.List.of(fixture.journals.nid()), modules, "one file, for the module it is written under");
        assertFalse(imported.replaced());
        assertFalse(ScopeOperations.exportedScope(written), "an ordinary change set");

        // A store rebuilt from its folders has it: the file alone brings it back.
        int members = Scope.of(nid(j.pattern())).members().size();
        ScopeOperations.delete(Scope.of(nid(j.pattern())));
        assertFalse(PrimitiveData.get().hasPublicId(j.root()));
        new dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile(written).compute();
        EntityBoundaries.clear();
        assertEquals(members, Scope.of(nid(j.pattern())).members().size());
    }

    @Test
    void whatIsHereAlreadyIsReplacedOnlyWhenAsked() {
        Doc j = fixture.journals("J9")[0];
        File older = new File(WORK, "j9-older.zip");
        ScopeOperations.export(Scope.of(nid(j.pattern())), older);
        fixture.reviseJournal(j, "said since");
        assertThrows(ScopeOperations.AlreadyHereException.class, () -> ScopeOperations.importScope(older));
        assertEquals("said since", ((dev.ikm.tinkar.entity.SemanticEntityVersion) latest(j.comment())).fieldValues().get(0), "nothing was changed");

        ScopeOperations.Imported imported = ScopeOperations.importScope(older, true);
        assertTrue(imported.replaced());
        assertEquals("zebracornJ9 annotation", ((dev.ikm.tinkar.entity.SemanticEntityVersion) latest(j.comment())).fieldValues().get(0),
                "the file's copy, and not the one that was here");
    }

    /** A file whose components cannot be read leaves the copy that was here as it was. */
    @Test
    void aReplacementThatCannotBeReadPutsBackWhatWasHere() throws Exception {
        Doc j = fixture.journals("J10")[0];
        fixture.reviseJournal(j, "said since");
        int members = Scope.of(nid(j.pattern())).members().size();
        File file = new File(WORK, "j10-scope.zip");
        ScopeOperations.export(Scope.of(nid(j.pattern())), file);
        try (FileSystem zip = FileSystems.newFileSystem(URI.create("jar:" + file.toURI()), Map.of());
             java.util.stream.Stream<java.nio.file.Path> entries = Files.list(zip.getPath("records"))) {
            for (java.nio.file.Path entry : entries.filter(Files::isRegularFile).toList()) {
                Files.write(entry, new byte[] {42, 42, 42, 42, 42, 42, 42, 42});
            }
        }
        assertThrows(RuntimeException.class, () -> ScopeOperations.importScope(file, true));
        assertEquals(members, Scope.of(nid(j.pattern())).members().size());
        assertEquals("said since", ((dev.ikm.tinkar.entity.SemanticEntityVersion) latest(j.comment())).fieldValues().get(0));
    }

    /** A file whose module is of another kind here would be taken for that kind: refused. */
    @Test
    void anImportIsRefusedWhenItsModuleIsOfAnotherKindHere() throws Exception {
        Doc j = fixture.journals("J11")[0];
        Scope scope = Scope.of(nid(j.pattern()));
        File file = new File(WORK, "j11-scope.zip");
        ScopeOperations.export(scope, file);
        ScopeOperations.delete(scope);
        rewriteManifest(file, text -> text.replace("Scope-Kind: local", "Scope-Kind: shared"));
        ScopeOperations.ImportRefused refused = assertThrows(ScopeOperations.ImportRefused.class, () -> ScopeOperations.importScope(file));
        assertTrue(refused.getMessage().startsWith("It was exported as shared data, and here its module, "), refused.getMessage());
        assertTrue(refused.getMessage().endsWith("is a module of local data."), refused.getMessage());
        assertFalse(PrimitiveData.get().hasPublicId(j.root()), "nothing was loaded");
    }

    /** A file exported before its modules were recorded cannot be checked: refused. */
    @Test
    void anImportIsRefusedWithoutTheModulesItIsWrittenUnder() throws Exception {
        Doc j = fixture.journals("J12")[0];
        Scope scope = Scope.of(nid(j.pattern()));
        File file = new File(WORK, "j12-scope.zip");
        ScopeOperations.export(scope, file);
        ScopeOperations.delete(scope);
        rewriteManifest(file, text -> text.replaceAll("(?m)^Module: .*\\R", ""));
        ScopeOperations.ImportRefused refused = assertThrows(ScopeOperations.ImportRefused.class, () -> ScopeOperations.importScope(file));
        assertTrue(refused.getMessage().contains("An earlier version of Komet"), refused.getMessage());
        assertFalse(PrimitiveData.get().hasPublicId(j.root()), "nothing was loaded");
    }

    // ── what nothing lists (step 3) ───────────────────────────────────────────

    @Test
    void theInventoryListsEachGroupNothingElseListsOnce() {
        Doc[] docs = fixture.documents("K13", "L13");
        Doc journal = fixture.journals("M13")[0];
        assertTrue(DataInventory.components().containsAll(nid(docs[0].root()), nid(docs[1].comment()), nid(journal.line())));
        assertFalse(DataInventory.components().contains(fixture.term.nid()), "knowledge is not listed");

        Scope listed = Scope.of(nid(docs[0].pattern()));
        java.util.List<Scope> rest = DataInventory.unlisted(listed.members());
        assertEquals(0, rest.stream().filter(scope -> scope.contains(nid(docs[0].root()))).count());
        assertEquals(1, rest.stream().filter(scope -> scope.contains(nid(docs[1].root()))).count());
        Scope found = rest.stream().filter(scope -> scope.contains(nid(journal.comment()))).findFirst().orElseThrow();
        assertEquals(DataKind.LOCAL, found.kind());
        assertEquals(nid(journal.pattern()), found.roots().getFirst(), "by the pattern it is under");
        assertEquals(Scope.of(nid(journal.pattern())).members().size(), found.members().size(), "as one whole group");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static void rewriteManifest(File file, java.util.function.UnaryOperator<String> change) throws Exception {
        try (FileSystem zip = FileSystems.newFileSystem(URI.create("jar:" + file.toURI()), Map.of())) {
            java.nio.file.Path manifest = zip.getPath(ScopeOperations.SCOPE_MANIFEST);
            Files.writeString(manifest, change.apply(Files.readString(manifest, StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        }
    }

    private static long nid(PublicId id) {
        return PrimitiveData.nid(id);
    }

    private static EntityVersion latest(PublicId id) {
        EntityVersion latest = null;
        for (Object o : EntityHandle.get(nid(id)).expectEntity().versions()) {
            EntityVersion v = (EntityVersion) o;
            if (latest == null || v.time() > latest.time()) {
                latest = v;
            }
        }
        return latest;
    }

    private static boolean searchFinds(String text, long nid) throws Exception {
        PrimitiveDataSearchResult[] results = PrimitiveData.get().search(text, 100);
        return results != null && Arrays.stream(results).anyMatch(r -> r.nid() == nid);
    }
}
