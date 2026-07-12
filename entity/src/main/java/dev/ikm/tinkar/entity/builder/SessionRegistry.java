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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The session-wide identity discipline of one {@link KnowledgeSet}: every identity a
 * session adopts or derives — components, generic semantics, stated-axiom semantics —
 * is registered under every UUID it carries, so two declarations can never silently
 * share an identity and cross-write one merged chronology. Stamps register separately
 * with <em>agreement</em> semantics rather than uniqueness: restating a stamp across
 * ledgers is the norm, but the same stamp identity restated with a different tuple is
 * a fork the store would resolve silently and arbitrarily, so it is rejected here.
 * Package-private — {@link KnowledgeSet} owns one per session.
 */
final class SessionRegistry {

    private final Map<UUID, String> identityOwners = new LinkedHashMap<>();
    private final Map<UUID, Stamp> stampsByUuid = new LinkedHashMap<>();

    /**
     * Registers an identity under every UUID it carries, rejecting any overlap with an
     * identity already registered to a different owner. Registering the same owner
     * again is a no-op, so resumes are safe.
     *
     * @param identity the identity being adopted or derived
     * @param owner    a stable, human-readable owner description, for example
     *                 {@code concept "Journal element (Test)"}
     * @throws IllegalArgumentException if any UUID of {@code identity} is registered to
     *                                  a different owner
     */
    void registerIdentity(PublicId identity, String owner) {
        for (UUID uuid : identity.asUuidArray()) {
            String registered = identityOwners.get(uuid);
            if (registered != null && !registered.equals(owner)) {
                throw new IllegalArgumentException(
                        "Identity " + uuid + " of " + owner + " is already adopted by " + registered
                                + " — one identity, one declaration; the store would silently merge"
                                + " both into one chronology");
            }
        }
        for (UUID uuid : identity.asUuidArray()) {
            identityOwners.put(uuid, owner);
        }
    }

    /**
     * Requires that a stamp agrees with every earlier declaration of the same identity:
     * the full tuple — state, time, author, module, path — must match, compared
     * store-free by primordial UUIDs. First sight of an identity registers the stamp
     * under every UUID it carries.
     *
     * @param stamp the stamp as declared at a scope or written by a ledger
     * @throws IllegalArgumentException if the same stamp identity was declared earlier
     *                                  with a different tuple
     */
    void requireStampAgreement(Stamp stamp) {
        Stamp registered = null;
        for (UUID uuid : stamp.publicId().asUuidArray()) {
            Stamp known = stampsByUuid.get(uuid);
            if (known != null) {
                registered = known;
                break;
            }
        }
        if (registered == null) {
            for (UUID uuid : stamp.publicId().asUuidArray()) {
                stampsByUuid.put(uuid, stamp);
            }
            return;
        }
        boolean agrees = registered.state() == stamp.state()
                && registered.time() == stamp.time()
                && firstUuid(registered.author().publicId()).equals(firstUuid(stamp.author().publicId()))
                && firstUuid(registered.module().publicId()).equals(firstUuid(stamp.module().publicId()))
                && firstUuid(registered.path().publicId()).equals(firstUuid(stamp.path().publicId()));
        if (!agrees) {
            throw new IllegalArgumentException(
                    "Stamp " + stamp.publicId() + " is already declared in this session with a"
                            + " different tuple — a declared stamp identity binds one tuple; the store"
                            + " would otherwise merge both versions and pick one arbitrarily");
        }
    }

    /**
     * The stamp registered under any UUID of the given stamp's identity, if one is.
     *
     * @param stamp the stamp whose identity to look up
     * @return the first-registered stamp sharing any UUID, or null if none is registered
     */
    Stamp registeredStamp(Stamp stamp) {
        for (UUID uuid : stamp.publicId().asUuidArray()) {
            Stamp known = stampsByUuid.get(uuid);
            if (known != null) {
                return known;
            }
        }
        return null;
    }

    private static UUID firstUuid(PublicId publicId) {
        return publicId.asUuidArray()[0];
    }
}
