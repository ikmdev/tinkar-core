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

import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.common.util.io.CountingInputStream;
import dev.ikm.tinkar.schema.PublicId;
import dev.ikm.tinkar.schema.TinkarMsg;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipFile;

/**
 * What a change set is made of, without a store: its format version, its entries and their
 * sizes, its records by kind and pattern with their run structure, its component table, and
 * how its references are written. The report is what an engineer reads before an import, and
 * what the {@code inspect} goal and menu item print.
 */
public final class ChangeSetInspection extends TrackingCallable<ChangeSetInspection.Report> {

    /** The records of one kind, or of one semantic pattern: how many, and in how many runs. */
    public record Kind(String key, long records, long runs, long longestRun) {
    }

    public record Entry(String name, long compressedBytes, long records) {
    }

    public record Report(String file, int formatVersion, long manifestCount, List<Entry> entries, List<Kind> kinds,
                         long records, long runs, long carried, long referencedOnly, long patterns,
                         long referencesBySequence, long referencesByUuid) {
        /** The report as text, one line per fact. */
        public String text() {
            StringBuilder text = new StringBuilder()
                    .append(file).append(": format version ").append(formatVersion)
                    .append(", ").append(String.format("%,d", manifestCount)).append(" records by the manifest, ")
                    .append(String.format("%,d", records)).append(" read in ").append(String.format("%,d", runs)).append(" run(s)\n");
            if (formatVersion >= 3) {
                text.append("  component table: ").append(String.format("%,d", carried)).append(" carried, ")
                        .append(String.format("%,d", referencedOnly)).append(" referenced only, ")
                        .append(patterns).append(" pattern(s)\n");
            }
            text.append("  references: ").append(String.format("%,d", referencesBySequence)).append(" by sequence, ")
                    .append(String.format("%,d", referencesByUuid)).append(" by UUID\n");
            for (Entry entry : entries) {
                text.append("  entry ").append(entry.name()).append(": ").append(String.format("%,d", entry.records()))
                        .append(" record(s), ").append(String.format("%,d", entry.compressedBytes())).append(" bytes compressed\n");
            }
            for (Kind kind : kinds) {
                text.append("  ").append(kind.key()).append(": ").append(String.format("%,d", kind.records()))
                        .append(" record(s) in ").append(String.format("%,d", kind.runs())).append(" run(s), longest ")
                        .append(String.format("%,d", kind.longestRun())).append("\n");
            }
            return text.toString();
        }
    }

    private final File changeSet;

    public ChangeSetInspection(File changeSet) {
        super(false, true);
        this.changeSet = changeSet;
        updateTitle("Inspect change set " + changeSet.getName());
    }

