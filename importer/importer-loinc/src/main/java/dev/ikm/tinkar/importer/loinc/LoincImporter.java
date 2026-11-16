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
package dev.ikm.tinkar.importer.loinc;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.InactiveStamp;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.ext.lang.owl.Rf2OwlToLogicAxiomTransformer;
import dev.ikm.tinkar.importer.ImportException;
import dev.ikm.tinkar.importer.ImportReport;
import dev.ikm.tinkar.importer.ImportSource;
import dev.ikm.tinkar.importer.KnowledgeImporter;
import dev.ikm.tinkar.importer.StoreWriter;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import org.eclipse.collections.api.factory.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.ikm.tinkar.importer.loinc.LoincTerms.*;

/**
 * Imports a LOINC release: the parts, the component hierarchy and the LOINC codes. Ported from
 * IKM's {@code loinc-data} transformation plugin; identities are minted as that plugin mints them,
 * so the two agree on which concept is which.
 *
 * <p>Before the release, the importer writes the concepts and patterns LOINC is written in terms
 * of ({@link LoincStarterData}). Then, in the order the plugin works:
 * <ul>
 *   <li>{@code Part.csv}: the parts of the seven part types the codes use (component, property,
 *   time aspect, system, scale, method and class), each a concept with its display name (fully
 *   qualified) and part name, its LOINC part number as an identifier, and a stated parent, its
 *   part type's concept;</li>
 *   <li>{@code ComponentHierarchyBySystem.csv}: components take their hierarchy parents instead
 *   (the root joins Component), and each also defines an "Observable Entity of Component" concept
 *   by an OWL axiom;</li>
 *   <li>{@code Loinc.csv}: each code is a concept with its long common name (fully qualified),
 *   consumer, short, related and display names, its definition, its LOINC number as an identifier,
 *   its definition by its six axes as an OWL axiom, its class and class type, example units, and
 *   test membership (order, observation, both, subset). A deprecated code is inactive; a trial or
 *   discouraged one is a member of the pattern for it.</li>
 * </ul>
 * Finally the OWL axioms are transformed into EL++ stated axioms.
 *
 * <p>A record the release states unusably (a code naming a part the part table lacks, a class
 * type that is not a number) does not stop the import: what it cannot say is left out, counted
 * under the report's details, and logged.
 *
 * <p>Options:
 * <dl>
 *   <dt>{@code releaseDate}</dt><dd>the release's date, {@code yyyy-MM-dd}; by default the date
 *   {@code Loinc.csv} was written, which the release carries.</dd>
 *   <dt>{@code namespace}</dt><dd>the namespace identities are minted in (default IKM's LOINC
 *   pipeline's).</dd>
 *   <dt>{@code path}</dt><dd>the path concept's UUID (default the development path).</dd>
 *   <dt>{@code starterData}</dt><dd>{@code false} to skip writing the concepts and patterns the
 *   codes are written in terms of. They are written as a layer of their own: by the starter data
 *   author, on the Primordial module and path, at the Primordial (pre-inception) time.</dd>
 *   <dt>{@code owlTransform}</dt><dd>{@code false} to leave axioms as syntax, untransformed.</dd>
 * </dl>
 */
public final class LoincImporter implements KnowledgeImporter {

    private static final Logger LOG = LoggerFactory.getLogger(LoincImporter.class);

    /** The part types the codes use; the others (the ones for answers and the like) are left out. */
    private static final Set<String> PART_TYPES = Set.of("COMPONENT", "PROPERTY", "TIME", "SYSTEM", "SCALE", "METHOD", "CLASS");

    @Override
    public String name() {
        return "loinc";
    }

    @Override
    public String description() {
        return "LOINC (Regenstrief's Loinc.csv, Part.csv and ComponentHierarchyBySystem.csv)";
    }

