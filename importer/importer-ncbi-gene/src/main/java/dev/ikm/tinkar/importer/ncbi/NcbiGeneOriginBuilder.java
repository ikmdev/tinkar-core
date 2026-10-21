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
import dev.ikm.tinkar.importer.ncbi.OriginManifest.ScopedTaxon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds an NCBI Gene origin ({@link NcbiGeneOrigin}) for some organisms, from NCBI's live
 * services:
 * <ol>
 *   <li>for each taxon, Entrez's gene search for the genes alive under it and its descendants:
 *   {@code esearch.fcgi?db=gene&term=txid{taxon}[Organism:exp] AND alive[prop]}, which for a
 *   virus species includes every reference strain NCBI annotates (Datasets' own
 *   {@code /gene/taxon/{taxon}} refuses a species-level virus taxon), less NCBI's placeholder
 *   records ({@code NEWENTRY}, one per organism, kept to accept GeneRIFs for genes not in
 *   Gene), which are not genes and have no Datasets report;</li>
 *   <li>NCBI Datasets' gene reports for those IDs, {@code /datasets/v2/gene/id/{ids}}, in
 *   batches, following its pages;</li>
 *   <li>NCBI Datasets' taxonomy names for the taxa, {@code /datasets/v2/taxonomy/taxon/{ids}},
 *   for the manifest.</li>
 * </ol>
 * The reports are written exactly as returned, one per line of {@code genes.jsonl} in gene ID
 * order, with a {@code manifest.json} recording the retrieval time, the taxa and their gene IDs,
 * the counts and every URL requested. Requests are paced at no more than three a second, NCBI's
 * limit without an API key.
 *
 * <p>Usage: {@code NcbiGeneOriginBuilder --taxon 2697049 --taxon 11320,11520 --out <path>
 * [--date yyyy-MM-dd]}. The output is a directory, or a zip when the path ends in {@code .zip},
 * holding {@code <name>/manifest.json} and {@code <name>/genes.jsonl}; the default name is
 * {@code ncbi-gene-origin-<yyyyMMdd>}.
 */
public final class NcbiGeneOriginBuilder {

    /** NCBI Datasets' API root. */
    public static final String DATASETS_API = "https://api.ncbi.nlm.nih.gov/datasets/v2";
    /** Entrez E-utilities' root. */
    public static final String EUTILS = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils";
    /**
     * The Entrez search term for a taxon's live genes, with {@code %s} for the taxon ID: the
     * taxon and its descendants, alive, less NCBI's {@code NEWENTRY} placeholder records.
     */
    public static final String SEARCH_TERM = "txid%s[Organism:exp] AND alive[prop] NOT NEWENTRY[Gene Name]";

