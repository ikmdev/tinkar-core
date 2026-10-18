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
package dev.ikm.tinkar.entity.graph;

import dev.ikm.tinkar.common.id.LongIdList;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.DiagnosticText;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.terms.ComponentWithNid;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityFacade;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.LongPredicate;

/**
 * Derivation v1 of newborn vertex UUIDs (arch-vertex-identity-birth; IKE-Network/ike-issues#1310):
 * T5 of the parent's inherited UUID, the vertex's key and an ordinal among siblings with an equal
 * key; the root derives from the semantic's public id and the field that holds the tree.
 *
 * <p>Everything derivation reads is a public id, never a nid, and of a public id its least UUID,
 * which no order of its UUIDs changes, so the same structure derives the same UUIDs in every
 * store: independent births converge, and replaying a source gives back the
 * UUIDs it gave before. The ordinal ranks equal-key siblings by a canonical hash computed the same
 * way ({@link #publicHashes}).
 *
 * <p>Derivation is never authoritative: after a move a vertex's UUID is not what its new parent
 * would derive, and nothing may check identity by recomputing it. A change to anything here
 * changes every UUID derived after it, so it is a new version of derivation, with its own
 * namespace.
 */
public final class VertexDerivation {

    /** The namespace of derivation v1. */
    public static final UUID NAMESPACE_V1 = UUID.fromString("c3369f5f-a291-4e1a-a1ab-44fc5e778120");

    private VertexDerivation() {
    }

    /**
     * The root UUID of the tree a semantic holds in a field.
     *
     * @param semantic   the semantic's public id; its least UUID is the one derivation reads
     * @param fieldIndex the field that holds the tree
     * @return the root's derived UUID
     */
    public static UUID root(PublicId semantic, int fieldIndex) {
        return UuidT5Generator.get(NAMESPACE_V1, "root|" + semantic.leastUuid() + "|" + fieldIndex);
    }

    /**
     * A child's derived UUID.
     *
     * @param parent  the parent's inherited UUID
     * @param key     the child's {@link #key}
     * @param ordinal its rank among the parent's children with an equal key
     * @return the derived UUID
     */
    public static UUID child(UUID parent, String key, int ordinal) {
        return UuidT5Generator.get(NAMESPACE_V1, parent + "|" + key + "|" + ordinal);
    }

    /**
     * A vertex's key as derivation spells it (arch-vertex-identity-table): its meaning and, as an
     * operand, the values of its identifying properties, each component by its public id.
     *
     * @param vertex  the vertex
     * @param operand whether its parent's successors are operands
     * @return the key
     */
    public static String key(EntityVertex vertex, boolean operand) {
        return key(vertex, operand, VertexIdentityTable.LOGICAL);
    }

    /**
     * A vertex's key, its identifying properties from a table.
     *
     * @param vertex  the vertex
     * @param operand whether its parent's successors are operands
     * @param table   the identity table of the vertex's vocabulary
     * @return the key
     */
    public static String key(EntityVertex vertex, boolean operand, VertexIdentityTable table) {
        StringBuilder key = new StringBuilder(uuid(vertex.getMeaningNid()).toString());
        if (operand) {
            for (ConceptFacade property : table.rule(vertex.getMeaningNid()).identifying()) {
                key.append('|').append(text(vertex.properties().get(property.nid())));
            }
        }
        return key.toString();
    }

    /**
     * Every vertex's canonical hash, as {@link DiTreeIsomorphism#vertexHashes} computes it but
     * from public ids rather than nids, so the same subtree hashes alike in every store.
     *
     * @param tree the tree
     * @return the hashes, by vertex index
     */
    public static long[] publicHashes(DiTreeEntity tree) {
        return publicHashes(tree, DiTreeIsomorphism.UNORDERED);
    }