    @Override
    public ImportReport importInto(ImportSource source, StoreWriter writer) {
        source.requireExists();
        Instant start = Instant.now();
        UUID namespace = source.uuidOption("namespace").orElse(LoincTerms.DEFAULT_NAMESPACE);
        LoincTerms terms = new LoincTerms(namespace);
        ConceptFacade path = source.uuidOption("path").<ConceptFacade>map(uuid -> EntityProxy.Concept.make(PublicIds.of(uuid)))
                .orElse(KernelConcept.DEVELOPMENT_PATH);
        Map<String, Long> details = new LinkedHashMap<>();
        StoreWriter.Tally before = writer.counts().tally();

        try (LoincRelease release = LoincRelease.open(source.path())) {
            LocalDate releaseDate = source.option("releaseDate").map(LocalDate::parse).orElseGet(
                    () -> release.lastModified(LoincRelease.LOINC_TABLE).atZone(ZoneOffset.UTC).toLocalDate());
            long time = releaseDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            EntityProxy.Concept author = terms.concept(AUTHOR);
            EntityProxy.Concept module = terms.concept(LOINC_MODULE);
            Stamp stamp = new ActiveStamp(time, author, module, path);
            Stamp retired = new InactiveStamp(time, author, module, path);
            LOG.info("Importing LOINC {} ({}) from {}", release.name(), releaseDate, source.path());

            // The starter data is its own layer: its own author, on the Primordial module and path, at the
            // Primordial (pre-inception) time, so it is told apart from the release and is the same in every release.
            EntityProxy.Concept starterAuthor = terms.concept(STARTER_AUTHOR);
            Stamp starterStamp = new ActiveStamp(PrimitiveData.PRE_INCEPTION_TIME,
                    starterAuthor, KernelConcept.PRIMORDIAL_MODULE, KernelConcept.PRIMORDIAL_PATH);

            Set<Long> starterAxiomConcepts = new HashSet<>();
            Set<Long> axiomConcepts = new HashSet<>();
            writeAuthor(writer, stamp, terms, AUTHOR, "LOINC Author",
                    "Regenstrief Institute, Inc. Author - The entity responsible for publishing LOINC");
            if (source.option("starterData").map(Boolean::parseBoolean).orElse(true)) {
                writeAuthor(writer, starterStamp, terms, STARTER_AUTHOR, "LOINC Starter Data Author",
                        "LOINC Starter Data Author - The entity responsible for the LOINC starter data");
                starterAxiomConcepts.addAll(LoincStarterData.write(writer, starterStamp, terms));
            }

            Run run = new Run(writer, terms, stamp, retired, release, details, axiomConcepts);
            run.readParts();
            run.writeComponents();
            run.writeParts();
            run.writeLeftOverComponents();
            run.writeCodes();

            if (source.option("owlTransform").map(Boolean::parseBoolean).orElse(true)) {
                // Each layer's axioms are transformed under that layer's own author, module and path.
                transform(starterAxiomConcepts, starterAuthor, KernelConcept.PRIMORDIAL_MODULE, KernelConcept.PRIMORDIAL_PATH);
                transform(axiomConcepts, author, module, path);
            }
            return ImportReport.of(name(), release.name(), writer.counts().tally().minus(before), details,
                    Duration.between(start, Instant.now())).withReleaseDate(Instant.ofEpochMilli(time));
        }
    }

    /** Transforms the OWL axioms a layer wrote into EL++ stated axioms, written as that layer. */
    private static void transform(Set<Long> concepts, ConceptFacade author, ConceptFacade module, ConceptFacade path) {
        if (concepts.isEmpty()) {
            return;
        }
        LOG.info("Transforming {} OWL axioms into EL++ stated axioms", author.description());
        try {
            new Rf2OwlToLogicAxiomTransformer(new Transaction("LOINC OWL to logic"),
                    KernelPattern.OWL_AXIOM_SYNTAX_PATTERN, KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN,
                    author.nid(), module.nid(), path.nid(), concepts::contains,
                    owlStamp -> owlStamp.moduleNid() == module.nid() && owlStamp.authorNid() == author.nid()).call();
        } catch (Exception e) {
            throw new ImportException("Transforming LOINC OWL axioms failed", e);
        }
    }

