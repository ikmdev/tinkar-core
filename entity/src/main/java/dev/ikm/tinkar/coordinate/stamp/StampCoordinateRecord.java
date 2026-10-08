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
package dev.ikm.tinkar.coordinate.stamp;

import dev.ikm.tinkar.common.binary.Decoder;
import dev.ikm.tinkar.common.binary.DecoderInput;
import dev.ikm.tinkar.common.binary.Encodable;
import dev.ikm.tinkar.common.binary.Encoder;
import dev.ikm.tinkar.common.binary.EncoderOutput;
import dev.ikm.tinkar.common.id.LongIdList;
import dev.ikm.tinkar.common.id.LongIdSet;
import dev.ikm.tinkar.common.id.LongIds;
import dev.ikm.tinkar.coordinate.ImmutableCoordinate;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import dev.ikm.tinkar.terms.ConceptFacade;
import io.soabase.recordbuilder.core.RecordBuilder;

import java.util.Collection;
import java.util.Set;

/**
 * A filter that operates in coordinate with path coordinate and the version computer. After the version computer computes the
 * latest versions at a point on a path, the filter provides a non-interfering, stateless predicate to apply to each element
 * to determine if it should be included in the results set.
 *
 * <p>Filters must be immutable.
 *
 * <p>q: How does the stamp coordinate relate to a Stamp?
 *
 * <p>a: A Stamp is a unique combination of Status, Time, Author, Module, and Path...
 * A stamp coordinate specifies a position on a  path, with a particular set of modules, and allowed state values.
 * Author constraints are not handled by the stamp filter. If necessary, results can be post-processed.
 *
 * <p>Created by kec on 2/16/15.
 */

@RecordBuilder
public record StampCoordinateRecord(StateSet allowedStates, StampPositionRecord stampPosition, LongIdSet moduleNids,
                                    LongIdSet excludedModuleNids, LongIdList modulePriorityNidList)
        implements StampCoordinate, ImmutableCoordinate, StampCoordinateImmutable, StampCoordinateRecordBuilder.With {

    @Decoder
    public static StampCoordinateRecord decode(DecoderInput in) {
        switch (Encodable.checkVersion(in)) {
            default:
                return new StampCoordinateRecord(StateSet.decode(in),
                        StampPositionRecord.decode(in),
                        LongIds.set.of(in.readNidArray()),
                        LongIds.set.of(in.readNidArray()),
                        LongIds.list.of(in.readNidArray()));
        }
    }

    /**
     * @param allowedStates
     * @param stampPosition
     * @param moduleNids    - null is treated as an empty set, which allows any module
     * @return
     */
    public static StampCoordinateRecord make(StateSet allowedStates,
                                             StampPosition stampPosition,
                                             LongIdSet moduleNids) {
        return new StampCoordinateRecord(allowedStates, stampPosition.toStampPositionImmutable(),
                moduleNids, LongIds.set.empty(), LongIds.list.empty());
    }

    public static StampCoordinateRecord make(StateSet allowedStates, long path,
                                             Set<ConceptFacade> modules) {
        LongIdSet moduleNids = LongIds.set.of(modules.stream().mapToLong(value -> value.nid()).toArray());
        StampPositionRecord stampPosition = StampPositionRecord.make(Long.MAX_VALUE, path);

        return new StampCoordinateRecord(allowedStates,
                stampPosition, moduleNids, LongIds.set.empty(), LongIds.list.empty());
    }

    public static StampCoordinateRecord make(StateSet allowedStates, long path) {
        StampPositionRecord stampPosition = StampPositionRecord.make(Long.MAX_VALUE, path);

        return new StampCoordinateRecord(allowedStates,
                stampPosition,
                LongIds.set.empty(),
                LongIds.set.empty(),
                LongIds.list.empty());
    }

    public static StampCoordinateRecord make(StateSet allowedStates, StampPosition stampPosition) {
        return new StampCoordinateRecord(allowedStates,
                stampPosition.toStampPositionImmutable(),
                LongIds.set.empty(),
                LongIds.set.empty(),
                LongIds.list.empty());
    }

    @Override
    @Encoder
    public void encode(EncoderOutput out) {
        this.allowedStates.encode(out);
        this.stampPosition.encode(out);
        out.writeNidArray(moduleNids.toArray());
        out.writeNidArray(excludedModuleNids.toArray());
        out.writeNidArray(modulePriorityNidList.toArray());
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

    public static StampCoordinateRecord make(StateSet allowedStates,
                                             StampPosition stampPosition,
                                             LongIdSet moduleNids,
                                             LongIdList modulePreferenceOrder) {
        return new StampCoordinateRecord(allowedStates, stampPosition.toStampPositionImmutable(),
                moduleNids, LongIds.set.empty(), modulePreferenceOrder);
    }

    @Override
    public StampCoordinateRecord withAllowedStates(StateSet allowedStates) {
        return StampCoordinateRecordBuilder.With.super.withAllowedStates(allowedStates);
    }

    @Override
    public long pathNidForFilter() {
        return this.stampPosition.getPathForPositionNid();
    }

    @Override
    public StampCoordinateRecord withModules(Collection<ConceptFacade> modules) {
        LongIdSet mis = modules == null ? LongIds.set.empty() :
                LongIds.set.of(modules.stream().mapToLong(concept -> concept.nid()).toArray());
        return make(this.allowedStates,
                this.stampPosition,
                mis, this.excludedModuleNids, LongIds.list.empty());
    }

    public static StampCoordinateRecord make(StateSet allowedStates,
                                             StampPosition stampPosition,
                                             LongIdSet moduleNids,
                                             LongIdSet excludedModuleNids,
                                             LongIdList modulePreferenceOrder) {
        return new StampCoordinateRecord(allowedStates, stampPosition.toStampPositionImmutable(),
                moduleNids, excludedModuleNids, modulePreferenceOrder);
    }

    @Override
    public StampCoordinateRecord withModuleNids(LongIdSet moduleNids) {
        return StampCoordinateRecordBuilder.With.super.withModuleNids(moduleNids);
    }

    @Override
    public StampCoordinateRecord withExcludedModuleNids(LongIdSet excludedModuleNids) {
        return StampCoordinateRecordBuilder.With.super.withExcludedModuleNids(excludedModuleNids);
    }

    @Override
    public StampCoordinateRecord withModulePriorityNidList(LongIdList modulePriorityNidList) {
        return StampCoordinateRecordBuilder.With.super.withModulePriorityNidList(modulePriorityNidList);
    }

    @Override
    public StampCoordinateRecord withPath(ConceptFacade pathForPosition) {
        return make(this.allowedStates,
                StampPositionRecord.make(this.stampPosition.time(), pathForPosition.nid()),
                this.moduleNids, this.excludedModuleNids, this.modulePriorityNidList);
    }

    @Override
    public StampCoordinateRecord withStampPosition(StampPositionRecord stampPosition) {
        return StampCoordinateRecordBuilder.With.super.withStampPosition(stampPosition);
    }

    public StampCoordinateRecord toStampCoordinateRecord() {
        return this;
    }
}

