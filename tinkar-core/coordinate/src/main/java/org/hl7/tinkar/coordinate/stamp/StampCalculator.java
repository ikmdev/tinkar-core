package org.hl7.tinkar.coordinate.stamp;
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

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import com.google.auto.service.AutoService;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.primitive.ImmutableIntSet;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.hl7.tinkar.collection.ConcurrentReferenceHashMap;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.component.PatternVersion;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.entity.calculator.Latest;
import org.hl7.tinkar.component.graph.DiTree;
import org.hl7.tinkar.coordinate.CoordinateUtil;
import org.hl7.tinkar.entity.calculator.RelativePosition;
import org.hl7.tinkar.entity.calculator.TriConsumer;
import org.hl7.tinkar.entity.calculator.VersionCalculator;
import org.hl7.tinkar.entity.graph.DiTreeEntity;
import org.hl7.tinkar.entity.graph.VersionVertex;
import org.hl7.tinkar.terms.State;
import org.hl7.tinkar.terms.TinkarTerm;

import static org.hl7.tinkar.entity.calculator.RelativePosition.*;

/**
 * The Class RelativePositionCalculator.
 *
 * @author kec
 */
public class StampCalculator implements VersionCalculator{
    /** The Constant LOG. */
    private static final Logger LOG = CoordinateUtil.LOG;

    private static final ConcurrentReferenceHashMap<StampFilterRecord, StampCalculator> SINGLETONS =
            new ConcurrentReferenceHashMap<>(ConcurrentReferenceHashMap.ReferenceType.WEAK,
                    ConcurrentReferenceHashMap.ReferenceType.WEAK);

    @AutoService(CachingService.class)
    public static class CacheProvider implements CachingService {
        @Override
        public void reset() {
            SINGLETONS.clear();
        }
    }

    /** The error count. */
    private int  errorCount   = 0;

    /** The coordinate. */
    private final StampFilterRecord filter;
    private final StateSet allowedStates;
    private final ConcurrentHashMap<Integer, Boolean> stampOnRoute = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Boolean> stampIsAllowedState = new ConcurrentHashMap<>();

    /**
     * Mapping from pathNid to each segment for that pathNid. There is one entry
     * for each path reachable antecedent to the destination position of the
     * computer.
     */
    ConcurrentHashMap<Integer, Segment> pathNidSegmentMap;

    /**
     * Instantiates a new relative position calculator.
     *
     * @param filter the coordinate
     */
    private StampCalculator(StampFilterRecord filter) {
        //For the internal callback to populate the cache
        this.filter = filter;
        this.pathNidSegmentMap = setupPathNidSegmentMap(filter.stampPosition().toStampPositionImmutable());
        this.allowedStates          = filter.allowedStates();
    }

    /**
     * Fast relative position.
     *
     * @param stampNid1 a stamp nid
     * @param stampNid2 a stamp nid
     * @return the relative position
     */
    public RelativePosition fastRelativePosition(int stampNid1,
                                                 int stampNid2) {
        StampEntity stamp1 = Entity.getStamp(stampNid1);
        StampEntity stamp2 = Entity.getStamp(stampNid2);

        return getRelativePosition(stamp1, stamp2);
    }