    private static void writeAuthor(StoreWriter writer, Stamp stamp, LoincTerms terms, String name, String synonym,
                                    String definition) {
        PublicId id = terms.id(name);
        long authorNid = writer.concept(stamp, id);
        writer.describe(stamp, authorNid, UuidT5Generator.get(terms.uuid(name), "fqn"), name,
                KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE);
        writer.describe(stamp, authorNid, UuidT5Generator.get(terms.uuid(name), "synonym"), synonym,
                KernelConcept.REGULAR_NAME_DESCRIPTION_TYPE);
        writer.describe(stamp, authorNid, UuidT5Generator.get(terms.uuid(name), "definition"), definition,
                KernelConcept.DEFINITION_DESCRIPTION_TYPE);
        writer.statedParent(stamp, id, KernelConcept.USER.publicId());
    }

    /** One part of Part.csv. */
    private record Part(String number, String type, String name, String displayName) {
    }

    /** One import: the release read and written, with what the steps share. */
    private static final class Run {
        private final StoreWriter writer;
        private final LoincTerms terms;
        private final Stamp stamp;
        private final Stamp retired;
        private final LoincRelease release;
        private final Map<String, Long> details;
        private final Set<Long> axiomConcepts;

        private final List<Part> parts = new ArrayList<>();
        /** Part number by lower-case part name and type: how a code's axes find their parts. */
        private final Map<String, String> partNumbers = new HashMap<>();
        /** The immediate parents of each component code, in the order the hierarchy states them. */
        private final Map<String, Set<String>> componentParents = new HashMap<>();
        /** Components the hierarchy states and the part table has not yet: code to text. */
        private final Map<String, String> componentsAwaitingPart = new LinkedHashMap<>();
        private final Map<String, Long> skipped = new LinkedHashMap<>();

        Run(StoreWriter writer, LoincTerms terms, Stamp stamp, Stamp retired, LoincRelease release,
            Map<String, Long> details, Set<Long> axiomConcepts) {
            this.writer = writer;
            this.terms = terms;
            this.stamp = stamp;
            this.retired = retired;
            this.release = release;
            this.details = details;
            this.axiomConcepts = axiomConcepts;
        }

        private static String partKey(String name, String type) {
            return name.toLowerCase() + "::" + type;
        }

        private void skip(String what, String detail) {
            long count = skipped.merge(what, 1L, Long::sum);
            if (count <= 3) {
                LOG.warn("Leaving out {}: {}", what, detail);
            }
        }

        private void reportSkipped() {
            skipped.forEach((what, count) -> details.put("left out: " + what, count));
        }

        /** Part.csv, read for the parts of the part types the codes use. */
        void readParts() {
            long rows = release.read(LoincRelease.PART_TABLE, row -> {
                String type = row.getOrDefault("PartTypeName", "");
                if (PART_TYPES.contains(type)) {
                    Part part = new Part(row.getOrDefault("PartNumber", ""), type, row.getOrDefault("PartName", ""),
                            row.getOrDefault("PartDisplayName", ""));
                    parts.add(part);
                    partNumbers.put(partKey(part.name(), type), part.number());
                }
            });
            LOG.info("Read {} part rows, {} of the types the codes use", String.format("%,d", rows), String.format("%,d", parts.size()));
            details.put("part rows", rows);
        }

