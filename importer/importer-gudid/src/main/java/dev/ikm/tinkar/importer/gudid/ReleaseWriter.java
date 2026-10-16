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
package dev.ikm.tinkar.importer.gudid;

import dev.ikm.tinkar.common.id.LongIds;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.InactiveStamp;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpressionBuilder;
import dev.ikm.tinkar.importer.StoreWriter;
import dev.ikm.tinkar.importer.gudid.GudidRelease.ReleaseFile;
import dev.ikm.tinkar.importer.gudid.GudidTerms.IssuingAgency;
import dev.ikm.tinkar.importer.gudid.GudidTerms.MedicalSpecialty;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.primitive.LongSets;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Writes a GUDID release's files, one after another in IKM's order, each a port of one of IKM's
 * transformers ({@code ikmdev/gudid-data}, {@code gudid-transformation-maven-plugin}).
 *
 * <p>Identities are IKM's wherever IKM named them: every concept (devices, FDA product codes, GMDN
 * terms) and every semantic IKM composed with an explicit identity (product codes' stated axioms,
 * names and codes; devices' OWL axioms, identifiers and premarket submission numbers; the
 * device-to-GMDN semantics), each a type 5 UUID in the import's namespace from the same name. The
 * semantics IKM attached without one were given random identities by its Composer, so no two of
 * IKM's runs agree on them; here they are derived from their concept's UUID and a word naming
 * them ({@code uuid}, {@code fqn}, {@code definition}, {@code public device record key},
 * {@code gmdn code}), as {@link StoreWriter} derives a description's dialect.
 *
 * <p>Where IKM's names had no US dialect, this writer adds one, preferred, as every importer here
 * does. Stamps: a device's versions are under its publish date and record status; everything
 * else is under the release date, active, except a device's GMDN term semantic, inactive when the
 * GMDN code is not active.
 */
final class ReleaseWriter {

    private static final Logger LOG = LoggerFactory.getLogger(ReleaseWriter.class);
    private static final long PROGRESS = 500_000;

    /** The product code a device with no known product code is stated under. */
    static final String UNKNOWN_PRODUCT_CODE = "UNKNOWN";

    private final StoreWriter writer;
    private final GudidRelease release;
    private final SpecialtyFilter filter;
    private final UUID namespace;
    private final ConceptFacade path;
    private final Stamp active;
    private final Stamp inactive;

    private final Map<String, Long> details = new LinkedHashMap<>();
    private final Set<String> devices = new HashSet<>();
    private final Map<String, Boolean> productCodeExists = new HashMap<>();
    private final Map<String, Long> gmdnConcepts = new HashMap<>();
    /** The devices this import wrote OWL axioms for: the ones to transform. */
    final MutableLongSet axiomConcepts = LongSets.mutable.empty();

    /**
     * A writer for one release.
     *
     * @param writer      the store writer every write goes through
     * @param release     the release's files
     * @param filter      which product codes and devices are included
     * @param namespace   the namespace identities are minted in
     * @param path        the path every stamp is on
     * @param releaseTime the release date, epoch milliseconds: the time of every stamp but a device's
     */
    ReleaseWriter(StoreWriter writer, GudidRelease release, SpecialtyFilter filter, UUID namespace,
                  ConceptFacade path, long releaseTime) {
        this.writer = writer;
        this.release = release;
        this.filter = filter;
        this.namespace = namespace;
        this.path = path;
        this.active = new ActiveStamp(releaseTime, GudidTerms.AUTHOR, GudidTerms.MODULE, path);
        this.inactive = new InactiveStamp(releaseTime, GudidTerms.AUTHOR, GudidTerms.MODULE, path);
    }

    /**
     * What was read and written, by file, in the order reported.
     *
     * @return the counts
     */
    Map<String, Long> details() {
        return details;
    }

