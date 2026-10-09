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

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.schema.TinkarMsg;
import dev.ikm.tinkar.terms.EntityBinding;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The changeset format: a zip whose record entries hold length-delimited {@link TinkarMsg}
 * records, beside metadata under {@code META-INF/}.
 *
 * <p>Three format versions exist, and this release writes only the third:
 * <ul>
 *   <li>Version 1, written by every release before format version 2: UUIDs as text, no
 *       format version in the manifest, no identity index. Still read.
 *   <li>Version 2: the manifest names it ({@value #VERSION_ATTRIBUTE}: 2); every UUID is
 *       two longs ({@link SchemaIds}); and the {@link IdentityIndex} lists every component's
 *       pattern, so a store whose nids encode the pattern loads it in one pass. Still read,
 *       and still written by the incremental change-set writer.
 *   <li>Version 3 ({@value #VERSION_ATTRIBUTE}: 3): the {@link ComponentTable} comes first
 *       and numbers every component by its position; the records follow in one gzip entry
 *       per pattern, in table order, under {@value #RECORDS_PREFIX}; a reference is a
 *       sequence into the table rather than a public id; and the manifest, last, lists the
 *       record entries with their counts and hashes. See
 *       {@code notes/design-2026-10-08-changeset-format-3.adoc} in ike-dev.
 * </ul>
 * New software reads old changesets; old software cannot read new ones, by design. A reader
 * refuses a version newer than it knows rather than misread it.
 */
public final class ChangeSetFormat {

    /** The manifest attribute naming a changeset's format version; absent means version 1. */
    public static final String VERSION_ATTRIBUTE = "Ike-Format-Version";

    /** The format version this release writes, and the newest it reads. */
    public static final int CURRENT_VERSION = 3;
    /** The newest version whose components are listed by an identity index rather than a table. */
    public static final int IDENTITY_INDEX_VERSION = 2;

    /** The zip entry holding the manifest. */
    public static final String MANIFEST = "META-INF/MANIFEST.MF";

    /** Format 3: the zip entry holding the component table, first in the zip. */
    public static final String COMPONENT_TABLE = "META-INF/components.pb";
    /** Format 3: the entry listing components referenced but not carried, after the records. */
    public static final String REFERENCE_TABLE = "META-INF/references.pb";
    /** Format 3: the prefix of every record entry. */
    public static final String RECORDS_PREFIX = "records/";
    /** Format 3: a record entry is a gzip stream stored, not deflated, by the zip. */
    public static final String GZIP_SUFFIX = ".gz";
    /** Format 3: the manifest attribute counting the record entries. */
    public static final String RECORD_ENTRIES_ATTRIBUTE = "Ike-Record-Entries";
    /** Format 3: the manifest attribute counting the components, carried and referenced. */
    public static final String COMPONENT_COUNT_ATTRIBUTE = "Ike-Component-Count";
    /** Format 3: the prefix of the manifest attribute describing record entry N: name, count, SHA-256. */
    public static final String ENTRY_ATTRIBUTE_PREFIX = "Ike-Entry-";
    /** The zip entry holding the identity index (formats 1 and 2). */
    public static final String IDENTITY_INDEX = "META-INF/identities.pb";
    /** The one record entry of a format-1 or format-2 change set, as the incremental writer names it. */
    public static final String IDENTITY_INDEX_RECORDS = "Entities";

    private ChangeSetFormat() {
    }

    /**
     * Whether a zip entry holds metadata rather than records. Everything under
     * {@code META-INF/} — the manifest, the identity index, and anything added later — is
     * metadata a record reader skips.
     */
    public static boolean isMetadata(String entryName) {
        return entryName.startsWith("META-INF/");
    }

    /** Whether a changeset carries an identity index. */
    public static boolean hasIdentityIndex(File changeSet) throws IOException {
        try (ZipFile zip = new ZipFile(changeSet)) {
            return zip.getEntry(IDENTITY_INDEX) != null;
        }
    }

    /** Whether a changeset carries a component table (format 3). */
    public static boolean hasComponentTable(ZipFile zip) {
        return zip.getEntry(COMPONENT_TABLE) != null;
    }

    /** The name of record entry {@code ordinal}, counted from 1: {@code records/0003-concepts.pb.gz}. */
    public static String recordEntryName(int ordinal, String label) {
        return RECORDS_PREFIX + String.format("%04d-%s.pb", ordinal, label) + GZIP_SUFFIX;
    }

    /** The manifest attribute describing record entry {@code ordinal}, counted from 1. */
    public static String entryAttribute(int ordinal) {
        return ENTRY_ATTRIBUTE_PREFIX + String.format("%04d", ordinal);
    }

    /**
     * A record entry as the manifest describes it: its zip entry, the records it holds, and
     * the SHA-256 of its records uncompressed. A format-1 or format-2 changeset describes
     * nothing, so its entries carry {@code -1} and {@code null}.
     */
    public record RecordEntry(ZipEntry entry, long count, String sha256) {
    }

    /**
     * The record entries of a changeset in the order they are read: for format 3, the order
     * the manifest lists; before it, every entry that is not metadata, in zip order.
     *
     * @throws IllegalStateException if the manifest names an entry the zip does not hold
     */
    public static List<RecordEntry> recordEntries(ZipFile zip, Manifest manifest) {
        Attributes main = manifest.getMainAttributes();
        String listed = main.getValue(RECORD_ENTRIES_ATTRIBUTE);
        List<RecordEntry> entries = new ArrayList<>();
        if (listed != null) {
            int count = Integer.parseInt(listed.strip());
            for (int ordinal = 1; ordinal <= count; ordinal++) {
                String value = main.getValue(entryAttribute(ordinal));
                if (value == null) {
                    throw new IllegalStateException(zip.getName() + " lists " + count + " record entries but "
                            + entryAttribute(ordinal) + " is missing from its manifest");
                }
                String[] parts = value.strip().split("\\s+");
                ZipEntry entry = zip.getEntry(parts[0]);
                if (entry == null) {
                    throw new IllegalStateException(zip.getName() + " has no entry " + parts[0] + " named by its manifest");
                }
                entries.add(new RecordEntry(entry, parts.length > 1 ? Long.parseLong(parts[1]) : -1,
                        parts.length > 2 ? parts[2] : null));
            }
            return entries;
        }
        for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
            ZipEntry entry = e.nextElement();
            if (!isMetadata(entry.getName()) && !entry.isDirectory()) {
                entries.add(new RecordEntry(entry, -1, null));
            }
        }
        return entries;
    }

    /**
     * The records of an entry as a stream of length-delimited {@code TinkarMsg}: a format-3
     * entry is a gzip stream the zip stores, inflated here; an older entry is deflated by the
     * zip itself. Buffered; the caller closes it.
     */
    public static InputStream openRecords(ZipFile zip, ZipEntry entry) throws IOException {
        InputStream raw = zip.getInputStream(entry);
        if (entry.getName().endsWith(GZIP_SUFFIX)) {
            return new BufferedInputStream(new GZIPInputStream(raw, 1 << 16), 1 << 20);
        }
        return new BufferedInputStream(raw, 1 << 20);
    }

    /**
     * The manifest of a changeset, read through the zip's central directory. The exporter
     * writes the manifest last, after the records and the identity index, and a streaming
     * reader has to inflate every entry before it to reach it: 47 seconds of a DeX import
     * went to reading a 2.4 KB manifest (IKE-Network/ike-issues#1269). Empty when the zip
     * carries no manifest.
     */
    public static Optional<Manifest> manifest(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry(MANIFEST);
        if (entry == null) {
            return Optional.empty();
        }
        try (InputStream in = zip.getInputStream(entry)) {
            return Optional.of(new Manifest(in));
        }
    }

    /**
     * The format version a manifest names: 1 when it names none.
     *
     * @throws UnsupportedFormatException if it names a version this reader does not know
     */
    public static int version(Attributes manifest, String changeSet) {
        String value = manifest.getValue(VERSION_ATTRIBUTE);
        if (value == null) {
            return 1;
        }
        int version;
        try {
            version = Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            throw new UnsupportedFormatException(changeSet, value);
        }
        if (version < 1 || version > CURRENT_VERSION) {
            throw new UnsupportedFormatException(changeSet, value);
        }
        return version;
    }

    /**
     * The pattern a record's component is an element of: a semantic names its own; a concept,
     * a pattern and a stamp are elements of their kind's pattern.
     *
     * <p>A concept, pattern or stamp record may name its pattern; it must name its kind's. A
     * store enumerates concepts, patterns and stamps by their kind's pattern, so a component
     * filed under any other would be lost to that enumeration.
     *
     * @throws IllegalStateException if a concept, pattern or stamp names another pattern
     */
    public static PublicId patternOf(TinkarMsg record) {
        return switch (record.getValueCase()) {
            case SEMANTIC_CHRONOLOGY -> SchemaIds.toPublicId(record.getSemanticChronology().getPatternForSemanticPublicId());
            case CONCEPT_CHRONOLOGY -> kindPattern(record, EntityBinding.Concept.pattern().publicId(),
                    record.getConceptChronology().hasPatternForConceptPublicId(),
                    record.getConceptChronology().getPatternForConceptPublicId());
            case PATTERN_CHRONOLOGY -> kindPattern(record, EntityBinding.Pattern.pattern().publicId(),
                    record.getPatternChronology().hasPatternForPatternPublicId(),
                    record.getPatternChronology().getPatternForPatternPublicId());
            case STAMP_CHRONOLOGY -> kindPattern(record, EntityBinding.Stamp.pattern().publicId(),
                    record.getStampChronology().hasPatternForStampPublicId(),
                    record.getStampChronology().getPatternForStampPublicId());
            case VALUE_NOT_SET -> throw new IllegalStateException("Tinkar message value not set");
        };
    }

    /** The public id of a record's component, as the record carries it. */
    public static dev.ikm.tinkar.schema.PublicId componentOf(TinkarMsg record) {
        return switch (record.getValueCase()) {
            case CONCEPT_CHRONOLOGY -> record.getConceptChronology().getPublicId();
            case SEMANTIC_CHRONOLOGY -> record.getSemanticChronology().getPublicId();
            case PATTERN_CHRONOLOGY -> record.getPatternChronology().getPublicId();
            case STAMP_CHRONOLOGY -> record.getStampChronology().getPublicId();
            case VALUE_NOT_SET -> throw new IllegalStateException("Tinkar message value not set");
        };
    }

    private static PublicId kindPattern(TinkarMsg record, PublicId kindPattern, boolean named,
                                        dev.ikm.tinkar.schema.PublicId namedPattern) {
        if (named && !PublicId.equals(kindPattern, SchemaIds.toPublicId(namedPattern))) {
            throw new IllegalStateException("A " + record.getValueCase() + " record for "
                    + Arrays.toString(SchemaIds.uuids(componentOf(record)))
                    + " names pattern " + Arrays.toString(SchemaIds.uuids(namedPattern))
                    + "; a component of its kind is an element of " + kindPattern.idString() + " only");
        }
        return kindPattern;
    }

    /** A changeset in a format version this reader does not know. */
    public static final class UnsupportedFormatException extends IllegalStateException {
        UnsupportedFormatException(String changeSet, String version) {
            super(changeSet + " names " + VERSION_ATTRIBUTE + ": " + version
                    + "; this release reads format versions 1 to " + CURRENT_VERSION
                    + ". A newer release wrote it.");
        }
    }
}