        /** ComponentHierarchyBySystem.csv: each component's parents and its observable entity. */
        void writeComponents() {
            List<String[]> rows = new ArrayList<>();
            release.read(LoincRelease.COMPONENT_HIERARCHY, row -> rows.add(new String[]{
                    row.getOrDefault("IMMEDIATE_PARENT", ""), row.getOrDefault("CODE", ""), row.getOrDefault("CODE_TEXT", "")}));
            if (rows.isEmpty()) {
                return;
            }
            // The first row is the root, "{component}": it joins the Component the starter data wrote.
            String root = rows.getFirst()[1];
            writer.concept(stamp, PublicIds.of(terms.uuid(COMPONENT), terms.uuid(root)));
            for (String[] row : rows.subList(1, rows.size())) {
                componentParents.computeIfAbsent(row[1], code -> new LinkedHashSet<>()).add(row[0]);
            }
            Set<String> written = new HashSet<>();
            Set<String> observableEntities = new HashSet<>();
            long duplicates = 0;
            for (String[] row : rows.subList(1, rows.size())) {
                String code = row[1];
                String text = row[2];
                if (!code.startsWith("LP") || !written.add(code)) {
                    continue;
                }
                PublicId codeId = terms.id(code);
                writer.concept(stamp, codeId);
                List<PublicId> parents = new ArrayList<>();
                for (String parent : componentParents.get(code)) {
                    parents.add(terms.id(parent));
                }
                writer.statedParents(stamp, codeId, parents);
                componentsAwaitingPart.put(code, text);

                String observableEntity = "Observable Entity of Component " + text;
                PublicId entityId = terms.id(observableEntity);
                long entityNid = writer.concept(stamp, entityId);
                writer.describe(stamp, entityNid, UuidT5Generator.get(entityId.leastUuid(), "fqn"), observableEntity,
                        KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE);
                if (!observableEntities.add(observableEntity)) {
                    // Two components of one text name one concept; its axiom is written once.
                    duplicates++;
                    continue;
                }
                axiom(entityNid, entityId.leastUuid(), text, terms.componentAxiom(entityId, codeId));
            }
            details.put("components", (long) written.size());
            if (duplicates != 0) {
                details.put("components sharing a text with another", duplicates);
            }
        }

        /** Part.csv's parts, as concepts. */
        void writeParts() {
            for (Part part : parts) {
                PublicId id = terms.id(part.number());
                UUID uuid = id.leastUuid();
                long nid = writer.concept(stamp, id);
                if (!part.displayName().isEmpty()) {
                    writer.describe(stamp, nid, description(uuid, part.displayName(), "FQN"), part.displayName(),
                            KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE, KernelConcept.PREFERRED);
                }
                if (!part.name().isEmpty()) {
                    writer.describe(stamp, nid, description(uuid, part.name(), "Regular"), part.name(),
                            KernelConcept.REGULAR_NAME_DESCRIPTION_TYPE, KernelConcept.PREFERRED);
                }
                identifier(nid, uuid, part.number());
                // A component the hierarchy states has its parents already; any other part is a kind of its type.
                if (componentsAwaitingPart.remove(part.number()) == null) {
                    PublicId parent = terms.parentForPartType(part.type());
                    if (parent != null) {
                        writer.statedParent(stamp, id, parent);
                    }
                }
            }
            details.put("parts", (long) parts.size());
        }

        /** Components the hierarchy states that the part table does not: concepts of their own. */
        void writeLeftOverComponents() {
            componentsAwaitingPart.forEach((code, text) -> {
                PublicId id = terms.id(code);
                long nid = writer.concept(stamp, id);
                writer.describe(stamp, nid, UuidT5Generator.get(id.leastUuid(), "fqn"), text,
                        KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE);
                identifier(nid, id.leastUuid(), code);
            });
            if (!componentsAwaitingPart.isEmpty()) {
                details.put("components not in the part table", (long) componentsAwaitingPart.size());
            }
        }

        /** Loinc.csv: the codes. */
        void writeCodes() {
            long[] written = {0};
            long rows = release.read(LoincRelease.LOINC_TABLE, row -> {
                String loincNum = row.getOrDefault("LOINC_NUM", "");
                if (loincNum.isEmpty()) {
                    skip("a Loinc.csv row with no LOINC_NUM", row.toString());
                    return;
                }
                writeCode(loincNum, row);
                written[0]++;
            });
            details.put("codes", written[0]);
            reportSkipped();
            LOG.info("Wrote {} of {} LOINC codes", String.format("%,d", written[0]), String.format("%,d", rows));
        }

