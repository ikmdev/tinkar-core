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

import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.importer.ImportException;
import dev.ikm.tinkar.importer.StoreWriter;
import dev.ikm.tinkar.importer.gsrs.GsrsTerms.Shared;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import org.eclipse.collections.api.factory.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Writes GSRS substances into the store, one substance at a time, through the {@link StoreWriter}.
 *
 * <p>Before the first substance, the importer's own concepts ({@link Shared}): its module under
 * the starter set's Module, its author under User, its root under the root vertex, Substance under
 * the root, a concept per substance class under Substance, and the UNII and GSRS UUID identifier
 * sources under Identifier Source. Each is named by a fully qualified and a regular name.
 *
 * <p>Per substance: a concept, named in full by the name GSRS shows it by with the semantic tag
 * {@code (GSRS substance)} and by that name alone (preferred); identified by its GSRS UUID and,
 * when it has one, its UNII; and stated a kind of its class. Its other names, codes, structure
 * and relationships are not written yet.
 *
 * <p>Every version is written under one stamp: the release date, GSRS's author and module, the
 * import's path.
 */
final class SubstanceWriter {

    private static final Logger LOG = LoggerFactory.getLogger(SubstanceWriter.class);

    private final StoreWriter writer;
    private final UUID namespace;
    private final Stamp stamp;
    private final Map<String, Long> byClass = new TreeMap<>();
    private long substances;
    private long uniiIdentifiers;
    private long unnamed;
    private boolean sharedWritten;

    /**
     * A writer for one import.
     *
     * @param writer      the store writer every write goes through
     * @param namespace   the namespace identities are minted in
     * @param path        the path every stamp is on
     * @param releaseTime the release date, epoch milliseconds: the time of every stamp
     */
    SubstanceWriter(StoreWriter writer, UUID namespace, ConceptFacade path, long releaseTime) {
        this.writer = writer;
        this.namespace = namespace;
        this.stamp = new ActiveStamp(releaseTime, Shared.AUTHOR.concept(namespace), Shared.MODULE.concept(namespace), path);
    }

    /**
     * What was written, in the order reported.
     *
     * @return the counts
     */
    Map<String, Long> details() {
        Map<String, Long> details = new LinkedHashMap<>();
        details.put("shared concepts", (long) Shared.values().length);
        details.put("substances", substances);
        byClass.forEach((substanceClass, count) -> details.put("substances: " + substanceClass, count));
        details.put("UNII identifiers", uniiIdentifiers);
        details.put("substances named by UNII or UUID", unnamed);
        return details;
    }

    /**
     * Writes the importer's own concepts, once.
     */
    void shared() {
        if (sharedWritten) {
            return;
        }
        sharedWritten = true;
        for (Shared shared : Shared.values()) {
            UUID uuid = shared.uuid(namespace);
            long nid = writer.concept(stamp, PublicIds.of(uuid));
            describe(nid, uuid, shared.fullyQualifiedName(), shared.regularName());
            writer.statedParent(stamp, PublicIds.of(uuid), shared.parent(namespace));
        }
    }

    /**
     * Writes a primary substance.
     *
     * @param substance the substance
     * @throws ImportException when the substance is an alternative definition, or of a class this
     *                         importer does not know
     */
    void substance(Substance substance) {
        if (!substance.primary()) {
            throw new ImportException("GSRS record " + substance.uuid() + " is an alternative definition, not a substance");
        }
        Shared substanceClass = Shared.ofSubstanceClass(substance.substanceClass()).orElseThrow(() ->
                new ImportException("GSRS record " + substance.uuid() + " is of class " + substance.substanceClass()
                        + ", which this importer does not know"));
        shared();
        UUID uuid = GsrsTerms.substanceUuid(namespace, substance.uuid());
        long nid = writer.concept(stamp, PublicIds.of(uuid));
        String name = substance.name();
        describe(nid, uuid, name + " (GSRS substance)", name);
        writer.semantic(stamp, PublicIds.of(UuidT5Generator.get(uuid, "gsrs uuid")), KernelPattern.IDENTIFIER_PATTERN,
                nid, Lists.immutable.of(Shared.GSRS_UUID.concept(namespace), substance.uuid().toString()));
        if (substance.unii().isPresent()) {
            writer.semantic(stamp, PublicIds.of(UuidT5Generator.get(uuid, "unii")), KernelPattern.IDENTIFIER_PATTERN,
                    nid, Lists.immutable.of(Shared.UNII.concept(namespace), substance.unii().get()));
            uniiIdentifiers++;
        }
        writer.statedParent(stamp, PublicIds.of(uuid), PublicIds.of(substanceClass.uuid(namespace)));
        byClass.merge(substance.substanceClass(), 1L, Long::sum);
        if (!substance.named()) {
            unnamed++;
        }
        if (++substances % 10_000 == 0) {
            LOG.info("substances: {}", substances);
        }
    }

    private void describe(long nid, UUID uuid, String fullyQualifiedName, String regularName) {
        writer.describe(stamp, nid, UuidT5Generator.get(uuid, "fqn"), fullyQualifiedName,
                KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE);
        writer.describe(stamp, nid, UuidT5Generator.get(uuid, "name"), regularName,
                KernelConcept.REGULAR_NAME_DESCRIPTION_TYPE);
    }
}
