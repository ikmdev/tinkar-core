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

import dev.ikm.tinkar.schema.ConceptChronology;
import dev.ikm.tinkar.schema.ConceptVersion;
import dev.ikm.tinkar.schema.Field;
import dev.ikm.tinkar.schema.FieldDefinition;
import dev.ikm.tinkar.schema.PatternChronology;
import dev.ikm.tinkar.schema.PatternVersion;
import dev.ikm.tinkar.schema.PublicId;
import dev.ikm.tinkar.schema.SemanticChronology;
import dev.ikm.tinkar.schema.SemanticVersion;
import dev.ikm.tinkar.schema.StampChronology;
import dev.ikm.tinkar.schema.StampVersion;
import dev.ikm.tinkar.schema.TinkarMsg;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The four tools over a small change set written by hand in the format-2 layout: compaction to
 * format 3, verification and inspection of the result, expansion back, and the records' survival.
 */
class ChangeSetToolsTest {

    @TempDir
    Path dir;

    static final UUID STAMP = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID CONCEPT_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static final UUID CONCEPT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    static final UUID PATTERN_X = UUID.fromString("00000000-0000-0000-0000-000000000010");
    static final UUID SEMANTIC_S = UUID.fromString("00000000-0000-0000-0000-000000000051");
    static final UUID SEMANTIC_T = UUID.fromString("00000000-0000-0000-0000-000000000052");
    static final UUID STATUS = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-0000000000e2");
    static final UUID MODULE = UUID.fromString("00000000-0000-0000-0000-0000000000e3");
    static final UUID PATH = UUID.fromString("00000000-0000-0000-0000-0000000000e4");
    static final UUID NEVER_WRITTEN = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    static PublicId id(UUID... uuids) {
        return SchemaIds.toSchema(uuids);
    }

    static TinkarMsg stamp(UUID stamp) {
        return TinkarMsg.newBuilder().setStampChronology(StampChronology.newBuilder()
                .setPublicId(id(stamp))
                .setFirstStampVersion(StampVersion.newBuilder()
                        .setStatusPublicId(id(STATUS)).setAuthorPublicId(id(AUTHOR))
                        .setModulePublicId(id(MODULE)).setPathPublicId(id(PATH)).setTime(1_000))).build();
    }

    static TinkarMsg concept(UUID concept, int versions) {
        ConceptChronology.Builder chronology = ConceptChronology.newBuilder().setPublicId(id(concept));
        for (int i = 0; i < versions; i++) {
            chronology.addConceptVersions(ConceptVersion.newBuilder().setStampChronologyPublicId(id(STAMP)));
        }
        return TinkarMsg.newBuilder().setConceptChronology(chronology).build();
    }

    static TinkarMsg pattern(UUID pattern) {
        return TinkarMsg.newBuilder().setPatternChronology(PatternChronology.newBuilder()
                .setPublicId(id(pattern))
                .addPatternVersions(PatternVersion.newBuilder()
                        .setStampChronologyPublicId(id(STAMP))
                        .setReferencedComponentPurposePublicId(id(CONCEPT_A))
                        .setReferencedComponentMeaningPublicId(id(CONCEPT_A))
                        .addFieldDefinitions(FieldDefinition.newBuilder()
                                .setMeaningPublicId(id(CONCEPT_A)).setDataTypePublicId(id(CONCEPT_A))
                                .setPurposePublicId(id(CONCEPT_A)).setIndex(0)))).build();
    }

    static TinkarMsg semantic(UUID semantic, UUID referenced, UUID pattern, UUID fieldValue) {
        return TinkarMsg.newBuilder().setSemanticChronology(SemanticChronology.newBuilder()
                .setPublicId(id(semantic))
                .setReferencedComponentPublicId(id(referenced))
                .setPatternForSemanticPublicId(id(pattern))
                .addSemanticVersions(SemanticVersion.newBuilder()
                        .setStampChronologyPublicId(id(STAMP))
                        .addFields(Field.newBuilder().setPublicId(id(fieldValue))))).build();
    }

    /** The records as the incremental writer journals them: a stamp, then its dependents; concept A twice. */
    static List<TinkarMsg> journal() {
        return List.of(stamp(STAMP), concept(CONCEPT_A, 1), semantic(SEMANTIC_S, CONCEPT_A, PATTERN_X, NEVER_WRITTEN),
                concept(CONCEPT_B, 1), pattern(PATTERN_X), concept(CONCEPT_A, 2),
                semantic(SEMANTIC_T, CONCEPT_B, PATTERN_X, CONCEPT_B));
    }

