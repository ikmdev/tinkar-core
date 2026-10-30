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
package dev.ikm.tinkar.importer.ncbi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ikm.tinkar.importer.ImportException;
import dev.ikm.tinkar.importer.ncbi.OriginManifest.EntrezCheck;
import dev.ikm.tinkar.importer.ncbi.OriginManifest.Extract;
import dev.ikm.tinkar.importer.ncbi.OriginManifest.ScopedTaxon;
import dev.ikm.tinkar.importer.ncbi.OriginManifest.SourceFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a {@linkplain OriginManifest.Format#BULK_FILES bulk-file} NCBI Gene origin for some
 * organisms from NCBI's published bulk files, rather than from its web services:
 * <ol>
 *   <li>downloads NCBI Gene's {@code gene_info.gz}, {@code gene2refseq.gz} and
 *   {@code gene_summary.gz} and NCBI Taxonomy's {@code taxdump.tar.gz} into a cache, reusing a
 *   cached file whose size and date match what the server serves now, and takes each file's
 *   SHA-256;</li>
 *   <li>reads the taxonomy's parent links ({@code nodes.dmp}) and keeps a row of a gene file
 *   when its {@code tax_id} is a scoped taxon or descends from one, the way Entrez's
 *   {@code txid{taxon}[Organism:exp]} does; a {@code gene2refseq} row is kept only when it states
 *   a genomic accession, since only those place a gene;</li>
 *   <li>writes the kept rows verbatim, each file under its source's header line, with the
 *   scientific names ({@code names.dmp}) of the scoped taxa and of every taxon a kept gene is
 *   of, taking each extract's SHA-256 and row count as it is written;</li>
 *   <li>asks Entrez for each scoped taxon's live gene count ({@link NcbiGeneOriginBuilder#SEARCH_TERM})
 *   and records it beside the origin's own count: the bulk files are refreshed daily and Entrez is
 *   live, so the two may differ by a few genes, and a large difference says the filter is
 *   wrong.</li>
 * </ol>
 * The manifest records every file downloaded and every extract written ({@link OriginManifest}).
 *
 * <p>Usage: {@code NcbiGeneBulkOriginBuilder --taxon 9606 --taxon 10239 --out <path>
 * [--cache <dir>] [--date yyyy-MM-dd]}. The output is a directory, or a zip when the path ends in
 * {@code .zip}, holding the four extracts and {@code manifest.json}, under
 * {@code ncbi-gene-origin-<yyyyMMdd>/} in a zip named so; the default name is
 * {@code ncbi-gene-origin-<yyyyMMdd>}. The cache defaults to {@code ncbi-gene-bulk} in the
 * system's temporary directory.
 */
public final class NcbiGeneBulkOriginBuilder {

    /** NCBI Gene's bulk files. */
    public static final String GENE_DATA = "https://ftp.ncbi.nlm.nih.gov/gene/DATA";
    /** NCBI Taxonomy's dump. */
    public static final String TAXONOMY_DUMP = "https://ftp.ncbi.nlm.nih.gov/pub/taxonomy/taxdump.tar.gz";

    private static final Logger LOG = LoggerFactory.getLogger(NcbiGeneBulkOriginBuilder.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String NODES_DUMP = "nodes.dmp";
    private static final String NAMES_DUMP = "names.dmp";
    private static final String SCIENTIFIC_NAME = "scientific name";
    private static final long PROGRESS_ROWS = 10_000_000;

    /**
     * The files an origin is filtered from.
     */
    public enum Source {
        /** Every gene: {@code gene_info.gz}. */
        GENE_INFO("gene_info.gz", GENE_DATA + "/gene_info.gz"),
        /** Every gene's RefSeq records and genomic placements: {@code gene2refseq.gz}. */
        GENE2REFSEQ("gene2refseq.gz", GENE_DATA + "/gene2refseq.gz"),
        /** NCBI's gene summaries: {@code gene_summary.gz}. */
        GENE_SUMMARY("gene_summary.gz", GENE_DATA + "/gene_summary.gz"),
        /** NCBI Taxonomy's names and parent links: {@code taxdump.tar.gz}. */
        TAXONOMY("taxdump.tar.gz", TAXONOMY_DUMP);

        private final String fileName;
        private final String url;

        Source(String fileName, String url) {
            this.fileName = fileName;
            this.url = url;
        }

        /**
         * The file's name, in the cache and in the manifest.
         *
         * @return the name
         */
        public String fileName() {
            return fileName;
        }

        /**
         * Where the file is downloaded from.
         *
         * @return the URL
         */
        public String url() {
            return url;
        }
    }

    /**
     * Reads NCBI: downloads files and asks Entrez.
     */
    public interface Fetcher {
        /**
         * Brings a URL's file into a path, unless the path already holds what the server serves.
         *
         * @param url  the URL
         * @param file where the file goes
         * @return what was fetched
         * @throws ImportException when the file cannot be downloaded
         */
        Fetched fetch(String url, Path file);

        /**
         * Gets a URL's JSON.
         *
         * @param url the URL
         * @return its JSON
         * @throws ImportException when the request fails
         */
        JsonNode get(String url);
    }

    /**
     * A file as fetched.
     *
     * @param file         where it is
     * @param lastModified the server's {@code Last-Modified} for it, as sent
     */
    public record Fetched(Path file, String lastModified) {
    }

    private final Fetcher fetcher;
    private final List<String> requests = new ArrayList<>();

    /**
     * A builder over NCBI's servers.
     */
    public NcbiGeneBulkOriginBuilder() {
        this(new HttpFetcher());
    }

    /**
     * A builder over a fetcher.
     *
     * @param fetcher the fetcher every download and query goes through
     */
    public NcbiGeneBulkOriginBuilder(Fetcher fetcher) {
        this.fetcher = fetcher;
    }

    /**
     * Runs the builder from the command line.
     *
     * @param args {@code --taxon <id>[,<id>...]} (repeatable), {@code --out <path>},
     *             {@code --cache <dir>}, {@code --date yyyy-MM-dd} (default today, UTC)
     */
    public static void main(String[] args) {
        List<String> taxa = new ArrayList<>();
        Path out = null;
        Path cache = Path.of(System.getProperty("java.io.tmpdir"), "ncbi-gene-bulk");
        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        String usage = "usage: --taxon <id>[,<id>...] --out <path> [--cache <dir>] [--date yyyy-MM-dd]";
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--taxon" -> {
                    for (String taxon : args[++i].split("\\s*,\\s*")) {
                        taxa.add(taxon.strip());
                    }
                }
                case "--out" -> out = Path.of(args[++i]);
                case "--cache" -> cache = Path.of(args[++i]);
                case "--date" -> date = LocalDate.parse(args[++i]);
                default -> throw new IllegalArgumentException("Unknown argument " + args[i] + "; " + usage);
            }
        }
        if (taxa.isEmpty() || out == null) {
            throw new IllegalArgumentException(usage);
        }
        Path written = new NcbiGeneBulkOriginBuilder().build(taxa, out, cache, date);
        LOG.info("Wrote {}", written);
    }

    /**
     * Builds an origin.
     *
     * @param taxa  the NCBI Taxonomy IDs to scope to, in order
     * @param out   the directory to write, or a zip when its name ends in {@code .zip}
     * @param cache the directory the bulk files are downloaded into and reused from
     * @param date  the date the origin is dated by, and named by
     * @return the path written
     * @throws ImportException when NCBI cannot be read or the origin cannot be written
     */
    public Path build(List<String> taxa, Path out, Path cache, LocalDate date) {
        for (String taxon : taxa) {
            if (taxon.isEmpty() || !taxon.chars().allMatch(Character::isDigit)) {
                throw new ImportException(taxon + " is not an NCBI Taxonomy ID");
            }
        }
        Instant retrievedAt = Instant.now();
        Map<Source, SourceFile> sources = new LinkedHashMap<>();
        Map<Source, Path> files = new LinkedHashMap<>();
        try {
            Files.createDirectories(cache);
            for (Source source : Source.values()) {
                Path file = cache.resolve(source.fileName());
                Fetched fetched = fetcher.fetch(record(source.url()), file);
                files.put(source, fetched.file());
                sources.put(source, new SourceFile(source.fileName(), source.url(), fetched.lastModified(),
                        Files.size(fetched.file()), sha256(fetched.file())));
                LOG.info("{}: {} bytes, {}", source.fileName(), Files.size(fetched.file()), fetched.lastModified());
            }
        } catch (IOException e) {
            throw new ImportException("Cannot use the cache " + cache, e);
        }

        Scope scope = new Scope(taxa, readParents(files.get(Source.TAXONOMY)));
        String name = out.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")
                ? out.getFileName().toString().substring(0, out.getFileName().toString().length() - ".zip".length())
                : "ncbi-gene-origin-" + date.format(DateTimeFormatter.BASIC_ISO_DATE);
        List<Extract> extracts = new ArrayList<>();
        Map<String, TreeSet<Long>> geneIds = new LinkedHashMap<>();
        taxa.forEach(taxon -> geneIds.put(taxon, new TreeSet<>()));
        Set<String> taxaPresent = new HashSet<>(taxa);
        try (Sink sink = out.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")
                ? new ZipSink(out, name) : new DirectorySink(out)) {
            String treeRule = "tax_id is a scoped taxon (" + String.join(", ", taxa)
                    + ") or descends from one in nodes.dmp of " + Source.TAXONOMY.fileName();
            extracts.add(filter(sink, files.get(Source.GENE_INFO), BulkGeneReader.GENE_INFO_FILE, Source.GENE_INFO,
                    treeRule, (row, header) -> {
                        String taxId = field(row, 0);
                        List<String> scopes = scope.scopesOf(taxId);
                        if (scopes.isEmpty()) {
                            return false;
                        }
                        taxaPresent.add(taxId);
                        if (!BulkGeneReader.NEWENTRY.equals(field(row, 2))) {
                            long geneId = Long.parseLong(field(row, 1));
                            scopes.forEach(taxon -> geneIds.get(taxon).add(geneId));
                        }
                        return true;
                    }));
            extracts.add(filter(sink, files.get(Source.GENE2REFSEQ), BulkGeneReader.GENE2REFSEQ_FILE,
                    Source.GENE2REFSEQ, treeRule + ", and genomic_nucleotide_accession.version is stated",
                    (row, header) -> !scope.scopesOf(field(row, 0)).isEmpty()
                            && !BulkGeneReader.ABSENT.equals(field(row, header.genomicAccession()))));
            extracts.add(filter(sink, files.get(Source.GENE_SUMMARY), BulkGeneReader.GENE_SUMMARY_FILE,
                    Source.GENE_SUMMARY, treeRule, (row, header) -> !scope.scopesOf(field(row, 0)).isEmpty()));
            extracts.add(names(sink, files.get(Source.TAXONOMY), taxaPresent));

            Map<String, String> organismNames = scopedNames(sink, taxa);
            List<ScopedTaxon> scoped = new ArrayList<>();
            List<EntrezCheck> checks = new ArrayList<>();
            Set<Long> allGenes = new TreeSet<>();
            for (String taxon : taxa) {
                List<String> ids = geneIds.get(taxon).stream().map(String::valueOf).toList();
                allGenes.addAll(geneIds.get(taxon));
                scoped.add(new ScopedTaxon(taxon, organismNames.getOrDefault(taxon, ""), "rows of "
                        + Source.GENE_INFO.fileName() + " whose tax_id is " + taxon + " or descends from it in "
                        + NODES_DUMP + ", less NEWENTRY placeholder rows", ids.size(), ids));
                checks.add(entrezCheck(taxon, ids.size()));
            }
            OriginManifest manifest = new OriginManifest(OriginManifest.Format.BULK_FILES, retrievedAt, date, scoped,
                    allGenes.size(), List.of(), requests, List.copyOf(sources.values()), extracts, checks);
            try (Writer writer = new OutputStreamWriter(sink.entry(NcbiGeneOrigin.MANIFEST_FILE), StandardCharsets.UTF_8)) {
                writer.write(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(manifest.toJson()));
                writer.write('\n');
            }
            LOG.info("Origin {}: {} genes; {}", out, allGenes.size(), checks);
        } catch (IOException e) {
            throw new ImportException("Cannot write the NCBI Gene origin " + out, e);
        }
        return out;
    }

    /** A gene file's header, with the columns a filter reads. */
    private record Header(String line, int genomicAccession) {
    }

    /** Whether a row of a gene file is kept. */
    @FunctionalInterface
    private interface RowFilter {
        boolean keep(String row, Header header);
    }

    private Extract filter(Sink sink, Path gz, String entryName, Source source, String rule, RowFilter filter)
            throws IOException {
        MessageDigest digest = newDigest();
        long rows = 0;
        long read = 0;
        try (BufferedReader reader = gunzip(gz);
             Writer writer = new BufferedWriter(new OutputStreamWriter(
                     new DigestOutputStream(sink.entry(entryName), digest), StandardCharsets.UTF_8), 1 << 16)) {
            String headerLine = reader.readLine();
            if (headerLine == null || !headerLine.startsWith("#")) {
                throw new ImportException(source.fileName() + " does not begin with its header line");
            }
            List<String> columns = List.of(headerLine.split("\t", -1));
            Header header = new Header(headerLine, columns.indexOf("genomic_nucleotide_accession.version"));
            writer.write(headerLine);
            writer.write('\n');
            String row;
            while ((row = reader.readLine()) != null) {
                if (row.isEmpty()) {
                    continue;
                }
                if (++read % PROGRESS_ROWS == 0) {
                    LOG.info("{}: {} rows read, {} kept", source.fileName(), read, rows);
                }
                if (filter.keep(row, header)) {
                    writer.write(row);
                    writer.write('\n');
                    rows++;
                }
            }
        }
        LOG.info("{}: {} of {} rows kept", entryName, rows, read);
        return new Extract(entryName, source.fileName(), rule, rows, HexFormat.of().formatHex(digest.digest()));
    }

    private Extract names(Sink sink, Path taxonomy, Set<String> taxaPresent) throws IOException {
        MessageDigest digest = newDigest();
        long[] rows = {0};
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                new DigestOutputStream(sink.entry(BulkGeneReader.TAXONOMY_NAMES_FILE), digest), StandardCharsets.UTF_8))) {
            TaxonomyDump.lines(taxonomy, NAMES_DUMP, line -> {
                String[] fields = BulkGeneReader.dumpFields(line);
                if (fields.length >= 4 && fields[3].equals(SCIENTIFIC_NAME) && taxaPresent.contains(fields[0])) {
                    writer.write(line);
                    writer.write('\n');
                    rows[0]++;
                }
            });
        }
        return new Extract(BulkGeneReader.TAXONOMY_NAMES_FILE, Source.TAXONOMY.fileName(),
                "scientific-name rows of " + NAMES_DUMP + " for the scoped taxa and every tax_id of "
                        + BulkGeneReader.GENE_INFO_FILE, rows[0], HexFormat.of().formatHex(digest.digest()));
    }

    /** The scoped taxa's names, read back from the names the sink was given. */
    private static Map<String, String> scopedNames(Sink sink, List<String> taxa) {
        Map<String, String> names = new HashMap<>();
        for (String line : sink.namesWritten()) {
            String[] fields = BulkGeneReader.dumpFields(line);
            if (taxa.contains(fields[0])) {
                names.put(fields[0], fields[1]);
            }
        }
        return names;
    }

    private EntrezCheck entrezCheck(String taxon, long originCount) {
        String term = NcbiGeneOriginBuilder.SEARCH_TERM.formatted(taxon);
        String url = NcbiGeneOriginBuilder.EUTILS + "/esearch.fcgi?db=gene&retmode=json&retmax=0&term="
                + URLEncoder.encode(term, StandardCharsets.UTF_8);
        long count = fetcher.get(record(url)).path("esearchresult").path("count").asLong(-1);
        if (count != originCount) {
            LOG.warn("Taxon {}: the origin holds {} genes, Entrez reports {}", taxon, originCount, count);
        }
        return new EntrezCheck(taxon, term, count, originCount);
    }

    private static Map<String, String> readParents(Path taxonomy) {
        Map<String, String> parents = new HashMap<>(1 << 22);
        try {
            TaxonomyDump.lines(taxonomy, NODES_DUMP, line -> {
                String[] fields = BulkGeneReader.dumpFields(line);
                if (fields.length >= 2) {
                    parents.put(fields[0], fields[1]);
                }
            });
        } catch (IOException e) {
            throw new ImportException("Cannot read " + NODES_DUMP + " in " + taxonomy, e);
        }
        if (parents.isEmpty()) {
            throw new ImportException(taxonomy + " holds no " + NODES_DUMP);
        }
        return parents;
    }

    /**
     * Which scoped taxa a taxon falls under: itself, or the scoped taxa among its ancestors. Each
     * taxon's answer is kept, since every gene of a taxon asks the same question.
     */
    private static final class Scope {
        private final Set<String> scoped;
        private final Map<String, String> parents;
        private final Map<String, List<String>> answers = new HashMap<>();

        Scope(List<String> taxa, Map<String, String> parents) {
            this.scoped = new HashSet<>(taxa);
            this.parents = parents;
            for (String taxon : taxa) {
                if (!parents.containsKey(taxon)) {
                    throw new ImportException("Taxon " + taxon + " is not in NCBI Taxonomy's " + NODES_DUMP);
                }
            }
        }

        List<String> scopesOf(String taxId) {
            return answers.computeIfAbsent(taxId, this::walk);
        }

        private List<String> walk(String taxId) {
            List<String> found = new ArrayList<>();
            String current = taxId;
            for (int depth = 0; depth < 200 && current != null; depth++) {
                if (scoped.contains(current)) {
                    found.add(current);
                }
                String parent = parents.get(current);
                if (parent == null || parent.equals(current)) {
                    break;
                }
                current = parent;
            }
            return List.copyOf(found);
        }
    }

    /** Where an origin's files are written. */
    private interface Sink extends AutoCloseable {
        /**
         * Opens a file of the origin for writing; closing the stream finishes the file.
         */
        OutputStream entry(String fileName) throws IOException;

        /** The rows written to the taxonomy names file, for the scoped taxa's names. */
        List<String> namesWritten();

        @Override
        void close() throws IOException;
    }

    /** Remembers the names file's rows as they are written, for {@link Sink#namesWritten()}. */
    private abstract static class RecordingSink implements Sink {
        private final List<String> names = new ArrayList<>();

        OutputStream recording(String fileName, OutputStream out) {
            if (!fileName.equals(BulkGeneReader.TAXONOMY_NAMES_FILE)) {
                return out;
            }
            return new OutputStream() {
                private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

                @Override
                public void write(int b) throws IOException {
                    out.write(b);
                    if (b == '\n') {
                        names.add(bytes.toString(StandardCharsets.UTF_8));
                        bytes.reset();
                    } else {
                        bytes.write(b);
                    }
                }

                @Override
                public void write(byte[] buffer, int off, int len) throws IOException {
                    for (int i = off; i < off + len; i++) {
                        write(buffer[i]);
                    }
                }

                @Override
                public void flush() throws IOException {
                    out.flush();
                }

                @Override
                public void close() throws IOException {
                    out.close();
                }
            };
        }

        @Override
        public List<String> namesWritten() {
            return names;
        }
    }

    private static final class DirectorySink extends RecordingSink {
        private final Path directory;

        DirectorySink(Path directory) throws IOException {
            this.directory = Files.createDirectories(directory);
        }

        @Override
        public OutputStream entry(String fileName) throws IOException {
            return recording(fileName, Files.newOutputStream(directory.resolve(fileName)));
        }

        @Override
        public void close() {
        }
    }

    private static final class ZipSink extends RecordingSink {
        private final ZipOutputStream zip;
        private final String name;

        ZipSink(Path file, String name) throws IOException {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            this.zip = new ZipOutputStream(Files.newOutputStream(file), StandardCharsets.UTF_8);
            this.name = name;
        }

        @Override
        public OutputStream entry(String fileName) throws IOException {
            zip.putNextEntry(new ZipEntry(name + "/" + fileName));
            return recording(fileName, new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    zip.write(b);
                }

                @Override
                public void write(byte[] bytes, int off, int len) throws IOException {
                    zip.write(bytes, off, len);
                }

                @Override
                public void close() throws IOException {
                    zip.closeEntry();
                }
            });
        }

        @Override
        public void close() throws IOException {
            zip.close();
        }
    }

    private String record(String url) {
        requests.add(url);
        return url;
    }

    /** The {@code i}th tab-separated field of a row, read without splitting the whole row. */
    private static String field(String row, int i) {
        int start = 0;
        for (int field = 0; field < i; field++) {
            start = row.indexOf('\t', start) + 1;
            if (start == 0) {
                return "";
            }
        }
        int end = row.indexOf('\t', start);
        return end < 0 ? row.substring(start) : row.substring(start, end);
    }

    private static BufferedReader gunzip(Path gz) throws IOException {
        return new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(gz), 1 << 16),
                StandardCharsets.UTF_8), 1 << 16);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("The JDK lacks SHA-256", e);
        }
    }

    /**
     * A file's SHA-256, lowercase hexadecimal.
     *
     * @param file the file
     * @return its digest
     * @throws IOException when it cannot be read
     */
    static String sha256(Path file) throws IOException {
        MessageDigest digest = newDigest();
        byte[] buffer = new byte[1 << 16];
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * NCBI over HTTP: a download is reused when the cached file's size and date match the server's,
     * and every request retries a rate-limit, server or network failure with a growing wait.
     */
    static final class HttpFetcher implements Fetcher {

        private static final int ATTEMPTS = 6;
        private static final Duration PACE = Duration.ofMillis(350);

        private final HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        private Instant lastRequest = Instant.EPOCH;

        @Override
        public Fetched fetch(String url, Path file) {
            HttpResponse<Void> head = send(HttpRequest.newBuilder(URI.create(url)).method("HEAD",
                    HttpRequest.BodyPublishers.noBody()), HttpResponse.BodyHandlers.discarding(), url);
            String lastModified = head.headers().firstValue("Last-Modified").orElse("");
            long length = head.headers().firstValueAsLong("Content-Length").orElse(-1);
            Optional<Instant> serverTime = parseHttpDate(lastModified);
            try {
                if (Files.isRegularFile(file) && Files.size(file) == length && serverTime.isPresent()
                        && Files.getLastModifiedTime(file).toInstant().equals(serverTime.get())) {
                    LOG.info("Reusing {} ({})", file, lastModified);
                    return new Fetched(file, lastModified);
                }
                LOG.info("Downloading {} ({} bytes, {})", url, length, lastModified);
                Path partial = file.resolveSibling(file.getFileName() + ".part");
                HttpResponse<Path> response = send(HttpRequest.newBuilder(URI.create(url)).GET(),
                        HttpResponse.BodyHandlers.ofFile(partial), url);
                String sentModified = response.headers().firstValue("Last-Modified").orElse(lastModified);
                if (length >= 0 && Files.size(partial) != length) {
                    throw new ImportException("Downloaded " + Files.size(partial) + " bytes of " + url + ", expected "
                            + length);
                }
                Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                Optional<Instant> sentTime = parseHttpDate(sentModified);
                if (sentTime.isPresent()) {
                    Files.setLastModifiedTime(file, FileTime.from(sentTime.get()));
                }
                return new Fetched(file, sentModified);
            } catch (IOException e) {
                throw new ImportException("Cannot download " + url + " to " + file, e);
            }
        }

        @Override
        public JsonNode get(String url) {
            HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url)).GET()
                    .header("Accept", "application/json"), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8), url);
            try {
                return MAPPER.readTree(response.body());
            } catch (IOException e) {
                throw new ImportException("Cannot read the JSON of " + url, e);
            }
        }

        private <T> HttpResponse<T> send(HttpRequest.Builder builder, HttpResponse.BodyHandler<T> handler, String url) {
            HttpRequest request = builder
                    .header("User-Agent", "importer-ncbi-gene (IKE Network; support@ike.network)")
                    .timeout(Duration.ofHours(2))
                    .build();
            ImportException last = null;
            for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
                pace();
                try {
                    HttpResponse<T> response = http.send(request, handler);
                    int status = response.statusCode();
                    if (status == 200) {
                        return response;
                    }
                    last = new ImportException("NCBI answered " + status + " for " + url);
                    if (status != 429 && status < 500) {
                        throw last;
                    }
                } catch (IOException e) {
                    last = new ImportException("Cannot read " + url, e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ImportException("Interrupted while reading " + url, e);
                }
                LOG.warn("Attempt {} of {} failed: {}", attempt, ATTEMPTS, last.getMessage());
                sleep(Duration.ofSeconds(5L << attempt));
            }
            throw last;
        }

        private void pace() {
            Duration since = Duration.between(lastRequest, Instant.now());
            if (since.compareTo(PACE) < 0) {
                sleep(PACE.minus(since));
            }
            lastRequest = Instant.now();
        }

        private static Optional<Instant> parseHttpDate(String value) {
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            try {
                return Optional.of(ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            } catch (DateTimeParseException e) {
                return Optional.empty();
            }
        }

        private static void sleep(Duration duration) {
            try {
                Thread.sleep(duration);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ImportException("Interrupted while waiting on NCBI", e);
            }
        }
    }
}
