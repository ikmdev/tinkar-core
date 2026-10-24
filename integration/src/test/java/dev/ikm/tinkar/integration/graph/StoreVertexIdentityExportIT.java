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
package dev.ikm.tinkar.integration.graph;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.DataActivity;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.PublicIdentifierRecord;
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.SemanticRecordBuilder;
import dev.ikm.tinkar.entity.SemanticVersionRecord;
import dev.ikm.tinkar.entity.SemanticVersionRecordBuilder;
import dev.ikm.tinkar.entity.changeset.ChangeSetFormat;
import dev.ikm.tinkar.entity.export.ExportEntitiesToProtobufFile;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.VertexDerivation;
import dev.ikm.tinkar.entity.graph.VertexIdentityWrite;
import dev.ikm.tinkar.entity.transaction.StampedWriter;
import dev.ikm.tinkar.fixtures.ForkedJvm;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.fixtures.TestTags;
import dev.ikm.tinkar.integration.graph.VertexIdentityChains.Version;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import dev.ikm.tinkar.terms.State;
import network.ike.foundation.ike.bindings.IkeConcept;
import org.eclipse.collections.api.factory.Lists;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static dev.ikm.tinkar.integration.graph.DiTreeShuffle.uuidAt;
import static dev.ikm.tinkar.integration.graph.DiTreeShuffle.withUuids;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An export declares its vertex identities derived only when its store's are
 * (IKE-Network/ike-issues#1310). A store records at its creation that the build creating it
 * derives identities; one created before derivation holds trees whose vertex UUIDs were never
 * derived, and its export must leave the loader to correlate them, or the loader keeps them and
 * refuses the history as a broken invariant.
 *
 * <p>A store created before derivation is made here as one is found: a store whose format file
 * lacks the marker, holding a definition whose later version gives an earlier version's UUID to
 * another vertex, as trees written before derivation did. Each store lifetime is a
 * {@link ForkedJvm} stage.
 */
@Tag(TestTags.STAGED)
class StoreVertexIdentityExportIT {
    private static final File WORK = TestConstants.createFilePathInTargetFromClassName.apply(StoreVertexIdentityExportIT.class);

    private static final String STORE = "store";
    private static final String UNDERIVED = "underived";
    private static final String EXPORT_FILE = "export.file";
    private static final String CONCEPT = "concept";
    private static final String SEMANTIC = "semantic";
    private static final String DERIVED_AT_CREATION = "derived.at.creation";
    private static final String DERIVED_REOPENED = "derived.reopened";
    private static final String REFUSAL = "refusal";
    private static final String VERSIONS = "versions";
    private static final String ROOTS_DERIVED = "roots.derived";

    /** When the definitions are written: the export takes this span only. */
    private static final long TIME = 1_960_000_000_000L;

    @BeforeAll
    static void beforeAll() {
        FileUtil.recursiveDelete(WORK);
        assertTrue(WORK.mkdirs(), "Could not create " + WORK);
    }

    @Test
    void aDerivedStoresExportDeclaresIt() throws IOException {
        Properties written = writeAndExport("derived", false);
        assertEquals("true", written.getProperty(DERIVED_AT_CREATION), "a store this build creates is derived");
        assertEquals("true", written.getProperty(DERIVED_REOPENED), "and says so when reopened");
        File export = new File(written.getProperty(EXPORT_FILE));
        assertEquals(String.valueOf(ChangeSetFormat.VERTEX_IDENTITY_VERSION),
                manifest(export).getMainAttributes().getValue(ChangeSetFormat.VERTEX_IDENTITY_ATTRIBUTE),
                "the export declares its identities derived");

        Properties loaded = ForkedJvm.run(LoadIntoFreshStore.class, written);
        assertNull(loaded.getProperty(REFUSAL), () -> "the declared export loads: " + loaded.getProperty(REFUSAL));
        assertEquals("2", loaded.getProperty(VERSIONS));
        assertEquals("true", loaded.getProperty(ROOTS_DERIVED));
    }

    @Test
    void anUnderivedStoresExportLoadsIntoAFresh64BitStore() throws IOException {
        Properties written = writeAndExport("underived", true);
        assertEquals("false", written.getProperty(DERIVED_REOPENED), "a store without the marker is underived");
        File export = new File(written.getProperty(EXPORT_FILE));
        assertNull(manifest(export).getMainAttributes().getValue(ChangeSetFormat.VERTEX_IDENTITY_ATTRIBUTE),
                "the export does not declare its identities derived");

        Properties loaded = ForkedJvm.run(LoadIntoFreshStore.class, written);
        assertNull(loaded.getProperty(REFUSAL), () -> "the export loads: " + loaded.getProperty(REFUSAL));
        assertEquals("2", loaded.getProperty(VERSIONS), "both versions of the definition load");
        assertEquals("true", loaded.getProperty(ROOTS_DERIVED), "and are correlated: the roots' UUIDs are derived");

        // What the exporter wrote before: the same file declaring its identities derived is refused.
        // The loader logs the record's break ("Vertex identity broken ... changed meaning") and
        // fails the import on the record it could not write.
        File declared = new File(WORK, "underived-declared-pb.zip");
        declareDerived(export, declared);
        Properties forged = new Properties();
        forged.putAll(written);
        forged.setProperty(EXPORT_FILE, declared.getPath());
        Properties refused = ForkedJvm.run(LoadIntoFreshStore.class, forged);
        String refusal = refused.getProperty(REFUSAL);
        assertTrue(refusal != null && refusal.contains("Import Failed"),
                () -> "the declared file is held to the invariants its history breaks: " + refusal);
    }

    /** Creates a store, makes it underived when asked, writes a definition's history and exports it. */
    private static Properties writeAndExport(String name, boolean underived) throws IOException {
        Properties in = new Properties();
        File store = new File(WORK, name + "-store");
        in.setProperty(STORE, store.getPath());
        in.setProperty(EXPORT_FILE, new File(WORK, name + "-pb.zip").getPath());
        in.setProperty(UNDERIVED, Boolean.toString(underived));
        Properties created = ForkedJvm.run(CreateStore.class, in);
        if (underived) {
            stripMarker(store);
        }
        return ForkedJvm.run(WriteAndExport.class, created);
    }

    /** Removes the marker from a store's format file, as a store created before derivation lacks it. */
    private static void stripMarker(File store) throws IOException {
        File format = new File(store, "store-format.properties");
        List<String> lines = Files.readAllLines(format.toPath(), StandardCharsets.ISO_8859_1);
        List<String> kept = lines.stream().filter(line -> !line.startsWith("vertexIdentity=")).toList();
        assertEquals(lines.size() - 1, kept.size(), "the format file held the marker");
        Files.write(format.toPath(), kept, StandardCharsets.ISO_8859_1);
    }

    private static Manifest manifest(File changeSet) throws IOException {
        try (ZipFile zip = new ZipFile(changeSet)) {
            return ChangeSetFormat.manifest(zip).orElseThrow();
        }
    }

    /** Copies a change set, its manifest declaring the identities derived. */
    private static void declareDerived(File from, File to) throws IOException {
        try (ZipFile zip = new ZipFile(from); OutputStream file = Files.newOutputStream(to.toPath());
             ZipOutputStream out = new ZipOutputStream(file)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                out.putNextEntry(new ZipEntry(entry.getName()));
                try (InputStream in = zip.getInputStream(entry)) {
                    if (entry.getName().equals(ChangeSetFormat.MANIFEST)) {
                        String manifest = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        int firstLine = manifest.indexOf('\n') + 1;
                        out.write((manifest.substring(0, firstLine) + ChangeSetFormat.vertexIdentityLine(true)
                                + manifest.substring(firstLine)).getBytes(StandardCharsets.UTF_8));
                    } else {
                        in.transferTo(out);
                    }
                }
                out.closeEntry();
            }
        }
    }

    private static StampedWriter open(String name, long time) {
        return StampedWriter.open(name, State.ACTIVE, time,
                KernelConcept.USER, IkeConcept.DEVELOPMENT_MODULE, KernelConcept.DEVELOPMENT_PATH);
    }

    /** Creates a store holding the starter set, and says whether it was created derived. */
    static class CreateStore implements ForkedJvm.Stage {
        @Override
        public void run(Properties in, Properties out) {
            out.putAll(in);
            TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, new File(in.getProperty(STORE)));
            try {
                out.setProperty(DERIVED_AT_CREATION, Boolean.toString(PrimitiveData.vertexIdentityDerived()));
                TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
            } finally {
                TestHelper.stopDatabase();
            }
        }
    }

    /**
     * Reopens the store, writes a definition with two versions, and exports what was written. In
     * a derived store the versions go through the write path; in an underived one the later
     * version gives the root's UUID to another vertex, kept as it arrived, as a tree written
     * before derivation could.
     */
    static class WriteAndExport implements ForkedJvm.Stage {
        @Override
        public void run(Properties in, Properties out) {
            out.putAll(in);
            TestHelper.reopenDataBase(DataStore.SPINED_ARRAY_STORE, new File(in.getProperty(STORE)));
            try {
                out.setProperty(DERIVED_REOPENED, Boolean.toString(PrimitiveData.vertexIdentityDerived()));
                List<Version> chain = VertexIdentityChains.chain(new Random(20261024L), 1);
                DiTreeEntity first = VertexIdentityChains.build(chain.get(0).root());
                DiTreeEntity second = VertexIdentityChains.build(chain.get(1).root());
                PublicId concept = PublicIds.newRandom();
                PublicId semantic = PublicIds.newRandom();
                out.setProperty(CONCEPT, concept.asUuidArray()[0].toString());
                out.setProperty(SEMANTIC, semantic.asUuidArray()[0].toString());
                PublicId pattern = KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.publicId();

                StampedWriter writer = open("concept", TIME);
                writer.concept(concept);
                writer.commit();
                if (Boolean.parseBoolean(in.getProperty(UNDERIVED))) {
                    long nid = EntityService.get().nidForSemantic(pattern, semantic);
                    StampedWriter one = open("underived, first", TIME + 1);
                    putKept(nid, semantic, concept, one.stamp().nid(), first);
                    one.commit();
                    DiTreeEntity reused = withUuids(second, j -> j == second.root().vertexIndex()
                            ? uuidAt(first, first.successors(first.root().vertexIndex()).get(0)) : uuidAt(second, j));
                    System.setProperty(VertexIdentityWrite.CARRIED_BREAKS, "warn");
                    StampedWriter two = open("underived, second", TIME + 2);
                    putKept(nid, semantic, concept, two.stamp().nid(), reused);
                    two.commit();
                    System.clearProperty(VertexIdentityWrite.CARRIED_BREAKS);
                } else {
                    StampedWriter one = open("derived, first", TIME + 1);
                    one.semantic(semantic, pattern, concept, first);
                    one.commit();
                    StampedWriter two = open("derived, second", TIME + 2);
                    two.semantic(semantic, pattern, concept, second);
                    two.commit();
                }
                new ExportEntitiesToProtobufFile(new File(in.getProperty(EXPORT_FILE)), TIME, TIME + 2).compute();
            } finally {
                TestHelper.stopDatabase();
            }
        }

        /** Puts one version, its UUIDs kept as they arrive. */
        private static void putKept(long nid, PublicId semantic, PublicId concept, long stampNid, DiTreeEntity tree) {
            PublicIdentifierRecord identifier = PublicIdentifierRecord.make(semantic);
            RecordListBuilder<SemanticVersionRecord> versions = RecordListBuilder.make();
            SemanticRecord record = SemanticRecordBuilder.builder()
                    .nid(nid)
                    .mostSignificantBits(identifier.mostSignificantBits())
                    .leastSignificantBits(identifier.leastSignificantBits())
                    .additionalUuidLongs(identifier.additionalUuidLongs())
                    .patternNid(EntityService.get().nidForPattern(KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.publicId()))
                    .referencedComponentNid(EntityService.get().nidForPublicId(concept))
                    .versions(versions)
                    .build();
            versions.add(SemanticVersionRecordBuilder.builder().chronology(record).stampNid(stampNid)
                    .fieldValues(Lists.immutable.of(tree)).build());
            EntityService.get().putEntity(SemanticRecordBuilder.builder(record).versions(versions.toImmutable()).build(),
                    DataActivity.LOADING_CHANGE_SET);
        }
    }

    /**
     * Loads the starter set and the export into a fresh memory-only 64-bit store, and reports the
     * refusal, if the load was refused, or the definition's versions and whether their roots
     * carry derived UUIDs.
     */
    static class LoadIntoFreshStore implements ForkedJvm.Stage {
        @Override
        public void run(Properties in, Properties out) {
            TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
            try {
                TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
                try {
                    TestHelper.loadDataFile(new File(in.getProperty(EXPORT_FILE)));
                } catch (RuntimeException refused) {
                    out.setProperty(REFUSAL, messages(refused));
                    return;
                }
                PublicId semantic = PublicIds.of(in.getProperty(SEMANTIC));
                long nid = EntityService.get().nidForSemantic(KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.publicId(), semantic);
                SemanticEntity<SemanticEntityVersion> loaded = EntityHandle.get(nid).expectSemantic();
                out.setProperty(VERSIONS, Integer.toString(loaded.versions().size()));
                boolean derived = true;
                for (SemanticEntityVersion version : loaded.versions()) {
                    DiTreeEntity tree = (DiTreeEntity) version.fieldValues().get(0);
                    derived &= VertexDerivation.root(semantic, 0).equals(uuidAt(tree, tree.root().vertexIndex()));
                }
                out.setProperty(ROOTS_DERIVED, Boolean.toString(derived));
            } finally {
                TestHelper.stopDatabase();
            }
        }

        private static String messages(Throwable thrown) {
            return java.util.stream.Stream.iterate(thrown, t -> t != null, Throwable::getCause)
                    .map(String::valueOf).collect(Collectors.joining(" <- "));
        }
    }
}
