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
package dev.ikm.tinkar.coordinate.stamp.calculator;


import dev.ikm.tinkar.common.util.time.DateTimeUtil;
import dev.ikm.tinkar.coordinate.PathService;
import dev.ikm.tinkar.coordinate.stamp.StampBranchRecord;
import dev.ikm.tinkar.coordinate.stamp.StampCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StampPathImmutable;
import dev.ikm.tinkar.coordinate.stamp.StampPositionRecord;
import dev.ikm.tinkar.coordinate.stamp.StateSet;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.State;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.map.primitive.MutableIntLongMap;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.tuple.primitive.IntLongPair;
import org.eclipse.collections.impl.factory.primitive.IntLongMaps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;

/**
 * The path-origin resolver behind {@link PathService}: derives the path topology
 * (origins and branches of every path) from the semantics of
 * {@link TinkarTerm#PATH_ORIGINS_PATTERN}. Multi-version origins semantics are
 * resolved to their latest version through the stamp-calculator machinery — see
 * {@link #latestOriginVersion(SemanticEntity)} for how that resolution stays well
 * founded while the path topology it feeds is still under construction.
 */
public class PathProvider implements PathService {

    private static final Logger LOG = LoggerFactory.getLogger(PathProvider.class);

    /**
     * Gets the branches of a path: one {@link StampBranchRecord} for every path whose
     * origins semantic declares it branches from {@code pathNid}.
     *
     * @param pathNid the path whose branches are requested
     * @return the branch records for every path originating from {@code pathNid}
     */
    @Override
    public ImmutableSet<StampBranchRecord> getPathBranches(int pathNid) {
        MutableSet<StampBranchRecord> branchSet = Sets.mutable.empty();
        EntityService.get().forEachSemanticOfPattern(TinkarTerm.PATH_ORIGINS_PATTERN.nid(), semanticEntity -> {
            // Referenced component = path for which this is an origin
            // Field 0 = path from which the origin is derived
            // Field 1 = instant of the origin
            latestOriginVersion(semanticEntity).ifPresent(originVersion -> {
                ImmutableList<Object> fields = originVersion.fieldValues();
                ConceptFacade pathFromWhichOriginDerived = EntityProxy.Concept.make(((EntityFacade) fields.get(0)).nid());
                if (pathFromWhichOriginDerived.nid() == pathNid) {
                    Instant originTime = (Instant) fields.get(1);
                    StampBranchRecord stampBranchRecord = new StampBranchRecord(semanticEntity.referencedComponentNid(),
                            DateTimeUtil.instantToEpochMs(originTime));
                    branchSet.add(stampBranchRecord);
                }
            });
        });
        return branchSet.toImmutable();
    }

    /**
     * Gets every path declared by a {@link TinkarTerm#PATHS_PATTERN} semantic,
     * each with its resolved origins.
     *
     * @return the declared paths
     */
    @Override
    public ImmutableSet<StampPathImmutable> getPaths() {
        int[] pathsPatternSemanticNids = EntityService.get().semanticNidsOfPattern(TinkarTerm.PATHS_PATTERN.nid());
        MutableSet<StampPathImmutable> pathSet = Sets.mutable.ofInitialCapacity(pathsPatternSemanticNids.length);
        for (int pathsPatternSemanticNid : pathsPatternSemanticNids) {
            SemanticEntity semanticEntity = Entity.getFast(pathsPatternSemanticNid);
            int pathNid = semanticEntity.referencedComponentNid();
            pathSet.add(StampPathImmutable.make(pathNid, getPathOrigins(pathNid)));
        }
        return pathSet.toImmutable();
    }

    /**
     * Gets the origins of a path from its {@link TinkarTerm#PATH_ORIGINS_PATTERN}
     * semantics. When no origin is declared, bootstrap fallbacks apply: the
     * primordial path has no origin by definition, the development path defaults to
     * an origin on the sandbox path, and the remaining well-known paths default to an
     * origin on the primordial path.
     *
     * @param pathNid the path whose origins are requested
     * @return the resolved origins of {@code pathNid}
     */
    @Override
    public ImmutableSet<StampPositionRecord> getPathOrigins(int pathNid) {
        MutableSet<StampPositionRecord> originSet = Sets.mutable.empty();
        EntityService.get().forEachSemanticForComponentOfPattern(pathNid, TinkarTerm.PATH_ORIGINS_PATTERN.nid(), semanticEntity -> {
            latestOriginVersion(semanticEntity).ifPresent(originVersion -> {
                ImmutableList<Object> fields = originVersion.fieldValues();
                ConceptFacade pathConcept;
                if (fields.get(0) instanceof ConceptFacade conceptFacade) {
                    pathConcept = conceptFacade;
                } else if (fields.get(0) instanceof EntityFacade entityFacade) {
                    pathConcept = EntityProxy.Concept.make(entityFacade.nid());
                } else {
                    throw new IllegalStateException("Can't construct ConceptFacade from: " + fields.get(0));
                }
                originSet.add(StampPositionRecord.make((Instant) fields.get(1), pathConcept));
            });
        });

        if (originSet.isEmpty() && pathNid != TinkarTerm.PRIMORDIAL_PATH.nid()) {
            // A boot strap issue, only the primordial path should have no origins.
            // If terminology not completely loaded, content may not yet be ready.
            if (pathNid != TinkarTerm.SANDBOX_PATH.nid() && pathNid != TinkarTerm.MASTER_PATH.nid() && pathNid != TinkarTerm.DEVELOPMENT_PATH.nid()) {
                throw new IllegalStateException("Path with no origin: " + EntityService.get().getEntityFast(pathNid));
            }
            if (pathNid == TinkarTerm.DEVELOPMENT_PATH.nid()) {
                return Sets.immutable.with(StampPositionRecord.make(Long.MAX_VALUE, TinkarTerm.SANDBOX_PATH.nid()));
            }
            return Sets.immutable.with(StampPositionRecord.make(Long.MAX_VALUE, TinkarTerm.PRIMORDIAL_PATH.nid()));
        }
        return originSet.toImmutable();
    }

