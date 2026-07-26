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
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.State;

import java.time.Instant;
import java.util.UUID;

/**
 * A declared STAMP for ledger-form content authoring: an explicit
 * (status, time, author, module, path) tuple with a declared literal time.
 * <p>
 * Declared stamps are first-class content. A ledger source file declares its stamps in an
 * append-only stamp file — stamps are never edited or deleted, only appended — and every
 * version declaration binds to one by reference. Because the tuple is fully explicit,
 * replaying the same ledger always produces the same stamps: there is no transaction, no
 * commit time, and nothing to cancel.
 * <p>
 * Stamp identity is derived from the tuple itself: two declarations of the same tuple
 * <em>are</em> the same stamp (see {@link #stampUuid(State, long, ConceptFacade, ConceptFacade, ConceptFacade)}).
 * A stamp whose identity was established elsewhere — a store-established stamp of an
 * ingested starter set, or a stamp minted interactively and lifted back into the ledger —
 * instead <em>declares</em> that identity
 * ({@link #active(PublicId, long, ConceptFacade, ConceptFacade, ConceptFacade)}), the
 * same adopt-never-re-mint discipline as declared component identities
 * ({@link KnowledgeSet#concept(String, java.util.UUID)}).
 * <p>
 * The {@link ActiveStamp} / {@link InactiveStamp} subtypes make status visible to the
 * compiler: {@link ConceptBuilder#at(ActiveStamp)} yields content verbs, while
 * {@link ConceptBuilder#at(InactiveStamp)} yields only retirement verbs, so authoring
 * content under an inactive stamp (or retiring under an active one) does not compile.
 */
public sealed interface Stamp permits ActiveStamp, InactiveStamp {

    /**
     * The status dimension of this stamp.
     *
     * @return the declared {@link State}; never null
     */
    State state();

    /**
     * The time dimension of this stamp.
     *
     * @return the declared time in epoch milliseconds
     */
    long time();

    /**
     * The author dimension of this stamp.
     *
     * @return the declared author concept; never null
     */
    ConceptFacade author();

    /**
     * The module dimension of this stamp.
     *
     * @return the declared module concept; never null
     */
    ConceptFacade module();

    /**
     * The path dimension of this stamp.
     *
     * @return the declared path concept; never null
     */
    ConceptFacade path();

    /**
     * The declared identity this stamp adopts, when its identity was established
     * elsewhere — a store-established stamp of an ingested starter set, or a stamp minted
     * interactively and lifted back into the ledger.
     *
     * @return the declared {@link PublicId}, or null when identity is tuple-derived
     */
    PublicId declaredIdentity();

    /**
     * The identity of this stamp: the declared identity when this stamp adopts one, else
     * derived from its tuple via
     * {@link #stampUuid(State, long, ConceptFacade, ConceptFacade, ConceptFacade)}.
     *
     * @return the {@link PublicId} of this stamp; never null
     */
    default PublicId publicId() {
        PublicId declared = declaredIdentity();
        if (declared != null) {
            return declared;
        }
        return PublicIds.of(stampUuid(state(), time(), author(), module(), path()));
    }

    /**
     * Derives the deterministic identity of a stamp tuple: a type-5 UUID in
     * {@link UuidT5Generator#STAMP_NAMESPACE} seeded by the canonical form
     * {@code statusUuid|time|authorUuid|moduleUuid|pathUuid}, where each UUID is the
     * primordial (first) UUID of the concept's public id and time is epoch milliseconds
     * in decimal. Two declarations of the same tuple therefore resolve to the same stamp
     * entity, which is what makes ledger replay idempotent at the stamp level.
     *
     * @param state  the status dimension
     * @param time   the time dimension, in epoch milliseconds
     * @param author the author dimension
     * @param module the module dimension
     * @param path   the path dimension
     * @return the tuple-derived UUID
     */
    static UUID stampUuid(State state, long time, ConceptFacade author, ConceptFacade module, ConceptFacade path) {
        String canonical = state.publicId().asUuidArray()[0]
                + "|" + time
                + "|" + author.publicId().asUuidArray()[0]
                + "|" + module.publicId().asUuidArray()[0]
                + "|" + path.publicId().asUuidArray()[0];
        return UuidT5Generator.get(UuidT5Generator.STAMP_NAMESPACE, canonical);
    }

    /**
     * Declares an active stamp with a literal ISO-8601 time.
     *
     * @param isoInstant the declared time as an ISO-8601 instant, for example
     *                   {@code "2026-07-15T00:00:00Z"}
     * @param author     the author dimension
     * @param module     the module dimension
     * @param path       the path dimension
     * @return the declared active stamp
     * @throws java.time.format.DateTimeParseException if {@code isoInstant} is not a valid ISO-8601 instant
     */
    static ActiveStamp active(String isoInstant, ConceptFacade author, ConceptFacade module, ConceptFacade path) {
        return new ActiveStamp(Instant.parse(isoInstant).toEpochMilli(), author, module, path);
    }

