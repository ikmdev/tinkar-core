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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.jar.Attributes;
import java.util.zip.ZipFile;

/**
 * The changeset format: a zip whose record entries hold length-delimited {@link TinkarMsg}
 * records, beside metadata under {@code META-INF/}.
 *
 * <p>Two format versions exist, and this release writes only the second:
 * <ul>
 *   <li>Version 1, written by every release before format version 2: UUIDs as text, no
 *       format version in the manifest, no identity index. Still read.
 *   <li>Version 2: the manifest names it ({@value #VERSION_ATTRIBUTE}: 2); every UUID is
 *       two longs ({@link SchemaIds}); and the {@link IdentityIndex} lists every component's
 *       pattern, so a store whose nids encode the pattern loads it in one pass.
 * </ul>
 * New software reads old changesets; old software cannot read new ones, by design. A reader
 * refuses a version newer than it knows rather than misread it.
 */
public final class ChangeSetFormat {

    /** The manifest attribute naming a changeset's format version; absent means version 1. */
    public static final String VERSION_ATTRIBUTE = "Ike-Format-Version";

    /** The format version this release writes, and the newest it reads. */
    public static final int CURRENT_VERSION = 2;

    /** The zip entry holding the manifest. */
    public static final String MANIFEST = "META-INF/MANIFEST.MF";

    /** The zip entry holding the identity index. */
    public static final String IDENTITY_INDEX = "META-INF/identities.pb";

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
