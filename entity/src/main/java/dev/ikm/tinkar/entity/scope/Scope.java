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
package dev.ikm.tinkar.entity.scope;

import dev.ikm.tinkar.common.service.internal.EntityStore;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.boundary.DataKind;
import dev.ikm.tinkar.entity.boundary.EntityBoundaries;
import dev.ikm.tinkar.entity.boundary.References;
import org.eclipse.collections.api.factory.primitive.LongLists;
import org.eclipse.collections.api.factory.primitive.LongObjectMaps;
import org.eclipse.collections.api.factory.primitive.LongSets;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;
import org.eclipse.collections.api.list.primitive.MutableLongList;
import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;
import org.eclipse.collections.api.set.primitive.MutableLongSet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * A scope of instance or ephemeral data (IKE-Network/ike-issues#1313; design
 * {@code design-2026-10-20-instance-data}): what a scope operation exports, imports or deletes
 * whole. It is resolved from its roots, a document's own pattern or its anchor, as everything of
 * the roots' kind they lead to: a pattern's elements, and every semantic of that kind citing a
 * member as its referenced component, transitively, when its pattern is the scope's own or
 * knowledge. A document's vertices, its git source, its annotations and
 * the names on its pattern are one scope.
 *
 * <p>Resolution reads only the scope: each member's elements and citations come from the store's
 * pattern and referencing indexes, and what a member references from its own versions. What
 * crosses the boundary is kept by direction. An <em>incoming</em> reference, a component outside
 * the scope citing a member, blocks a delete, since it would be left pointing at nothing; an
 * <em>outgoing</em> one, a member referencing data outside the scope, blocks an export, since the
 * imported scope would point at what the target store lacks; a <em>dangling</em> one, to a
 * component the store no longer holds, blocks an export too. A reference into the scope carried in
 * a field of a component outside it is not in the referencing index, so resolution cannot see it;
 * {@link ScopeOperations#delete(Scope)} finds those in the pass it makes anyway.
 *
 * @param kind      the scope's kind, never knowledge
 * @param roots     the roots it was resolved from
 * @param members   its concepts, patterns and semantics, in the order an export writes them:
 *                  concepts, then patterns, then semantics after what they reference
 * @param stamps    the STAMPs its versions are written under
 * @param knowledge the knowledge it references, which a target store must hold to import it:
 *                  what its versions reference, and its STAMPs' authors, modules and paths
 * @param incoming  components outside the scope citing a member, in words
 * @param outgoing  members referencing instance or ephemeral data outside the scope, in words
 * @param dangling  members referencing components the store does not hold, in words
 */
public record Scope(DataKind kind, ImmutableLongList roots, ImmutableLongList members, ImmutableLongList stamps,
                    ImmutableLongList knowledge, List<String> incoming, List<String> outgoing, List<String> dangling) {

    /** @return whether nothing crosses the scope's boundary in either direction, so it can be exported and deleted */
    public boolean closed() {
        return incoming.isEmpty() && outgoing.isEmpty() && dangling.isEmpty();
    }

    /** @return whether nothing the index shows cites into the scope, so a delete may go ahead to its own check */
    public boolean deletable() {
        return incoming.isEmpty();
    }

    /** @return whether nothing the scope references is missing from what an export carries and the manifest names */
    public boolean exportable() {
        return outgoing.isEmpty() && dangling.isEmpty();
    }

    /** @return whether the scope holds the component */
    public boolean contains(long nid) {
        return members.contains(nid) || stamps.contains(nid);
    }

    /**
     * Resolves the scope of roots.
     *
     * @param roots the roots: a document's own pattern, an anchor, any instance or ephemeral component
     * @return the scope
     * @throws IllegalArgumentException when a root is knowledge, is absent, or the roots differ in kind
     */
    public static Scope of(long... roots) {
        if (roots.length == 0) {
            throw new IllegalArgumentException("A scope needs at least one root");
        }
        MutableLongObjectMap<DataKind> kinds = LongObjectMaps.mutable.empty();
        DataKind kind = null;
        for (long root : roots) {
            if (read(root).isEmpty()) {
                throw new IllegalArgumentException("The store holds no component " + root);
            }
            DataKind rootKind = kindOf(root, kinds);
            if (rootKind == DataKind.KNOWLEDGE) {
                throw new IllegalArgumentException(describe(root) + " is knowledge; a scope holds instance or ephemeral data");
            }
            if (kind != null && kind != rootKind) {
                throw new IllegalArgumentException("The roots differ in kind: " + kind.word() + " and " + rootKind.word());
            }
            kind = rootKind;
        }
        EntityStore store = EntityStore.current();
        MutableLongSet members = LongSets.mutable.empty();
        List<String> incoming = new ArrayList<>();
        List<String> outgoing = new ArrayList<>();
        List<String> dangling = new ArrayList<>();
        Deque<Long> queue = new ArrayDeque<>();
        MutableLongList concepts = LongLists.mutable.empty();
        MutableLongList patterns = LongLists.mutable.empty();
        MutableLongList semantics = LongLists.mutable.empty();
        for (long root : roots) {
            if (members.add(root)) {
                queue.add(root);
            }
        }
        DataKind scopeKind = kind;
        while (!queue.isEmpty()) {
            long nid = queue.poll();
            Optional<? extends Entity<? extends EntityVersion>> held = read(nid);
            if (held.isEmpty()) {
                continue;
            }
            switch (held.get()) {
                case ConceptEntity<?> _ -> concepts.add(nid);
                case PatternEntity<?> _ -> {
                    patterns.add(nid);
                    store.forEachSemanticNidOfPattern(nid, element -> {
                        if (members.add(element)) {
                            queue.add(element);
                        }
                    });
                }
                case SemanticEntity<?> _ -> semantics.add(nid);
                default -> { }
            }
            store.forEachSemanticNidForComponent(nid, citing -> {
                if (members.contains(citing)) {
                    return;
                }
                DataKind citingKind = kindOf(citing, kinds);
                long citingPattern = read(citing).map(e -> e instanceof SemanticEntity<?> sem ? sem.patternNid() : 0L).orElse(0L);
                // A citation joins the scope when it is of the scope's kind and its pattern is the
                // scope's own or knowledge (a name, a comment, a git source); from another scope's
                // own pattern, it crosses scopes.
                if (citingKind == scopeKind && (members.contains(citingPattern) || kindOf(citingPattern, kinds) == DataKind.KNOWLEDGE)) {
                    members.add(citing);
                    queue.add(citing);
                } else {
                    incoming.add(describe(citing) + ", " + citingKind.word() + ", cites " + describe(nid) + " in the scope");
                }
            });
        }
        MutableLongSet stamps = LongSets.mutable.empty();
        MutableLongSet knowledge = LongSets.mutable.empty();
        members.forEach(nid -> read(nid).ifPresent(entity -> {
            for (EntityVersion version : entity.versions()) {
                stamps.add(version.stampNid());
                References.of(version, (where, target) -> {
                    if (members.contains(target) || target == 0) {
                        return;
                    }
                    if (read(target).isEmpty()) {
                        dangling.add(describe(nid) + " references " + target + ", which the store does not hold, by its " + where);
                        return;
                    }
                    DataKind targetKind = kindOf(target, kinds);
                    if (targetKind == DataKind.KNOWLEDGE) {
                        knowledge.add(target);
                    } else {
                        outgoing.add(describe(nid) + " references " + describe(target) + ", " + targetKind.word()
                                + " outside the scope, by its " + where);
                    }
                });
            }
        }));
        stamps.forEach(stamp -> read(stamp).ifPresent(entity -> {
            if (entity instanceof StampEntity<?> stampEntity) {
                knowledge.add(stampEntity.authorNid());
                knowledge.add(stampEntity.moduleNid());
                knowledge.add(stampEntity.pathNid());
            }
        }));
        MutableLongList ordered = LongLists.mutable.empty();
        ordered.addAll(concepts);
        ordered.addAll(patterns);
        ordered.addAll(semantics);
        return new Scope(scopeKind, LongLists.immutable.of(roots), ordered.toImmutable(), stamps.toSortedList().toImmutable(),
                knowledge.toSortedList().toImmutable(), List.copyOf(incoming), List.copyOf(outgoing), List.copyOf(dangling));
    }

    private static DataKind kindOf(long nid, MutableLongObjectMap<DataKind> kinds) {
        return kinds.getIfAbsentPut(nid, () -> EntityBoundaries.kindOf(nid));
    }

    private static Optional<? extends Entity<? extends EntityVersion>> read(long nid) {
        try {
            return EntityHandle.get(nid).entity();
        } catch (RuntimeException absent) {
            return Optional.empty();
        }
    }

    /** A component in words: its kind and public id. */
    static String describe(long nid) {
        return read(nid).map(e -> switch (e) {
            case ConceptEntity<?> _ -> "concept ";
            case PatternEntity<?> _ -> "pattern ";
            case SemanticEntity<?> _ -> "semantic ";
            case StampEntity<?> _ -> "STAMP ";
            default -> "";
        } + e.publicId().idString()).orElse("absent component " + nid);
    }
}
