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
package dev.ikm.tinkar.importer.elk;

import dev.ikm.tinkar.importer.ImportException;
import dev.ikm.tinkar.importer.ImportReport;
import dev.ikm.tinkar.importer.ImportSource;
import dev.ikm.tinkar.importer.KnowledgeImporter;
import dev.ikm.tinkar.importer.StoreWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Lifts the ELK reasoner's test corpus into the reasoner test knowledge base: each classification
 * case under {@code test_input} — an {@code .owl} input with its expected {@code .taxonomy} or
 * {@code .propertytaxonomy} — becomes one module holding the case's concepts, their stated
 * definitions, and the inferred navigation and equivalence semantics classification is expected to
 * produce for them. {@link ElkCase} says what is read and what puts a case out of scope;
 * {@link CaseWriter} what is written.
 *
 * <p>The source is the corpus: a directory holding {@code test_input} (or that directory itself),
 * or a jar or zip with {@code test_input} at its root, such as {@code elk-reasoner}'s test jar.
 * The lift is meant to be run once: its delta becomes the knowledge base's baseline, which is
 * edited in Komet from then on.
 *
 * <p>Options:
 * <dl>
 *   <dt>{@code namespace}</dt><dd>the namespace identities are minted in (default the reasoner test
 *   knowledge base's, {@value CaseWriter#DEFAULT_NAMESPACE}).</dd>
 *   <dt>{@code date}</dt><dd>the date every version is written at, {@code yyyy-MM-dd} (default
 *   {@value #DEFAULT_DATE}, the date the corpus was first lifted).</dd>
 * </dl>
 */
public final class ElkTestCaseImporter implements KnowledgeImporter {

    private static final Logger LOG = LoggerFactory.getLogger(ElkTestCaseImporter.class);

    /** The date the corpus was first lifted, which every version is written at by default. */
    public static final String DEFAULT_DATE = "2026-10-16";

    @Override
    public String name() {
        return "elk-test-cases";
    }

    @Override
    public String description() {
        return "The ELK reasoner's OWL classification test cases, lifted into stated logical expressions"
                + " and expected inferred semantics, one module per case";
    }

    @Override
    public ImportReport importInto(ImportSource source, StoreWriter writer) {
        source.requireExists();
        Instant start = Instant.now();
        UUID namespace = source.uuidOption("namespace").orElse(UUID.fromString(CaseWriter.DEFAULT_NAMESPACE));
        long time = LocalDate.parse(source.option("date").orElse(DEFAULT_DATE))
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        StoreWriter.Tally before = writer.counts().tally();
        CaseWriter caseWriter = new CaseWriter(writer, namespace, time);

        Map<String, Long> details = new LinkedHashMap<>();
        Map<String, List<String>> outOfScope = new TreeMap<>();
        long lifted = 0;
        long withoutExpectations = 0;
        for (CaseFiles files : read(source.path())) {
            if (files.taxonomy().isEmpty() && files.propertyTaxonomy().isEmpty()) {
                // Class-query and entailment expectations are not lifted yet.
                withoutExpectations++;
                continue;
            }
            try {
                ElkCase elkCase = ElkCase.read(files.id(), files.owl(), files.taxonomy(), files.propertyTaxonomy());
                caseWriter.write(elkCase);
                lifted++;
            } catch (ElkCase.OutOfScope e) {
                outOfScope.computeIfAbsent(e.construct(), _ -> new ArrayList<>()).add(files.id());
            }
        }
        details.put("cases lifted", lifted);
        details.put("cases without classification expectations", withoutExpectations);
        outOfScope.forEach((construct, cases) -> {
            details.put("cases out of scope: " + construct, (long) cases.size());
            LOG.info("Out of scope ({}): {}", construct, cases);
        });
        return ImportReport.of(name(), source.path().getFileName().toString(),
                writer.counts().tally().minus(before), details, Duration.between(start, Instant.now()));
    }

    /**
     * The files of one case.
     *
     * @param id               the case's path below {@code test_input}, without its extension
     * @param owl              its input
     * @param taxonomy         its expected class taxonomy, if any
     * @param propertyTaxonomy its expected object property taxonomy, if any
     */
    record CaseFiles(String id, String owl, Optional<String> taxonomy, Optional<String> propertyTaxonomy) {
    }

    /**
     * Reads every case of a corpus, in path order.
     *
     * @param corpus a directory holding {@code test_input} or that directory, or a jar or zip
     * @return the cases
     */
    static List<CaseFiles> read(Path corpus) {
        try {
            if (Files.isDirectory(corpus)) {
                Path root = Files.isDirectory(corpus.resolve("test_input")) ? corpus.resolve("test_input") : corpus;
                return readTree(root);
            }
            try (FileSystem jar = FileSystems.newFileSystem(corpus)) {
                Path root = jar.getPath("/test_input");
                if (!Files.isDirectory(root)) {
                    throw new ImportException(corpus + " has no test_input directory");
                }
                return readTree(root);
            }
        } catch (IOException e) {
            throw new ImportException("Reading the ELK test corpus " + corpus + " failed", e);
        }
    }

    private static List<CaseFiles> readTree(Path root) throws IOException {
        List<Path> inputs;
        try (Stream<Path> paths = Files.walk(root)) {
            inputs = paths.filter(path -> path.getFileName() != null && path.getFileName().toString().endsWith(".owl"))
                    .sorted((a, b) -> root.relativize(a).toString().compareTo(root.relativize(b).toString()))
                    .toList();
        }
        List<CaseFiles> cases = new ArrayList<>();
        for (Path input : inputs) {
            String relative = root.relativize(input).toString().replace('\\', '/');
            String id = relative.substring(0, relative.length() - ".owl".length());
            String base = input.getFileName().toString();
            base = base.substring(0, base.length() - ".owl".length());
            cases.add(new CaseFiles(id, Files.readString(input, StandardCharsets.UTF_8),
                    sibling(input, base + ".taxonomy"), sibling(input, base + ".propertytaxonomy")));
        }
        return cases;
    }

    private static Optional<String> sibling(Path input, String name) throws IOException {
        Path sibling = input.resolveSibling(name);
        return Files.isRegularFile(sibling) ? Optional.of(Files.readString(sibling, StandardCharsets.UTF_8))
                : Optional.empty();
    }
}
