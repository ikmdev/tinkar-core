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
package dev.ikm.tinkar.terms;

import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;

import java.util.UUID;

/**
 * Java bindings for the default-value and template content seam
 * (IKE-Network/ike-issues#886): the three concepts that anchor default value
 * semantics and template semantics as ordinary working-path content.
 *
 * <ul>
 *   <li>A <em>default value semantic</em> of pattern P is a semantic of P whose
 *       referenced component is {@link #DEFAULT_VALUE_CONCEPT}.</li>
 *   <li>A <em>template semantic</em> of P for a purpose is a semantic of P whose
 *       referenced component is that purpose concept — a child of
 *       {@link #TEMPLATE_CONCEPT}.</li>
 *   <li>Every version of a defaults/template chronology is in
 *       {@link #DEFAULTS_AND_TEMPLATES_MODULE}, and that module holds only
 *       defaults/template content (a bidirectional live-and-die invariant). The
 *       module is the category boundary the stamp calculator uses to exclude
 *       defaults/template semantics from version-iteration operations.</li>
 * </ul>
 *
 * <p>Semantic identity is the stock convention:
 * {@link UuidT5Generator#singleSemanticUuid(dev.ikm.tinkar.common.id.PublicId,
 * dev.ikm.tinkar.common.id.PublicId)} of the pattern's public id and the
 * attachment concept's public id — see
 * {@code StampCalculator.getDefault(PatternFacade)} and
 * {@code StampCalculator.getTemplate(ConceptFacade)}.
 *
 * <h2>Home</h2>
 * TinkarTerm-style bindings live in this package ({@code dev.ikm.tinkar.terms},
 * the {@code terms} module) — see {@link TinkarTerm}. This class is deliberately
 * separate and small: these are not SOLOR starter-data bindings but the
 * declared-identity seam for concepts the IkeFoundation knowledge set
 * ({@code ike-starter-set}'s {@code ike-terms} ledger) mints. tinkar-core
 * declares the identities; {@code ike-starter-set} mints content against them.
 *
 * <h2>Identity derivation</h2>
 * The IkeFoundation ledger mints concept identities from their birth fully
 * qualified name: {@code KnowledgeSet.uuidFor(birthFqn)}, which is
 * {@code UuidT5Generator.get(setUuid, birthFqn)} with the set's UUID as the
 * RFC-4122 type-5 namespace. Each constant below uses exactly that derivation,
 * against the IkeFoundation set UUID ({@link #IKE_FOUNDATION_NAMESPACE}), so the
 * bound identity equals the identity the ledger mints for the same birth FQN.
 * Each constant's initializer is a single expression, so swapping one identity
 * (should a birth FQN be renamed before integration) is a one-line change.
 *
 * <p><strong>Sub-decision record (KEC, 2026-07-17):</strong> the defaults
 * attachment concept is freshly <em>minted</em> — adopting the pre-existing base
 * "Default Data Concept" ({@code 4a32d2ad-baca-42b5-a432-4c4ae6431668}) was
 * considered and not taken. The module concept's birth FQN is decided verbatim
 * ("Defaults and templates module (IkeFoundation)", regular-name synonym
 * "Defaults"); the other two FQNs are KEC-approved working titles.
 */
public final class DefaultsTemplateTerm {

    /**
     * The IkeFoundation knowledge set's UUID — the RFC-4122 type-5 namespace from
     * which every identity in that set derives ({@code KnowledgeSet.uuidFor}).
     * Declared in {@code ike-starter-set}'s {@code ike-terms} ledger
     * ({@code network.ike.foundation.ike.terms.Ike#SET}); restated here so the
     * seam constants below equal the identities that ledger mints.
     */
    public static final UUID IKE_FOUNDATION_NAMESPACE =
            UUID.fromString("d890e06f-ec35-429a-b541-d0ead19695e2");

    /**
     * The defaults attachment concept: the referenced component of every default
     * value semantic. A default value semantic of pattern P has identity
     * {@code UuidT5Generator.singleSemanticUuid(P.publicId(), DEFAULT_VALUE_CONCEPT.publicId())}.
     * <p>
     * Identity is the birth-FQN-derived IkeFoundation mint
     * {@code T5(IKE_FOUNDATION_NAMESPACE, "Default value concept (IkeFoundation)")}
     * = {@code b3af49c5-9e8a-5b94-94d4-b1e2b72e1c2f}.
     */
    public static final EntityProxy.Concept DEFAULT_VALUE_CONCEPT = EntityProxy.Concept.make(
            "Default value concept (IkeFoundation)",
            UuidT5Generator.get(IKE_FOUNDATION_NAMESPACE, "Default value concept (IkeFoundation)"));

    /**
     * The template concept parent: template purpose concepts are its children. A
     * template semantic of pattern P for a purpose concept has identity
     * {@code UuidT5Generator.singleSemanticUuid(P.publicId(), purpose.publicId())}.
     * <p>
     * Identity is the birth-FQN-derived IkeFoundation mint
     * {@code T5(IKE_FOUNDATION_NAMESPACE, "Template concept (IkeFoundation)")}
     * = {@code 663fb8eb-8035-5e22-b423-9fca85a3f11c}.
     */
    public static final EntityProxy.Concept TEMPLATE_CONCEPT = EntityProxy.Concept.make(
            "Template concept (IkeFoundation)",
            UuidT5Generator.get(IKE_FOUNDATION_NAMESPACE, "Template concept (IkeFoundation)"));

    /**
     * The defaults/templates module concept: the category boundary. Every version
     * of a defaults/template chronology carries this module in its stamp, and this
     * module holds only defaults/template content. The stamp calculator excludes
     * versions in this module from version-iteration operations; the dedicated
     * accessors ({@code getDefault}/{@code getTemplate}) include it.
     * <p>
     * Identity is the birth-FQN-derived IkeFoundation mint
     * {@code T5(IKE_FOUNDATION_NAMESPACE, "Defaults and templates module (IkeFoundation)")}
     * = {@code 8d013392-1db6-58cd-a1cb-74a1e603399a}.
     */
    public static final EntityProxy.Concept DEFAULTS_AND_TEMPLATES_MODULE = EntityProxy.Concept.make(
            "Defaults and templates module (IkeFoundation)",
            UuidT5Generator.get(IKE_FOUNDATION_NAMESPACE, "Defaults and templates module (IkeFoundation)"));

    /**
     * Bindings are collections of static members; instantiation is meaningless.
     */
    private DefaultsTemplateTerm() {
    }
}
