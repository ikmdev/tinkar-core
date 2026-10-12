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
package dev.ikm.tinkar.importer.snomed;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelTerm;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SNOMED CT identity: the public id of an SCTID, the concepts and patterns RF2's metadata codes
 * stand for, and OWL axiom text with its SCTIDs turned into public ids.
 *
 * <p>An SCTID's public id is {@link UuidUtil#fromSNOMED}, the same identity IKM's pipeline, the
 * earlier SOLOR knowledge bases, and the kernel use. The few metadata concepts the kernel already
 * defines under its own identity (description types, case significance, acceptability) resolve to
 * the kernel concept, so SNOMED's rows for them land on it rather than beside it; IKM's pipeline
 * reached the same end by adding SNOMED's UUIDs to the kernel concepts in a starter-data step.
 */
final class SnomedIds {

    /** SNOMED's identifier scheme concept for SCTIDs, as the kernel defines it. */
    static final String SNOMED_CT_IDENTIFIER = "900000000000294009";
    /** The OWL axiom reference set: rows hold one axiom each. */
    static final String OWL_AXIOM_REFSET = "733073007";

    private static final PublicId ACCEPTABLE = PublicIds.of(UUID.fromString("12b9e103-060e-3256-9982-18c1191af60e"));

    private static final Map<String, PublicId> KERNEL = Map.ofEntries(
            Map.entry("900000000000446008", KernelTerm.DESCRIPTION_TYPE.publicId()),
            Map.entry("900000000000003001", KernelTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.publicId()),
            Map.entry("900000000000013009", KernelTerm.REGULAR_NAME_DESCRIPTION_TYPE.publicId()),
            Map.entry("900000000000550004", KernelTerm.DEFINITION_DESCRIPTION_TYPE.publicId()),
            Map.entry("900000000000447004", KernelTerm.DESCRIPTION_CASE_SIGNIFICANCE.publicId()),
            Map.entry("900000000000448009", KernelTerm.DESCRIPTION_NOT_CASE_SENSITIVE.publicId()),
            Map.entry("900000000000017005", KernelTerm.DESCRIPTION_CASE_SENSITIVE.publicId()),
            Map.entry("900000000000020002", KernelTerm.DESCRIPTION_INITIAL_CHARACTER_CASE_SENSITIVE.publicId()),
            Map.entry("900000000000511003", KernelTerm.DESCRIPTION_ACCEPTABILITY.publicId()),
            Map.entry("900000000000548007", KernelTerm.PREFERRED.publicId()),
            Map.entry("900000000000549004", ACCEPTABLE),
            Map.entry(SNOMED_CT_IDENTIFIER, KernelTerm.SCTID.publicId()));

    private static final Map<String, EntityProxy.Pattern> DIALECTS = Map.of(
            "900000000000509007", KernelTerm.US_DIALECT_PATTERN,
            "900000000000508004", KernelTerm.GB_DIALECT_PATTERN);

    private static final Map<String, EntityProxy.Concept> LANGUAGES = Map.of(
            "en", KernelTerm.ENGLISH_LANGUAGE,
            "es", KernelTerm.SPANISH_LANGUAGE);

    private static final Pattern SCTID_IN_OWL = Pattern.compile("(?<=:)([0-9]+)");
    private static final Pattern IRI_IN_OWL = Pattern.compile("<[^>]+>");

    private final UUID namespace;

    /**
     * Identity in a namespace.
     *
     * @param namespace the namespace SCTIDs are minted in; SNOMED's own for every SNOMED release
     */
    SnomedIds(UUID namespace) {
        this.namespace = namespace;
    }

    UUID namespace() {
        return namespace;
    }

    /**
     * The public id of the component an SCTID names.
     *
     * @param sctid the SCTID
     * @return its public id
     */
    PublicId publicId(String sctid) {
        PublicId kernel = KERNEL.get(sctid);
        return kernel != null ? kernel : PublicIds.of(uuid(sctid));
    }

    UUID uuid(String sctid) {
        PublicId kernel = KERNEL.get(sctid);
        return kernel != null ? kernel.leastUuid() : UuidT5Generator.get(namespace, sctid);
    }

    EntityProxy.Concept concept(String sctid) {
        return EntityProxy.Concept.make(publicId(sctid));
    }

    /**
     * The dialect pattern a language reference set is recorded in.
     *
     * @param refsetId the language reference set's SCTID
     * @return the pattern, if the kernel has one for it
     */
    static Optional<EntityProxy.Pattern> dialect(String refsetId) {
        return Optional.ofNullable(DIALECTS.get(refsetId));
    }

    /**
     * The language concept for an RF2 language code.
     *
     * @param code the code, {@code en} or {@code es}
     * @return the concept, if the kernel has one for it
     */
    static Optional<EntityProxy.Concept> language(String code) {
        return Optional.ofNullable(LANGUAGES.get(code));
    }

    /**
     * An OWL axiom with its identifiers turned into public ids, the form the OWL-to-logic
     * transform reads: each {@code :SCTID} becomes {@code :[uuid]}, each full IRI becomes
     * {@code <[uuid]>} minted from the IRI in the namespace.
     *
     * @param owl the axiom as RF2 carries it
     * @return the axiom with public ids
     */
    String owlWithPublicIds(String owl) {
        Matcher iris = IRI_IN_OWL.matcher(owl);
        String withIris = iris.replaceAll(m -> Matcher.quoteReplacement(
                "<[" + UuidT5Generator.get(namespace, m.group().substring(1, m.group().length() - 1)) + "]>"));
        Matcher ids = SCTID_IN_OWL.matcher(withIris);
        return ids.replaceAll(m -> Matcher.quoteReplacement("[" + uuid(m.group()) + "]"));
    }
}
