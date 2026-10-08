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
package dev.ikm.tinkar.reasoner.service;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.collections.api.block.procedure.primitive.LongObjectProcedure;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;
import org.eclipse.collections.api.map.primitive.ImmutableLongObjectMap;
import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.primitive.ImmutableLongSet;
import org.eclipse.collections.impl.factory.primitive.LongLists;
import org.eclipse.collections.impl.factory.primitive.LongObjectMaps;
import org.eclipse.collections.impl.factory.primitive.LongSets;

import dev.ikm.tinkar.common.binary.Decoder;
import dev.ikm.tinkar.common.binary.DecoderInput;
import dev.ikm.tinkar.common.binary.Encodable;
import dev.ikm.tinkar.common.binary.Encoder;
import dev.ikm.tinkar.common.binary.EncoderOutput;
import dev.ikm.tinkar.coordinate.view.ViewCoordinateRecord;

public class ClassifierResults implements Encodable {

    public static final int marshalVersion = 2;
    /**
     * Set of concepts potentially affected by the last classification.
     */
    private final ImmutableLongList classificationConceptSet;

    private final ImmutableLongList conceptsWithInferredChanges;

    private final ImmutableLongList conceptsWithNavigationChanges;
    /**
     * The equivalent sets.
     */
    private final ImmutableSet<ImmutableLongList> equivalentSets;

    /**
     * The commit record.
     */
    private final ViewCoordinateRecord viewCoordinate;
    //A map of a concept nid, to a HashSet of int arrays, where each int[] is a cycle present on the concept.
    private final ImmutableLongObjectMap<Set<long[]>> conceptsWithCycles;
    private final ImmutableLongSet orphanedConcepts;

    private ClassifierResults(DecoderInput data) {
        this.classificationConceptSet = LongLists.immutable.of(data.readNidArray());
        this.conceptsWithInferredChanges = LongLists.immutable.of(data.readNidArray());
        this.conceptsWithNavigationChanges = LongLists.immutable.of(data.readNidArray());
        int equivalentSetSize = data.readInt();
        MutableSet<ImmutableLongList> equivalentMutibleSets = Sets.mutable.ofInitialCapacity(equivalentSetSize);
        for (int i = 0; i < equivalentSetSize; i++) {
            equivalentMutibleSets.add(LongLists.immutable.of(data.readNidArray()));
        }
        this.equivalentSets = equivalentMutibleSets.toImmutable();
        if (data.readBoolean()) {
            int cycleMapSize = data.readInt();
            MutableLongObjectMap<Set<long[]>> conceptsWithCyclesMutable = LongObjectMaps.mutable.ofInitialCapacity(cycleMapSize);
            for (int i = 0; i < cycleMapSize; i++) {
                int key = data.readInt();
                int setCount = data.readInt();
                Set<long[]> cycleSets = new TreeSet<>();
                for (int j = 0; j < setCount; j++) {
                    cycleSets.add(data.readNidArray());
                }
                conceptsWithCyclesMutable.put(key, cycleSets);
            }
            this.conceptsWithCycles = conceptsWithCyclesMutable.toImmutable();
        } else {
            this.conceptsWithCycles = LongObjectMaps.immutable.empty();
        }
        this.orphanedConcepts = LongSets.immutable.of(data.readNidArray());
        this.viewCoordinate = ViewCoordinateRecord.decode(data);
    }

    /**
     * Instantiates a new classifier results.
     *
     * @param classificationConceptSet the affected concepts
     * @param equivalentSets           the equivalent sets
     * @param viewCoordinateRecord
     */
    public ClassifierResults(ImmutableLongList classificationConceptSet,
                             ImmutableLongList conceptsWithInferredChanges,
                             ImmutableLongList conceptsWithNavigationChanges,
                             Set<ImmutableLongList> equivalentSets,
                             ViewCoordinateRecord viewCoordinateRecord) {
        this.classificationConceptSet = classificationConceptSet;
        this.conceptsWithInferredChanges = conceptsWithInferredChanges;
        this.conceptsWithNavigationChanges = conceptsWithNavigationChanges;
        MutableSet<ImmutableLongList> equivalentMutableSets = Sets.mutable.ofInitialCapacity(equivalentSets.size());
        for (ImmutableLongList set : equivalentSets) {
            equivalentMutableSets.add(LongLists.immutable.of(set.toSortedArray()));
        }

        this.equivalentSets = equivalentMutableSets.toImmutable();
        this.orphanedConcepts = LongSets.immutable.empty();
        this.conceptsWithCycles = LongObjectMaps.immutable.empty();
        this.viewCoordinate = viewCoordinateRecord;
        verifyCoordinates();
    }