    public RelativePosition getRelativePosition(StampEntity stamp1, StampEntity stamp2) {
        final long ss1Time = stamp1.time();
        final int  ss1ModuleNid = stamp1.moduleNid();
        final int  ss1PathNid   = stamp1.pathNid();
        final long ss2Time           = stamp2.time();
        final int  ss2ModuleNid = stamp2.moduleNid();
        final int  ss2PathNid   = stamp2.pathNid();

        if (ss1PathNid == ss2PathNid) {
            final Segment seg = this.pathNidSegmentMap.get(ss1PathNid);

            if (seg == null) {
                throw new IllegalStateException("Segment cannot be null.");
            }

            if (seg.containsPosition(ss1PathNid, ss1ModuleNid, ss1Time) &&
                    seg.containsPosition(ss2PathNid, ss2ModuleNid, ss2Time)) {
                if (ss1Time < ss2Time) {
                    return BEFORE;
                }

                if (ss1Time > ss2Time) {
                    return AFTER;
                }

                if (ss1Time == ss2Time) {
                    return EQUAL;
                }
            }

            return RelativePosition.UNREACHABLE;
        }

        final Segment seg1 = this.pathNidSegmentMap.get(ss1PathNid);
        final Segment seg2 = this.pathNidSegmentMap.get(ss2PathNid);

        if ((seg1 == null) || (seg2 == null)) {
            return RelativePosition.UNREACHABLE;
        }

        if (!(seg1.containsPosition(ss1PathNid, ss1ModuleNid, ss1Time) &&
                seg2.containsPosition(ss2PathNid, ss2ModuleNid, ss2Time))) {
            return RelativePosition.UNREACHABLE;
        }

        if (seg1.precedingSegments.contains(seg2.segmentSequence)) {
            return BEFORE;
        }

        if (seg2.precedingSegments.contains(seg1.segmentSequence)) {
            return AFTER;
        }

        return RelativePosition.CONTRADICTION;
    }

    /**
     * Fast relative position.
     *
     * @param v1 the v 1
     * @param v2 the v 2
     * @return the relative position
     */
    public RelativePosition fastRelativePosition(EntityVersion v1,
                                                 EntityVersion v2) {
        StampEntity v1Stamp = v1.stamp();
        StampEntity v2Stamp = v2.stamp();
        return getRelativePosition(v1Stamp, v2Stamp);
    }
    /**
     * On route.
     *
     * @param stampNid the stamp sequence
     * @return true, if successful
     */
    public boolean onRoute(int stampNid) {

        StampEntity stamp = Entity.getStamp(stampNid);
        return onRoute(stamp);
    }

    public boolean onRoute(StampEntity stamp) {
        if (stampOnRoute.containsKey(stamp.nid())) {
            return stampOnRoute.get(stamp.nid());
        }
        final Segment seg = this.pathNidSegmentMap.get(stamp.pathNid());
        boolean returnValue = false;
        if (seg != null) {
            returnValue = seg.containsPosition(
                    stamp.pathNid(),
                    stamp.moduleNid(),
                    stamp.time());
        }
        stampOnRoute.put(stamp.pathNid(), returnValue);
        return returnValue;
    }

    /**
     * On route.
     *
      * @param version the version
     * @return true, if successful
     */
    public boolean onRoute(EntityVersion version) {
        return onRoute(version.stamp());
    }

    /**
     * Relative position.
     *
     * @param stampNid the stamp sequence 1
     * @param stampNid the stamp sequence 2
     * @return the relative position
     */
    public RelativePosition relativePosition(int stampNid, int stampNid2) {
        if (!(onRoute(stampNid) && onRoute(stampNid2))) {
            return RelativePosition.UNREACHABLE;
        }

        return fastRelativePosition(stampNid, stampNid2);
    }

    /**
     * To string.
     *
     * @return the string
     */
    @Override
    public String toString() {
        return "RelativePositionCalculator{" + this.filter + '}';
    }

    /**
     * Adds the origins to path nid segment map.
     *
     * @param destination the destination
     * @param pathNidSegmentMap the path nid segment map
     * @param segmentSequence the segment sequence
     * @param precedingSegments the preceding segments
     */

