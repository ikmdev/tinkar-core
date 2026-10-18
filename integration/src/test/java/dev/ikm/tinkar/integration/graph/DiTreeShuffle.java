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
package dev.ikm.tinkar.integration.graph;

import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.map.primitive.MutableIntIntMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.primitive.IntIntMaps;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.eclipse.collections.impl.factory.primitive.LongObjectMaps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.IntFunction;

/**
 * Copies of a tree that differ from it only in what is not supposed to matter: vertex indexes,
 * child order, and (for an incoming tree that carries no identity) vertex UUIDs.
 *
 * <p>This is the test-time counterpart of Java's randomized iteration order for unordered
 * collections: anything that answers differently for a shuffled copy depends on an order it
 * must not depend on (arch-vertex-identity-verification, "Shuffle and compare"). It belongs
 * in tests and validation, never in production output.
 */
final class DiTreeShuffle {

    private DiTreeShuffle() {
    }

    /**
     * A copy of a tree with its vertices at other indexes.
     *
     * @param tree     the copy
     * @param newIndex for each index of the original tree, the index of its vertex in the copy
     */
    record Shuffled(DiTreeEntity tree, int[] newIndex) {

        /** For each index of the copy, the index of its vertex in the original tree. */
        int[] originalIndex() {
            int[] original = new int[newIndex.length];
            for (int i = 0; i < newIndex.length; i++) {
                original[newIndex[i]] = i;
            }
            return original;
        }
    }

    /**
     * The tree with its vertex indexes permuted and each vertex's children in a random order,
     * every vertex keeping its UUID, meaning and properties.
     */
    static Shuffled shuffle(DiTreeEntity tree, Random random) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < tree.vertexMap().size(); i++) {
            order.add(i);
        }
        Collections.shuffle(order, random);
        int[] newIndex = order.stream().mapToInt(Integer::intValue).toArray();
        return new Shuffled(copy(tree, newIndex, random, i -> tree.vertex(i).asUuidList().getFirst()), newIndex);
    }

    /**
     * The tree with a fresh UUID on every vertex, at the same indexes in the same order: what
     * a writer that carries no vertex identity hands the write path.
     */
    static DiTreeEntity strip(DiTreeEntity tree, Random random) {
        int[] identity = new int[tree.vertexMap().size()];
        for (int i = 0; i < identity.length; i++) {
            identity[i] = i;
        }
        return copy(tree, identity, null, i -> uuid(random));
    }

    /** The tree with each vertex given the UUID asked for, at the same indexes in the same order. */
    static DiTreeEntity withUuids(DiTreeEntity tree, IntFunction<UUID> uuidOfIndex) {
        int[] identity = new int[tree.vertexMap().size()];
        for (int i = 0; i < identity.length; i++) {
            identity[i] = i;
        }
        return copy(tree, identity, null, uuidOfIndex);
    }

    /** A random UUID from the given source, so a seeded test is repeatable. */
    static UUID uuid(Random random) {
        return new UUID(random.nextLong(), random.nextLong());
    }

    /** The vertex at an index's UUID. */
    static UUID uuidAt(DiTreeEntity tree, int index) {
        return tree.vertex(index).asUuidList().getFirst();
    }

    /**
     * Copies the tree, moving the vertex at index i to newIndex[i] with the UUID asked for,
     * children in their order or shuffled.
     */
    private static DiTreeEntity copy(DiTreeEntity tree, int[] newIndex, Random childOrder, IntFunction<UUID> uuidOfIndex) {
        int size = tree.vertexMap().size();
        EntityVertex[] vertices = new EntityVertex[size];
        MutableIntObjectMap<ImmutableIntList> successors = IntObjectMaps.mutable.empty();
        MutableIntIntMap predecessors = IntIntMaps.mutable.empty();
        for (int i = 0; i < size; i++) {
            EntityVertex original = tree.vertex(i);
            EntityVertex vertex = EntityVertex.make(uuidOfIndex.apply(i), original.getMeaningNid());
            MutableLongObjectMap<Object> properties = LongObjectMaps.mutable.empty();
            original.properties().forEachKeyValue(properties::put);
            vertex.setProperties(properties);
            int at = newIndex[i];
            vertex.setVertexIndex(at);
            vertices[at] = vertex;

            ImmutableIntList children = tree.successorMap().get(i);
            if (children != null && !children.isEmpty()) {
                MutableIntList moved = IntLists.mutable.empty();
                children.forEach(child -> moved.add(newIndex[child]));
                if (childOrder != null) {
                    moved.shuffleThis(childOrder);
                }
                successors.put(at, moved.toImmutable());
                moved.forEach(child -> predecessors.put(child, at));
            }
        }
        return new DiTreeEntity(vertices[newIndex[tree.root().vertexIndex()]], Lists.immutable.of(vertices),
                successors.toImmutable(), predecessors.toImmutable());
    }
}
