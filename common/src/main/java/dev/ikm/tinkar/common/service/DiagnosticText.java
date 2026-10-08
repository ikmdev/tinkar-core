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
package dev.ikm.tinkar.common.service;

import dev.ikm.tinkar.common.id.PublicId;

import java.util.Optional;
import java.util.UUID;

/**
 * How a component is identified in a diagnostic message, such as the message of an exception
 * ({@code IKE-Network/ike-issues#1189}).
 *
 * <p>A message identifies a component by its description and its UUIDs (every one: each
 * identifies the component, and none is primordial; most components have one). A nid is written only
 * when the store has no public id for it, because the nid is then the only identifier there
 * is, and the text states that it is a nid of this store. A nid means something only in the
 * store that assigned it, and a message leaves the store: a service copies it into an error
 * response, and a person reads it beside another store.
 *
 * <table>
 *   <caption>What {@link #component(int)} writes</caption>
 *   <tr><th>The store has</th><th>The text</th></tr>
 *   <tr><td>a description and a public id</td>
 *       <td>{@code Chronic lung disease (UUID 0b6f…)}</td></tr>
 *   <tr><td>a public id, no description</td>
 *       <td>{@code UUID 0b6f…}</td></tr>
 *   <tr><td>a description, no public id</td>
 *       <td>{@code Chronic lung disease (nid -2147483000 in this store, which has no public id
 *       for it)}</td></tr>
 *   <tr><td>neither</td>
 *       <td>{@code nid -2147483000 in this store, which has no public id for it}</td></tr>
 * </table>
 *
 * <p>The last two rows are real. The public id of a nid is read from the entity. For a
 * component that is referred to but was never written there is no entity, and only a store
 * that keeps an index from nid to UUID beside its entities can give the public id.
 *
 * <p>This is the form for diagnostics only. Text that is kept — a saved search, a
 * conversation, a field of a response — holds no nid at all and has routines of its own,
 * because it can be read later against another store.
 *
 * <p>No method here throws, and none assigns a nid. A lookup that fails is treated as finding
 * nothing, so that composing a message never replaces the failure the message is about. That
 * includes the case where no store is running.
 */
public final class DiagnosticText {

    private DiagnosticText() {
    }

    /**
     * The identification of a component, for the place in a message that tells which
     * component the message is about.
     *
     * @param nid the component's nid in the open store
     * @return the description followed by the UUIDs in parentheses; the UUIDs alone when the
     *         component has no description; and in place of the UUIDs, the nid as a nid of this
     *         store when the store has no public id for it
     */
    public static String component(long nid) {
        Optional<UUID[]> uuids = uuids(nid);
        String identifier = uuids.isPresent()
                ? uuidLabel(uuids.get())
                : nidInThisStore(nid) + ", which has no public id for it";
        return description(nid).map(text -> text + " (" + identifier + ")").orElse(identifier);
    }

    /**
     * The identification of a component that is known by its public id, such as one a proxy
     * names. The store is asked for a description only when it holds the public id, so no nid
     * is assigned.
     *
     * @param publicId the component's public id; may be null
     * @return the description followed by the UUIDs in parentheses; the UUIDs alone when the
     *         store holds no description for it; {@code a component with no public id} when
     *         the public id is null or holds no UUID
     */
    public static String component(PublicId publicId) {
        Optional<UUID[]> uuids = uuids(publicId);
        if (uuids.isEmpty()) {
            return "a component with no public id";
        }
        String identifier = uuidLabel(uuids.get());
        return description(publicId).map(text -> text + " (" + identifier + ")").orElse(identifier);
    }

    /**
     * The name of a component, for a place where many components are written together: the
     * vertices of a definition tree, or the elements of a list.
     *
     * @param nid the component's nid in the open store
     * @return the description; the UUIDs when the component has no description; the nid as a
     *         nid of this store when the store has neither
     */
    public static String name(long nid) {
        Optional<String> description = description(nid);
        if (description.isPresent()) {
            return description.get();
        }
        Optional<UUID[]> uuids = uuids(nid);
        return uuids.isPresent() ? joined(uuids.get()) : nidInThisStore(nid);
    }

    /** A nid, written so that it cannot be read as a nid of any other store. */
    private static String nidInThisStore(long nid) {
        return "nid " + nid + " in this store";
    }

    /**
     * Every UUID of a component, labelled: {@code UUID 0b6f…} for one, {@code UUIDs 0b6f…, 4a2c…}
     * for several. Each identifies the component, and none of them is its primordial UUID, so a
     * message names them all.
     */
    private static String uuidLabel(UUID[] uuids) {
        return (uuids.length == 1 ? "UUID " : "UUIDs ") + joined(uuids);
    }

    /** UUIDs separated by commas, in the order the public id lists them. */
    private static String joined(UUID[] uuids) {
        StringBuilder text = new StringBuilder();
        for (UUID uuid : uuids) {
            text.append(text.isEmpty() ? "" : ", ").append(uuid);
        }
        return text.toString();
    }

    /** The UUIDs of the public id the store has for a nid, or empty when it has none. */
    private static Optional<UUID[]> uuids(long nid) {
        try {
            return uuids(PrimitiveData.publicId(nid));
        } catch (RuntimeException noPublicIdForTheNid) {
            return Optional.empty();
        }
    }

    /** The UUIDs of a public id, or empty when it is null or holds none. */
    private static Optional<UUID[]> uuids(PublicId publicId) {
        if (publicId == null) {
            return Optional.empty();
        }
        try {
            UUID[] uuids = publicId.asUuidArray();
            return uuids.length == 0 ? Optional.empty() : Optional.of(uuids);
        } catch (RuntimeException noUuids) {
            return Optional.empty();
        }
    }

    /** The store's default description of a component, or empty when it has none. */
    private static Optional<String> description(long nid) {
        try {
            return PrimitiveData.textOptional(nid).filter(text -> !text.isBlank());
        } catch (RuntimeException noDescription) {
            return Optional.empty();
        }
    }

    /**
     * The store's default description of the component a public id names, or empty when the
     * store does not hold the public id. The store is asked whether it holds the public id
     * before it is asked for the nid, because some stores assign a nid to a public id they do
     * not hold.
     */
    private static Optional<String> description(PublicId publicId) {
        try {
            PrimitiveDataService store = PrimitiveData.get();
            if (!store.hasPublicId(publicId)) {
                return Optional.empty();
            }
            return description(store.nidForPublicId(publicId));
        } catch (RuntimeException notHeld) {
            return Optional.empty();
        }
    }
}
