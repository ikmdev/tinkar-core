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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The identity root and ledger session of a content set. A namespace turns a concept's
 * <em>fully qualified name at birth</em> into its {@link dev.ikm.tinkar.common.id.PublicId}:
 * identity is {@code T5(namespace, birthFqn)}, so identity cannot be minted without a
 * meaningful name, and one UUID literal per namespace replaces one per concept.
 *
 * <h2>The session registry — builders resume</h2>
 * A namespace keeps every builder it has opened, keyed by birth FQN:
 * {@link #concept(String)} and {@link #pattern(String)} return the <em>same</em> builder
 * on every call with the same FQN, so a ledger can pull a component back up at any point
 * — typically under a later stamp — and continue without restating anything. The source
 * therefore reads time-major, as a true ledger: declare a stamp inline, make the edits
 * under it across any components, declare the next stamp, continue. (Stamps need no
 * shared constants for this — stamp identity derives from the tuple, so restating the
 * same tuple anywhere is the same stamp.)
 * <p>
 * {@link #write()} replays the whole session into the open datastore. It is idempotent
 * and repeatable — all identities and stamps are derived, so re-writing merges to the
 * same state. Ledger composition is single-threaded by design.
 * <p>
 * The birth FQN is the identity seed <em>only</em>: the FQN description it creates may be
 * revised later without changing identity (the append-only ledger permanently records the
 * seed at its first declaration). Two rules keep the derivation safe, enforced by the
 * release verifier rather than this class: a birth FQN is never reused within a
 * namespace, even after retirement; and a released declaration's birth FQN is never
 * edited. Within one session, using the same FQN for both a concept and a pattern is an
 * error this class does reject — the identity would collide.
 */
public final class Namespace {

    private final UUID uuid;
    private final Map<String, ConceptBuilder> concepts = new LinkedHashMap<>();
    private final Map<String, PatternBuilder> patterns = new LinkedHashMap<>();

    private Namespace(UUID uuid) {
        this.uuid = uuid;
    }

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
     * The namespace UUID — the content set's single identity literal.
     *
     * @return the namespace UUID
     */
    public UUID uuid() {
        return uuid;
    }

    /**
     * Opens — or resumes — the concept declaration for the given birth FQN. The first
     * call creates the builder; every later call with the same FQN returns the same
     * builder, so the ledger continues rather than restates. The builder's first
     * {@link ConceptBuilder#at(ActiveStamp)} scope is the birth scope.
     *
     * @param fullyQualifiedName the concept's fully qualified name at birth — both the
     *                           identity seed and the FQN description's initial text
     * @return the (new or resumed) builder for the concept's version ledger
     * @throws IllegalArgumentException if {@code fullyQualifiedName} is null or blank,
     *                                  or already names a pattern in this namespace
     */
    public ConceptBuilder concept(String fullyQualifiedName) {
        requireMeaningful(fullyQualifiedName, "concept");
        if (patterns.containsKey(fullyQualifiedName)) {
            throw new IllegalArgumentException(
                    "\"" + fullyQualifiedName + "\" already names a pattern in this namespace — identity would collide");
        }
        return concepts.computeIfAbsent(fullyQualifiedName, fqn -> new ConceptBuilder(this, fqn));
    }

    /**
     * Opens — or resumes — the pattern declaration for the given birth FQN. The first
     * call creates the builder; every later call with the same FQN returns the same
     * builder. The builder's first {@link PatternBuilder#at(ActiveStamp)} scope is the
     * birth scope and must declare the pattern's meaning and purpose.
     *
     * @param fullyQualifiedName the pattern's fully qualified name at birth — both the
     *                           identity seed and the FQN description's initial text
     * @return the (new or resumed) builder for the pattern's version ledger
     * @throws IllegalArgumentException if {@code fullyQualifiedName} is null or blank,
     *                                  or already names a concept in this namespace
     */
    public PatternBuilder pattern(String fullyQualifiedName) {
        requireMeaningful(fullyQualifiedName, "pattern");
        if (concepts.containsKey(fullyQualifiedName)) {
            throw new IllegalArgumentException(
                    "\"" + fullyQualifiedName + "\" already names a concept in this namespace — identity would collide");
        }
        return patterns.computeIfAbsent(fullyQualifiedName, fqn -> new PatternBuilder(this, fqn));
    }

    /**
     * Replays the whole session — every concept and pattern builder this namespace has
     * opened — into the open datastore. Idempotent and repeatable: identities and stamps
     * are derived, so writing again merges to the same state. May be called mid-ledger
     * and again later; the final state is the same single continuous ledger.
     *
     * @throws IllegalStateException if any opened declaration is incomplete — a concept
     *                               or pattern with no birth scope, or a pattern whose
     *                               pending version lacks meaning or purpose
     */
    public void write() {
        for (ConceptBuilder builder : concepts.values()) {
            builder.writeInto();
        }
        for (PatternBuilder builder : patterns.values()) {
            builder.writeInto();
        }
    }

    /**
     * Resolves the identity a birth FQN derives in this namespace, as a reference handle —
     * for citing a concept (for example in a stated axiom) without opening its builder.
     *
     * @param birthFqn the concept's fully qualified name at birth
     * @return a concept proxy carrying the derived identity and the given name as its description
     */
    public EntityProxy.Concept conceptRef(String birthFqn) {
        return EntityProxy.Concept.make(birthFqn, PublicIds.of(uuidFor(birthFqn)));
    }

    /**
     * Resolves the identity a birth FQN derives in this namespace, as a pattern reference
     * handle — for citing a pattern without opening its builder.
     *
     * @param birthFqn the pattern's fully qualified name at birth
     * @return a pattern proxy carrying the derived identity and the given name as its description
     */
    public EntityProxy.Pattern patternRef(String birthFqn) {
        return EntityProxy.Pattern.make(birthFqn, PublicIds.of(uuidFor(birthFqn)));
    }

    /**
     * Derives the type-5 UUID this namespace assigns to a name.
     *
     * @param name the identity seed — for concepts and patterns, the fully qualified name at birth
     * @return {@code T5(namespace, name)}
     */
    public UUID uuidFor(String name) {
        return UuidT5Generator.get(uuid, name);
    }

    private static void requireMeaningful(String fullyQualifiedName, String kind) {
        if (fullyQualifiedName == null || fullyQualifiedName.isBlank()) {
            throw new IllegalArgumentException(
                    "A " + kind + " declaration requires a meaningful fully qualified name");
        }
    }
}