    private void verifyCoordinates() {
        if (viewCoordinate.stampCoordinate().stampPosition().time() == Long.MAX_VALUE) {
            throw new IllegalStateException("Filter position time must reflect the actual commit time, not 'latest' (Long.MAX_VALUE) ");
        }
    }

    /**
     * This constructor is only intended to be used when a classification wasn't performed, because there were cycles present.
     *
     * @param conceptsWithCycles
     * @param orphans
     * @param viewCoordinateRecord
     */
    public ClassifierResults(ImmutableLongList classificationConceptSet,
                             ImmutableLongObjectMap<Set<long[]>> conceptsWithCycles,
                             ImmutableLongSet orphans,
                             ViewCoordinateRecord viewCoordinateRecord) {
        this.classificationConceptSet = classificationConceptSet;
        this.conceptsWithInferredChanges = LongLists.immutable.empty();
        this.conceptsWithNavigationChanges = LongLists.immutable.empty();
        this.equivalentSets = Sets.immutable.empty();
        this.conceptsWithCycles = conceptsWithCycles;
        this.orphanedConcepts = orphans;
        this.viewCoordinate = viewCoordinateRecord;
        verifyCoordinates();
    }

    @Decoder
    public static ClassifierResults decode(DecoderInput in) {
        return new ClassifierResults(in);
    }

    @Encoder
    public final void encode(EncoderOutput out) {
        out.writeInt(marshalVersion);
        out.writeNidArray(this.classificationConceptSet.toArray());
        out.writeNidArray(this.conceptsWithInferredChanges.toArray());
        out.writeNidArray(this.conceptsWithNavigationChanges.toArray());
        out.writeInt(equivalentSets.size());
        for (ImmutableLongList equivalentSet : equivalentSets) {
            out.writeNidArray(equivalentSet.toArray());
        }
        if (!conceptsWithCycles.isEmpty()) {
            out.writeBoolean(true);
            out.writeInt(conceptsWithCycles.size());
            conceptsWithCycles.forEachKeyValue(new LongObjectProcedure<Set<long[]>>() {
                @Override
                public void value(long conceptNid, Set<long[]> cycleNids) {
                    out.writeNid(conceptNid);
                    out.writeInt(cycleNids.size());
                    for (long[] cycle : cycleNids) {
                        out.writeNidArray(cycle);
                    }
                }
            });
        } else {
            out.writeBoolean(false);
        }
        out.writeNidArray(orphanedConcepts.toArray());
        this.viewCoordinate.encode(out);
    }

    @Override
    public String toString() {
        return "ClassifierResults{"
                + " classifiedConcepts=" + this.classificationConceptSet.size()
                + ", inferred changes=" + this.conceptsWithInferredChanges.size()
                + ", navigation changes=" + this.conceptsWithNavigationChanges.size()
                + ", equivalentSets="
                + this.equivalentSets.size() + ", Orphans detected=" + orphanedConcepts.size()
                + " Concepts with cycles=" + conceptsWithCycles.size() + '}';
    }

    public ImmutableLongList getClassificationConceptSet() {
        return this.classificationConceptSet;
    }

    public ImmutableLongList getConceptsWithNavigationChanges() {
        return this.conceptsWithNavigationChanges;
    }

    public ImmutableSet<ImmutableLongList> getEquivalentSets() {
        return this.equivalentSets;
    }

    public ImmutableLongObjectMap<Set<long[]>> getCycles() {
        return conceptsWithCycles;
    }

    public ImmutableLongSet getOrphans() {
        return orphanedConcepts.toImmutable();
    }

    public ViewCoordinateRecord getViewCoordinate() {
        return viewCoordinate;
    }

    public Instant getCommitTime() {
        return this.viewCoordinate.stampCoordinate().stampPosition().instant();
    }

    public ImmutableLongList getConceptsWithInferredChanges() {
        return conceptsWithInferredChanges;
    }
}