    // recursively called method
    private void addOriginsToPathNidSegmentMap(StampPositionImmutable destination,
                                               ConcurrentHashMap<Integer, Segment> pathNidSegmentMap,
                                               AtomicInteger segmentSequence,
                                               ConcurrentSkipListSet<Integer> precedingSegments) {
        final Segment segment = new Segment(
                segmentSequence.getAndIncrement(),
                destination.getPathForPositionNid(),
                destination.time(),
                precedingSegments);

        // precedingSegments is cumulative, each recursive call adds another
        precedingSegments.add(segment.segmentSequence);
        Segment old = pathNidSegmentMap.put(destination.getPathForPositionNid(), segment);
        if (old != null) {
            LOG.severe("Overwrite segment " + old +
                    " with " + segment + " for path " + destination.getPathForPositionConcept());
        }
        destination.getPathOrigins()
                .stream()
                .forEach((StampPositionImmutable origin) ->
                    // Recursive call
                    addOriginsToPathNidSegmentMap(
                            origin,
                            pathNidSegmentMap,
                            segmentSequence,
                            precedingSegments)
                );
    }

    /**
     * Handle part.
     *
     * @param partsForPosition the parts for position
     * @param part the part
     */
    private void handlePart(HashSet<EntityVersion> partsForPosition, EntityVersion part) {
        // create a list of values so we don't have any
        // concurrent modification issues with removing/adding
        // items to the partsForPosition.
        final List<EntityVersion> partsToCompare = new ArrayList<>(partsForPosition);

        for (final EntityVersion prevPartToTest: partsToCompare) {
            switch (fastRelativePosition(part, prevPartToTest)) {
                case AFTER:
                    partsForPosition.remove(prevPartToTest);
                    partsForPosition.add(part);
                    break;

                case BEFORE:
                    break;

                case CONTRADICTION:
                    partsForPosition.add(part);
                    break;

                case EQUAL:

                    // Can only have one part per time/path
                    // combination.
                    if (prevPartToTest.equals(part)) {
                        // part already added from another position.
                        // No need to add again.
                        break;
                    }

                    // Duplicate values encountered.
                    this.errorCount++;

                    if (this.errorCount < 5) {
                        LOG.warning("EQUAL should never happen. Data is malformed. Stamp: " + part.stamp() +
                                        " Part:\n" + part + " \n  Part to test: \n" + prevPartToTest + "\n");
                    }

                    break;

                case UNREACHABLE:

                    // Should have failed mapper.onRoute(part)
                    // above.
                    throw new RuntimeException(RelativePosition.UNREACHABLE + " should never happen.");
            }
        }
    }

    /**
     * Handle stamp.
     *
     * @param stampsForPosition the stamps for position
     * @param stampNid the stamp sequence
     */
    private void handleStamp(MutableIntSet stampsForPosition, int stampNid, boolean allowUncommitted) {

        if (!allowUncommitted) {
            throw new UnsupportedOperationException();

//            if (getStampService()
//                    .isUncommitted(stampNid)) {
//                return;
//            }
        }

        if (!onRoute(stampNid)) {
            return;
        }

        if (stampsForPosition.isEmpty()) {
            stampsForPosition.add(stampNid);
            return;
        }

        // create a list of values so we don't have any
        // concurrent modification issues with removing/adding
        // items to the stampsForPosition.
        final ImmutableIntSet stampsToCompare = IntSets.immutable.ofAll(stampsForPosition);

        stampsToCompare.forEach(prevStamp -> {
            switch (fastRelativePosition(stampNid, prevStamp)) {
                case AFTER:
                    stampsForPosition.remove(prevStamp);
                    stampsForPosition.add(stampNid);
                    break;

                case BEFORE:
                    break;

                case CONTRADICTION:
                    stampsForPosition.add(stampNid);
                    break;

                case EQUAL:

                    // Can only have one stampNid per time/path
                    // combination.
                    if (prevStamp == stampNid) {
                        // stampNid already added from another position.
                        // No need to add again.
                        break;
                    }

                    // Duplicate values encountered.  Likely two stamps at the same time on different modules.
                    //TODO this should be using the module preference order to determine which one to put at the top...
                    stampsForPosition.add(stampNid);
                    break;

                case UNREACHABLE:

                    // nothing to do...
                    break;

                default:
                    throw new UnsupportedOperationException(
                            "n Can't handle: " + fastRelativePosition(
                                    stampNid,
                                    prevStamp));
            }
        });

    }

