/*
 * Copyright © 2026 IKE Network (support@ike.network)
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
package dev.ikm.tinkar.importer.gsrs;

import dev.ikm.tinkar.importer.ImportException;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * What a GSRS origin holds, as its {@code META-INF/MANIFEST.MF} says: which release of the GSRS
 * public data, where it was published, and the dump's size, SHA-256 and record count, so an
 * origin can be checked against the file GSRS publishes and an import against the records it
 * holds. The attributes are {@code Ike-} attributes, as the protobufs and stores the knowledge
 * sources build carry in theirs.
 *
 * @param format      the origin format, {@value #FORMAT}
 * @param source      what the dump is, {@value GsrsOriginBuilder#SOURCE}
 * @param site        the site GSRS publishes it on
 * @param url         where the dump was downloaded from
 * @param releaseDate the date the release is dated by: the date in the dump's name
 * @param fileName    the dump's file name, as GSRS publishes it
 * @param bytes       the dump's size
 * @param sha256      the dump's SHA-256, in lower-case hex
 * @param records     how many substance records the dump holds, one per line
 */
public record OriginManifest(String format, String source, URI site, URI url, LocalDate releaseDate,
                             String fileName, long bytes, String sha256, long records) {

    /** The format this reader reads and the builder writes. */
    public static final String FORMAT = "gsrs-origin/1";

    /** The manifest attribute naming the origin format. */
    public static final String FORMAT_ATTRIBUTE = "Ike-Origin-Format";
    /** The manifest attribute naming what the dump is. */
    public static final String SOURCE_ATTRIBUTE = "Ike-Source";
    /** The manifest attribute naming the site the dump is published on. */
    public static final String SITE_ATTRIBUTE = "Ike-Source-Site";
    /** The manifest attribute naming where the dump was downloaded from. */
    public static final String URL_ATTRIBUTE = "Ike-Source-Url";
    /** The manifest attribute naming the release date. */
    public static final String RELEASE_DATE_ATTRIBUTE = "Ike-Release-Date";
    /** The manifest attribute naming the dump's file. */
    public static final String FILE_ATTRIBUTE = "Ike-Source-File";
    /** The manifest attribute counting the dump's bytes. */
    public static final String BYTES_ATTRIBUTE = "Ike-Source-Bytes";
    /** The manifest attribute holding the dump's SHA-256. */
    public static final String SHA256_ATTRIBUTE = "Ike-Source-Sha256";
    /** The manifest attribute counting the dump's records. */
    public static final String RECORDS_ATTRIBUTE = "Ike-Record-Count";

    /**
     * Validates.
     */
    public OriginManifest {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(site, "site");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(releaseDate, "releaseDate");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(sha256, "sha256");
    }

    /**
     * Reads a manifest.
     *
     * @param manifest the manifest, as {@code META-INF/MANIFEST.MF} holds it
     * @return the origin manifest
     * @throws ImportException when the manifest is not of this format or is incomplete
     */
    public static OriginManifest parse(Manifest manifest) {
        Attributes attributes = manifest.getMainAttributes();
        String format = attributes.getValue(FORMAT_ATTRIBUTE);
        if (!FORMAT.equals(format)) {
            throw new ImportException("The origin manifest's " + FORMAT_ATTRIBUTE + " is "
                    + (format == null ? "missing" : format) + ", not " + FORMAT);
        }
        try {
            return new OriginManifest(format, required(attributes, SOURCE_ATTRIBUTE),
                    URI.create(required(attributes, SITE_ATTRIBUTE)), URI.create(required(attributes, URL_ATTRIBUTE)),
                    LocalDate.parse(required(attributes, RELEASE_DATE_ATTRIBUTE)), required(attributes, FILE_ATTRIBUTE),
                    Long.parseLong(required(attributes, BYTES_ATTRIBUTE)), required(attributes, SHA256_ATTRIBUTE),
                    Long.parseLong(required(attributes, RECORDS_ATTRIBUTE)));
        } catch (DateTimeParseException | IllegalArgumentException e) {
            throw new ImportException("The origin manifest has an unreadable attribute: " + e.getMessage(), e);
        }
    }

    /**
     * This manifest as {@code META-INF/MANIFEST.MF} holds it, its attributes in the order the
     * builder writes them.
     *
     * @return the manifest
     */
    public Manifest toManifest() {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Created-By", GsrsOriginBuilder.class.getSimpleName());
        attributes.putValue(FORMAT_ATTRIBUTE, format);
        attributes.putValue(SOURCE_ATTRIBUTE, source);
        attributes.putValue(SITE_ATTRIBUTE, site.toString());
        attributes.putValue(URL_ATTRIBUTE, url.toString());
        attributes.putValue(RELEASE_DATE_ATTRIBUTE, releaseDate.toString());
        attributes.putValue(FILE_ATTRIBUTE, fileName);
        attributes.putValue(BYTES_ATTRIBUTE, Long.toString(bytes));
        attributes.putValue(SHA256_ATTRIBUTE, sha256);
        attributes.putValue(RECORDS_ATTRIBUTE, Long.toString(records));
        return manifest;
    }

    private static String required(Attributes attributes, String name) {
        String value = attributes.getValue(name);
        if (value == null || value.isBlank()) {
            throw new ImportException("The origin manifest has no " + name);
        }
        return value.strip();
    }
}
