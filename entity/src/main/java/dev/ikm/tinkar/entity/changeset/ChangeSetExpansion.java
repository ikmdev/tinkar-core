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
import dev.ikm.tinkar.schema.PublicId;
import dev.ikm.tinkar.schema.TinkarMsg;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * A format-3 change set written again in the format-2 layout: one deflated entry of records
 * that refer to components by UUID words alone, an identity index, and a manifest naming
 * version {@value ChangeSetFormat#IDENTITY_INDEX_VERSION}. The expansion is for a reader that
 * knows no sequences: an older release, or a plain implementation in another language. Nothing
 * of the content changes; every record is the same chronology with its references spelled out
 * through the component table, in the order the source holds them.
 */
public final class ChangeSetExpansion extends TrackingCallable<ChangeSetExpansion.Result> {

    public record Result(File target, long records, long components, long referencesExpanded) {
        public String text() {
            return target.getName() + ": " + String.format("%,d", records) + " record(s) in the format-"
                    + ChangeSetFormat.IDENTITY_INDEX_VERSION + " layout, " + String.format("%,d", referencesExpanded)
                    + " reference(s) expanded from sequences through a table of " + String.format("%,d", components)
                    + " component(s)";
        }
    }

    private final File source;
    private final File target;

    public ChangeSetExpansion(File source, File target) {
        super(false, true);
        this.source = source;
        this.target = target;
        updateTitle("Expand change set " + source.getName());
    }

    @Override
    protected Result compute() throws IOException {
        try (ZipFile zip = new ZipFile(source)) {
            Manifest manifest = ChangeSetFormat.manifest(zip)
                    .orElseThrow(() -> new IllegalArgumentException(source.getName() + " has no manifest, " + ChangeSetFormat.MANIFEST));
            int version = ChangeSetFormat.version(manifest.getMainAttributes(), source.getName());
            if (version < ChangeSetFormat.CURRENT_VERSION) {
                throw new IllegalArgumentException(source.getName() + " is in format version " + version
                        + ", which refers to components by UUID already; there is nothing to expand");
            }
            updateMessage("Reading the component table...");
            Table table = Table.read(zip, manifest);
            List<ChangeSetFormat.RecordEntry> entries = ChangeSetFormat.recordEntries(zip, manifest);
            long total = 0;
            for (ChangeSetFormat.RecordEntry entry : entries) {
                total += Math.max(0, entry.entry().getCompressedSize());
            }
            updateProgress(0, Math.max(1, total));
            long[] expanded = {0};
            UnaryOperator<PublicId> expand = id -> {
                if (id.hasSequence() && id.getUuidBitsCount() == 0 && id.getUuidsCount() == 0) {
                    expanded[0]++;
                    return table.publicId(id.getSequence());
                }
                return id;
            };
            long[] byKind = new long[4];
            long records = 0;
            long done = 0;
            Path parent = target.getAbsoluteFile().toPath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(target.toPath()), 1 << 20));
                 IdentityIndex.Writer identities = new IdentityIndex.Writer(false)) {
                zos.putNextEntry(new ZipEntry(ChangeSetFormat.IDENTITY_INDEX_RECORDS));
                for (ChangeSetFormat.RecordEntry entry : entries) {
                    updateMessage("Expanding " + entry.entry().getName() + "...");
                    try (InputStream in = ChangeSetFormat.openRecords(zip, entry.entry())) {
                        TinkarMsg record;
                        while ((record = TinkarMsg.parseDelimitedFrom(in)) != null) {
                            TinkarMsg rewritten = PublicIdRewriter.rewriteReferences(record, expand);
                            rewritten.writeDelimitedTo(zos);
                            identities.add(rewritten);
                            byKind[ChangeSetWriter.kindIndex(rewritten)]++;
                            records++;
                        }
                    }
                    done += Math.max(0, entry.entry().getCompressedSize());
                    updateProgress(done, Math.max(1, total));
                }
                zos.closeEntry();
                identities.writeTo(zos);
                zos.putNextEntry(new ZipEntry(ChangeSetFormat.MANIFEST));
                zos.write(ChangeSetWriter.identityIndexManifestContent(ChangeSetWriter.counts(byKind),
                        ChangeSetWriter.sections(manifest)).getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            updateProgress(1, 1);
            updateMessage("Expanded " + String.format("%,d", records) + " record(s)");
            return new Result(target, records, table.size(), expanded[0]);
        }
    }

    /**
     * The component table as UUIDs by sequence: two longs for each component's first UUID, and
     * the full UUID list of the few components that have more than one, kept aside.
     */
    static final class Table {
        private long[] msb;
        private long[] lsb;
        private final Map<Integer, UUID[]> several = new HashMap<>();
        private int size;

        private Table(int expected) {
            msb = new long[expected];
            lsb = new long[expected];
        }

        static Table read(ZipFile zip, Manifest manifest) throws IOException {
            String declared = manifest.getMainAttributes().getValue(ChangeSetFormat.COMPONENT_COUNT_ATTRIBUTE);
            int expected = declared == null ? 1 << 16 : (int) Math.min(Integer.MAX_VALUE - 8, Long.parseLong(declared.strip()) + 1);
            Table table = new Table(Math.max(16, expected));
            ComponentTable.forEach(zip, table::add);
            return table;
        }

        private void add(ComponentTable.Component component) {
            int sequence = component.sequence();
            if (sequence >= msb.length) {
                int capacity = Math.max(sequence + 1, msb.length + (msb.length >> 1));
                msb = Arrays.copyOf(msb, capacity);
                lsb = Arrays.copyOf(lsb, capacity);
            }
            UUID[] uuids = component.uuids();
            msb[sequence] = uuids[0].getMostSignificantBits();
            lsb[sequence] = uuids[0].getLeastSignificantBits();
            if (uuids.length > 1) {
                several.put(sequence, uuids);
            }
            size = Math.max(size, sequence);
        }

        int size() {
            return size;
        }

        /** The public id of the component at {@code sequence}, by UUID words. */
        PublicId publicId(int sequence) {
            if (sequence <= 0 || sequence > size) {
                throw new IllegalStateException("A reference to sequence " + sequence + ", outside the table of " + size + " component(s)");
            }
            UUID[] uuids = several.get(sequence);
            if (uuids != null) {
                return SchemaIds.toSchema(uuids);
            }
            return PublicId.newBuilder().addUuidBits(msb[sequence]).addUuidBits(lsb[sequence]).build();
        }
    }
}
