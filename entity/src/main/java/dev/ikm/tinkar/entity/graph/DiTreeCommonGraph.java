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

import dev.ikm.tinkar.entity.graph.DiTreeEditScript.Placed;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The common graph of any number of versions of one definition, read off the vertex UUIDs
 * (arch-vertex-identity-analysis; IKE-Network/ike-issues#1310):
 *
 * <ul>
 *   <li>{@link #commonVertices} -- the UUIDs live in every version;
 *   <li>{@link #contentCommonVertices} -- those whose meaning and properties are also equal in
 *       every version;
 *   <li>{@link #commonEdges} -- the parent-child UUID pairs present in every version;
 *   <li>{@link #commonTree} -- the content-common vertices reachable from the root through
 *       common edges.
 * </ul>
 *
 * <p>A vertex that moved is common but its edge is not, so it falls out of the common tree; a
 * vertex updated in one version is common but not content-common. Only the vertices reachable
 * from each version's root take part. The vertex and edge sets iterate depth first through the first version.
 *
 * @param commonVertices        the UUIDs live in every version
 * @param contentCommonVertices the common UUIDs whose content is equal in every version
 * @param commonEdges           the parent-child pairs present in every version
 * @param commonTree            the content-common UUIDs reachable from the root through common edges
 */
public record DiTreeCommonGraph(Set<UUID> commonVertices, Set<UUID> contentCommonVertices,
                                Set<Edge> commonEdges, Set<UUID> commonTree) {

    /**
     * A parent-child pair.
     *
     * @param parent the parent's UUID
     * @param child  the child's UUID
     */
    public record Edge(UUID parent, UUID child) {
    }

    /**
     * The common graph of the versions.
     *
     * @param versions at least one version of one definition, in any order
     * @return their common graph
     * @throws IllegalArgumentException when there are no versions, or a version holds a UUID twice
     */
    public static DiTreeCommonGraph of(List<DiTreeEntity> versions) {
        if (versions.isEmpty()) {
            throw new IllegalArgumentException("No versions");
        }
        List<Map<UUID, Placed>> placed = new ArrayList<>();
        versions.forEach(version -> placed.add(DiTreeEditScript.placed(version)));
        DiTreeEntity first = versions.getFirst();
        Map<UUID, Placed> firstPlaced = placed.getFirst();

        Set<UUID> common = new LinkedHashSet<>();
        Set<UUID> contentCommon = new LinkedHashSet<>();
        Set<Edge> edges = new LinkedHashSet<>();
        for (Placed vertex : firstPlaced.values()) {
            boolean everywhere = true;
            boolean sameContent = true;
            boolean sameParent = vertex.parent() != null;
            for (int v = 1; v < versions.size() && everywhere; v++) {
                Placed other = placed.get(v).get(vertex.uuid());
                if (other == null) {
                    everywhere = false;
                } else {
                    EntityVertex a = first.vertex(vertex.index());
                    EntityVertex b = versions.get(v).vertex(other.index());
                    sameContent &= a.getMeaningNid() == b.getMeaningNid()
                            && DiTreeEditScript.changedKeys(a.properties(), b.properties()).isEmpty();
                    sameParent &= vertex.parent() != null && vertex.parent().equals(other.parent());
                }
            }
            if (everywhere) {
                common.add(vertex.uuid());
                if (sameContent) {
                    contentCommon.add(vertex.uuid());
                }
                if (sameParent) {
                    edges.add(new Edge(vertex.parent(), vertex.uuid()));
                }
            }
        }

        Set<UUID> tree = new LinkedHashSet<>();
        UUID root = EntityVertexUuid.of(first.root());
        if (contentCommon.contains(root)) {
            Deque<Integer> next = new ArrayDeque<>();
            next.push(first.root().vertexIndex());
            tree.add(root);
            while (!next.isEmpty()) {
                int index = next.pop();
                UUID parent = EntityVertexUuid.of(first.vertex(index));
                first.successors(index).forEach(child -> {
                    UUID uuid = EntityVertexUuid.of(first.vertex(child));
                    if (contentCommon.contains(uuid) && edges.contains(new Edge(parent, uuid))) {
                        tree.add(uuid);
                        next.push(child);
                    }
                });
            }
        }
        return new DiTreeCommonGraph(Collections.unmodifiableSet(common), Collections.unmodifiableSet(contentCommon),
                Collections.unmodifiableSet(edges), Collections.unmodifiableSet(tree));
    }
}