    /**
     * The FDA product classification file ({@code foiclass.txt}, IKM's {@code FoiClassTransformer}):
     * a concept per product code, stated under its medical specialty, named by the device name,
     * identified by the code; then a catch-all for devices with no known product code.
     */
    void foiClass() {
        long written = 0;
        long skipped = 0;
        try (Stream<String[]> rows = release.rows(ReleaseFile.FOI_CLASS)) {
            for (String[] row : (Iterable<String[]>) rows::iterator) {
                // reviewPanel | medicalSpecialty | productCode | deviceName | ...
                if (row.length < 4) {
                    skipped++;
                    continue;
                }
                if (!filter.includesProductCode(row[1], row[2])) {
                    continue;
                }
                if (row[2].isBlank() || row[3].isBlank()) {
                    skipped++;
                    continue;
                }
                productCode(row[1], row[2], row[3]);
                written++;
            }
        }
        productCode(UNKNOWN_PRODUCT_CODE, UNKNOWN_PRODUCT_CODE, "Unknown FDA Product Code");
        details.put("product codes", written);
        details.put("product code rows skipped", skipped);
        LOG.info("FDA product codes: {} written, {} skipped", written, skipped);
    }

    private void productCode(String specialty, String productCode, String deviceName) {
        UUID uuid = productCodeUuid(productCode);
        PublicId conceptId = PublicIds.of(uuid);
        long nid = writer.concept(active, conceptId);
        uuidIdentifier(active, nid, uuid);
        statedParent(nid, PublicIds.of(UuidT5Generator.get(namespace, uuid + specialty + "AXIOM")),
                MedicalSpecialty.parentFor(specialty).concept);
        writer.describe(active, nid, UuidT5Generator.get(namespace, uuid + deviceName + "FQN"), deviceName,
                KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE);
        writer.semantic(active, PublicIds.of(UuidT5Generator.get(namespace, uuid.toString() + productCode)),
                KernelPattern.IDENTIFIER_PATTERN, nid, Lists.immutable.of(GudidTerms.FDA_PRODUCT_CODE, productCode));
        productCodeExists.put(productCode, Boolean.TRUE);
    }

    /**
     * The devices ({@code device.txt}, IKM's {@code DeviceTransformer}): a concept per primary
     * device identifier, under a stamp at its publish date, active when published; named by brand
     * name and version or model number; identified by its UUID and public device record key.
     */
    void devices() {
        long written = 0;
        long skipped = 0;
        try (Stream<String[]> rows = release.rows(ReleaseFile.DEVICE)) {
            for (String[] row : (Iterable<String[]>) rows::iterator) {
                // primaryDi | publicDeviceRecordKey | publicVersionStatus | deviceRecordStatus | ...
                // devicePublishDate (6) | ... | brandName (9) | versionModelNumber (10) | ...
                if (row.length < 11 || !filter.includesDevice(row[0])) {
                    skipped += row.length < 11 ? 1 : 0;
                    continue;
                }
                Optional<Long> time = publishTime(row[6]);
                if (time.isEmpty()) {
                    skipped++;
                    continue;
                }
                Stamp stamp = "Published".equals(row[3])
                        ? new ActiveStamp(time.get(), GudidTerms.AUTHOR, GudidTerms.MODULE, path)
                        : new InactiveStamp(time.get(), GudidTerms.AUTHOR, GudidTerms.MODULE, path);
                UUID uuid = deviceUuid(row[0]);
                long nid = writer.concept(stamp, PublicIds.of(uuid));
                uuidIdentifier(stamp, nid, uuid);
                writer.describe(stamp, nid, UuidT5Generator.get(uuid, "fqn"), row[9] + " " + row[10],
                        KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE);
                writer.semantic(stamp, PublicIds.of(UuidT5Generator.get(uuid, "public device record key")),
                        KernelPattern.IDENTIFIER_PATTERN, nid,
                        Lists.immutable.of(GudidTerms.PUBLIC_DEVICE_RECORD_KEY, row[1]));
                devices.add(row[0]);
                if (++written % PROGRESS == 0) {
                    LOG.info("devices: {}", written);
                }
            }
        }
        details.put("devices", written);
        details.put("device rows skipped", skipped);
        LOG.info("devices: {} written, {} skipped", written, skipped);
    }