    private static final Logger LOG = LoggerFactory.getLogger(NcbiGeneOriginBuilder.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int BATCH = 50;
    private static final int PAGE_SIZE = 100;
    private static final int SEARCH_MAX = 10_000;
    private static final Duration PACE = Duration.ofMillis(350);
    private static final int ATTEMPTS = 4;

    private final NcbiClient client;
    private final List<String> requests = new ArrayList<>();

    /**
     * Fetches JSON from NCBI.
     */
    public interface NcbiClient {
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
     * A builder over NCBI's live services, paced.
     */
    public NcbiGeneOriginBuilder() {
        this(new PacedClient());
    }

    /**
     * A builder over a client.
     *
     * @param client the client every request goes through
     */
    public NcbiGeneOriginBuilder(NcbiClient client) {
        this.client = client;
    }

    /**
     * Runs the builder from the command line.
     *
     * @param args {@code --taxon <id>[,<id>...]} (repeatable), {@code --out <path>},
     *             {@code --date yyyy-MM-dd} (default today, UTC)
     */
    public static void main(String[] args) {
        List<String> taxa = new ArrayList<>();
        Path out = null;
        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--taxon" -> {
                    for (String taxon : args[++i].split("\\s*,\\s*")) {
                        taxa.add(taxon.strip());
                    }
                }
                case "--out" -> out = Path.of(args[++i]);
                case "--date" -> date = LocalDate.parse(args[++i]);
                default -> throw new IllegalArgumentException("Unknown argument " + args[i]
                        + "; usage: --taxon <id>[,<id>...] --out <path> [--date yyyy-MM-dd]");
            }
        }
        if (taxa.isEmpty() || out == null) {
            throw new IllegalArgumentException("usage: --taxon <id>[,<id>...] --out <path> [--date yyyy-MM-dd]");
        }
        Path written = new NcbiGeneOriginBuilder().build(taxa, out, date);
        LOG.info("Wrote {}", written);
    }

    /**
     * Builds an origin.
     *
     * @param taxa the NCBI Taxonomy IDs to scope to, in order
     * @param out  the directory to write, or a zip when its name ends in {@code .zip}
     * @param date the date the origin is dated by, and named by
     * @return the path written
     * @throws ImportException when NCBI cannot be read or the origin cannot be written
     */
    public Path build(List<String> taxa, Path out, LocalDate date) {
        Instant retrievedAt = Instant.now();
        Map<String, String> names = taxonNames(taxa);
        List<ScopedTaxon> scoped = new ArrayList<>();
        Set<String> geneIds = new LinkedHashSet<>();
        for (String taxon : taxa) {
            ScopedTaxon scopedTaxon = search(taxon, names.getOrDefault(taxon, ""));
            scoped.add(scopedTaxon);
            geneIds.addAll(scopedTaxon.geneIds());
            LOG.info("Taxon {} ({}): {} live genes", taxon, scopedTaxon.organismName(), scopedTaxon.count());
        }
        Map<Long, JsonNode> reports = reports(geneIds);
        List<String> withoutReport = geneIds.stream().filter(id -> !reports.containsKey(Long.parseLong(id))).toList();
        if (!withoutReport.isEmpty()) {
            LOG.warn("NCBI Datasets returned no report for {} genes, which the origin lacks: {}", withoutReport.size(),
                    withoutReport);
        }
        OriginManifest manifest = new OriginManifest(OriginManifest.FORMAT, retrievedAt, date, scoped,
                reports.size(), withoutReport, requests);
        String name = out.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")
                ? stripZip(out.getFileName().toString())
                : "ncbi-gene-origin-" + date.format(DateTimeFormatter.BASIC_ISO_DATE);
        try {
            if (out.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                writeZip(out, name, manifest, reports);
            } else {
                writeDirectory(out, manifest, reports);
            }
        } catch (IOException e) {
            throw new ImportException("Cannot write the NCBI Gene origin " + out, e);
        }
        return out;
    }

    private Map<String, String> taxonNames(List<String> taxa) {
        Map<String, String> names = new LinkedHashMap<>();
        JsonNode response = client.get(record(DATASETS_API + "/taxonomy/taxon/" + String.join(",", taxa)));
        for (JsonNode node : response.path("taxonomy_nodes")) {
            JsonNode taxonomy = node.path("taxonomy");
            String taxId = taxonomy.path("tax_id").asText("");
            if (!taxId.isEmpty()) {
                names.put(taxId, taxonomy.path("organism_name").asText(""));
            }
        }
        return names;
    }

    private ScopedTaxon search(String taxon, String organismName) {
        String term = SEARCH_TERM.formatted(taxon);
        String url = EUTILS + "/esearch.fcgi?db=gene&retmode=json&retmax=" + SEARCH_MAX + "&term="
                + URLEncoder.encode(term, StandardCharsets.UTF_8);
        JsonNode result = client.get(record(url)).path("esearchresult");
        long count = result.path("count").asLong(0);
        List<String> ids = new ArrayList<>();
        for (JsonNode id : result.path("idlist")) {
            ids.add(id.asText());
        }
        if (count != ids.size()) {
            throw new ImportException("Entrez reports " + count + " genes for taxon " + taxon + " but listed "
                    + ids.size() + "; raise the search limit");
        }
        return new ScopedTaxon(taxon, organismName, term, count, ids);
    }

    /**
     * The reports by numeric gene ID, so the file is in gene ID order. A gene Datasets has no
     * report for is simply absent; the caller records it.
     */
    private Map<Long, JsonNode> reports(Set<String> geneIds) {
        Map<Long, JsonNode> reports = new TreeMap<>();
        List<String> ids = new ArrayList<>(geneIds);
        for (int from = 0; from < ids.size(); from += BATCH) {
            List<String> batch = ids.subList(from, Math.min(from + BATCH, ids.size()));
            String base = DATASETS_API + "/gene/id/" + String.join(",", batch) + "?page_size=" + PAGE_SIZE;
            String pageToken = "";
            do {
                String url = pageToken.isEmpty() ? base : base + "&page_token=" + URLEncoder.encode(pageToken, StandardCharsets.UTF_8);
                JsonNode response = client.get(record(url));
                for (JsonNode report : response.path("reports")) {
                    String geneId = report.path("gene").path("gene_id").asText("");
                    if (geneId.isEmpty()) {
                        throw new ImportException("A gene report from " + url + " has no gene_id");
                    }
                    reports.put(Long.parseLong(geneId), report);
                }
                pageToken = response.path("next_page_token").asText("");
            } while (!pageToken.isEmpty());
        }
        return reports;
    }

    private String record(String url) {
        requests.add(url);
        return url;
    }

    private static void writeDirectory(Path directory, OriginManifest manifest, Map<Long, JsonNode> reports)
            throws IOException {
        Files.createDirectories(directory);
        try (Writer writer = Files.newBufferedWriter(directory.resolve(NcbiGeneOrigin.REPORTS_FILE), StandardCharsets.UTF_8)) {
            writeReports(writer, reports);
        }
        try (Writer writer = Files.newBufferedWriter(directory.resolve(NcbiGeneOrigin.MANIFEST_FILE), StandardCharsets.UTF_8)) {
            writeManifest(writer, manifest);
        }
    }

    private static void writeZip(Path zip, String name, OriginManifest manifest, Map<Long, JsonNode> reports)
            throws IOException {
        if (zip.getParent() != null) {
            Files.createDirectories(zip.getParent());
        }
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip), StandardCharsets.UTF_8)) {
            out.putNextEntry(new ZipEntry(name + "/" + NcbiGeneOrigin.REPORTS_FILE));
            Writer reportWriter = new BufferedWriter(new OutputStreamWriter(unclosable(out), StandardCharsets.UTF_8));
            writeReports(reportWriter, reports);
            reportWriter.flush();
            out.closeEntry();
            out.putNextEntry(new ZipEntry(name + "/" + NcbiGeneOrigin.MANIFEST_FILE));
            Writer manifestWriter = new BufferedWriter(new OutputStreamWriter(unclosable(out), StandardCharsets.UTF_8));
            writeManifest(manifestWriter, manifest);
            manifestWriter.flush();
            out.closeEntry();
        }
    }

    private static void writeReports(Writer writer, Map<Long, JsonNode> reports) throws IOException {
        for (JsonNode report : reports.values()) {
            writer.write(MAPPER.writeValueAsString(report));
            writer.write('\n');
        }
    }

    private static void writeManifest(Writer writer, OriginManifest manifest) throws IOException {
        writer.write(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(manifest.toJson()));
        writer.write('\n');
    }

    private static OutputStream unclosable(OutputStream out) {
        return new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                out.write(b);
            }

            @Override
            public void write(byte[] bytes, int off, int len) throws IOException {
                out.write(bytes, off, len);
            }

            @Override
            public void flush() throws IOException {
                out.flush();
            }
        };
    }

    private static String stripZip(String fileName) {
        return fileName.substring(0, fileName.length() - ".zip".length());
    }

    /**
     * NCBI over HTTP, paced to {@value #PACE_DESCRIPTION}, retrying a rate-limit or server
     * failure a few times with a growing wait.
     */
    static final class PacedClient implements NcbiClient {

        static final String PACE_DESCRIPTION = "one request per 350 ms";

        private final HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        private Instant lastRequest = Instant.EPOCH;

        @Override
        public JsonNode get(String url) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "application/json")
                    .header("User-Agent", "importer-ncbi-gene (IKE Network; support@ike.network)")
                    .timeout(Duration.ofMinutes(2))
                    .GET()
                    .build();
            ImportException last = null;
            for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
                pace();
                try {
                    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    int status = response.statusCode();
                    if (status == 200) {
                        return MAPPER.readTree(response.body());
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
                sleep(Duration.ofSeconds(2L << attempt));
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

        private static void sleep(Duration duration) {
            try {
                Thread.sleep(duration);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ImportException("Interrupted while pacing NCBI requests", e);
            }
        }
    }
}