        private void writeCode(String loincNum, Map<String, String> row) {
            String status = row.getOrDefault("STATUS", "");
            Stamp under = "DEPRECATED".equals(status) ? retired : stamp;
            PublicId id = terms.id(loincNum);
            UUID uuid = id.leastUuid();
            long nid = writer.concept(under, id);

            if ("TRIAL".equals(status)) {
                writer.semantic(under, PublicIds.of(UuidT5Generator.get(terms.namespace(), uuid + loincNum + status)),
                        terms.pattern(LOINC_TRIAL_STATUS_PATTERN), nid, Lists.immutable.empty());
            } else if ("DISCOURAGED".equals(status)) {
                writer.semantic(under, PublicIds.of(UuidT5Generator.get(terms.namespace(), uuid + loincNum + status)),
                        terms.pattern(LOINC_DISCOURAGED_STATUS_PATTERN), nid, Lists.immutable.empty());
            }

            // The long common name is the fully qualified name; one regular name, the first the code has of
            // display, consumer and short name, is the preferred one, and every other is acceptable.
            String displayName = row.getOrDefault("DisplayName", "");
            String consumerName = row.getOrDefault("CONSUMER_NAME", "");
            String shortName = row.getOrDefault("SHORTNAME", "");
            String relatedNames = row.getOrDefault("RELATEDNAMES2", "");
            String preferred = !displayName.isEmpty() ? displayName : !consumerName.isEmpty() ? consumerName : shortName;
            describe(under, nid, uuid, row.getOrDefault("LONG_COMMON_NAME", ""), "FQN",
                    KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE, KernelConcept.PREFERRED);
            for (String name : new String[]{consumerName, shortName, relatedNames, displayName}) {
                describe(under, nid, uuid, name, "Regular", KernelConcept.REGULAR_NAME_DESCRIPTION_TYPE,
                        name.equals(preferred) ? KernelConcept.PREFERRED : ACCEPTABLE);
            }
            describe(under, nid, uuid, row.getOrDefault("DefinitionDescription", ""), "Definition",
                    KernelConcept.DEFINITION_DESCRIPTION_TYPE, KernelConcept.PREFERRED);

            writer.semantic(under, PublicIds.of(UuidT5Generator.get(terms.namespace(), uuid + loincNum)),
                    KernelPattern.IDENTIFIER_PATTERN, nid, Lists.immutable.of(terms.concept(LOINC_NUMBER), loincNum));

            codeAxiom(under, nid, uuid, loincNum, row);
            classOf(under, nid, uuid, row);

            String units = row.getOrDefault("EXAMPLE_UNITS", "");
            if (!units.isEmpty()) {
                writer.semantic(under, PublicIds.of(UuidT5Generator.get(terms.namespace(), uuid + units + "UCUM")),
                        terms.pattern(EXAMPLE_UCUM_UNITS_PATTERN), nid, Lists.immutable.of(units));
            }
            testMembership(under, nid, uuid, row.getOrDefault("ORDER_OBS", ""));
        }

        private static final EntityProxy.Concept ACCEPTABLE =
                EntityProxy.Concept.make(PublicIds.of(UUID.fromString("12b9e103-060e-3256-9982-18c1191af60e")));

        /** The description as IKM mints it: the concept's UUID, the text, the kind of description and "DESC". */
        private UUID description(UUID conceptUuid, String text, String kind) {
            return UuidT5Generator.get(terms.namespace(), conceptUuid + text + kind + "DESC");
        }

        private void describe(Stamp under, long nid, UUID conceptUuid, String text, String kind, ConceptFacade type,
                              ConceptFacade acceptability) {
            if (!text.isEmpty()) {
                writer.describe(under, nid, description(conceptUuid, text, kind), text, type, acceptability);
            }
        }

        private void identifier(long nid, UUID conceptUuid, String value) {
            writer.semantic(stamp, PublicIds.of(UuidT5Generator.get(terms.namespace(), conceptUuid + value)),
                    KernelPattern.IDENTIFIER_PATTERN, nid, Lists.immutable.of(terms.concept(LOINC_NUMBER), value));
        }

