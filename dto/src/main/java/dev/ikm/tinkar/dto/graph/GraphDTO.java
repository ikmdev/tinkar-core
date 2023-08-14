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
package dev.ikm.tinkar.dto.graph;

import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.map.primitive.ImmutableIntObjectMap;
import dev.ikm.tinkar.component.graph.Graph;
import dev.ikm.tinkar.component.graph.GraphAdaptorFactory;

public record GraphDTO(ImmutableList<VertexDTO> vertexMap,
                       ImmutableIntObjectMap<ImmutableIntList> successorMap)
        implements Graph<VertexDTO>, GraphDefaults {

    private static final int LOCAL_MARSHAL_VERSION = 3;

    @Override
    public <A> A adapt(GraphAdaptorFactory<A> adaptorFactory) {
        throw new UnsupportedOperationException("Adaptors are ephemeral, and are not transfer objects");
    }

    @Override
    public ImmutableIntList successors(int vertexIndex) {
        return successorMap.get(vertexIndex);
    }

}