    /**
     * Setup path nid segment map.
     *
     * @param destination the destination
     * @return the open int object hash map
     */
    private ConcurrentHashMap<Integer, Segment> setupPathNidSegmentMap(StampPositionImmutable destination) {
        final ConcurrentHashMap<Integer, Segment> pathNidSegmentMapToSetup = new ConcurrentHashMap<>();
        final AtomicInteger                 segmentSequence               = new AtomicInteger(0);

        // the sequence of the preceding segments is set in the recursive
        // call.
        final ConcurrentSkipListSet<Integer> precedingSegments = new ConcurrentSkipListSet<>();

        // call to recursive method...
        addOriginsToPathNidSegmentMap(destination,
                pathNidSegmentMapToSetup,
                segmentSequence,
                precedingSegments);
        return pathNidSegmentMapToSetup;
    }

    private boolean isAllowedState(int stampNid) {
        if (stampIsAllowedState.containsKey(stampNid)) {
            return stampIsAllowedState.get(stampNid);
        }
        StampEntity stamp = Entity.getStamp(stampNid);
        boolean allowed = this.allowedStates.contains(State.fromConceptNid(stamp.stateNid()));
        stampIsAllowedState.put(stampNid, allowed);
        return allowed;
    }

    /**
     * Gets the calculator.
     *
     * @param filter the filter
     * @return the calculator
     */
    public static StampCalculator getCalculator(StampFilterRecord filter) {
        return SINGLETONS.computeIfAbsent(filter,
                filterKey -> new StampCalculator(filter));
    }