    static Path writeFormat2(Path file, List<TinkarMsg> records, boolean nameVersion) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file));
             IdentityIndex.Writer identities = new IdentityIndex.Writer(true)) {
            out.putNextEntry(new ZipEntry(ChangeSetFormat.IDENTITY_INDEX_RECORDS));
            long[] byKind = new long[4];
            for (TinkarMsg record : records) {
                record.writeDelimitedTo(out);
                identities.add(record);
                byKind[ChangeSetWriter.kindIndex(record)]++;
            }
            out.closeEntry();
            if (nameVersion) {
                identities.writeTo(out);
            }
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            if (nameVersion) {
                manifest.getMainAttributes().putValue(ChangeSetFormat.VERSION_ATTRIBUTE, "2");
            }
            manifest.getMainAttributes().putValue("Total-Count", Long.toString(records.size()));
            manifest.getMainAttributes().putValue("Concept-Count", Long.toString(byKind[0]));
            manifest.getMainAttributes().putValue("Semantic-Count", Long.toString(byKind[1]));
            manifest.getMainAttributes().putValue("Pattern-Count", Long.toString(byKind[2]));
            manifest.getMainAttributes().putValue("Stamp-Count", Long.toString(byKind[3]));
            Attributes module = new Attributes();
            module.putValue("Description", "Test module");
            manifest.getEntries().put(MODULE.toString(), module);
            out.putNextEntry(new ZipEntry(ChangeSetFormat.MANIFEST));
            manifest.write(out);
            out.closeEntry();
        }
        return file;
    }

    static Map<UUID, TinkarMsg> recordsByComponent(Path file) throws IOException {
        Map<UUID, TinkarMsg> records = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(file.toFile())) {
            Manifest manifest = ChangeSetFormat.manifest(zip).orElseThrow();
            for (ChangeSetFormat.RecordEntry entry : ChangeSetFormat.recordEntries(zip, manifest)) {
                try (InputStream in = ChangeSetFormat.openRecords(zip, entry.entry())) {
                    TinkarMsg record;
                    while ((record = TinkarMsg.parseDelimitedFrom(in)) != null) {
                        records.put(SchemaIds.uuids(ChangeSetFormat.componentOf(record))[0], record);
                    }
                }
            }
        }
        return records;
    }

    @Test
    void compactsVerifiesInspectsAndExpandsBack() throws Exception {
        Path format2 = writeFormat2(dir.resolve("journal.zip"), journal(), true);
        Path format3 = dir.resolve("compact.zip");
        Path back = dir.resolve("expanded.zip");

        ChangeSetCompaction.Result compacted = new ChangeSetCompaction(format2.toFile(), format3.toFile()).call();
        assertEquals(6, compacted.records(), "concept A's two records became one");
        assertEquals(1, compacted.repeatsSuperseded());
        assertEquals(4, compacted.entries(), "patterns, stamps, concepts, and one semantic pattern");
        // Six carried; the three binding patterns, the stamp's four concepts and the never-written
        // field value referenced only.
        assertEquals(14, compacted.components());
        assertEquals(8, compacted.referencedOnly());
        assertEquals(0, compacted.referencesLeftAsUuids());

        ChangeSetVerification.Verification verified = new ChangeSetVerification(format3.toFile()).call();
        assertTrue(verified.ok(), verified.text());
        assertEquals(3, verified.formatVersion());
        assertEquals(6, verified.records());

        ChangeSetInspection.Report inspected = new ChangeSetInspection(format3.toFile()).call();
        assertEquals(3, inspected.formatVersion());
        assertEquals(6, inspected.records());
        assertEquals(6, inspected.carried());
        assertEquals(8, inspected.referencedOnly());
        assertEquals(4, inspected.patterns(), "the pattern pattern, the stamp and concept patterns, and X");
        assertEquals(21, inspected.referencesBySequence());
        assertEquals(0, inspected.referencesByUuid());
        assertEquals(List.of("records/0001-patterns.pb.gz", "records/0002-stamps.pb.gz", "records/0003-concepts.pb.gz",
                        "records/0004-" + PATTERN_X + ".pb.gz"),
                inspected.entries().stream().map(ChangeSetInspection.Entry::name).toList());
        assertTrue(inspected.text().contains("semantic " + PATTERN_X + ": 2 record(s)"), inspected.text());

        Map<UUID, TinkarMsg> compactRecords = recordsByComponent(format3);
        assertEquals(2, compactRecords.get(CONCEPT_A).getConceptChronology().getConceptVersionsCount(), "the last record carries A");
        PublicId stampReference = compactRecords.get(CONCEPT_B).getConceptChronology().getConceptVersions(0).getStampChronologyPublicId();
        assertTrue(stampReference.hasSequence());
        assertEquals(0, stampReference.getUuidBitsCount());
        try (ZipFile zip = new ZipFile(format3.toFile())) {
            Manifest manifest = ChangeSetFormat.manifest(zip).orElseThrow();
            assertEquals("Test module", manifest.getAttributes(MODULE.toString()).getValue("Description"));
            assertEquals("14", manifest.getMainAttributes().getValue(ChangeSetFormat.COMPONENT_COUNT_ATTRIBUTE));
            List<ComponentTable.Component> table = new ArrayList<>();
            ComponentTable.forEach(zip, table::add);
            assertTrue(table.get(0).isPatternPattern(), "the pattern pattern names itself at sequence 1");
            assertTrue(table.get(0).referencedOnly());
            assertEquals(PATTERN_X, table.get(3).uuids()[0]);
            assertEquals(1, table.get(3).patternSequence());
            assertEquals(CONCEPT_B, table.get(5).uuids()[0], "concepts listed where their last record is: B before A");
            assertEquals(CONCEPT_A, table.get(6).uuids()[0]);
            assertEquals(NEVER_WRITTEN, table.get(13).uuids()[0]);
            assertEquals(0, table.get(13).patternSequence(), "a field value's pattern is unknown");
            assertEquals(STATUS, table.get(9).uuids()[0]);
        }

        ChangeSetExpansion.Result expanded = new ChangeSetExpansion(format3.toFile(), back.toFile()).call();
        assertEquals(6, expanded.records());
        assertEquals(14, expanded.components());
        assertEquals(21, expanded.referencesExpanded());

        ChangeSetVerification.Verification backVerified = new ChangeSetVerification(back.toFile()).call();
        assertTrue(backVerified.ok(), backVerified.text());
        assertEquals(2, backVerified.formatVersion());
        assertTrue(ChangeSetFormat.hasIdentityIndex(back.toFile()));
        ChangeSetInspection.Report backInspected = new ChangeSetInspection(back.toFile()).call();
        assertEquals(0, backInspected.referencesBySequence());
        assertEquals(21, backInspected.referencesByUuid());

        Map<UUID, TinkarMsg> original = new LinkedHashMap<>();
        for (TinkarMsg record : journal()) {
            original.put(SchemaIds.uuids(ChangeSetFormat.componentOf(record))[0], record);
        }
        Map<UUID, TinkarMsg> restored = recordsByComponent(back);
        assertEquals(original.keySet(), restored.keySet());
        original.forEach((component, record) -> assertEquals(record, restored.get(component), "record of " + component));
        try (ZipFile zip = new ZipFile(back.toFile())) {
            Manifest manifest = ChangeSetFormat.manifest(zip).orElseThrow();
            assertEquals("Test module", manifest.getAttributes(MODULE.toString()).getValue("Description"));
        }
    }

    @Test
    void aFormat1RecordNamedByTextIsCarriedByWords() throws Exception {
        TinkarMsg textNamed = TinkarMsg.newBuilder().setConceptChronology(ConceptChronology.newBuilder()
                .setPublicId(PublicId.newBuilder().addUuids(CONCEPT_A.toString()))
                .addConceptVersions(ConceptVersion.newBuilder()
                        .setStampChronologyPublicId(PublicId.newBuilder().addUuids(STAMP.toString())))).build();
        Path format1 = writeFormat2(dir.resolve("text.zip"), List.of(stamp(STAMP), textNamed), false);
        Path format3 = dir.resolve("text-compact.zip");
        ChangeSetCompaction.Result compacted = new ChangeSetCompaction(format1.toFile(), format3.toFile()).call();
        assertEquals(2, compacted.records());
        ChangeSetVerification.Verification verified = new ChangeSetVerification(format3.toFile()).call();
        assertTrue(verified.ok(), verified.text());
        TinkarMsg carried = recordsByComponent(format3).get(CONCEPT_A);
        assertNotNull(carried);
        PublicId own = carried.getConceptChronology().getPublicId();
        assertEquals(2, own.getUuidBitsCount());
        assertEquals(0, own.getUuidsCount());
        assertTrue(carried.getConceptChronology().getConceptVersions(0).getStampChronologyPublicId().hasSequence());
    }

    @Test
    void theToolsRefuseTheWrongDirection() throws Exception {
        Path format2 = writeFormat2(dir.resolve("journal.zip"), journal(), true);
        Path format3 = dir.resolve("compact.zip");
        new ChangeSetCompaction(format2.toFile(), format3.toFile()).call();
        assertThrows(IllegalArgumentException.class, () -> new ChangeSetCompaction(format3.toFile(), dir.resolve("again.zip").toFile()).call());
        assertThrows(IllegalArgumentException.class, () -> new ChangeSetExpansion(format2.toFile(), dir.resolve("again.zip").toFile()).call());
        assertFalse(Files.exists(dir.resolve("again.zip")));
    }
}
