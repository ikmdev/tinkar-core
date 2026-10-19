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

import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.terms.ComponentWithNid;
import dev.ikm.tinkar.terms.ConceptFacade;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.primitive.LongLists;
import org.eclipse.collections.api.factory.primitive.LongObjectMaps;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;
import org.eclipse.collections.api.list.primitive.MutableLongList;
import org.eclipse.collections.api.map.primitive.ImmutableLongObjectMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The edit script from one version of a definition tree to the next, read straight off the
 * vertex UUIDs (arch-vertex-identity-analysis; IKE-Network/ike-issues#1310):
 *
 * <ul>
 *   <li>{@link Insert} -- a UUID appears;
 *   <li>{@link Delete} -- a UUID disappears;
 *   <li>{@link Move} -- the same UUID under another parent;
 *   <li>{@link Update} -- the same UUID with another property.
 * </ul>
 *
 * <p>No matching is done: the write path settled identity when each version was written
 * ({@link DiTreeCorrelation}), so a role moved from a necessary set to a sufficient set is one
 * move, its filler travelling with it, and a filler refined to a more specific concept is one
 * update. A vertex both moved and updated has one of each. The identity rule
 * ({@link dev.ikm.tinkar.entity.graph.adaptor.axiom.IdentityRule}) guarantees a kept UUID changes
 * only non-identifying properties; this class reads whatever differs and does not check it.
 *
 * <p>Only the vertices reachable from each tree's root take part. Edits are ordered depth first
 * through the later tree (inserts, moves, updates), then through the earlier one (deletes).
 *
 * @param before the earlier version
 * @param after  the later version
 * @param edits  the edits from {@code before} to {@code after}
 */
public record DiTreeEditScript(DiTreeEntity before, DiTreeEntity after, ImmutableList<Edit> edits) {

    /** One action of the script. */
    public sealed interface Edit permits Insert, Delete, Move, Update {
        /** @return the UUID of the vertex the action is on */
        UUID uuid();
    }

    /**
     * A UUID appears.
     *
     * @param uuid   the vertex's UUID
     * @param index  its index in the later tree
     * @param parent its parent's UUID, null for a root
     */
    public record Insert(UUID uuid, int index, UUID parent) implements Edit {
    }

    /**
     * A UUID disappears.
     *
     * @param uuid   the vertex's UUID
     * @param index  its index in the earlier tree
     * @param parent its parent's UUID there, null for a root
     */
    public record Delete(UUID uuid, int index, UUID parent) implements Edit {
    }

    /**
     * The same UUID under another parent; its subtree travels with it.
     *
     * @param uuid        the vertex's UUID
     * @param beforeIndex its index in the earlier tree
     * @param afterIndex  its index in the later tree
     * @param fromParent  its parent's UUID in the earlier tree, null for a root
     * @param toParent    its parent's UUID in the later tree, null for a root
     */
    public record Move(UUID uuid, int beforeIndex, int afterIndex, UUID fromParent, UUID toParent) implements Edit {
    }

    /**
     * The same UUID with another meaning or other properties.
     *
     * @param uuid           the vertex's UUID
     * @param beforeIndex    its index in the earlier tree
     * @param afterIndex     its index in the later tree
     * @param meaningChanged whether the meaning differs (which the identity rule forbids)
     * @param propertyKeys   the nids of the property keys whose values differ, added or removed
     */
    public record Update(UUID uuid, int beforeIndex, int afterIndex, boolean meaningChanged,
                         ImmutableLongList propertyKeys) implements Edit {
    }

    /**
     * The edit script between two versions, by vertex UUID.
     *
     * @param before the earlier version
     * @param after  the later version
     * @return the script; empty when every UUID keeps its parent and its content
     * @throws IllegalArgumentException when a tree holds a UUID twice
     */
    public static DiTreeEditScript between(DiTreeEntity before, DiTreeEntity after) {
        Map<UUID, Placed> was = placed(before);
        Map<UUID, Placed> is = placed(after);
        MutableList<Edit> edits = Lists.mutable.empty();
        for (Placed now : is.values()) {
            Placed then = was.get(now.uuid);
            if (then == null) {
                edits.add(new Insert(now.uuid, now.index, now.parent));
                continue;
            }
            if (!Objects.equals(then.parent, now.parent)) {
                edits.add(new Move(now.uuid, then.index, now.index, then.parent, now.parent));
            }
            EntityVertex earlier = before.vertex(then.index);
            EntityVertex later = after.vertex(now.index);
            boolean meaningChanged = earlier.getMeaningNid() != later.getMeaningNid();
            ImmutableLongList keys = changedKeys(earlier.properties(), later.properties());
            if (meaningChanged || !keys.isEmpty()) {
                edits.add(new Update(now.uuid, then.index, now.index, meaningChanged, keys));
            }
        }
        for (Placed then : was.values()) {
            if (!is.containsKey(then.uuid)) {
                edits.add(new Delete(then.uuid, then.index, then.parent));
            }
        }
        return new DiTreeEditScript(before, after, edits.toImmutable());
    }

    /**
     * The edit script between two stored versions of a definition. Versions written since vertex
     * identity hold the UUIDs the write path settled, and their script is {@link #between}. Two
     * versions that share no UUID at all were written before it (every vertex minted afresh), so
     * the later one is first correlated against the earlier one, as the write path now would.
     *
     * @param before the earlier version
     * @param after  the later version
     * @return the script
     */
    public static DiTreeEditScript ofVersions(DiTreeEntity before, DiTreeEntity after) {
        if (shareNoUuid(before, after)) {
            after = DiTreeCorrelation.correlate(List.of(before), after).tree();
        }
        return between(before, after);
    }

    /** @return whether there are no edits */
    public boolean isEmpty() {
        return edits.isEmpty();
    }

    /** @return the inserts, in script order */
    public ImmutableList<Insert> inserts() {
        return edits.selectInstancesOf(Insert.class);
    }

    /** @return the deletes, in script order */
    public ImmutableList<Delete> deletes() {
        return edits.selectInstancesOf(Delete.class);
    }

    /** @return the moves, in script order */
    public ImmutableList<Move> moves() {
        return edits.selectInstancesOf(Move.class);
    }

    /** @return the updates, in script order */
    public ImmutableList<Update> updates() {
        return edits.selectInstancesOf(Update.class);
    }

    /** @return the inserts whose parent is not itself inserted: the roots of what was added */
    public ImmutableList<Insert> insertRoots() {
        Set<UUID> inserted = inserts().collect(Insert::uuid).toSet();
        return inserts().reject(insert -> insert.parent != null && inserted.contains(insert.parent));
    }

    /** @return the deletes whose parent is not itself deleted: the roots of what was removed */
    public ImmutableList<Delete> deleteRoots() {
        Set<UUID> deleted = deletes().collect(Delete::uuid).toSet();
        return deletes().reject(delete -> delete.parent != null && deleted.contains(delete.parent));
    }

    /**
     * The script as text for a change view, one section per kind of action, empty when there are
     * no edits. An added or removed subtree is shown once, from its root, without the vertices
     * beneath it that moved in or out (they are shown as moves); a moved vertex is shown with
     * its subtree and the path of meanings to its old and new parents.
     *
     * @return the text
     */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        if (!moves().isEmpty()) {
            sb.append("\nMoves: \n\n");
            for (Move move : moves()) {
                sb.append("  ").append(after.fragmentToString(after.vertex(move.afterIndex)));
                sb.append("    from: ").append(pathText(before, move.beforeIndex)).append("\n");
                sb.append("    to:   ").append(pathText(after, move.afterIndex)).append("\n\n");
            }
        }
        if (!updates().isEmpty()) {
            sb.append("\nUpdates: \n\n");
            for (Update update : updates()) {
                EntityVertex earlier = before.vertex(update.beforeIndex);
                EntityVertex later = after.vertex(update.afterIndex);
                sb.append("  ").append(PrimitiveData.text(later.getMeaningNid()));
                sb.append(" in ").append(pathText(after, update.afterIndex)).append("\n");
                if (update.meaningChanged) {
                    sb.append("    •meaning: ").append(PrimitiveData.text(earlier.getMeaningNid()))
                            .append(" ➞ ").append(PrimitiveData.text(later.getMeaningNid())).append("\n");
                }
                update.propertyKeys.forEach(key -> sb.append("    •").append(PrimitiveData.text(key)).append(": ")
                        .append(valueText(earlier.properties(), key)).append(" ➞ ")
                        .append(valueText(later.properties(), key)).append("\n"));
                sb.append("\n");
            }
        }
        if (!inserts().isEmpty()) {
            Set<UUID> inserted = inserts().collect(Insert::uuid).toSet();
            sb.append("\nAdditions: \n\n");
            for (Insert insert : insertRoots()) {
                sb.append("  ").append(fragmentText(after, insert.index, inserted)).append("\n");
            }
        }
        if (!deletes().isEmpty()) {
            Set<UUID> deleted = deletes().collect(Delete::uuid).toSet();
            sb.append("\nDeletions: \n\n");
            for (Delete delete : deleteRoots()) {
                sb.append("  ").append(fragmentText(before, delete.index, deleted)).append("\n");
            }
        }
        return sb.toString();
    }

    // ----- reading the trees -----

    /** A reachable vertex: its UUID, index and parent UUID. */
    record Placed(UUID uuid, int index, UUID parent) {
    }

    /** Every reachable vertex by UUID, depth first from the root, each with its parent's UUID. */
    static Map<UUID, Placed> placed(DiTreeEntity tree) {
        Map<UUID, Placed> placed = new LinkedHashMap<>();
        place(tree, tree.root().vertexIndex(), null, placed);
        return placed;
    }

    private static void place(DiTreeEntity tree, int index, UUID parent, Map<UUID, Placed> placed) {
        UUID uuid = EntityVertexUuid.of(tree.vertex(index));
        if (placed.putIfAbsent(uuid, new Placed(uuid, index, parent)) != null) {
            throw new IllegalArgumentException("Vertex UUID " + uuid + " appears twice in " + tree);
        }
        ImmutableIntList children = tree.successors(index);
        for (int i = 0; i < children.size(); i++) {
            place(tree, children.get(i), uuid, placed);
        }
    }

    private static boolean shareNoUuid(DiTreeEntity before, DiTreeEntity after) {
        Set<UUID> earlier = placed(before).keySet();
        return placed(after).keySet().stream().noneMatch(earlier::contains);
    }

    /** The keys whose values differ, by nid for components; added and removed keys included. */
    static ImmutableLongList changedKeys(ImmutableLongObjectMap<Object> a, ImmutableLongObjectMap<Object> b) {
        ImmutableLongObjectMap<Object> earlier = a == null ? LongObjectMaps.immutable.empty() : a;
        ImmutableLongObjectMap<Object> later = b == null ? LongObjectMaps.immutable.empty() : b;
        MutableLongList keys = LongLists.mutable.empty();
        for (long key : earlier.keySet().toSortedArray()) {
            if (!later.containsKey(key) || !sameValue(earlier.get(key), later.get(key))) {
                keys.add(key);
            }
        }
        for (long key : later.keySet().toSortedArray()) {
            if (!earlier.containsKey(key)) {
                keys.add(key);
            }
        }
        return keys.toImmutable();
    }

    /** As in {@link DiTreeIsomorphism}: components by nid, anything else by either's equals. */
    private static boolean sameValue(Object a, Object b) {
        if (a instanceof ComponentWithNid componentA && b instanceof ComponentWithNid componentB) {
            return componentA.nid() == componentB.nid();
        }
        return Objects.equals(a, b) || b != null && b.equals(a);
    }

    // ----- text -----

    /** The meanings from beneath the root down to a vertex's parent, e.g. "Necessary set ➞ And". */
    private static String pathText(DiTreeEntity tree, int index) {
        List<String> meanings = new ArrayList<>();
        int parent = tree.predecessor(index).orElse(-1);
        while (parent >= 0 && parent != tree.root().vertexIndex()) {
            meanings.addFirst(PrimitiveData.text(tree.vertex(parent).getMeaningNid()));
            parent = tree.predecessor(parent).orElse(-1);
        }
        return meanings.isEmpty() ? PrimitiveData.text(tree.root().getMeaningNid()) : String.join(" ➞ ", meanings);
    }

    private static String valueText(ImmutableLongObjectMap<Object> properties, long key) {
        if (properties == null || !properties.containsKey(key)) {
            return "∅";
        }
        Object value = properties.get(key);
        return value instanceof ConceptFacade concept ? PrimitiveData.text(concept.nid()) : String.valueOf(value);
    }

    /** A subtree as the tree prints it, leaving out the vertices beneath it that are not in {@code shown}. */
    private static String fragmentText(DiTreeEntity tree, int index, Set<UUID> shown) {
        StringBuilder sb = new StringBuilder();
        fragmentText(tree, index, shown, 1, sb);
        return sb.toString();
    }

    private static void fragmentText(DiTreeEntity tree, int index, Set<UUID> shown, int depth, StringBuilder sb) {
        EntityVertex vertex = tree.vertex(index);
        sb.append(vertex.toGraphFormatString("  ".repeat(depth), "", tree));
        ImmutableIntList children = tree.successors(index);
        for (int i = 0; i < children.size(); i++) {
            int child = children.get(i);
            if (shown.contains(EntityVertexUuid.of(tree.vertex(child)))) {
                fragmentText(tree, child, shown, depth + 1, sb);
            } else {
                sb.append("  ".repeat(depth + 1)).append(" [").append(child).append("] ")
                        .append(PrimitiveData.text(tree.vertex(child).getMeaningNid())).append(" (moved)\n");
            }
        }
    }
}
