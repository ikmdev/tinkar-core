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

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.terms.ComponentWithNid;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.map.primitive.ImmutableLongObjectMap;

import java.util.Arrays;
import java.util.Objects;

/**
 * Whether two trees are the same tree: there is a one-to-one map between their vertices,
 * root to root, that preserves every edge and under which every vertex has its image's meaning
 * and properties. A vertex's children are unordered, and vertex identities and indexes do not
 * matter.
 *
 * <p>A property whose value is a component matches any other form of the same component: a
 * proxy, a binding enum and a record of one component agree, as {@code EntityProxy} defines.
 * {@link EntityVertex#equivalent(EntityVertex)} compares values with {@code equals}, which is
 * not symmetric across those forms (a binding enum equals only itself), so a necessary normal
 * form built with {@code KernelConcept} values was not equivalent to the same form read back
 * from a store, whose values are proxies; this compares components by nid.
 *
 * <p>This answers exactly, without a search: each vertex gets a canonical hash computed bottom
 * up (its meaning, its properties in no particular order, its children's hashes sorted), which
 * isomorphic trees always share; trees whose root hashes differ are different, and trees whose
 * root hashes are equal are then matched vertex by vertex, children paired within groups of equal
 * hash. Because "is isomorphic to" is an equivalence relation, pairing greedily within a group is
 * exact. Groups are single children unless siblings look alike, so the work is close to linear,
 * apart from sorting each vertex's children.
 *
 * <p>Unlike {@link DiTreeEntity#isomorphic(DiTreeEntity)}, which runs the isomorphic analysis
 * to compute a difference and reports equivalence at the level of set elements, this compares
 * whole trees and finds nothing else.
 */
public final class DiTreeIsomorphism {

    private DiTreeIsomorphism() {
    }

    /**
     * Whether two trees are the same tree, as the class describes.
     *
     * @param a one tree
     * @param b the other tree
     * @return true when they are isomorphic
     */
    public static boolean isomorphic(DiTreeEntity a, DiTreeEntity b) {
        if (a == b) {
            return true;
        }
        long[] hashesA = vertexHashes(a);
        long[] hashesB = vertexHashes(b);
        int rootA = a.root().vertexIndex();
        int rootB = b.root().vertexIndex();
        if (hashesA[rootA] != hashesB[rootB]) {
            return false;
        }
        return match(a, hashesA, rootA, b, hashesB, rootB);
    }

    /**
     * The tree's canonical hash: isomorphic trees always have the same one, so trees with
     * different hashes are different. Equal hashes do not prove trees isomorphic; use
     * {@link #isomorphic(DiTreeEntity, DiTreeEntity)} for that.
     *
     * @param tree the tree
     * @return its hash
     */
    public static long hash(DiTreeEntity tree) {
        return vertexHashes(tree)[tree.root().vertexIndex()];
    }

    /** Every reachable vertex's canonical hash, by vertex index; unreachable indexes stay zero. */
    private static long[] vertexHashes(DiTreeEntity tree) {
        long[] hashes = new long[tree.vertexMap().size()];
        vertexHash(tree, tree.root().vertexIndex(), hashes);
        return hashes;
    }

    private static long vertexHash(DiTreeEntity tree, int index, long[] hashes) {
        EntityVertex vertex = tree.vertex(index);
        ImmutableIntList children = tree.successors(index);
        long[] childHashes = new long[children.size()];
        for (int i = 0; i < childHashes.length; i++) {
            childHashes[i] = vertexHash(tree, children.get(i), hashes);
        }
        Arrays.sort(childHashes);
        long hash = mix(0x5DEECE66DL ^ vertex.getMeaningNid());
        hash = mix(hash ^ propertiesHash(vertex.properties()));
        hash = mix(hash ^ childHashes.length);
        for (long childHash : childHashes) {
            hash = mix(hash ^ childHash);
        }
        hashes[index] = hash;
        return hash;
    }