    @Override
    protected Report compute() throws IOException {
        try (ZipFile zip = new ZipFile(changeSet)) {
            Manifest manifest = ChangeSetFormat.manifest(zip).orElseGet(Manifest::new);
            Attributes main = manifest.getMainAttributes();
            int version = main.getValue(ChangeSetFormat.VERSION_ATTRIBUTE) == null ? 1
                    : Integer.parseInt(main.getValue(ChangeSetFormat.VERSION_ATTRIBUTE).strip());
            long manifestCount = main.getValue("Total-Count") == null ? -1 : Long.parseLong(main.getValue("Total-Count").strip());

            // The table, for naming a pattern a semantic refers to by sequence.
            List<UUID[]> uuidsBySequence = new ArrayList<>();
            uuidsBySequence.add(null);
            long[] carried = {0};
            long[] referencedOnly = {0};
            long[] patterns = {0};
            if (ChangeSetFormat.hasComponentTable(zip)) {
                updateMessage("Reading the component table...");
                int[] patternPattern = {-1};
                ComponentTable.forEach(zip, component -> {
                    uuidsBySequence.add(component.uuids());
                    if (component.referencedOnly()) {
                        referencedOnly[0]++;
                    } else {
                        carried[0]++;
                    }
                    if (component.isPatternPattern()) {
                        patternPattern[0] = component.sequence();
                    }
                    if (component.patternSequence() == patternPattern[0]) {
                        patterns[0]++;
                    }
                });
            }

            List<ChangeSetFormat.RecordEntry> recordEntries = ChangeSetFormat.recordEntries(zip, manifest);
            long total = 0;
            for (ChangeSetFormat.RecordEntry entry : recordEntries) {
                total += Math.max(0, entry.entry().getCompressedSize());
            }
            updateProgress(0, Math.max(1, total));
            Map<String, long[]> kinds = new LinkedHashMap<>(); // key -> {records, runs, longest, current}
            List<Entry> entries = new ArrayList<>();
            long records = 0;
            long runs = 0;
            long bySequence = 0;
            long byUuid = 0;
            long done = 0;
            String previous = null;
            for (ChangeSetFormat.RecordEntry entry : recordEntries) {
                updateMessage("Reading " + entry.entry().getName() + "...");
                long inEntry = 0;
                CountingInputStream counting = new CountingInputStream(new BufferedInputStream(zip.getInputStream(entry.entry()), 1 << 20));
                try (InputStream in = entry.entry().getName().endsWith(ChangeSetFormat.GZIP_SUFFIX)
                        ? new BufferedInputStream(new GZIPInputStream(counting, 1 << 16), 1 << 20)
                        : new BufferedInputStream(counting, 1 << 20)) {
                    TinkarMsg record;
                    while ((record = TinkarMsg.parseDelimitedFrom(in)) != null) {
                        inEntry++;
                        records++;
                        String key = key(record, uuidsBySequence);
                        long[] stats = kinds.computeIfAbsent(key, k -> new long[4]);
                        stats[0]++;
                        if (!key.equals(previous)) {
                            runs++;
                            stats[1]++;
                            stats[3] = 0;
                            previous = key;
                        }
                        stats[3]++;
                        stats[2] = Math.max(stats[2], stats[3]);
                        PublicId own = ChangeSetFormat.componentOf(record);
                        long[] forms = {0, 0};
                        PublicIdRewriter.forEachPublicId(record, id -> {
                            if (id == own) {
                                return;
                            }
                            if (id.hasSequence() && id.getUuidBitsCount() == 0 && id.getUuidsCount() == 0) {
                                forms[0]++;
                            } else {
                                forms[1]++;
                            }
                        });
                        bySequence += forms[0];
                        byUuid += forms[1];
                        if (records % 10_000 == 0) {
                            updateProgress(done + counting.getBytesRead(), Math.max(1, total));
                        }
                    }
                }
                done += Math.max(0, entry.entry().getCompressedSize());
                entries.add(new Entry(entry.entry().getName(), entry.entry().getCompressedSize(), inEntry));
            }
            List<Kind> kindList = new ArrayList<>();
            kinds.forEach((key, stats) -> kindList.add(new Kind(key, stats[0], stats[1], stats[2])));
            updateProgress(1, 1);
            updateMessage("Inspected " + String.format("%,d", records) + " record(s)");
            return new Report(changeSet.getName(), version, manifestCount, entries, kindList, records, runs,
                    carried[0], referencedOnly[0], patterns[0], bySequence, byUuid);
        }
    }

    /** The kind of a record, and for a semantic its pattern: "semantic <uuid>" named through the table when given by sequence. */
    static String key(TinkarMsg record, List<UUID[]> uuidsBySequence) {
        return switch (record.getValueCase()) {
            case CONCEPT_CHRONOLOGY -> "concept";
            case STAMP_CHRONOLOGY -> "stamp";
            case PATTERN_CHRONOLOGY -> "pattern";
            case SEMANTIC_CHRONOLOGY -> {
                PublicId pattern = record.getSemanticChronology().getPatternForSemanticPublicId();
                if (pattern.hasSequence() && pattern.getUuidBitsCount() == 0 && pattern.getUuidsCount() == 0) {
                    int sequence = pattern.getSequence();
                    UUID[] uuids = sequence > 0 && sequence < uuidsBySequence.size() ? uuidsBySequence.get(sequence) : null;
                    yield "semantic " + (uuids == null ? "#" + sequence : uuids[0]);
                }
                yield "semantic " + SchemaIds.uuids(pattern)[0];
            }
            case VALUE_NOT_SET -> "unset";
        };
    }
}