        private void axiom(long nid, UUID conceptUuid, String discriminator, String owl) {
            writer.semantic(stamp, PublicIds.of(UuidT5Generator.get(terms.namespace(), conceptUuid + discriminator + "AXIOM")),
                    KernelPattern.OWL_AXIOM_SYNTAX_PATTERN, nid, Lists.immutable.of(owl));
            axiomConcepts.add(nid);
        }

        /** A code is defined by its six axes: the part each names, found by name in the part table. */
        private void codeAxiom(Stamp under, long nid, UUID uuid, String loincNum, Map<String, String> row) {
            String[] columns = {"COMPONENT", "PROPERTY", "TIME_ASPCT", "SYSTEM", "SCALE_TYP", "METHOD_TYP"};
            String[] types = {"COMPONENT", "PROPERTY", "TIME", "SYSTEM", "SCALE", "METHOD"};
            String[] axes = new String[columns.length];
            for (int i = 0; i < columns.length; i++) {
                String name = row.getOrDefault(columns[i], "");
                if (name.isEmpty()) {
                    continue;
                }
                axes[i] = partNumbers.get(partKey(name, types[i]));
                if (axes[i] == null) {
                    skip("the axiom of a code naming a " + types[i] + " the part table lacks", loincNum + " names '" + name + "'");
                    return;
                }
            }
            writer.semantic(under, PublicIds.of(UuidT5Generator.get(terms.namespace(), uuid + row.getOrDefault("COMPONENT", "") + "AXIOM")),
                    KernelPattern.OWL_AXIOM_SYNTAX_PATTERN, nid, Lists.immutable.of(terms.codeAxiom(loincNum, axes)));
            axiomConcepts.add(nid);
        }

        /** The class (a part) and class type (a number) of a code. */
        private void classOf(Stamp under, long nid, UUID uuid, Map<String, String> row) {
            String loincClass = row.getOrDefault("CLASS", "");
            String classNumber = partNumbers.get(partKey(loincClass, "CLASS"));
            int classType;
            try {
                classType = Integer.parseInt(row.getOrDefault("CLASSTYPE", ""));
            } catch (NumberFormatException e) {
                skip("the class of a code whose class type is not a number", row.get("LOINC_NUM") + " has '" + row.get("CLASSTYPE") + "'");
                return;
            }
            if (classNumber == null) {
                skip("the class of a code naming a class the part table lacks", row.get("LOINC_NUM") + " names '" + loincClass + "'");
                return;
            }
            writer.semantic(under, PublicIds.of(UuidT5Generator.get(terms.namespace(), uuid + loincClass)),
                    terms.pattern(LOINC_CLASS_PATTERN), nid, Lists.immutable.of(terms.concept(classNumber), classType));
        }

        /** ORDER_OBS says whether a code is for ordering, for reporting, both, or is a subset of a panel. */
        private void testMembership(Stamp under, long nid, UUID uuid, String orderObs) {
            List<String> patterns = switch (orderObs.toLowerCase()) {
                case "order" -> List.of(TEST_ORDERABLE_MEMBERSHIP_PATTERN);
                case "observation" -> List.of(TEST_REPORTABLE_MEMBERSHIP_PATTERN);
                case "subset" -> List.of(TEST_SUBSET_MEMBERSHIP_PATTERN);
                case "both" -> List.of(TEST_ORDERABLE_MEMBERSHIP_PATTERN, TEST_REPORTABLE_MEMBERSHIP_PATTERN);
                default -> List.of();
            };
            if (patterns.isEmpty() && !orderObs.isEmpty()) {
                skip("the test membership of a code whose ORDER_OBS is unknown", "'" + orderObs + "'");
            }
            for (String pattern : patterns) {
                // The first keeps IKM's identity; a code that is both has a second semantic, named for its pattern.
                String suffix = pattern.equals(patterns.getFirst()) ? "TESTMEM" : "TESTMEM" + pattern;
                writer.semantic(under, PublicIds.of(UuidT5Generator.get(terms.namespace(), uuid + orderObs + suffix)),
                        terms.pattern(pattern), nid, Lists.immutable.empty());
            }
        }
    }
}
