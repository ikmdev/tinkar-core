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
package dev.ikm.tinkar.entity.builder;

import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.terms.EntityProxy;

import java.util.UUID;

/**
 * The identity root of a ledger-authored content set. A namespace turns a concept's
 * <em>fully qualified name at birth</em> into its {@link dev.ikm.tinkar.common.id.PublicId}:
 * identity is {@code T5(namespace, birthFqn)}, so identity cannot be minted without a
 * meaningful name, and one UUID literal per namespace replaces one per concept.
 * <p>
 * The birth FQN is the identity seed <em>only</em>: the FQN description it creates may be
 * revised later without changing identity (the append-only ledger permanently records the
 * seed at its first declaration). Two rules keep the derivation safe, both enforced by the
 * release verifier rather than this class: a birth FQN is never reused within a namespace,
 * even after retirement; and a released declaration's birth FQN is never edited.
 *
 * @param uuid the namespace UUID — the only identity literal a content module declares
 */
public record Namespace(UUID uuid) {

    /**
     * Creates a namespace from a UUID literal.
     *
     * @param uuidString the namespace UUID in canonical string form
     * @return the namespace
     * @throws IllegalArgumentException if {@code uuidString} is not a valid UUID
     */
    public static Namespace of(String uuidString) {
        return new Namespace(UUID.fromString(uuidString));
    }

    /**
     * Opens a concept declaration whose identity is derived from its fully qualified name
     * at birth. The returned builder's first {@link ConceptBuilder#at(ActiveStamp)} scope
     * is the birth scope: it writes the concept's first version and its fully qualified
     * name description.
     *
     * @param fullyQualifiedName the concept's fully qualified name at birth — both the
     *                           identity seed and the FQN description's initial text
     * @return a builder for the concept's version ledger
     * @throws IllegalArgumentException if {@code fullyQualifiedName} is null or blank
     */
    public ConceptBuilder concept(String fullyQualifiedName) {
        if (fullyQualifiedName == null || fullyQualifiedName.isBlank()) {
            throw new IllegalArgumentException("A concept declaration requires a meaningful fully qualified name");
        }
        return new ConceptBuilder(this, fullyQualifiedName);
    }

    /**
     * Resolves the identity a birth FQN derives in this namespace, as a reference handle —
     * for citing a concept (for example in a stated axiom) without building it here.
     *
     * @param birthFqn the concept's fully qualified name at birth
     * @return a concept proxy carrying the derived identity and the given name as its description
     */
    public EntityProxy.Concept conceptRef(String birthFqn) {
        return EntityProxy.Concept.make(birthFqn, PublicIds.of(uuidFor(birthFqn)));
    }

    /**
     * Derives the type-5 UUID this namespace assigns to a name.
     *
     * @param name the identity seed — for concepts, the fully qualified name at birth
     * @return {@code T5(namespace, name)}
     */
    public UUID uuidFor(String name) {
        return UuidT5Generator.get(uuid, name);
    }
}
