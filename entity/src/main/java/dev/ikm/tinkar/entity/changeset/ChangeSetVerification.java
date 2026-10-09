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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;

/**
 * Whether a change set conforms to its format: the conformance test a writer runs on its own
 * output, and what the {@code verify} goal and menu item report. For format 3: the manifest
 * names the version; the component table is there and the manifest's count matches it; the
 * pattern pattern names itself and patterns precede their members; every record entry the
 * manifest lists exists, holds the records it says, and hashes as it says; every record names
 * itself by UUID words and refers to components by sequences within the table or by UUID
 * words; every carried record's component is listed, as carried, in file order. For formats
 * 1 and 2: the counts match the manifest, and a format-2 identity index lists every record.
 */
public final class ChangeSetVerification extends TrackingCallable<ChangeSetVerification.Verification> {

    public record Verification(String file, int formatVersion, long records, List<String> errors, List<String> warnings) {
        public boolean ok() {
            return errors.isEmpty();
        }

        public String text() {
            StringBuilder text = new StringBuilder(file).append(": format version ").append(formatVersion)
                    .append(", ").append(String.format("%,d", records)).append(" record(s), ")
                    .append(ok() ? "conforms" : errors.size() + " error(s)").append("\n");
            errors.forEach(error -> text.append("  error: ").append(error).append("\n"));
            warnings.forEach(warning -> text.append("  warning: ").append(warning).append("\n"));
            return text.toString();
        }
    }

    private static final int REPORTED_PER_KIND = 5;
    private final File changeSet;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public ChangeSetVerification(File changeSet) {
        super(false, true);
        this.changeSet = changeSet;
        updateTitle("Verify change set " + changeSet.getName());
    }

    private void error(String kind, int[] count, String detail) {
        if (count[0]++ < REPORTED_PER_KIND) {
            errors.add(kind + ": " + detail);
        } else if (count[0] == REPORTED_PER_KIND + 1) {
            errors.add(kind + ": and more");
        }
    }

    @Override
    protected Verification compute() throws IOException {
        try (ZipFile zip = new ZipFile(changeSet)) {
            Manifest manifest = ChangeSetFormat.manifest(zip).orElse(null);
            if (manifest == null) {
                errors.add("no manifest, " + ChangeSetFormat.MANIFEST);
                return new Verification(changeSet.getName(), 0, 0, errors, warnings);
            }
            Attributes main = manifest.getMainAttributes();
            int version;
            try {
                version = ChangeSetFormat.version(main, changeSet.getName());
            } catch (ChangeSetFormat.UnsupportedFormatException e) {
                errors.add(e.getMessage());
                return new Verification(changeSet.getName(), 0, 0, errors, warnings);
            }
            long manifestCount = main.getValue("Total-Count") == null ? -1 : Long.parseLong(main.getValue("Total-Count").strip());
            if (manifestCount < 0) {
                errors.add("the manifest names no Total-Count");
            }
            long records = version >= 3 ? verifyFormat3(zip, manifest) : verifyRecords(zip, manifest, version);
            if (manifestCount >= 0 && records != manifestCount) {
                errors.add("the manifest says " + manifestCount + " records; the entries hold " + records);
            }
            updateProgress(1, 1);
            updateMessage(errors.isEmpty() ? "Conforms" : errors.size() + " error(s)");
            return new Verification(changeSet.getName(), version, records, errors, warnings);
        }
    }

    /** Formats 1 and 2: the records parse, and a format-2 identity index lists each of them. */
    private long verifyRecords(ZipFile zip, Manifest manifest, int version) throws IOException {
        long records = 0;
        for (ChangeSetFormat.RecordEntry entry : ChangeSetFormat.recordEntries(zip, manifest)) {
            try (InputStream in = ChangeSetFormat.openRecords(zip, entry.entry())) {
                while (TinkarMsg.parseDelimitedFrom(in) != null) {
                    records++;
                }
            }
        }
        if (version == 2 && !ChangeSetFormat.hasIdentityIndex(changeSet)) {
            errors.add("format version 2 without an identity index, " + ChangeSetFormat.IDENTITY_INDEX);
        }
        return records;
    }

