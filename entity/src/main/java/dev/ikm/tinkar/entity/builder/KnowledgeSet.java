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

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.terms.EntityProxy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A knowledge set: the identity root and ledger session of an authored content set — the
 * thing whose releases are change sets. The set's UUID acts as the RFC-4122 type-5
 * namespace for identity derivation: a concept's identity is
 * {@code T5(setUuid, fullyQualifiedNameAtBirth)}, so identity cannot be minted without a
 * meaningful name, and one UUID literal per knowledge set replaces one per concept.
 * Birth-FQN derivation is the seed for the formative declarations of every set; content
 * whose identity was established elsewhere — an existing starter set being ingested into
 * ledger form, or a concept minted interactively and lifted back into the source —
 * instead <em>declares</em> that identity alongside its FQN
 * ({@link #concept(String, UUID)}, {@link #pattern(String, UUID)} — or the
 * {@link #concept(String, PublicId)} / {@link #pattern(String, PublicId)} forms when the
 * established identity carries multiple UUIDs).
 *
 * <h2>The session registry — builders resume</h2>
 * A knowledge set keeps every builder it has opened, keyed by birth FQN:
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
 * knowledge set, even after retirement; and a released declaration's birth FQN is never
 * edited. Within one session, using the same FQN for both a concept and a pattern is an
 * error this class does reject — the identity would collide.
 */
public final class KnowledgeSet {

    private final UUID uuid;
    private final Map<String, ConceptBuilder> concepts = new LinkedHashMap<>();
    private final Map<String, PatternBuilder> patterns = new LinkedHashMap<>();
    private final SessionRegistry registry = new SessionRegistry();
    private final Set<String> derivedReferencesIssued = new LinkedHashSet<>();

    private KnowledgeSet(UUID uuid) {
        this.uuid = uuid;
    }

    /**
     * Creates a knowledge set from its UUID literal.
     *
     * @param uuidString the set's UUID in canonical string form
     * @return the knowledge set
     * @throws IllegalArgumentException if {@code uuidString} is not a valid UUID
     */
    public static KnowledgeSet of(String uuidString) {
        return new KnowledgeSet(UUID.fromString(uuidString));
    }

    /**
     * The set's UUID — its single identity literal, and the type-5 namespace (RFC 4122
     * sense) from which every identity in the set derives.
     *
     * @return the set's UUID
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
     *                                  or already names a pattern in this knowledge set
     */
    public ConceptBuilder concept(String fullyQualifiedName) {
        return openConcept(fullyQualifiedName, null);
    }

    /**
     * Opens — or resumes — the concept declaration for the given birth FQN with a
     * <em>declared</em> identity: the concept's public id is the given UUID rather than
     * the {@code T5(setUuid, fqn)} derivation. Birth-FQN derivation remains the seed for
     * the formative declarations of every set; a declared identity adopts an identity
     * established elsewhere — content ingested from an existing starter set, or a concept
     * minted interactively (for example in Komet) and lifted back into the ledger. The
     * FQN is still the registry key and the FQN description's initial text, and is still
     * never reused within the set.
     * <p>
     * Resuming must agree on identity: once opened, a later call for the same FQN with a
     * different declared identity — or a declared identity where the derived one was
     * already opened, and vice versa when they differ — is rejected.
     *
     * @param fullyQualifiedName the concept's fully qualified name at birth
     * @param declaredIdentity   the established identity to adopt
     * @return the (new or resumed) builder for the concept's version ledger
     * @throws IllegalArgumentException if {@code fullyQualifiedName} is null or blank,
     *                                  already names a pattern in this knowledge set, or
     *                                  was already opened under a different identity;
     *                                  or if {@code declaredIdentity} is null
     */
    public ConceptBuilder concept(String fullyQualifiedName, UUID declaredIdentity) {
        if (declaredIdentity == null) {
            throw new IllegalArgumentException(
                    "A declared identity requires a UUID — use concept(fqn) for the derived identity");
        }
        return openConcept(fullyQualifiedName, PublicIds.of(declaredIdentity));
    }

    /**
     * Opens — or resumes — the concept declaration for the given birth FQN with a
     * <em>declared</em> identity carrying the full established {@link PublicId} — which
     * may hold multiple UUIDs, as store-established identities sometimes do. See
     * {@link #concept(String, UUID)} for the declared-identity semantics; this overload
     * exists because adoption must be identity-exact: an established id with two UUIDs
     * is adopted with both.
     *
     * @param fullyQualifiedName the concept's fully qualified name at birth
     * @param declaredIdentity   the established identity to adopt, in full
     * @return the (new or resumed) builder for the concept's version ledger
     * @throws IllegalArgumentException if {@code fullyQualifiedName} is null or blank,
     *                                  already names a pattern in this knowledge set, or
     *                                  was already opened under a different identity;
     *                                  or if {@code declaredIdentity} is null or empty
     */
    public ConceptBuilder concept(String fullyQualifiedName, PublicId declaredIdentity) {
        return openConcept(fullyQualifiedName, requireDeclared(declaredIdentity, "concept(fqn)"));
    }

    private ConceptBuilder openConcept(String fullyQualifiedName, PublicId declaredIdentity) {
        requireMeaningful(fullyQualifiedName, "concept");
        if (patterns.containsKey(fullyQualifiedName)) {
            throw new IllegalArgumentException(
                    "\"" + fullyQualifiedName + "\" already names a pattern in this knowledge set — identity would collide");
        }
        ConceptBuilder opened = concepts.get(fullyQualifiedName);
        if (opened != null) {
            requireIdentityAgreement(fullyQualifiedName, opened.publicId(), declaredIdentity);
            return opened;
        }
        PublicId identity =
                declaredIdentity != null ? declaredIdentity : PublicIds.of(uuidFor(fullyQualifiedName));
        requireReferenceFollowsDeclaration(fullyQualifiedName, declaredIdentity);
        registry.registerIdentity(identity, "concept \"" + fullyQualifiedName + "\"");
        ConceptBuilder created = new ConceptBuilder(identity, fullyQualifiedName, registry);
        concepts.put(fullyQualifiedName, created);
        return created;
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
     *                                  or already names a concept in this knowledge set
     */
    public PatternBuilder pattern(String fullyQualifiedName) {
        return openPattern(fullyQualifiedName, null);
    }

    /**
     * Opens — or resumes — the pattern declaration for the given birth FQN with a
     * <em>declared</em> identity: the pattern's public id is the given UUID rather than
     * the {@code T5(setUuid, fqn)} derivation. See {@link #concept(String, UUID)} for the
     * declared-identity semantics — adoption of an established identity (ingest or lift),
     * with the FQN still the registry key and never reused.
     *
     * @param fullyQualifiedName the pattern's fully qualified name at birth
     * @param declaredIdentity   the established identity to adopt
     * @return the (new or resumed) builder for the pattern's version ledger
     * @throws IllegalArgumentException if {@code fullyQualifiedName} is null or blank,
     *                                  already names a concept in this knowledge set, or
     *                                  was already opened under a different identity;
     *                                  or if {@code declaredIdentity} is null
     */
    public PatternBuilder pattern(String fullyQualifiedName, UUID declaredIdentity) {
        if (declaredIdentity == null) {
            throw new IllegalArgumentException(
                    "A declared identity requires a UUID — use pattern(fqn) for the derived identity");
        }
        return openPattern(fullyQualifiedName, PublicIds.of(declaredIdentity));
    }

    /**
     * Opens — or resumes — the pattern declaration for the given birth FQN with a
     * <em>declared</em> identity carrying the full established {@link PublicId} — which
     * may hold multiple UUIDs, as store-established identities sometimes do. See
     * {@link #concept(String, PublicId)}.
     *
     * @param fullyQualifiedName the pattern's fully qualified name at birth
     * @param declaredIdentity   the established identity to adopt, in full
     * @return the (new or resumed) builder for the pattern's version ledger
     * @throws IllegalArgumentException if {@code fullyQualifiedName} is null or blank,
     *                                  already names a concept in this knowledge set, or
     *                                  was already opened under a different identity;
     *                                  or if {@code declaredIdentity} is null or empty
     */
    public PatternBuilder pattern(String fullyQualifiedName, PublicId declaredIdentity) {
        return openPattern(fullyQualifiedName, requireDeclared(declaredIdentity, "pattern(fqn)"));
    }

    private PatternBuilder openPattern(String fullyQualifiedName, PublicId declaredIdentity) {
        requireMeaningful(fullyQualifiedName, "pattern");
        if (concepts.containsKey(fullyQualifiedName)) {
            throw new IllegalArgumentException(
                    "\"" + fullyQualifiedName + "\" already names a concept in this knowledge set — identity would collide");
        }
        PatternBuilder opened = patterns.get(fullyQualifiedName);
        if (opened != null) {
            requireIdentityAgreement(fullyQualifiedName, opened.publicId(), declaredIdentity);
            return opened;
        }
        PublicId identity =
                declaredIdentity != null ? declaredIdentity : PublicIds.of(uuidFor(fullyQualifiedName));
        requireReferenceFollowsDeclaration(fullyQualifiedName, declaredIdentity);
        registry.registerIdentity(identity, "pattern \"" + fullyQualifiedName + "\"");
        PatternBuilder created = new PatternBuilder(identity, fullyQualifiedName, registry);
        patterns.put(fullyQualifiedName, created);
        return created;
    }

    /**
     * Replays the whole session — every concept and pattern builder this knowledge set
     * has opened — into the open datastore. Idempotent and repeatable: identities and stamps
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
     * The declarations this session has opened, in declaration order — the read surface
     * for tooling: bindings generation, draft reports, and the release verifier.
     *
     * @return one {@link Declaration} per opened concept and pattern builder
     */
    public List<Declaration> declarations() {
        List<Declaration> result = new ArrayList<>();
        for (ConceptBuilder builder : concepts.values()) {
            result.add(new Declaration(Declaration.Kind.CONCEPT, builder.ledger().birthFqn,
                    builder.publicId(), builder.ledger().currentDefinition()));
        }
        for (PatternBuilder builder : patterns.values()) {
            result.add(new Declaration(Declaration.Kind.PATTERN, builder.ledger().birthFqn,
                    builder.publicId(), builder.ledger().currentDefinition()));
        }
        return result;
    }

    /**
     * One opened declaration of a knowledge set: its kind, the fully qualified name at
     * birth (the identity seed), the derived identity, and the current definition text if
     * the ledger declared one.
     *
     * @param kind       concept or pattern
     * @param birthFqn   the fully qualified name at birth
     * @param publicId   the identity — derived {@code T5(setUuid, birthFqn)}, or the
     *                   declared identity the ledger adopted
     * @param definition the current text of the first live definition description, if any
     */
    public record Declaration(Kind kind, String birthFqn, PublicId publicId, Optional<String> definition) {

        /** The kind of a declaration. */
        public enum Kind {
            /** A concept declaration. */
            CONCEPT,
            /** A pattern declaration. */
            PATTERN
        }
    }

    /**
     * Resolves the identity a birth FQN carries in this knowledge set, as a reference
     * handle — for citing a concept (for example in a stated axiom) without opening its
     * builder. An opened declaration answers with its actual identity — declared or
     * derived; an FQN not yet opened answers with the derivation, so a reference to a
     * declared-identity concept must follow its declaration.
     *
     * @param birthFqn the concept's fully qualified name at birth
     * @return a concept proxy carrying the identity and the given name as its description
     */
    public EntityProxy.Concept conceptRef(String birthFqn) {
        ConceptBuilder opened = concepts.get(birthFqn);
        if (opened == null) {
            derivedReferencesIssued.add(birthFqn);
            return EntityProxy.Concept.make(birthFqn, PublicIds.of(uuidFor(birthFqn)));
        }
        return EntityProxy.Concept.make(birthFqn, opened.publicId());
    }

    /**
     * Resolves the identity a birth FQN carries in this knowledge set, as a pattern
     * reference handle — for citing a pattern without opening its builder. An opened
     * declaration answers with its actual identity — declared or derived; an FQN not yet
     * opened answers with the derivation, so a reference to a declared-identity pattern
     * must follow its declaration.
     *
     * @param birthFqn the pattern's fully qualified name at birth
     * @return a pattern proxy carrying the identity and the given name as its description
     */
    public EntityProxy.Pattern patternRef(String birthFqn) {
        PatternBuilder opened = patterns.get(birthFqn);
        if (opened == null) {
            derivedReferencesIssued.add(birthFqn);
            return EntityProxy.Pattern.make(birthFqn, PublicIds.of(uuidFor(birthFqn)));
        }
        return EntityProxy.Pattern.make(birthFqn, opened.publicId());
    }

    /**
     * Derives the type-5 UUID this knowledge set assigns to a name — the set's UUID is
     * the T5 namespace.
     *
     * @param name the identity seed — for concepts and patterns, the fully qualified name at birth
     * @return {@code T5(setUuid, name)}
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

    private void requireReferenceFollowsDeclaration(String fullyQualifiedName, PublicId declaredIdentity) {
        if (declaredIdentity == null || !derivedReferencesIssued.contains(fullyQualifiedName)) {
            return;
        }
        UUID derived = uuidFor(fullyQualifiedName);
        UUID[] declared = declaredIdentity.asUuidArray();
        if (declared.length == 1 && declared[0].equals(derived)) {
            return;
        }
        throw new IllegalArgumentException(
                "\"" + fullyQualifiedName + "\" was referenced before this declared-identity"
                        + " declaration, and the reference answered the derived identity " + derived
                        + " — a reference to a declared-identity component must follow its declaration");
    }

    private static PublicId requireDeclared(PublicId declaredIdentity, String derivedForm) {
        if (declaredIdentity == null || declaredIdentity.uuidCount() == 0) {
            throw new IllegalArgumentException(
                    "A declared identity requires a PublicId with at least one UUID — use "
                            + derivedForm + " for the derived identity");
        }
        return declaredIdentity;
    }

    private static void requireIdentityAgreement(String fullyQualifiedName, PublicId openedIdentity,
                                                 PublicId declaredIdentity) {
        // Identity-exact agreement: the full UUID lists must match, in order. Comparison
        // is by UUID array because PublicId implementations vary by arity.
        if (declaredIdentity != null
                && !Arrays.equals(declaredIdentity.asUuidArray(), openedIdentity.asUuidArray())) {
            throw new IllegalArgumentException(
                    "\"" + fullyQualifiedName + "\" is already opened with identity " + openedIdentity
                            + " — cannot resume it with declared identity " + declaredIdentity);
        }
    }
}
