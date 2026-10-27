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

import dev.ikm.tinkar.component.graph.Graph;
import dev.ikm.tinkar.component.graph.GraphAdaptorFactory;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;

import java.util.function.Function;

/**
 * Attaches a profile to the cached {@code adapt} hook every stored tree has, as
 * {@code LogicalExpressionAdaptorFactory} attaches the axiom stack: {@code tree.adapt(factory)}
 * reads the tree through the profile once and keeps the view with the tree.
 *
 * @param <T> the profile's tree view
 */
public final class ProfiledTreeFactory<T extends ProfiledTree<?>> implements GraphAdaptorFactory<T> {

    private final Function<DiTreeEntity, T> reader;

    /**
     * @param reader what reads a stored tree through the profile
     */
    public ProfiledTreeFactory(Function<DiTreeEntity, T> reader) {
        this.reader = reader;
    }

    @Override
    public T adapt(Graph graph) {
        if (graph instanceof DiTreeEntity tree) {
            return reader.apply(tree);
        }
        throw new IllegalArgumentException("a profile reads a directed tree, not " + graph.getClass().getSimpleName());
    }
}