    /**
     * Checks if latest active.
     *
     * @param stampNids A stream of stampNids from which the latest is
     * found, and then tested to determine if the latest is active.
     * @return true if any of the latest stampNids (may be multiple in the
     * case of a contradiction) are active.
     */
    public boolean isLatestActive(int[] stampNids) {
        for (int stampNid: getLatestStampNidsAsSet(stampNids)) {
            StampEntity stamp = Entity.getStamp(stampNid);
            if (State.fromConceptNid(stamp.stateNid()) == State.ACTIVE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the latest (committed only) stamp sequences as a sorted set in an array.
     *
     * @param stampNids the stamp sequence stream
     * @return the latest stamp sequences as a sorted set in an array
     */
    public int[] getLatestCommittedStampNidsAsSet(int[] stampNids) {
        MutableIntSet stampsForPosition = IntSets.mutable.empty();
        for (int stampToCompare: stampNids) {
            handleStamp(stampsForPosition, stampToCompare, false);
        }
        return getResults(stampsForPosition);
    }

    /**
     * Gets the latest stamp sequences as an array, allowing uncommitted stamps.
     * The latest stamp sequences independent of allowed states of the stamp coordinate are identified.
     * Then, if those latest stamp's status values are included in the allowed states, then the stamps are included in the result.
     * If none of the latest stamps are of an allowed state, then an empty set is returned.
     *
     * @param stampNids the input stamp sequences
     * @return the latest stamp sequences as an array. Empty array if none of the
     * latest stamps match the allowed states of the stamp coordinate.
     */
    public int[] getLatestStampNidsAsSet(int[] stampNids) {

        MutableIntSet stampsForPosition = IntSets.mutable.empty();
        for (int stampToCompare: stampNids) {
            handleStamp(stampsForPosition, stampToCompare, true);
        }

        return getResults(stampsForPosition);
    }

    private int[] getResults(MutableIntSet stampsForPosition) {
        MutableIntSet resultList = IntSets.mutable.of();

        stampsForPosition.forEach(stampNid -> {
            if (isAllowedState(stampNid)) {
                resultList.add(stampNid);
            }
        });
        return resultList.toArray();
    }

    /**
     * Gets the latest EntityVersion.
     *
     * @param chronicle the chronicle
     * @return the latest version
     */
    public <V extends EntityVersion> Latest<V> latest(Entity<V> chronicle) {
        final HashSet<EntityVersion> latestVersionSet = new HashSet<>();

        chronicle.versions()
                .stream()
                .filter((newVersionToTest) -> (newVersionToTest.stamp().time() > Long.MIN_VALUE))
                .filter((newVersionToTest) -> (onRoute(newVersionToTest.stamp())))
                .forEach(
                        (newVersionToTest) -> {
                            if (latestVersionSet.isEmpty()) {
                                latestVersionSet.add((V) newVersionToTest);
                            } else {
                                handlePart(latestVersionSet, newVersionToTest);
                            }
                        });

        if (this.filter.allowedStates().isActiveOnly()) {
            final HashSet<EntityVersion> inactiveVersions = new HashSet<>();

            latestVersionSet.stream()
                    .forEach((version) -> {
                        if (State.fromConceptNid(version.stamp().stateNid()) != State.ACTIVE) {
                            inactiveVersions.add(version);
                        }
                    });
            latestVersionSet.removeAll(inactiveVersions);
        }

        final List<EntityVersion> latestVersionList = new ArrayList<>(latestVersionSet);

        if (latestVersionList.isEmpty()) {
            return new Latest<>();
        }

        if (latestVersionList.size() == 1) {
            return (Latest<V>) new Latest<>(latestVersionList.get(0));
        }

        return (Latest<V>) new Latest<>(latestVersionList.get(0), latestVersionList.subList(1, latestVersionList.size()));
    }

    /**
     * The Class Segment.
     */
    private class Segment {
        /**
         * Each segment gets it's own sequence which gets greater the further
         * prior to the position of the relative position computer.
         */
        int segmentSequence;

        /**
         * The pathConceptNid of this segment. Each ancestor path to the
         * position of the computer gets it's own segment.
         */
        int pathConceptNid;

        /**
         * The end time of the position of the relative position computer. stamps
         * with times after the end time are not part of the path.
         */
        long endTime;

        /** The preceding segments. */
        ConcurrentSkipListSet<Integer> precedingSegments;

        /**
         * Instantiates a new segment.
         *
         * @param segmentSequence the segment sequence
         * @param pathConceptNid the path concept nid
         * @param endTime the end time
         * @param precedingSegments the preceding segments
         */
        private Segment(int segmentSequence, int pathConceptNid, long endTime, ConcurrentSkipListSet<Integer> precedingSegments) {
            this.segmentSequence     = segmentSequence;
            this.pathConceptNid = pathConceptNid;
            this.endTime             = endTime;
            this.precedingSegments   = precedingSegments.clone();
        }

        /**
         * To string.
         *
         * @return the string
         */
        @Override
        public String toString() {
            return "Segment{" + this.segmentSequence + ", pathConcept=" + PrimitiveData.text(this.pathConceptNid) + "<" + this.pathConceptNid + ">, endTime=" +
                    this.endTime + ", precedingSegments=" + this.precedingSegments + '}';
        }

        /**
         * Contains position.
         *
         * @param pathConceptNid the path concept nid
         * @param moduleConceptNid the module concept nid
         * @param time the time
         * @return true, if successful
         */
        private boolean containsPosition(int pathConceptNid, int moduleConceptNid, long time) {
            if (StampCalculator.this.filter.moduleNids().isEmpty() ||
                    StampCalculator.this.filter.moduleNids().contains(moduleConceptNid)) {
                if (StampCalculator.this.filter.excludedModuleNids().isEmpty() ||
                        !StampCalculator.this.filter.excludedModuleNids().contains(moduleConceptNid)) {
                    if ((this.pathConceptNid == pathConceptNid) && (time != Long.MIN_VALUE)) {
                        return time < this.endTime;
                    }
                }
            }

            return false;
        }
    }

    public static <V extends EntityVersion> List<DiTree<VersionVertex<V>>> getVersionGraphList(Collection<V> versionList) {
        SortedSet<VersionWithDistance<V>> versionWithDistances = new TreeSet<>();
        versionList.forEach(v -> versionWithDistances.add(new VersionWithDistance<>(v)));

        final List<DiTree<VersionVertex<V>>> results = new ArrayList<>();

        int loopCheck = 0;
        while (!versionWithDistances.isEmpty()) {
            loopCheck++;
            if (loopCheck > 100) {
                throw new IllegalStateException("loopCheck = " + loopCheck);
            }
            DiTreeEntity.Builder<VersionVertex<V>> treeBuilder = DiTreeEntity.builder();

            Set<VersionVertex<V>> leafNodes = new HashSet<>();
            SortedSet<VersionWithDistance<V>> nodesInTree = new TreeSet<>();
            for (VersionWithDistance versionWithDistance: versionWithDistances) {
                if (treeBuilder.getRoot() == null) {
                    VersionVertex root = VersionVertex.make(versionWithDistance.version);
                    treeBuilder.setRoot(root);
                    leafNodes.add(root);
                    nodesInTree.add(versionWithDistance);
                } else {
                    List<VersionVertex> leafList = new ArrayList<>(leafNodes);
                    for (VersionVertex leafNode: leafList) {
                        switch (getRelativePosition(versionWithDistance.version, leafNode.version())) {
                            case AFTER:
                                VersionVertex newLeaf = VersionVertex.make(versionWithDistance.version);
                                treeBuilder.addEdge(newLeaf, leafNode);
                                nodesInTree.add(versionWithDistance);
                                leafNodes.remove(leafNode);
                                leafNodes.add(newLeaf);
                                break;
                            case EQUAL:
                                // TODO handle different modules... ?
                                throw new IllegalStateException("Version can only be in one module at a time. \n"
                                        + leafNode.version() + "\n" + versionWithDistance.version);
                            case BEFORE:
                                throw new IllegalStateException("Sort order error. \n"
                                        + leafNode.version() + "\n" + versionWithDistance.version);
                            case UNREACHABLE:
                                // if not after by any leaf (unreachable from any leaf), then node will be left in set, and possibly added to next graph.
                                break;
                            default:
                                throw new IllegalStateException("Sort order error. Unhandled relative position:\n"
                                        + leafNode.version() + "\n" + versionWithDistance.version +
                                        getRelativePosition(leafNode.version(), versionWithDistance.version));
                        }
                    }
                }
            }
            results.add(treeBuilder.build());
            versionWithDistances.removeAll(nodesInTree);
        }
        return results;
    }


    private static BigInteger getDistance(StampPosition position) {
        int pathDistanceFromOrigin = pathDistanceFromOrigin(0, position.toStampPositionImmutable());
        return BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(pathDistanceFromOrigin)).add(BigInteger.valueOf(position.time()));
    }

    private static int pathDistanceFromOrigin(int cumulativeDistance, StampPositionImmutable positionImmutable) {
        if (positionImmutable.getPathForPositionNid() != TinkarTerm.PRIMORDIAL_PATH.nid()) {
            int computedDistance = Integer.MAX_VALUE;
            for (StampPositionImmutable origin: positionImmutable.getPathOrigins()) {
                computedDistance = Math.min(computedDistance, pathDistanceFromOrigin(cumulativeDistance + 1, origin));
            }
            return computedDistance;
        }
        return cumulativeDistance;
    }

    private static class VersionWithDistance<V extends EntityVersion> implements Comparable<VersionWithDistance> {
        final BigInteger computedDistance;
        final V version;

        public VersionWithDistance(V version) {
            this.version = version;
            this.computedDistance = getDistance(StampPositionImmutable.make(version.stamp().time(), version.stamp().pathNid()));
        }

        @Override
        public int compareTo(VersionWithDistance o) {
            return this.computedDistance.compareTo(o.computedDistance);
        }
    }

    public static RelativePosition getRelativePosition(int stampNid1, int stampNid2) {
        if (stampNid1 == stampNid2) {
            return RelativePosition.EQUAL;
        }
        StampEntity stamp1 = Entity.getStamp(stampNid1);
        StampEntity stamp2 = Entity.getStamp(stampNid2);

        if (stamp1.pathNid() == stamp2.pathNid()) {
            if (stamp1.time() < stamp2.time()) {
                return RelativePosition.BEFORE;
            }

            if (stamp1.time() > stamp2.time()) {
                return RelativePosition.AFTER;
            }

            return RelativePosition.EQUAL;
        }

        if (traverseOrigins(stampNid1, getStampPath(stamp2.pathNid())) == RelativePosition.BEFORE) {
            return RelativePosition.BEFORE;
        }

        return traverseOrigins(stampNid2, getStampPath(stamp1.pathNid()));
    }

    private static RelativePosition traverseOrigins(int pathNid, StampPath stampPath) {
        // The stamp path object contains the origins...
        throw new UnsupportedOperationException();
    }


    public static RelativePosition getRelativePosition(EntityVersion v1, EntityVersion v2) {
        if (v1.stamp().pathNid() == v2.stamp().pathNid()) {
            if (v1.stamp().time() < v2.stamp().time()) {
                return RelativePosition.BEFORE;
            }

            if (v1.stamp().time() > v2.stamp().time()) {
                return RelativePosition.AFTER;
            }

            return RelativePosition.EQUAL;
        }
        return getRelativePosition(v1.stamp().nid(),
                StampPositionImmutable.make(v2.stamp().time(), v2.stamp().pathNid()));
    }

    public static RelativePosition getRelativePosition(int stampNid, StampPosition position) {
        StampEntity stampEntity = Entity.getStamp(stampNid);
        if (stampEntity.pathNid() == position.getPathForPositionNid()) {
            if (stampEntity.time() < position.time()) {
                return RelativePosition.BEFORE;
            }

            if (stampEntity.time() > position.time()) {
                return RelativePosition.AFTER;
            }

            return RelativePosition.EQUAL;
        }
        // need to see if after on branched path.

        switch (traverseForks(stampNid, position)) {
            case AFTER:
                return RelativePosition.AFTER;
        }

        // before or unreachable.
        return traverseOrigins(stampNid, getStampPath(position.getPathForPositionNid()));
    }
    private static RelativePosition traverseForks(int stampNid, StampPosition position) {
        StampEntity stampEntity = Entity.getStamp(stampNid);
        int stampPathNid = stampEntity.pathNid();
        if (stampPathNid == position.getPathForPositionNid()) {
            throw new IllegalStateException("You must check for relative position on the same path before calling traverseForks: " +
                    //Get.stampService().describeStampSequence(stamp) +
                    " compared to: " + position);
        }

        for (StampBranchImmutable branch: getBranches(position.getPathForPositionNid())) {
            switch (traverseForks(stampPathNid, branch)) {
                case AFTER:
                    return RelativePosition.AFTER;
            }
        }
        return RelativePosition.UNREACHABLE;
    }
    private static RelativePosition traverseForks(int stampPathNid, StampBranchImmutable stampBranchImmutable) {
        int pathOfBranchNid = stampBranchImmutable.getPathOfBranchNid();
        if (pathOfBranchNid == stampPathNid) {
            return RelativePosition.AFTER;
        }
        for (StampBranchImmutable branch: getBranches(stampBranchImmutable.getPathOfBranchNid())) {
            switch (traverseForks(stampPathNid, branch)) {
                case AFTER:
                    return RelativePosition.AFTER;
            }
        }
        return RelativePosition.UNREACHABLE;
    }

     public static ImmutableSet<StampBranchImmutable> getBranches(int pathConceptNid) {
        throw new UnsupportedOperationException();
        //return branchMap.computeIfAbsent(pathConceptNid, (pathNid) -> Sets.immutable.empty());
    }

    public static boolean exists(int pathConceptId) {
        throw new UnsupportedOperationException();
//        if (this.pathMap.containsKey(pathConceptId)) {
//            return true;
//        }
//
//        final Optional<StampPathImmutable> stampPath = constructFromSemantics(pathConceptId);
//
//        return stampPath.isPresent();
    }

    public static StampPath getStampPath(int stampPathNid) {
 //        if (exists(stampPathNid)) {
//            return this.pathMap.get(stampPathNid);
//        }
//
        final Optional<StampPathImmutable> stampPath = constructFromSemantics(stampPathNid);

        if (stampPath.isPresent()) {
            return stampPath.get();
        }

        throw new IllegalStateException("No path for: " + stampPathNid + " " +
                EntityService.get().getEntityFast(stampPathNid));
    }

    private static Optional<StampPathImmutable> constructFromSemantics(int stampPathNid) {
        int[] nids = EntityService.get().semanticNidsForComponentOfPattern(stampPathNid, TinkarTerm.PATH.nid());
        if (nids.length == 1) {
            int pathId = nids[0];
            assert pathId == stampPathNid:
                    "pathId: " + pathId + " stampPathSequence: " + stampPathNid;
            final StampPathImmutable stampPath = StampPathImmutable.make(stampPathNid);
            //this.pathMap.put(stampPathNid, stampPath);
            return Optional.of(stampPath);
        } else {
            throw new UnsupportedOperationException("Wrong nid count: " + Arrays.toString(nids));
        }
   }

    @Override
    public <V extends EntityVersion> Latest<V> latest(int nid) {
        return this.latest(Entity.getFast(nid));
    }

    @Override
    public void forEachSemanticVersionOfPattern(int patternNid, BiConsumer<SemanticEntityVersion, PatternVersion> procedure) {
        Latest<PatternEntityVersion> latestPatternVersion = this.latest(patternNid);
        latestPatternVersion.ifPresent(patternEntityVersion -> PrimitiveData.get().forEachSemanticNidOfPattern(patternNid, semanticNid -> {
            Latest<SemanticEntityVersion> latestSemanticVersion = this.latest(semanticNid);
            latestSemanticVersion.ifPresent(semanticEntityVersion -> procedure.accept(semanticEntityVersion, patternEntityVersion));
         }));
    }

    @Override
    public void forEachSemanticVersionForComponent(int componentNid,
                                                   BiConsumer<SemanticEntityVersion, EntityVersion> procedure) {
        Latest<EntityVersion> latestEntityVersion = this.latest(componentNid);
        latestEntityVersion.ifPresent(entityVersion -> PrimitiveData.get().forEachSemanticNidForComponent(componentNid, semanticNid -> {
            Latest<SemanticEntityVersion> latestSemanticVersion = this.latest(semanticNid);
            latestSemanticVersion.ifPresent(semanticEntityVersion -> procedure.accept(semanticEntityVersion, entityVersion));
        }));
    }

    @Override
    public void forEachSemanticVersionForComponentOfPattern(int componentNid, int patternNid,
                                                            TriConsumer<SemanticEntityVersion, EntityVersion, PatternVersion> procedure) {
        Latest<EntityVersion> latestComponentVersion = this.latest(componentNid);
        latestComponentVersion.ifPresent(entityVersion -> {
            Latest<PatternEntityVersion> latestPatternVersion = this.latest(patternNid);
            latestPatternVersion.ifPresent(patternEntityVersion -> PrimitiveData.get().forEachSemanticNidOfPattern(patternNid, semanticNid -> {
                Latest<SemanticEntityVersion> latestSemanticVersion = this.latest(semanticNid);
                latestSemanticVersion.ifPresent(semanticEntityVersion -> procedure.accept(semanticEntityVersion, entityVersion, patternEntityVersion));
            }));
        });
    }
}