    /**
     * The devices' product codes ({@code productCodes.txt}, IKM's {@code ProductCodeTransformer}):
     * each device stated, as an OWL axiom, a kind of its product codes, or of the unknown product
     * code when it has none the classification file defines.
     */
    void productCodes() {
        Map<String, Set<String>> codesByDevice = new HashMap<>();
        long unknownCodes = 0;
        try (Stream<String[]> rows = release.rows(ReleaseFile.PRODUCT_CODES)) {
            for (String[] row : (Iterable<String[]>) rows::iterator) {
                // primaryDi | productCode | productCodeName
                if (row.length < 3 || !filter.includesDevice(row[0], row[1])) {
                    continue;
                }
                if (row[0].isBlank() || row[1].isBlank() || !productCodeExists(row[1])) {
                    unknownCodes++;
                    continue;
                }
                codesByDevice.computeIfAbsent(row[0], k -> new TreeSet<>()).add(row[1]);
            }
        }
        long unknownDevices = 0;
        for (String device : devices) {
            if (!codesByDevice.containsKey(device)) {
                codesByDevice.put(device, Set.of(UNKNOWN_PRODUCT_CODE));
                unknownDevices++;
            }
        }
        long written = 0;
        long absent = 0;
        for (Map.Entry<String, Set<String>> entry : codesByDevice.entrySet()) {
            UUID deviceUuid = deviceUuid(entry.getKey());
            PublicId deviceId = PublicIds.of(deviceUuid);
            if (!PrimitiveData.get().hasPublicId(deviceId)) {
                absent++;
                continue;
            }
            List<UUID> codes = entry.getValue().stream().map(this::productCodeUuid).toList();
            long deviceNid = EntityService.get().nidForConcept(deviceId);
            writer.semantic(active, PublicIds.of(UuidT5Generator.get(namespace, deviceUuid + "AXIOM")),
                    KernelPattern.OWL_AXIOM_SYNTAX_PATTERN, deviceNid, Lists.immutable.of(owlAxiom(deviceUuid, codes)));
            axiomConcepts.add(deviceNid);
            if (++written % PROGRESS == 0) {
                LOG.info("device axioms: {}", written);
            }
        }
        details.put("device axioms", written);
        details.put("device product code rows with no product code", unknownCodes);
        details.put("devices under the unknown product code", unknownDevices);
        details.put("product code rows for absent devices", absent);
        LOG.info("device axioms: {} written; {} devices under the unknown product code", written, unknownDevices);
    }

    /**
     * The devices' identifiers ({@code identifiers.txt}, IKM's {@code GudidIdentifierTransformer}):
     * each device identifier, of its issuing agency. A row naming an agency IKM's starter data has
     * no concept for is skipped, as IKM's transformer skips it.
     */
    void identifiers() {
        long written = 0;
        long skipped = 0;
        try (Stream<String[]> rows = release.rows(ReleaseFile.IDENTIFIERS)) {
            for (String[] row : (Iterable<String[]>) rows::iterator) {
                // primaryDi | deviceId | deviceIdType | deviceIdIssuingAgency | ...
                if (row.length < 4 || !filter.includesDevice(row[0])) {
                    skipped += row.length < 4 ? 1 : 0;
                    continue;
                }
                Optional<IssuingAgency> agency = IssuingAgency.named(row[3]);
                if (agency.isEmpty() || row[1].isBlank()) {
                    skipped++;
                    continue;
                }
                UUID deviceUuid = deviceUuid(row[0]);
                writer.semantic(active, PublicIds.of(UuidT5Generator.get(namespace, deviceUuid + row[1])),
                        KernelPattern.IDENTIFIER_PATTERN, EntityService.get().nidForConcept(PublicIds.of(deviceUuid)),
                        Lists.immutable.of(agency.get().concept, row[1]));
                if (++written % PROGRESS == 0) {
                    LOG.info("device identifiers: {}", written);
                }
            }
        }
        details.put("device identifiers", written);
        details.put("device identifier rows skipped", skipped);
        LOG.info("device identifiers: {} written, {} skipped", written, skipped);
    }