    /**
     * Declares an active stamp that adopts an established identity — the identity a
     * store-established stamp already carries — with a literal ISO-8601 time. The tuple
     * is still declared in full: the identity is adopted, the dimensions are stated, and
     * replaying the ledger writes the same stamp entity every time.
     *
     * @param declaredIdentity the established identity to adopt; may carry multiple UUIDs
     * @param isoInstant       the declared time as an ISO-8601 instant, for example
     *                         {@code "2026-07-15T00:00:00Z"}
     * @param author           the author dimension
     * @param module           the module dimension
     * @param path             the path dimension
     * @return the declared active stamp
     * @throws IllegalArgumentException if {@code declaredIdentity} is null
     * @throws java.time.format.DateTimeParseException if {@code isoInstant} is not a valid ISO-8601 instant
     */
    static ActiveStamp active(PublicId declaredIdentity, String isoInstant,
                              ConceptFacade author, ConceptFacade module, ConceptFacade path) {
        return new ActiveStamp(Instant.parse(isoInstant).toEpochMilli(), author, module, path,
                requireDeclared(declaredIdentity));
    }

    /**
     * Declares an active stamp that adopts an established identity, with a literal
     * epoch-millisecond time. See
     * {@link #active(PublicId, String, ConceptFacade, ConceptFacade, ConceptFacade)}.
     *
     * @param declaredIdentity the established identity to adopt; may carry multiple UUIDs
     * @param time             the declared time in epoch milliseconds
     * @param author           the author dimension
     * @param module           the module dimension
     * @param path             the path dimension
     * @return the declared active stamp
     * @throws IllegalArgumentException if {@code declaredIdentity} is null
     */
    static ActiveStamp active(PublicId declaredIdentity, long time,
                              ConceptFacade author, ConceptFacade module, ConceptFacade path) {
        return new ActiveStamp(time, author, module, path, requireDeclared(declaredIdentity));
    }

    /**
     * Declares an active stamp with a literal epoch-millisecond time.
     *
     * @param time   the declared time in epoch milliseconds
     * @param author the author dimension
     * @param module the module dimension
     * @param path   the path dimension
     * @return the declared active stamp
     */
    static ActiveStamp active(long time, ConceptFacade author, ConceptFacade module, ConceptFacade path) {
        return new ActiveStamp(time, author, module, path);
    }

    /**
     * Declares an inactive (retirement) stamp with a literal ISO-8601 time. Retirement is
     * always a new appended stamp — never a mutation of an existing one.
     *
     * @param isoInstant the declared time as an ISO-8601 instant, for example
     *                   {@code "2026-09-01T00:00:00Z"}
     * @param author     the author dimension
     * @param module     the module dimension
     * @param path       the path dimension
     * @return the declared inactive stamp
     * @throws java.time.format.DateTimeParseException if {@code isoInstant} is not a valid ISO-8601 instant
     */
    static InactiveStamp inactive(String isoInstant, ConceptFacade author, ConceptFacade module, ConceptFacade path) {
        return new InactiveStamp(Instant.parse(isoInstant).toEpochMilli(), author, module, path);
    }

    /**
     * Declares an inactive (retirement) stamp with a literal epoch-millisecond time.
     *
     * @param time   the declared time in epoch milliseconds
     * @param author the author dimension
     * @param module the module dimension
     * @param path   the path dimension
     * @return the declared inactive stamp
     */
    static InactiveStamp inactive(long time, ConceptFacade author, ConceptFacade module, ConceptFacade path) {
        return new InactiveStamp(time, author, module, path);
    }

    /**
     * Declares an inactive (retirement) stamp that adopts an established identity, with a
     * literal ISO-8601 time. See
     * {@link #active(PublicId, String, ConceptFacade, ConceptFacade, ConceptFacade)} for
     * the declared-identity semantics.
     *
     * @param declaredIdentity the established identity to adopt; may carry multiple UUIDs
     * @param isoInstant       the declared time as an ISO-8601 instant, for example
     *                         {@code "2026-09-01T00:00:00Z"}
     * @param author           the author dimension
     * @param module           the module dimension
     * @param path             the path dimension
     * @return the declared inactive stamp
     * @throws IllegalArgumentException if {@code declaredIdentity} is null
     * @throws java.time.format.DateTimeParseException if {@code isoInstant} is not a valid ISO-8601 instant
     */
    static InactiveStamp inactive(PublicId declaredIdentity, String isoInstant,
                                  ConceptFacade author, ConceptFacade module, ConceptFacade path) {
        return new InactiveStamp(Instant.parse(isoInstant).toEpochMilli(), author, module, path,
                requireDeclared(declaredIdentity));
    }

    /**
     * Declares an inactive (retirement) stamp that adopts an established identity, with a
     * literal epoch-millisecond time. See
     * {@link #active(PublicId, String, ConceptFacade, ConceptFacade, ConceptFacade)} for
     * the declared-identity semantics.
     *
     * @param declaredIdentity the established identity to adopt; may carry multiple UUIDs
     * @param time             the declared time in epoch milliseconds
     * @param author           the author dimension
     * @param module           the module dimension
     * @param path             the path dimension
     * @return the declared inactive stamp
     * @throws IllegalArgumentException if {@code declaredIdentity} is null
     */
    static InactiveStamp inactive(PublicId declaredIdentity, long time,
                                  ConceptFacade author, ConceptFacade module, ConceptFacade path) {
        return new InactiveStamp(time, author, module, path, requireDeclared(declaredIdentity));
    }

    private static PublicId requireDeclared(PublicId declaredIdentity) {
        if (declaredIdentity == null || declaredIdentity.uuidCount() == 0) {
            throw new IllegalArgumentException(
                    "A declared identity requires a PublicId with at least one UUID — use the"
                            + " tuple-derived factories otherwise");
        }
        return declaredIdentity;
    }
}
