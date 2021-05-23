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
import org.hl7.tinkar.common.binary.Decoder;
import org.hl7.tinkar.common.binary.DecoderInput;
import org.hl7.tinkar.common.binary.Encoder;
import org.hl7.tinkar.common.binary.EncoderOutput;
import org.hl7.tinkar.common.id.*;
import org.hl7.tinkar.coordinate.ImmutableCoordinate;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
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
public record StampCoordinateRecord(StateSet allowedStates, StampPositionRecord stampPosition, IntIdSet moduleNids,
                                    IntIdSet excludedModuleNids, IntIdList modulePriorityNidList)
        implements StampCoordinate, ImmutableCoordinate, StampCoordinateRecordBuilder.With {
    private static final int marshalVersion = 1;

    @Override
    @Encoder
    public void encode(EncoderOutput out) {
        this.allowedStates.encode(out);
        this.stampPosition.encode(out);
        out.writeNidArray(moduleNids.toArray());
        out.writeNidArray(excludedModuleNids.toArray());
        out.writeNidArray(modulePriorityNidList.toArray());
    }

    @Decoder
    public static StampCoordinateRecord decode(DecoderInput in) {
        int objectMarshalVersion = in.encodingFormatVersion();
        switch (objectMarshalVersion) {
            case marshalVersion:
                return new StampCoordinateRecord(StateSet.decode(in),
                        StampPositionRecord.decode(in),
                        IntIds.set.of(in.readNidArray()),
                        IntIds.set.of(in.readNidArray()),
                        IntIds.list.of(in.readNidArray()));
            default:
                throw new UnsupportedOperationException("Unsupported version: " + objectMarshalVersion);
        }
    }
    public static StampCoordinateRecord make(StateSet allowedStates,
                                             StampPosition stampPosition,
                                             IntIdSet moduleNids,
                                             IntIdSet excludedModuleNids,
                                             IntIdList modulePreferenceOrder) {
        return new StampCoordinateRecord(allowedStates, stampPosition.toStampPositionImmutable(),
                moduleNids, excludedModuleNids, modulePreferenceOrder);
    }

    public static StampCoordinateRecord make(StateSet allowedStates,
                                             StampPosition stampPosition,
                                             IntIdSet moduleNids,
                                             IntIdList modulePreferenceOrder) {
        return new StampCoordinateRecord(allowedStates, stampPosition.toStampPositionImmutable(),
                moduleNids, IntIds.set.empty(), modulePreferenceOrder);
    }

    /**
     * 
     * @param allowedStates
     * @param stampPosition
     * @param moduleNids - null is treated as an empty set, which allows any module
     * @return
     */
    public static StampCoordinateRecord make(StateSet allowedStates,
                                             StampPosition stampPosition,
                                             IntIdSet moduleNids) {
        return new StampCoordinateRecord(allowedStates, stampPosition.toStampPositionImmutable(),
                moduleNids, IntIds.set.empty(), IntIds.list.empty());
    }

    public static StampCoordinateRecord make(StateSet allowedStates, int path,
                                             Set<ConceptFacade> modules) {
        IntIdSet moduleNids = IntIds.set.of(modules.stream().mapToInt(value -> value.nid()).toArray());
        StampPositionRecord stampPosition = StampPositionRecord.make(Long.MAX_VALUE, path);

        return new StampCoordinateRecord(allowedStates,
                stampPosition, moduleNids, IntIds.set.empty(), IntIds.list.empty());
    }

    public static StampCoordinateRecord make(StateSet allowedStates, int path) {
        StampPositionRecord stampPosition = StampPositionRecord.make(Long.MAX_VALUE, path);

        return new StampCoordinateRecord(allowedStates,
                stampPosition,
                IntIds.set.empty(),
                IntIds.set.empty(),
                IntIds.list.empty());
    }

    public static StampCoordinateRecord make(StateSet allowedStates, StampPosition stampPosition) {
        return new StampCoordinateRecord(allowedStates,
                stampPosition.toStampPositionImmutable(),
                IntIds.set.empty(),
                IntIds.set.empty(),
                IntIds.list.empty());
    }

    @Override
    public String toString() {
        return "StampFilterRecord{" + toUserString() + "}";
    }

    public StampCalculatorWithCache stampCalculator() {
        return StampCalculatorWithCache.getCalculator(this);
    }

    @Override
    public StampCoordinateRecord withStampPositionTime(long stampPositionTime) {
        return make(this.allowedStates,
                StampPositionRecord.make(stampPositionTime, this.stampPosition.getPathForPositionNid()),
                this.moduleNids, this.modulePriorityNidList);
    }

    public StampCoordinateRecord toStampCoordinateRecord() {
        return this;
    }


    @Override
    public int pathNidForFilter() {
        return this.stampPosition.getPathForPositionNid();
    }


    @Override
    public StampCoordinateRecord withModules(Collection<ConceptFacade> modules) {
        IntIdSet mis = modules == null ? IntIds.set.empty() :
                IntIds.set.of(modules.stream().mapToInt(concept -> concept.nid()).toArray());
         return make(this.allowedStates,
                this.stampPosition,
                mis, this.excludedModuleNids, IntIds.list.empty());
    }

    @Override
    public StampCoordinateRecord withPath(ConceptFacade pathForPosition) {
        return make(this.allowedStates,
                StampPositionRecord.make(this.stampPosition.time(), pathForPosition.nid()),
                this.moduleNids, this.excludedModuleNids, this.modulePriorityNidList);
    }

    @Override
    public StampCoordinateRecord withAllowedStates(StateSet allowedStates) {
        return StampCoordinateRecordBuilder.With.super.withAllowedStates(allowedStates);
    }

    @Override
    public StampCoordinateRecord withStampPosition(StampPositionRecord stampPosition) {
        return StampCoordinateRecordBuilder.With.super.withStampPosition(stampPosition);
    }

    @Override
    public StampCoordinateRecord withModuleNids(IntIdSet moduleNids) {
        return StampCoordinateRecordBuilder.With.super.withModuleNids(moduleNids);
    }

    @Override
    public StampCoordinateRecord withExcludedModuleNids(IntIdSet excludedModuleNids) {
        return StampCoordinateRecordBuilder.With.super.withExcludedModuleNids(excludedModuleNids);
    }

    @Override
    public StampCoordinateRecord withModulePriorityNidList(IntIdList modulePriorityNidList) {
        return StampCoordinateRecordBuilder.With.super.withModulePriorityNidList(modulePriorityNidList);
    }
}

