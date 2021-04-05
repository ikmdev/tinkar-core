/* 
 * Licensed under the Apache License, Version 2.0 (the "License");
 *
 * You may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributions from 2013-2017 where performed either by US government 
 * employees, or under US Veterans Health Administration contracts. 
 *
 * US Veterans Health Administration contributions by government employees
 * are work of the U.S. Government and are not subject to copyright
 * protection in the United States. Portions contributed by government 
 * employees are USGovWork (17USC §105). Not subject to copyright. 
 * 
 * Contribution by contractors to the US Veterans Health Administration
 * during this period are contractually contributed under the
 * Apache License, Version 2.0.
 *
 * See: https://www.usa.gov/government-works
 * 
 * Contributions prior to 2013:
 *
 * Copyright (C) International Health Terminology Standards Development Organisation.
 * Licensed under the Apache License, Version 2.0.
 *
 */



package org.hl7.tinkar.coordinate.stamp;

import java.util.Collection;
import java.util.Set;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.set.primitive.ImmutableIntSet;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.hl7.tinkar.common.binary.Decoder;
import org.hl7.tinkar.common.binary.DecoderInput;
import org.hl7.tinkar.common.binary.Encoder;
import org.hl7.tinkar.common.binary.EncoderOutput;
import org.hl7.tinkar.coordinate.ImmutableCoordinate;
import org.hl7.tinkar.terms.ConceptFacade;

/**
 * A filter that operates in coordinate with path coordinate and the version computer. After the version computer computes the
 * latest versions at a point on a path, the filter provides a non-interfering, stateless predicate to apply to each element
 * to determine if it should be included in the results set.
 * <p\>
 * Filters must be immutable.
 * <p\>
 * q: How does the stamp coordinate relate to a Stamp?
 * <p\>
 a: A Stamp is a unique combination of Status, Time, Author, Module, and Path...
 A stamp coordinate specifies a position on a  path, with a particular set of modules, and allowed state values.
 Author constraints are not handled by the stamp filter. If necessary, results can be post-processed.

 <p\>
 * Created by kec on 2/16/15.
 *
 */

