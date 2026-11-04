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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a GSRS origin ({@link GsrsOrigin}) from a dump downloaded from GSRS:
 * <ol>
 *   <li>reads the dump once, taking its size, SHA-256 and CRC-32 and counting its records, and
 *   refuses it if a line is not a record;</li>
 *   <li>writes a zip holding {@code META-INF/MANIFEST.MF} ({@link OriginManifest}) first, and
 *   then the dump, stored without compression so it is the published file byte for byte.</li>
 * </ol>
 * The origin depends only on the dump and the arguments: every zip entry is dated by the release
 * date and the manifest records no build time, so building it again from the same dump writes
 * the same zip, and its checksum can be compared with an earlier build's.
 *
 * <p>Usage: {@code GsrsOriginBuilder --in dump-public-2026-08-06.gsrs --out gsrs-origin-20260806.zip
 * [--url <download URL>] [--date yyyy-MM-dd]}. The URL defaults to the dump's name under
 * {@value #DOWNLOADS}, where GSRS publishes its dumps; the date to the date in the dump's name.
 */
public final class GsrsOriginBuilder {

    /** The site GSRS publishes its public data on. */
    public static final String SITE = "https://gsrs.ncats.nih.gov/";
    /** Where GSRS publishes its dumps. */
    public static final String DOWNLOADS = SITE + "assets/downloads/";
    /** What the dump is, as the manifest names it. */
    public static final String SOURCE = "GSRS public data";

    private static final Logger LOG = LoggerFactory.getLogger(GsrsOriginBuilder.class);
    private static final Pattern DATE_IN_NAME = Pattern.compile("(?<!\\d)(\\d{4}-\\d{2}-\\d{2})(?!\\d)");

    private GsrsOriginBuilder() {
    }

    /**
     * Runs the builder from the command line.
     *
     * @param args {@code --in <dump>}, {@code --out <zip>}, {@code --url <download URL>}
     *             (default under {@value #DOWNLOADS}), {@code --date yyyy-MM-dd} (default the
     *             date in the dump's name)
     */
    public static void main(String[] args) {
        Path in = null;
        Path out = null;
        URI url = null;
        LocalDate date = null;
        String usage = "usage: --in <dump>.gsrs --out <origin>.zip [--url <download URL>] [--date yyyy-MM-dd]";
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--in" -> in = Path.of(args[++i]);
                case "--out" -> out = Path.of(args[++i]);
                case "--url" -> url = URI.create(args[++i]);
                case "--date" -> date = LocalDate.parse(args[++i]);
                default -> throw new IllegalArgumentException("Unknown argument " + args[i] + "; " + usage);
            }
        }
        if (in == null || out == null) {
            throw new IllegalArgumentException(usage);
        }
        OriginManifest manifest = build(in, out, url, date);
        LOG.info("Wrote {}: {} records, {} bytes, SHA-256 {}", out, manifest.records(), manifest.bytes(),
                manifest.sha256());
    }

    /**
     * Builds an origin.
     *
     * @param dump        the dump, as downloaded from GSRS
     * @param out         the zip to write; replaced when it exists
     * @param url         where the dump was downloaded from, or null for its name under {@value #DOWNLOADS}
     * @param releaseDate the release's date, or null for the date in the dump's name
     * @return the manifest written
     * @throws ImportException when the dump cannot be read, a line of it is not a record, no
     *                         release date is given or in its name, or the zip cannot be written
     */
    public static OriginManifest build(Path dump, Path out, URI url, LocalDate releaseDate) {
        String fileName = dump.getFileName().toString();
        if (!fileName.endsWith(GsrsOrigin.DUMP_SUFFIX)) {
            throw new ImportException("A GSRS dump is a " + GsrsOrigin.DUMP_SUFFIX + " file, not " + dump);
        }
        if (!out.getFileName().toString().endsWith(".zip")) {
            throw new ImportException("A GSRS origin is written as a zip, not " + out);
        }
        LocalDate date = releaseDate != null ? releaseDate : dateInName(fileName).orElseThrow(() ->
                new ImportException("The dump's name " + fileName + " carries no date; give the release date"));
        URI source = url != null ? url : URI.create(DOWNLOADS + fileName);

        GsrsOrigin.Measure measured;
        try (InputStream in = Files.newInputStream(dump)) {
            measured = GsrsOrigin.measure(in, dump.toString());
        } catch (IOException e) {
            throw new ImportException("Cannot read the GSRS dump " + dump, e);
        }
        OriginManifest manifest = new OriginManifest(OriginManifest.FORMAT, SOURCE, URI.create(SITE), source, date,
                fileName, measured.bytes(), measured.sha256(), measured.records());
        LOG.info("{}: {} records, {} bytes, SHA-256 {}", fileName, measured.records(), measured.bytes(),
                measured.sha256());

        Path partial = out.resolveSibling(out.getFileName() + ".part");
        try {
            if (out.toAbsolutePath().getParent() != null) {
                Files.createDirectories(out.toAbsolutePath().getParent());
            }
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(partial), StandardCharsets.UTF_8)) {
                ZipEntry manifestEntry = entry(GsrsOrigin.MANIFEST, date);
                zip.putNextEntry(manifestEntry);
                manifest.toManifest().write(zip);
                zip.closeEntry();

                // stored, not deflated: the dump is already a gzip, and stays the published file
                ZipEntry dumpEntry = entry(fileName, date);
                dumpEntry.setMethod(ZipEntry.STORED);
                dumpEntry.setSize(measured.bytes());
                dumpEntry.setCompressedSize(measured.bytes());
                dumpEntry.setCrc(measured.crc32());
                zip.putNextEntry(dumpEntry);
                try (InputStream in = Files.newInputStream(dump)) {
                    in.transferTo(zip);
                }
                zip.closeEntry();
            }
            Files.move(partial, out, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(partial);
            } catch (IOException ignored) {
                // the failure being reported is the one that matters
            }
            throw new ImportException("Cannot write the GSRS origin " + out, e);
        }
        return manifest;
    }

    /**
     * The date a dump's name carries, as GSRS names its dumps ({@code dump-public-2026-08-06.gsrs}).
     *
     * @param fileName the dump's file name
     * @return the date, if the name carries one
     */
    public static Optional<LocalDate> dateInName(String fileName) {
        Matcher matcher = DATE_IN_NAME.matcher(fileName);
        while (matcher.find()) {
            try {
                return Optional.of(LocalDate.parse(matcher.group(1)));
            } catch (DateTimeParseException notADate) {
                // a date-shaped run that is not a date: keep looking
            }
        }
        return Optional.empty();
    }

    /** A zip entry dated by the release, so the zip depends only on what it holds. */
    private static ZipEntry entry(String name, LocalDate date) {
        ZipEntry entry = new ZipEntry(name);
        entry.setTimeLocal(date.atStartOfDay());
        return entry;
    }
}