    /**
     * The devices' premarket submissions ({@code premarketSubmissions.txt}, IKM's
     * {@code PremarketSubmissionTransformer}): each submission number, as an identifier.
     */
    void premarketSubmissions() {
        long written = 0;
        long skipped = 0;
        try (Stream<String[]> rows = release.rows(ReleaseFile.PREMARKET_SUBMISSIONS)) {
            for (String[] row : (Iterable<String[]>) rows::iterator) {
                // primaryDi | submissionNumber | supplementNumber
                if (row.length < 2 || !filter.includesDevice(row[0])) {
                    skipped += row.length < 2 ? 1 : 0;
                    continue;
                }
                if (row[1].isBlank()) {
                    skipped++;
                    continue;
                }
                UUID deviceUuid = deviceUuid(row[0]);
                writer.semantic(active, PublicIds.of(UuidT5Generator.get(namespace, deviceUuid + "PREMARKET" + row[1])),
                        KernelPattern.IDENTIFIER_PATTERN, EntityService.get().nidForConcept(PublicIds.of(deviceUuid)),
                        Lists.immutable.of(GudidTerms.PREMARKET_SUBMISSION_NUMBER, row[1]));
                if (++written % PROGRESS == 0) {
                    LOG.info("premarket submissions: {}", written);
                }
            }
        }
        details.put("premarket submissions", written);
        details.put("premarket submission rows skipped", skipped);
        LOG.info("premarket submissions: {} written, {} skipped", written, skipped);
    }

    /**
     * The devices' GMDN terms ({@code gmdnTerms.txt}, IKM's {@code GmdnTermsTransformer}): a concept
     * per GMDN code, from the first row that names it, stated under the GMDN terms concept, named
     * and defined by the preferred term, identified by its UUID and code; and for each row, the
     * device's GMDN term semantic, inactive when the code is not active.
     */
    void gmdnTerms() {
        long written = 0;
        long skipped = 0;
        try (Stream<String[]> rows = release.rows(ReleaseFile.GMDN_TERMS)) {
            for (String[] row : (Iterable<String[]>) rows::iterator) {
                // primaryDi | gmdnPTName | gmdnPTDefinition | gmdnCode | gmdnCodeStatus | ...
                if (row.length < 5 || !filter.includesDevice(row[0])) {
                    skipped += row.length < 5 ? 1 : 0;
                    continue;
                }
                String code = row[3];
                if (code.isBlank()) {
                    skipped++;
                    continue;
                }
                long gmdnNid = gmdnConcepts.computeIfAbsent(code, c -> gmdnConcept(c, row[1], row[2]));
                String primaryDi = row[0];
                writer.semantic("Active".equals(row[4]) ? active : inactive,
                        PublicIds.of(UuidT5Generator.get(namespace, primaryDi + "GMDN_" + code + "_DEVICE_" + primaryDi)),
                        GudidTerms.GMDN_TERMS_PATTERN, EntityService.get().nidForConcept(PublicIds.of(deviceUuid(primaryDi))),
                        Lists.immutable.of(LongIds.set.of(gmdnNid)));
                if (++written % PROGRESS == 0) {
                    LOG.info("device GMDN terms: {}", written);
                }
            }
        }
        details.put("GMDN terms", (long) gmdnConcepts.size());
        details.put("device GMDN terms", written);
        details.put("GMDN rows skipped", skipped);
        LOG.info("GMDN terms: {}; device GMDN terms: {} written, {} skipped", gmdnConcepts.size(), written, skipped);
    }

