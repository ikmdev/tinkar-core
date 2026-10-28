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

import dev.ikm.tinkar.common.service.DiagnosticText;
import org.eclipse.collections.api.factory.primitive.LongObjectMaps;
import org.eclipse.collections.api.map.primitive.ImmutableLongObjectMap;
import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;

import java.util.Optional;

/**
 * The vertex kinds a profile admits: a dispatch from a vertex's meaning concept to the typed
 * node that stands for it, the profile's counterpart of {@code LogicalAxiomSemantic}. A meaning
 * the table lacks is not an error of the table: {@link ProfiledTree} records the vertex as
 * unprofiled and the profile's checks report it.
 *
 * @param <N> the profile's node type, usually a sealed interface
 */
public final class VertexKinds<N> {

    /** Makes the typed node for one vertex of a tree. */
    @FunctionalInterface
    public interface NodeFactory<N> {
        N make(ProfiledTree<N> tree, int vertexIndex);
    }

    private final ImmutableLongObjectMap<NodeFactory<N>> factoriesByMeaningNid;

    private VertexKinds(ImmutableLongObjectMap<NodeFactory<N>> factoriesByMeaningNid) {
        this.factoriesByMeaningNid = factoriesByMeaningNid;
    }

    public static <N> Builder<N> builder() {
        return new Builder<>();
    }

    /** The factory for a meaning, if the profile admits it. */
    public Optional<NodeFactory<N>> factoryFor(long meaningNid) {
        return Optional.ofNullable(factoriesByMeaningNid.get(meaningNid));
    }

    /** Whether the profile admits a vertex with this meaning. */
    public boolean admits(long meaningNid) {
        return factoriesByMeaningNid.containsKey(meaningNid);
    }

    public static final class Builder<N> {
        private final MutableLongObjectMap<NodeFactory<N>> factories = LongObjectMaps.mutable.empty();

        private Builder() {
        }

        /**
         * Admits a vertex kind.
         *
         * @param meaningNid the meaning concept a vertex of this kind carries
         * @param factory    what makes its typed node
         * @return this builder
         */
        public Builder<N> kind(long meaningNid, NodeFactory<N> factory) {
            if (factories.containsKey(meaningNid)) {
                throw new IllegalArgumentException("meaning " + DiagnosticText.component(meaningNid) + " is already a kind of this profile");
            }
            factories.put(meaningNid, factory);
            return this;
        }

        public VertexKinds<N> build() {
            return new VertexKinds<>(factories.toImmutable());
        }
    }
}