    /** Independent of the order the properties are held in: a sum of each entry's hash. */
    private static long propertiesHash(ImmutableLongObjectMap<Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return 0;
        }
        long[] sum = {0};
        properties.forEachKeyValue((key, value) -> sum[0] += mix(mix(key) ^ valueHash(value)));
        return sum[0];
    }

    /**
     * A property value's hash, equal for any two values {@link #sameValue} finds the same: a
     * component, and a public id the store knows, hash by nid; JDK values hash as they say. Any
     * other value contributes nothing here, and the exact match decides it.
     */
    private static long valueHash(Object value) {
        return switch (value) {
            case null -> 0;
            case ComponentWithNid component -> component.nid();
            case PublicId publicId -> PrimitiveData.get().hasPublicId(publicId) ? PrimitiveData.nid(publicId) : 0;
            case String text -> text.hashCode();
            case Number number -> number.hashCode();
            case Boolean bool -> bool.hashCode();
            default -> 0;
        };
    }

    /** The 64-bit finalizer of SplitMix64. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Whether the subtrees at two vertices are isomorphic, given their hashes are equal. */
    private static boolean match(DiTreeEntity a, long[] hashesA, int indexA,
                                 DiTreeEntity b, long[] hashesB, int indexB) {
        if (!sameVertex(a.vertex(indexA), b.vertex(indexB))) {
            return false;
        }
        int[] childrenA = byHash(a.successors(indexA), hashesA);
        int[] childrenB = byHash(b.successors(indexB), hashesB);
        if (childrenA.length != childrenB.length) {
            return false;
        }
        int start = 0;
        while (start < childrenA.length) {
            long groupHash = hashesA[childrenA[start]];
            int end = start;
            while (end < childrenA.length && hashesA[childrenA[end]] == groupHash) {
                end++;
            }
            for (int i = start; i < end; i++) {
                if (hashesB[childrenB[i]] != groupHash) {
                    return false;
                }
            }
            if (end < childrenB.length && hashesB[childrenB[end]] == groupHash) {
                return false;
            }
            if (!matchGroup(a, hashesA, childrenA, b, hashesB, childrenB, start, end)) {
                return false;
            }
            start = end;
        }
        return true;
    }

    /** The same meaning and the same properties, each value the same by {@link #sameValue}. */
    private static boolean sameVertex(EntityVertex a, EntityVertex b) {
        if (a.getMeaningNid() != b.getMeaningNid()) {
            return false;
        }
        ImmutableLongObjectMap<Object> propertiesA = a.properties();
        ImmutableLongObjectMap<Object> propertiesB = b.properties();
        int sizeA = propertiesA == null ? 0 : propertiesA.size();
        int sizeB = propertiesB == null ? 0 : propertiesB.size();
        if (sizeA != sizeB) {
            return false;
        }
        if (sizeA == 0) {
            return true;
        }
        for (long key : propertiesA.keySet().toArray()) {
            if (!propertiesB.containsKey(key) || !sameValue(propertiesA.get(key), propertiesB.get(key))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Two components are the same when their nids are; anything else is the same when either
     * finds the other equal, so a value whose {@code equals} is narrower than its counterpart's
     * does not decide by the order it is asked in.
     */
    private static boolean sameValue(Object a, Object b) {
        if (a instanceof ComponentWithNid componentA && b instanceof ComponentWithNid componentB) {
            return componentA.nid() == componentB.nid();
        }
        return Objects.equals(a, b) || Objects.equals(b, a);
    }

    /** Pairs the children of one hash group; greedy is exact because isomorphism is an equivalence. */
    private static boolean matchGroup(DiTreeEntity a, long[] hashesA, int[] childrenA,
                                      DiTreeEntity b, long[] hashesB, int[] childrenB, int start, int end) {
        if (end - start == 1) {
            return match(a, hashesA, childrenA[start], b, hashesB, childrenB[start]);
        }
        boolean[] used = new boolean[end - start];
        for (int i = start; i < end; i++) {
            boolean paired = false;
            for (int j = start; j < end && !paired; j++) {
                if (!used[j - start] && match(a, hashesA, childrenA[i], b, hashesB, childrenB[j])) {
                    used[j - start] = true;
                    paired = true;
                }
            }
            if (!paired) {
                return false;
            }
        }
        return true;
    }

    /** A vertex's children, ordered by hash. */
    private static int[] byHash(ImmutableIntList children, long[] hashes) {
        Integer[] boxed = new Integer[children.size()];
        for (int i = 0; i < boxed.length; i++) {
            boxed[i] = children.get(i);
        }
        Arrays.sort(boxed, (x, y) -> Long.compare(hashes[x], hashes[y]));
        int[] sorted = new int[boxed.length];
        for (int i = 0; i < sorted.length; i++) {
            sorted[i] = boxed[i];
        }
        return sorted;
    }
}