    private long gmdnConcept(String code, String name, String definition) {
        UUID uuid = UuidT5Generator.get(namespace, "GMDN_" + code);
        PublicId conceptId = PublicIds.of(uuid);
        long nid = writer.concept(active, conceptId);
        uuidIdentifier(active, nid, uuid);
        if (!name.isBlank()) {
            writer.describe(active, nid, UuidT5Generator.get(uuid, "fqn"), name,
                    KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE);
        }
        if (!definition.isBlank()) {
            writer.describe(active, nid, UuidT5Generator.get(uuid, "definition"), definition,
                    KernelConcept.DEFINITION_DESCRIPTION_TYPE);
        }
        writer.semantic(active, PublicIds.of(UuidT5Generator.get(uuid, "gmdn code")), KernelPattern.IDENTIFIER_PATTERN,
                nid, Lists.immutable.of(GudidTerms.GMDN_TERMS, code));
        writer.statedParent(active, conceptId, GudidTerms.GMDN_TERMS.publicId());
        return nid;
    }

    /**
     * A device's OWL axiom, as IKM's {@code GudidUtility.buildOwlExpression} writes it but with
     * its spacing regular: the device a subclass of its one product code, or of their intersection.
     *
     * @param device the device's UUID
     * @param codes  its product codes' UUIDs, in a stable order
     * @return the axiom
     */
    static String owlAxiom(UUID device, List<UUID> codes) {
        StringBuilder axiom = new StringBuilder("SubClassOf(:[").append(device).append("] ");
        if (codes.size() == 1) {
            axiom.append(":[").append(codes.getFirst()).append(']');
        } else {
            axiom.append("ObjectIntersectionOf(");
            for (int i = 0; i < codes.size(); i++) {
                axiom.append(i == 0 ? "" : " ").append(":[").append(codes.get(i)).append(']');
            }
            axiom.append(')');
        }
        return axiom.append(')').toString();
    }

    private void statedParent(long conceptNid, PublicId axiomId, EntityProxy.Concept parent) {
        UUID axiomUuid = axiomId.leastUuid();
        int[] ordinal = {0};
        LogicalExpressionBuilder builder = new LogicalExpressionBuilder(
                UuidT5Generator.get(axiomUuid, "definition root"),
                () -> UuidT5Generator.get(axiomUuid, "axiom vertex " + ordinal[0]++));
        builder.NecessarySet(builder.And(builder.ConceptAxiom(parent)));
        DiTreeEntity tree = (DiTreeEntity) builder.build().sourceGraph();
        writer.semantic(active, axiomId, KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN, conceptNid,
                Lists.immutable.of(tree));
    }

    private void uuidIdentifier(Stamp stamp, long conceptNid, UUID conceptUuid) {
        writer.semantic(stamp, PublicIds.of(UuidT5Generator.get(conceptUuid, "uuid")), KernelPattern.IDENTIFIER_PATTERN,
                conceptNid, Lists.immutable.of(GudidTerms.UUID_IDENTIFIER, conceptUuid.toString()));
    }

    private boolean productCodeExists(String productCode) {
        return productCodeExists.computeIfAbsent(productCode,
                code -> PrimitiveData.get().hasPublicId(PublicIds.of(productCodeUuid(code))));
    }

    private UUID productCodeUuid(String productCode) {
        return UuidT5Generator.get(namespace, "FDA_PRODUCT_CODE_" + productCode);
    }

    private UUID deviceUuid(String primaryDi) {
        return UuidT5Generator.get(namespace, primaryDi);
    }

    private static Optional<Long> publishTime(String date) {
        try {
            return Optional.of(LocalDate.parse(date.strip()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
        } catch (DateTimeParseException e) {
            LOG.warn("Skipping a device with an unreadable publish date: {}", date);
            return Optional.empty();
        }
    }
}