@RecordBuilder
public record StampFilterRecord(StateSet allowedStates, StampPositionImmutable stampPosition, ImmutableIntSet moduleNids,
                                ImmutableIntSet excludedModuleNids, ImmutableIntList modulePriorityOrder)
        implements StampFilter, ImmutableCoordinate, StampFilterRecordBuilder.With {
    private static final int marshalVersion = 1;

    @Override
    @Encoder
    public void encode(EncoderOutput out) {
        this.allowedStates.encode(out);
        this.stampPosition.encode(out);
        out.writeNidArray(moduleNids.toArray());
        out.writeNidArray(excludedModuleNids.toArray());
        out.writeNidArray(modulePriorityOrder.toArray());
    }

    @Decoder
    public static StampFilterRecord decode(DecoderInput in) {
        int objectMarshalVersion = in.encodingFormatVersion();
        switch (objectMarshalVersion) {
            case marshalVersion:
                return new StampFilterRecord(StateSet.decode(in),
                        StampPositionImmutable.decode(in),
                        IntSets.immutable.of(in.readNidArray()),
                        IntSets.immutable.of(in.readNidArray()),
                        IntLists.immutable.of(in.readNidArray()));
            default:
                throw new UnsupportedOperationException("Unsupported version: " + objectMarshalVersion);
        }
    }
    public static StampFilterRecord make(StateSet allowedStates,
                                         StampPosition stampPosition,
                                         ImmutableIntSet moduleNids,
                                         ImmutableIntSet excludedModuleNids,
                                         ImmutableIntList modulePreferenceOrder) {
        return new StampFilterRecord(allowedStates, stampPosition.toStampPositionImmutable(),
                moduleNids, excludedModuleNids, modulePreferenceOrder);
    }

    public static StampFilterRecord make(StateSet allowedStates,
                                         StampPosition stampPosition,
                                         ImmutableIntSet moduleNids,
                                         ImmutableIntList modulePreferenceOrder) {
        return new StampFilterRecord(allowedStates, stampPosition.toStampPositionImmutable(),
                moduleNids, IntSets.immutable.empty(), modulePreferenceOrder);
    }

    /**
     * 
     * @param allowedStates
     * @param stampPosition
     * @param moduleNids - null is treated as an empty set, which allows any module
     * @return
     */
    public static StampFilterRecord make(StateSet allowedStates,
                                         StampPosition stampPosition,
                                         ImmutableIntSet moduleNids) {
        return new StampFilterRecord(allowedStates, stampPosition.toStampPositionImmutable(),
                moduleNids, IntSets.immutable.empty(), IntLists.immutable.empty());
    }

    public static StampFilterRecord make(StateSet allowedStates, int path,
                                         Set<ConceptFacade> modules) {
        ImmutableIntSet moduleNids = IntSets.immutable.of(modules.stream().mapToInt(value -> value.nid()).toArray());
        StampPositionImmutable stampPosition = StampPositionImmutable.make(Long.MAX_VALUE, path);

        return new StampFilterRecord(allowedStates,
                stampPosition, moduleNids, IntSets.immutable.empty(), IntLists.immutable.empty());
    }

    public static StampFilterRecord make(StateSet allowedStates, int path) {
        StampPositionImmutable stampPosition = StampPositionImmutable.make(Long.MAX_VALUE, path);

        return new StampFilterRecord(allowedStates,
                stampPosition,
                IntSets.immutable.empty(),
                IntSets.immutable.empty(),
                IntLists.immutable.empty());
    }

    public static StampFilterRecord make(StateSet allowedStates, StampPosition stampPosition) {
        return new StampFilterRecord(allowedStates,
                stampPosition.toStampPositionImmutable(),
                IntSets.immutable.empty(),
                IntSets.immutable.empty(),
                IntLists.immutable.empty());
    }

    @Override
    public String toString() {
        return "StampFilterRecord{" + toUserString() + "}";
    }

    public StampCalculator stampCalculator() {
        return StampCalculator.getCalculator(this);
    }

    @Override
    public StampFilterRecord withAllowedStates(StateSet stateSet) {
        return StampFilterRecordBuilder.With.super.withAllowedStates(stateSet);
    }

    @Override
    public StampFilterRecord withStampPositionTime(long stampPositionTime) {
        return make(this.allowedStates,
                StampPositionImmutable.make(stampPositionTime, this.stampPosition.getPathForPositionNid()),
                this.moduleNids, this.modulePriorityOrder);
    }

    public StampFilterRecord toStampFilterImmutable() {
        return this;
    }


    @Override
    public int pathNidForFilter() {
        return this.stampPosition.getPathForPositionNid();
    }


    @Override
    public StampFilterRecord withModules(Collection<ConceptFacade> modules) {
        MutableIntSet mis = modules == null ? IntSets.mutable.empty() : 
        IntSets.mutable.ofAll(modules.stream().mapToInt(concept -> concept.nid()));
         return make(this.allowedStates,
                this.stampPosition,
                mis.toImmutable(), this.excludedModuleNids, IntLists.immutable.empty());
    }

    @Override
    public StampFilterRecord withPath(org.hl7.tinkar.terms.ConceptFacade pathForPosition) {
        return make(this.allowedStates,
                StampPositionImmutable.make(this.stampPosition.time(), pathForPosition.nid()),
                this.moduleNids, this.excludedModuleNids, this.modulePriorityOrder);
    }

    @Override
    public StampFilterTemplateImmutable toStampFilterTemplateImmutable() {
        return StampFilterTemplateImmutable.make(this.allowedStates, this.moduleNids, this.excludedModuleNids, this.modulePriorityOrder);
    }
}