    /**
     * Resolves the version of a path-origins semantic that defines current path
     * topology.
     *
     * <p>A single-version chronology resolves to that version directly, preserving
     * the long-standing bootstrap behavior. For a multi-version chronology the latest
     * version is computed by a single-shot {@link StampCalculatorWithCache} positioned
     * at {@link Long#MAX_VALUE} on the semantic's referenced path with
     * {@link StateSet#ACTIVE_AND_INACTIVE}. Because the path topology is exactly what
     * the enclosing computation is deriving, the calculator's segment map cannot come
     * from the recursive origin walk — that walk re-enters this resolution and, worse,
     * origins semantics are routinely authored on paths whose own topology they help
     * define, which makes any topology-based ordering circular. The map is grounded
     * instead in the only order well founded at this stratum — commit time: the
     * referenced path is the destination segment, followed by the distinct paths of
     * the chronology's committed version stamps ordered by their newest committed
     * version, newest first (ties broken by ascending path nid). Within each path the
     * calculator machinery resolves latest as usual.
     *
     * <p>A latest version that is not {@link State#ACTIVE} yields an empty result: a
     * retired origin definition no longer contributes to the path topology. An empty
     * result is also returned when no version is committed; callers skip the semantic
     * and the bootstrap fallbacks in {@link #getPathOrigins(int)} apply.
     *
     * @param semanticEntity a semantic of {@link TinkarTerm#PATH_ORIGINS_PATTERN}
     * @return the version whose fields define current topology, or empty if the
     *         definition is retired or no version is resolvable
     */
    private static Optional<SemanticEntityVersion> latestOriginVersion(SemanticEntity<SemanticEntityVersion> semanticEntity) {
        ImmutableList<SemanticEntityVersion> versions = semanticEntity.versions();
        if (versions.size() == 1) {
            return Optional.of(versions.get(0));
        }

        final int referencedPathNid = semanticEntity.referencedComponentNid();
        final MutableIntLongMap newestCommitTimeByPath = IntLongMaps.mutable.empty();
        for (SemanticEntityVersion version : versions) {
            StampEntity stamp = version.stamp();
            final long time = stamp.time();
            if (time == Long.MIN_VALUE || stamp.pathNid() == referencedPathNid) {
                // Uncommitted and premundane versions never resolve as latest; versions
                // stamped on the referenced path are covered by the destination segment.
                continue;
            }
            newestCommitTimeByPath.updateValue(stamp.pathNid(), time, existing -> Math.max(existing, time));
        }

        final int[] orderedSegmentPathNids = new int[newestCommitTimeByPath.size() + 1];
        orderedSegmentPathNids[0] = referencedPathNid;
        int segmentIndex = 1;
        for (IntLongPair pathAndNewestTime : newestCommitTimeByPath.keyValuesView().toSortedList(
                Comparator.<IntLongPair>comparingLong(IntLongPair::getTwo).reversed()
                        .thenComparingInt(IntLongPair::getOne))) {
            orderedSegmentPathNids[segmentIndex++] = pathAndNewestTime.getOne();
        }

        StampCoordinateRecord resolutionFilter = StampCoordinateRecord.make(StateSet.ACTIVE_AND_INACTIVE,
                StampPositionRecord.make(Long.MAX_VALUE, referencedPathNid));
        Latest<SemanticEntityVersion> latestVersion =
                new StampCalculatorWithCache(resolutionFilter, orderedSegmentPathNids).latest(semanticEntity);
        if (!latestVersion.isPresent()) {
            LOG.warn("No committed version of path origins semantic {} is resolvable; skipping its origin contribution",
                    semanticEntity.publicId());
            return Optional.empty();
        }
        SemanticEntityVersion definingVersion = latestVersion.get();
        if (definingVersion.state() != State.ACTIVE) {
            return Optional.empty();
        }
        return Optional.of(definingVersion);
    }

}
