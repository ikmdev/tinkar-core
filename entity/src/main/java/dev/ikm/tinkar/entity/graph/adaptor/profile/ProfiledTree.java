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
package dev.ikm.tinkar.entity.graph.adaptor.profile;

import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.terms.EntityFacade;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.primitive.IntLists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * A directed tree read through a profile: the typed view of a property graph held in one
 * {@code DITREE} field, the generic layer the EL++ axiom stack's {@code LogicalExpression} is a
 * hand-written instance of. The stored tree stays the truth; this class gives each vertex the
 * typed node its meaning concept selects through the profile's {@link VertexKinds}, keeps the
 * nodes by vertex index so children and parents resolve in constant time, and records the
 * vertices no kind admits rather than refusing the tree, so a profile's checks can name them.
 *
 * <p>A profile extends this class with its node type, its kinds and its checks, and reads vertex
 * properties through the typed accessors here: a concept-valued property is stored as the
 * concept's nid or as a facade, a number as any {@link Number}, a flag as a {@link Boolean}.
 *
 * @param <N> the profile's node type, usually a sealed interface
 */
public abstract class ProfiledTree<N> {

    private final DiTreeEntity source;
    private final ImmutableList<N> nodes;
    private final ImmutableIntList unprofiled;

    /**
     * Adapts every vertex of the tree through the kinds.
     *
     * @param source the stored tree
     * @param kinds  the profile's vertex kinds
     */
    protected ProfiledTree(DiTreeEntity source, VertexKinds<N> kinds) {
        this.source = source;
        int count = source.vertexCount();
        MutableList<N> adapted = Lists.mutable.ofInitialCapacity(count);
        MutableIntList missing = IntLists.mutable.empty();
        for (int index = 0; index < count; index++) {
            EntityVertex vertex = source.vertex(index);
            if (vertex == null) {
                adapted.add(null);
                continue;
            }
            Optional<VertexKinds.NodeFactory<N>> factory = kinds.factoryFor(vertex.getMeaningNid());
            if (factory.isPresent()) {
                adapted.add(factory.get().make(this, index));
            } else {
                adapted.add(null);
                missing.add(index);
            }
        }
        this.nodes = adapted.toImmutable();
        this.unprofiled = missing.toImmutable();
    }

    /** The stored tree this view reads. */
    public DiTreeEntity source() {
        return source;
    }

    /** The vertex at an index. */
    public EntityVertex vertex(int vertexIndex) {
        return source.vertex(vertexIndex);
    }

    /** The index of the tree's root vertex. */
    public int rootIndex() {
        return source.root().vertexIndex();
    }

    /** The typed node of a vertex, empty where no kind admits its meaning. */
    public Optional<N> node(int vertexIndex) {
        return Optional.ofNullable(nodes.get(vertexIndex));
    }

    /** The typed node of a vertex, which the caller knows the profile admits. */
    public N nodeFast(int vertexIndex) {
        N node = nodes.get(vertexIndex);
        if (node == null) {
            throw new IllegalStateException("vertex " + vertexIndex + " has no node under this profile");
        }
        return node;
    }

    /** The typed node of the root, if the profile admits it. */
    public Optional<N> root() {
        return node(rootIndex());
    }

    /** The indexes of the vertices no kind of the profile admits, in index order. */
    public ImmutableIntList unprofiled() {
        return unprofiled;
    }

    /** The indexes of a vertex's children, in stored order. */
    public ImmutableIntList childIndexes(int vertexIndex) {
        return source.successors(vertexIndex);
    }

    /** The typed children of a vertex, in stored order, leaving out any the profile does not admit. */
    public ImmutableList<N> children(int vertexIndex) {
        MutableList<N> result = Lists.mutable.empty();
        childIndexes(vertexIndex).forEach(child -> {
            N node = nodes.get(child);
            if (node != null) {
                result.add(node);
            }
        });
        return result.toImmutable();
    }

    /** The typed children of a vertex that are of one kind, in stored order. */
    public <K extends N> ImmutableList<K> children(int vertexIndex, Class<K> kind) {
        MutableList<K> result = Lists.mutable.empty();
        childIndexes(vertexIndex).forEach(child -> {
            N node = nodes.get(child);
            if (kind.isInstance(node)) {
                result.add(kind.cast(node));
            }
        });
        return result.toImmutable();
    }

    /** The index of a vertex's parent, empty at the root. */
    public OptionalInt parentIndex(int vertexIndex) {
        return source.predecessor(vertexIndex);
    }

    /** Every typed node of one kind, in vertex-index order. */
    public <K extends N> ImmutableList<K> nodesOfType(Class<K> kind) {
        MutableList<K> result = Lists.mutable.empty();
        nodes.forEach(node -> {
            if (kind.isInstance(node)) {
                result.add(kind.cast(node));
            }
        });
        return result.toImmutable();
    }

    /** Every typed node, in vertex-index order, with no entry for an unprofiled vertex. */
    public List<N> nodes() {
        return nodes.select(node -> node != null).castToList();
    }

    // ── Typed property reads ─────────────────────────────────────────────────

    /** A concept-valued property: the concept's nid, whether stored as a nid or a facade. */
    public OptionalLong conceptProperty(int vertexIndex, long propertyKeyNid) {
        Object value = vertex(vertexIndex).propertyFast(propertyKeyNid);
        return switch (value) {
            case null -> OptionalLong.empty();
            case Long nid -> OptionalLong.of(nid);
            case Integer nid -> OptionalLong.of(nid);
            case EntityFacade facade -> OptionalLong.of(facade.nid());
            default -> throw new IllegalStateException("vertex " + vertexIndex + " property " + propertyKeyNid
                    + " is not a concept: " + value);
        };
    }

    /** A number-valued property. */
    public OptionalDouble numberProperty(int vertexIndex, long propertyKeyNid) {
        Object value = vertex(vertexIndex).propertyFast(propertyKeyNid);
        return switch (value) {
            case null -> OptionalDouble.empty();
            case Number number -> OptionalDouble.of(number.doubleValue());
            default -> throw new IllegalStateException("vertex " + vertexIndex + " property " + propertyKeyNid
                    + " is not a number: " + value);
        };
    }

    /** A flag-valued property; absent reads as the given default. */
    public boolean flagProperty(int vertexIndex, long propertyKeyNid, boolean absent) {
        Object value = vertex(vertexIndex).propertyFast(propertyKeyNid);
        return switch (value) {
            case null -> absent;
            case Boolean flag -> flag;
            default -> throw new IllegalStateException("vertex " + vertexIndex + " property " + propertyKeyNid
                    + " is not a flag: " + value);
        };
    }

    /** Whether a vertex carries a property at all. */
    public boolean hasProperty(int vertexIndex, long propertyKeyNid) {
        return vertex(vertexIndex).propertyFast(propertyKeyNid) != null;
    }

    /**
     * The findings of the profile's checks over this tree. Every unprofiled vertex is a finding;
     * a profile adds its own. An empty list means the tree is well formed under the profile.
     */
    public abstract ImmutableList<ProfileFinding> check();

    /** The findings every profile shares: a vertex no kind admits. */
    protected final void checkProfiled(MutableList<ProfileFinding> findings) {
        unprofiled.forEach(index -> findings.add(new ProfileFinding(index,
                "meaning " + vertex(index).getMeaningNid() + " is no kind of this profile")));
    }
}