    private long verifyFormat3(ZipFile zip, Manifest manifest) throws IOException {
        Attributes main = manifest.getMainAttributes();
        if (!ChangeSetFormat.hasComponentTable(zip)) {
            errors.add("no component table, " + ChangeSetFormat.COMPONENT_TABLE);
            return 0;
        }
        if (ChangeSetFormat.hasIdentityIndex(changeSet)) {
            warnings.add("an identity index beside the component table, " + ChangeSetFormat.IDENTITY_INDEX + ": readers ignore it");
        }
        updateMessage("Reading the component table...");
        // The table: patterns precede members, the pattern pattern names itself once, and
        // every UUID of a carried component maps to its sequence.
        List<Boolean> referencedOnly = new ArrayList<>();
        referencedOnly.add(null);
        int[] patternPattern = {-1};
        int[] tableErrors = {0};
        int[] patternOrder = {0};
        UuidSequenceMap carried = new UuidSequenceMap(1 << 16);
        long[] count = {0};
        ComponentTable.forEach(zip, component -> {
            count[0]++;
            referencedOnly.add(component.referencedOnly());
            if (component.isPatternPattern()) {
                if (patternPattern[0] != -1) {
                    error("table", tableErrors, "a second pattern pattern at sequence " + component.sequence());
                }
                patternPattern[0] = component.sequence();
            } else if (component.patternSequence() > component.sequence()) {
                error("table", patternOrder, "component " + component.sequence() + " names pattern sequence "
                        + component.patternSequence() + ", which comes after it: patterns precede their members");
            }
            if (!component.referencedOnly()) {
                for (UUID uuid : component.uuids()) {
                    carried.put(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits(), component.sequence());
                }
            }
        });
        if (patternPattern[0] == -1) {
            errors.add("table: no component names itself as the pattern pattern");
        }
        String componentCount = main.getValue(ChangeSetFormat.COMPONENT_COUNT_ATTRIBUTE);
        if (componentCount == null) {
            warnings.add("the manifest names no " + ChangeSetFormat.COMPONENT_COUNT_ATTRIBUTE);
        } else if (Long.parseLong(componentCount.strip()) != count[0]) {
            errors.add("the manifest says " + componentCount.strip() + " components; the table lists " + count[0]);
        }
        if (main.getValue(ChangeSetFormat.RECORD_ENTRIES_ATTRIBUTE) == null) {
            errors.add("the manifest names no " + ChangeSetFormat.RECORD_ENTRIES_ATTRIBUTE);
            return 0;
        }
        List<ChangeSetFormat.RecordEntry> entries;
        try {
            entries = ChangeSetFormat.recordEntries(zip, manifest);
        } catch (IllegalStateException e) {
            errors.add(e.getMessage());
            return 0;
        }
        long total = 0;
        for (ChangeSetFormat.RecordEntry entry : entries) {
            total += Math.max(0, entry.entry().getCompressedSize());
        }
        updateProgress(0, Math.max(1, total));
        long records = 0;
        long done = 0;
        int previousSequence = 0;
        int[] ownIdErrors = {0};
        int[] referenceErrors = {0};
        int[] listingErrors = {0};
        int[] orderErrors = {0};
        for (ChangeSetFormat.RecordEntry entry : entries) {
            updateMessage("Verifying " + entry.entry().getName() + "...");
            MessageDigest sha256;
            try {
                sha256 = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
            long inEntry = 0;
            try (InputStream in = new BufferedInputStream(new DigestInputStream(ChangeSetFormat.openRecords(zip, entry.entry()), sha256), 1 << 20)) {
                TinkarMsg record;
                while ((record = TinkarMsg.parseDelimitedFrom(in)) != null) {
                    inEntry++;
                    records++;
                    PublicId own = ChangeSetFormat.componentOf(record);
                    if (own.getUuidBitsCount() == 0 && own.getUuidsCount() == 0) {
                        error("record", ownIdErrors, "record " + records + " of " + entry.entry().getName() + " names itself by sequence only");
                        continue;
                    }
                    UUID first = SchemaIds.uuids(own)[0];
                    int sequence = carried.get(first.getMostSignificantBits(), first.getLeastSignificantBits());
                    if (sequence == 0) {
                        error("listing", listingErrors, first + " has a record but is not listed as carried");
                    } else if (sequence <= previousSequence) {
                        error("order", orderErrors, first + " at sequence " + sequence + " after " + previousSequence + ": not in table order");
                    } else {
                        previousSequence = sequence;
                    }
                    PublicIdRewriter.forEachPublicId(record, id -> {
                        if (id == own) {
                            return;
                        }
                        if (id.hasSequence() && id.getUuidBitsCount() == 0 && id.getUuidsCount() == 0) {
                            if (id.getSequence() <= 0 || id.getSequence() > count[0]) {
                                error("reference", referenceErrors, first + " refers to sequence " + id.getSequence()
                                        + ", outside the table of " + count[0]);
                            }
                        } else if (id.getUuidBitsCount() == 0 && id.getUuidsCount() == 0) {
                            error("reference", referenceErrors, first + " carries an empty reference");
                        }
                    });
                }
            }
            String digest = HexFormat.of().formatHex(sha256.digest());
            if (entry.count() >= 0 && entry.count() != inEntry) {
                errors.add(entry.entry().getName() + ": the manifest says " + entry.count() + " records; it holds " + inEntry);
            }
            if (entry.sha256() != null && !entry.sha256().equalsIgnoreCase(digest)) {
                errors.add(entry.entry().getName() + ": the manifest says SHA-256 " + entry.sha256() + "; the records hash to " + digest);
            }
            done += Math.max(0, entry.entry().getCompressedSize());
            updateProgress(done, Math.max(1, total));
        }
        return records;
    }
}