    /**
     * Every vertex's public canonical hash, a vertex whose meaning the predicate names hashing
     * its children in their order.
     *
     * @param tree    the tree
     * @param ordered which meanings' successors are a sequence
     * @return the hashes, by vertex index
     */
    public static long[] publicHashes(DiTreeEntity tree, LongPredicate ordered) {
        long[] hashes = new long[tree.vertexMap().size()];
        hash(tree, tree.root().vertexIndex(), hashes, ordered);
        return hashes;
    }

    private static long hash(DiTreeEntity tree, int index, long[] hashes, LongPredicate ordered) {
        EntityVertex vertex = tree.vertex(index);
        ImmutableIntList children = tree.successors(index);
        long[] childHashes = new long[children.size()];
        for (int i = 0; i < childHashes.length; i++) {
            childHashes[i] = hash(tree, children.get(i), hashes, ordered);
        }
        if (!ordered.test(vertex.getMeaningNid())) {
            Arrays.sort(childHashes);
        }
        long[] properties = {0};
        vertex.properties().forEachKeyValue((key, value) ->
                properties[0] += DiTreeIsomorphism.mix(uuidHash(uuid(key)) ^ text(value).hashCode()));
        long hash = DiTreeIsomorphism.mix(0x5DEECE66DL ^ uuidHash(uuid(vertex.getMeaningNid())));
        hash = DiTreeIsomorphism.mix(hash ^ properties[0]);
        hash = DiTreeIsomorphism.mix(hash ^ childHashes.length);
        for (long childHash : childHashes) {
            hash = DiTreeIsomorphism.mix(hash ^ childHash);
        }
        hashes[index] = hash;
        return hash;
    }

    /**
     * A property value as derivation reads it: a component by its least UUID, anything else typed.
     * A component that carries its public id (an {@link dev.ikm.tinkar.terms.EntityProxy} made from
     * its UUIDs, an entity) is read by it, so derivation needs no store to name it.
     */
    private static String text(Object value) {
        return switch (value) {
            case null -> "∅";
            case EntityFacade component -> uuid(component).toString();
            case ComponentWithNid component -> uuid(component.nid()).toString();
            case PublicId publicId -> publicId.leastUuid().toString();
            case LongIdList list -> {
                StringBuilder text = new StringBuilder("[");
                for (long nid : list.toArray()) {
                    text.append(uuid(nid)).append(',');
                }
                yield text.append(']').toString();
            }
            default -> value.getClass().getSimpleName() + ":" + value;
        };
    }

    /** A component's least UUID, from the public id it carries, or the store's when it carries a nid alone. */
    private static UUID uuid(EntityFacade component) {
        try {
            return component.publicId().leastUuid();
        } catch (UnsupportedOperationException noReverseMap) {
            throw unnamed(component.nid(), noReverseMap);
        }
    }

    /**
     * A component's least UUID by its nid: the stored entity's or, for a component the store does
     * not hold, the identity map's. A store with no reverse identity map (the legacy 8-bit Rocks
     * layout) cannot name an absent component, and derivation never falls back to the nid, which
     * would derive another UUID in every store.
     */
    private static UUID uuid(long nid) {
        try {
            return PrimitiveData.publicId(nid).leastUuid();
        } catch (UnsupportedOperationException noReverseMap) {
            throw unnamed(nid, noReverseMap);
        }
    }

    private static IllegalStateException unnamed(long nid, UnsupportedOperationException noReverseMap) {
        return new IllegalStateException("Cannot derive a vertex UUID: the tree references " + DiagnosticText.component(nid)
                + ", a component this store does not hold, and the store keeps no reverse identity map"
                + " to name it by its public id (" + noReverseMap.getMessage() + "). Write the component first,"
                + " or give the vertex a value that carries its public id, such as an EntityProxy made from"
                + " its UUIDs.", noReverseMap);
    }

    private static long uuidHash(UUID uuid) {
        return DiTreeIsomorphism.mix(uuid.getMostSignificantBits() ^ DiTreeIsomorphism.mix(uuid.getLeastSignificantBits()));
    }
}
