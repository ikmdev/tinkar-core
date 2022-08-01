package org.hl7.tinkar.entity.graph;

import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.map.primitive.ImmutableIntIntMap;
import org.eclipse.collections.api.map.primitive.ImmutableIntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.hl7.tinkar.entity.EntityVersion;

public class DiTreeVersion<V extends EntityVersion> extends DiTreeAbstract<VersionVertex<V>> {
    public DiTreeVersion(VersionVertex<V> root,
                         ImmutableList<VersionVertex<V>> vertexMap,
                         ImmutableIntObjectMap<ImmutableIntList> successorMap,
                         ImmutableIntIntMap predecessorMap) {
        super(root, vertexMap, successorMap, predecessorMap);
    }


    public static DiTreeVersion.Builder builder() {
        return new DiTreeVersion.Builder();
    }

    public static class Builder<V extends EntityVersion> extends DiTreeAbstract.Builder<VersionVertex<V>> {
        protected Builder() {
        }

        public <V extends EntityVertex> DiTreeVersion build() {

            MutableIntObjectMap<ImmutableIntList> intermediateSuccessorMap = IntObjectMaps.mutable.ofInitialCapacity(successorMap.size());
            successorMap.forEachKeyValue((vertex, successorList) -> intermediateSuccessorMap.put(vertex, successorList.toImmutable()));

            return new DiTreeVersion(root,
                    vertexMap.toImmutable(),
                    intermediateSuccessorMap.toImmutable(),
                    predecessorMap.toImmutable());
        }
    }

}
