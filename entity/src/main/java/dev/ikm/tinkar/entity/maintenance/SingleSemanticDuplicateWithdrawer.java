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
package dev.ikm.tinkar.entity.maintenance;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.SemanticVersionRecord;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.State;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.api.tuple.primitive.IntObjectPair;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.eclipse.collections.impl.tuple.primitive.PrimitiveTuples;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Finds and withdraws duplicate semantics on patterns that conventionally hold
 * a single semantic per referenced component. Duplicates arise from a past
 * race condition in UUID generation; the canonical one is reproducible via
 * {@link UuidT5Generator#singleSemanticUuid(PublicId, PublicId)}.
 *
 * <p>Each non-canonical duplicate gets a new {@link State#WITHDRAWN} version
 * appended to its chronology. The withdrawal stamp inherits the duplicate's
 * own module and path; the author is supplied by the caller (typically the
 * editing author of the active view).
 *
 * <p>Patterns whose UUID is not registered in the active data store are
 * skipped with an INFO log so the same {@code DEFAULT} pattern set works
 * across datasets that load only a subset.
 *
 * <p>Concurrency: across-pattern fan-out, plus within each pattern a chunked
 * parallel grouping pass (chronicle lookup of every semantic on the pattern)
 * and a chunked parallel duplicate-processing pass. The {@link Transaction}
 * uses concurrent-safe internals for stamp and component registration, so
 * withdrawal writes can run from multiple threads.
 */
public final class SingleSemanticDuplicateWithdrawer {

    private static final Logger LOG = LoggerFactory.getLogger(SingleSemanticDuplicateWithdrawer.class);

    public record PatternResult(
            int patternNid,
            int componentsScanned,
            int componentsWithDuplicates,
            int duplicatesWithdrawn,
            int alreadyWithdrawn,
            int noCanonicalMatch,
            int wrongPatternSkipped) {

        public static PatternResult empty(int patternNid) {
            return new PatternResult(patternNid, 0, 0, 0, 0, 0, 0);
        }
    }

    public record Report(ImmutableList<PatternResult> perPattern) {

        public long totalDuplicatesWithdrawn() {
            return perPattern.collectInt(PatternResult::duplicatesWithdrawn).sum();
        }

        public long totalComponentsWithDuplicates() {
            return perPattern.collectInt(PatternResult::componentsWithDuplicates).sum();
        }
    }

    private final Transaction transaction;
    private final int currentAuthorNid;
    private final boolean dryRun;

    /**
     * @param transaction      transaction to which withdrawal versions are added; ignored when {@code dryRun} is true
     * @param currentAuthorNid author nid for the withdrawal stamps
     * @param dryRun           when true, scan and report only — no writes
     */
    public SingleSemanticDuplicateWithdrawer(Transaction transaction, int currentAuthorNid, boolean dryRun) {
        if (!dryRun && transaction == null) {
            throw new IllegalArgumentException("transaction is required when dryRun is false");
        }
        this.transaction = transaction;
        this.currentAuthorNid = currentAuthorNid;
        this.dryRun = dryRun;
    }

    public Report scan(Iterable<? extends EntityProxy.Pattern> patterns) {
        List<StructuredTaskScope.Subtask<PatternResult>> subtasks = new ArrayList<>();
        try (var scope = StructuredTaskScope.<PatternResult>open()) {
            for (EntityProxy.Pattern pattern : patterns) {
                subtasks.add(scope.fork(() -> scan(pattern)));
            }
            scope.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted scanning patterns", e);
        }
        MutableList<PatternResult> results = Lists.mutable.empty();
        for (StructuredTaskScope.Subtask<PatternResult> subtask : subtasks) {
            PatternResult r = subtask.get();
            if (r != null) {
                results.add(r);
            }
        }
        return new Report(results.toImmutable());
    }

    public PatternResult scan(EntityProxy.Pattern pattern) {
        int patternNid;
        try {
            patternNid = pattern.nid();
        } catch (IllegalStateException missing) {
            LOG.info("Skipping pattern not present in data store: {} ({})",
                    pattern.description(), pattern.publicId().idString());
            return null;
        }
        PatternEntity<?> patternEntity = EntityHandle.getPatternOrThrow(patternNid);

        MutableIntList allNids = IntLists.mutable.empty();
        PrimitiveData.get().forEachSemanticNidOfPattern(patternEntity.nid(), allNids::add);

        MutableIntObjectMap<MutableIntList> componentToSemantics = parallelGroupByComponent(allNids);

        List<IntObjectPair<MutableIntList>> duplicateGroups = new ArrayList<>();
        for (var iter = componentToSemantics.keyValuesView().iterator(); iter.hasNext(); ) {
            var entry = iter.next();
            if (entry.getTwo().size() > 1) {
                duplicateGroups.add(PrimitiveTuples.pair(entry.getOne(), entry.getTwo()));
            }
        }

        AtomicInteger duplicatesWithdrawn = new AtomicInteger();
        AtomicInteger alreadyWithdrawn = new AtomicInteger();
        AtomicInteger noCanonicalMatch = new AtomicInteger();
        AtomicInteger wrongPatternSkipped = new AtomicInteger();

        if (!duplicateGroups.isEmpty()) {
            int chunkSize = chunkSize(duplicateGroups.size());
            try (var scope = StructuredTaskScope.<Void>open()) {
                for (int start = 0; start < duplicateGroups.size(); start += chunkSize) {
                    int chunkStart = start;
                    int chunkEnd = Math.min(start + chunkSize, duplicateGroups.size());
                    scope.fork(() -> {
                        for (int i = chunkStart; i < chunkEnd; i++) {
                            IntObjectPair<MutableIntList> entry = duplicateGroups.get(i);
                            ComponentOutcome outcome = processComponent(patternEntity, entry.getOne(), entry.getTwo());
                            duplicatesWithdrawn.addAndGet(outcome.withdrawn);
                            alreadyWithdrawn.addAndGet(outcome.alreadyWithdrawn);
                            wrongPatternSkipped.addAndGet(outcome.wrongPattern);
                            if (outcome.noCanonicalMatch) {
                                noCanonicalMatch.incrementAndGet();
                            }
                        }
                        return null;
                    });
                }
                scope.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted processing duplicate groups", e);
            }
        }

        return new PatternResult(
                patternEntity.nid(),
                componentToSemantics.size(),
                duplicateGroups.size(),
                duplicatesWithdrawn.get(),
                alreadyWithdrawn.get(),
                noCanonicalMatch.get(),
                wrongPatternSkipped.get());
    }

    private MutableIntObjectMap<MutableIntList> parallelGroupByComponent(MutableIntList allNids) {
        MutableIntObjectMap<MutableIntList> merged = IntObjectMaps.mutable.empty();
        if (allNids.isEmpty()) {
            return merged;
        }
        int chunkSize = chunkSize(allNids.size());
        ImmutableIntList nids = allNids.toImmutable();
        List<StructuredTaskScope.Subtask<MutableIntObjectMap<MutableIntList>>> subtasks = new ArrayList<>();
        try (var scope = StructuredTaskScope.<MutableIntObjectMap<MutableIntList>>open()) {
            for (int start = 0; start < nids.size(); start += chunkSize) {
                int chunkStart = start;
                int chunkEnd = Math.min(start + chunkSize, nids.size());
                subtasks.add(scope.fork(() -> {
                    MutableIntObjectMap<MutableIntList> local = IntObjectMaps.mutable.empty();
                    for (int i = chunkStart; i < chunkEnd; i++) {
                        int nid = nids.get(i);
                        SemanticEntity<?> semantic = EntityHandle.getSemanticOrThrow(nid);
                        local.getIfAbsentPut(semantic.referencedComponentNid(), IntLists.mutable::empty).add(nid);
                    }
                    return local;
                }));
            }
            scope.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted grouping semantics by component", e);
        }
        for (StructuredTaskScope.Subtask<MutableIntObjectMap<MutableIntList>> subtask : subtasks) {
            MutableIntObjectMap<MutableIntList> local = subtask.get();
            local.forEachKeyValue((componentNid, nidsForComponent) ->
                    merged.getIfAbsentPut(componentNid, IntLists.mutable::empty).addAll(nidsForComponent));
        }
        return merged;
    }

    private static int chunkSize(int total) {
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        // Aim for ~4 chunks per core so work-stealing handles uneven chunks; floor at 1024 to amortize fork overhead.
        int target = Math.max(1024, (total + (cores * 4 - 1)) / (cores * 4));
        return Math.min(target, total);
    }

    private record ComponentOutcome(int withdrawn, int alreadyWithdrawn, int wrongPattern, boolean noCanonicalMatch) {}

    private ComponentOutcome processComponent(PatternEntity<?> patternEntity, int componentNid, MutableIntList semanticNids) {
        PublicId componentPublicId = PrimitiveData.publicId(componentNid);
        UUID canonicalUuid = UuidT5Generator.singleSemanticUuid(patternEntity, componentPublicId);

        int canonicalNid = -1;
        MutableIntList nonCanonical = IntLists.mutable.empty();
        int wrongPattern = 0;

        for (int i = 0; i < semanticNids.size(); i++) {
            int semanticNid = semanticNids.get(i);
            SemanticEntity<?> semantic = EntityHandle.getSemanticOrThrow(semanticNid);
            if (semantic.patternNid() != patternEntity.nid()) {
                wrongPattern++;
                LOG.error("Pattern mismatch in single-semantic scan. Expected pattern {}, semantic {} is on pattern {}, component {}",
                        PrimitiveData.textWithNid(patternEntity.nid()),
                        PrimitiveData.textWithNid(semanticNid),
                        PrimitiveData.textWithNid(semantic.patternNid()),
                        PrimitiveData.textWithNid(componentNid));
                continue;
            }
            if (canonicalNid == -1 && semantic.publicId().contains(canonicalUuid)) {
                canonicalNid = semanticNid;
            } else {
                nonCanonical.add(semanticNid);
            }
        }

        if (nonCanonical.isEmpty()) {
            return new ComponentOutcome(0, 0, wrongPattern, false);
        }

        boolean noCanonicalMatch = false;
        if (canonicalNid == -1) {
            noCanonicalMatch = true;
            nonCanonical.sortThis();
            int chosenVisibleNid = nonCanonical.removeAtIndex(0);
            LOG.info("No canonical UUID match for pattern {} component {}; canonicalUuid={}, keeping nid {} visible, withdrawing {}",
                    PrimitiveData.textWithNid(patternEntity.nid()),
                    PrimitiveData.textWithNid(componentNid),
                    canonicalUuid,
                    PrimitiveData.textWithNid(chosenVisibleNid),
                    nonCanonical.toList());
        }

        int withdrawn = 0;
        int alreadyWithdrawn = 0;
        for (int i = 0; i < nonCanonical.size(); i++) {
            int duplicateNid = nonCanonical.get(i);
            WithdrawOutcome o = withdrawDuplicate(duplicateNid);
            switch (o) {
                case WITHDRAWN -> withdrawn++;
                case ALREADY_WITHDRAWN -> alreadyWithdrawn++;
                case SKIPPED_NO_VERSIONS -> alreadyWithdrawn++;
            }
        }
        return new ComponentOutcome(withdrawn, alreadyWithdrawn, wrongPattern, noCanonicalMatch);
    }

    private enum WithdrawOutcome { WITHDRAWN, ALREADY_WITHDRAWN, SKIPPED_NO_VERSIONS }

    private WithdrawOutcome withdrawDuplicate(int duplicateNid) {
        SemanticRecord chronicle = EntityHandle.get(duplicateNid).expectSemanticRecord();
        SemanticVersionRecord mostRecent = mostRecentVersion(chronicle);
        if (mostRecent == null) {
            return WithdrawOutcome.SKIPPED_NO_VERSIONS;
        }
        StampEntity<?> mostRecentStamp = Entity.getStamp(mostRecent.stampNid());
        if (mostRecentStamp.state() == State.WITHDRAWN) {
            return WithdrawOutcome.ALREADY_WITHDRAWN;
        }
        if (dryRun) {
            return WithdrawOutcome.WITHDRAWN;
        }

        int withdrawalStampNid = transaction.getStamp(
                State.WITHDRAWN,
                currentAuthorNid,
                mostRecentStamp.moduleNid(),
                mostRecentStamp.pathNid()).nid();

        SemanticRecord withdrawn = chronicle.with(
                new SemanticVersionRecord(chronicle, withdrawalStampNid, mostRecent.fieldValues())).build();

        transaction.addComponent(withdrawn);
        Entity.provider().putEntityNoCache(withdrawn);
        return WithdrawOutcome.WITHDRAWN;
    }

    private static SemanticVersionRecord mostRecentVersion(SemanticRecord chronicle) {
        SemanticVersionRecord mostRecent = null;
        long mostRecentTime = Long.MIN_VALUE;
        for (SemanticVersionRecord version : chronicle.versions()) {
            long time = Entity.getStamp(version.stampNid()).time();
            if (time >= mostRecentTime) {
                mostRecentTime = time;
                mostRecent = version;
            }
        }
        return mostRecent;
    }
}
