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
import dev.ikm.tinkar.entity.EntityService;
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

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Finds and withdraws duplicate semantics on patterns that conventionally hold
 * a single semantic per referenced component. Duplicates arise from a past
 * race condition in UUID generation; the canonical one is reproducible via
 * {@link UuidT5Generator#singleSemanticUuid(PublicId, PublicId)}.
 *
 * <p>Each non-canonical duplicate gets a new {@link State#WITHDRAWN} version
 * appended to its chronology. The withdrawal stamp inherits the duplicate's
 * own module and path; the author is supplied by the caller.
 *
 * <p>Patterns whose UUID is not registered in the active data store are
 * skipped with an INFO log. Nids that the pattern index points at but for
 * which {@code getEntityFast} returns no bytes are counted as
 * {@link PatternResult#nullSemanticNids()} — these signal an indexing
 * inconsistency separate from duplicate detection.
 *
 * <p>Concurrency: across-pattern fan-out, plus within each pattern a chunked
 * parallel grouping pass and a chunked parallel duplicate-processing pass.
 * Progress is reported through an optional {@link ProgressListener}.
 */
public final class SingleSemanticDuplicateWithdrawer {

    private static final Logger LOG = LoggerFactory.getLogger(SingleSemanticDuplicateWithdrawer.class);

    /**
     * Receives scan progress callbacks from background threads. Implementations
     * that update UI state must marshal to the UI thread themselves.
     */
    public interface ProgressListener {
        /** Called once after enumeration with the total nids that will be processed. */
        default void onTotalNids(long total) {}

        /** Called periodically as nids are grouped (cumulative count). */
        default void onNidsProcessed(long processed) {}

        /** Called when a pattern starts its parallel grouping pass. */
        default void onPatternStarting(EntityProxy.Pattern pattern, int nidsForPattern) {}
    }

    private static final ProgressListener NO_OP_LISTENER = new ProgressListener() {};

    public record PatternResult(
            int patternNid,
            int componentsScanned,
            int componentsWithDuplicates,
            int duplicatesWithdrawn,
            int alreadyWithdrawn,
            int noCanonicalMatch,
            int wrongPatternSkipped,
            int nullSemanticNids) {

        public static PatternResult empty(int patternNid) {
            return new PatternResult(patternNid, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    public record Report(ImmutableList<PatternResult> perPattern) {

        public long totalDuplicatesWithdrawn() {
            return perPattern.collectInt(PatternResult::duplicatesWithdrawn).sum();
        }

        public long totalComponentsWithDuplicates() {
            return perPattern.collectInt(PatternResult::componentsWithDuplicates).sum();
        }

        public long totalNullSemanticNids() {
            return perPattern.collectInt(PatternResult::nullSemanticNids).sum();
        }

        /**
         * Renders the report as Markdown. Intended for export to a {@code .md}
         * file as a record of the run.
         *
         * @param runCompletedAt timestamp the run finished (formatted ISO-8601)
         * @param dryRun         whether the run was dry-run only
         * @param authorNid      author nid that would be / was used for withdrawal stamps
         */
        public String toMarkdown(ZonedDateTime runCompletedAt, boolean dryRun, int authorNid) {
            long totalScanned = perPattern.collectInt(PatternResult::componentsScanned).sum();
            long totalAlready = perPattern.collectInt(PatternResult::alreadyWithdrawn).sum();
            long totalNoCan = perPattern.collectInt(PatternResult::noCanonicalMatch).sum();
            long totalWrong = perPattern.collectInt(PatternResult::wrongPatternSkipped).sum();

            StringBuilder sb = new StringBuilder();
            sb.append("# Single-Semantic Duplicate Withdrawer Report\n\n");
            sb.append("- **Run completed:** ").append(runCompletedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).append('\n');
            sb.append("- **Mode:** ").append(dryRun ? "Dry run" : "Withdraw").append('\n');
            sb.append("- **Author:** ").append(escapeMarkdown(PrimitiveData.text(authorNid))).append('\n');
            sb.append("- **Patterns scanned:** ").append(perPattern.size()).append("\n\n");

            sb.append("## Summary\n\n");
            sb.append("| Metric | Count |\n");
            sb.append("|--------|------:|\n");
            appendSummaryRow(sb, "Components scanned", totalScanned);
            appendSummaryRow(sb, "Components with duplicates", totalComponentsWithDuplicates());
            appendSummaryRow(sb, "Duplicates withdrawn", totalDuplicatesWithdrawn());
            appendSummaryRow(sb, "Already withdrawn", totalAlready);
            appendSummaryRow(sb, "No canonical match", totalNoCan);
            appendSummaryRow(sb, "Wrong pattern skipped", totalWrong);
            appendSummaryRow(sb, "Null semantic nids", totalNullSemanticNids());
            sb.append('\n');

            sb.append("## Per-Pattern Results\n\n");
            sb.append("| Pattern | Scanned | With Duplicates | Withdrawn | Already Withdrawn | No Canonical | Wrong Pattern | Null Nids |\n");
            sb.append("|---------|--------:|----------------:|----------:|------------------:|-------------:|--------------:|----------:|\n");
            for (PatternResult r : perPattern) {
                sb.append("| ").append(escapeMarkdown(PrimitiveData.text(r.patternNid())))
                        .append(" | ").append(String.format("%,d", r.componentsScanned()))
                        .append(" | ").append(String.format("%,d", r.componentsWithDuplicates()))
                        .append(" | ").append(String.format("%,d", r.duplicatesWithdrawn()))
                        .append(" | ").append(String.format("%,d", r.alreadyWithdrawn()))
                        .append(" | ").append(String.format("%,d", r.noCanonicalMatch()))
                        .append(" | ").append(String.format("%,d", r.wrongPatternSkipped()))
                        .append(" | ").append(String.format("%,d", r.nullSemanticNids()))
                        .append(" |\n");
            }
            return sb.toString();
        }

        private static void appendSummaryRow(StringBuilder sb, String label, long value) {
            sb.append("| ").append(label).append(" | ").append(String.format("%,d", value)).append(" |\n");
        }

        private static String escapeMarkdown(String s) {
            return s == null ? "" : s.replace("|", "\\|").replace("\n", " ");
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
        return scan(patterns, NO_OP_LISTENER);
    }

    public Report scan(Iterable<? extends EntityProxy.Pattern> patterns, ProgressListener listener) {
        record PatternEnum(EntityProxy.Pattern pattern, PatternEntity<?> entity, MutableIntList allNids) {}

        // Phase 1: sequential enumeration so we know the total before parallel work starts.
        List<PatternEnum> enumerated = new ArrayList<>();
        long totalNids = 0;
        for (EntityProxy.Pattern pattern : patterns) {
            int patternNid;
            try {
                patternNid = pattern.nid();
            } catch (IllegalStateException missing) {
                LOG.info("Skipping pattern not present in data store: {} ({})",
                        pattern.description(), pattern.publicId().idString());
                continue;
            }
            PatternEntity<?> patternEntity = EntityHandle.getPatternOrThrow(patternNid);
            MutableIntList allNids = IntLists.mutable.empty();
            PrimitiveData.get().forEachSemanticNidOfPattern(patternNid, allNids::add);
            enumerated.add(new PatternEnum(pattern, patternEntity, allNids));
            totalNids += allNids.size();
        }
        listener.onTotalNids(totalNids);

        // Phase 2: parallel per-pattern processing.
        AtomicLong processedNids = new AtomicLong();
        List<StructuredTaskScope.Subtask<PatternResult>> subtasks = new ArrayList<>();
        try (var scope = StructuredTaskScope.<PatternResult>open()) {
            for (PatternEnum pe : enumerated) {
                subtasks.add(scope.fork(() -> {
                    listener.onPatternStarting(pe.pattern, pe.allNids.size());
                    return scanCollected(pe.entity, pe.allNids, processedNids, listener);
                }));
            }
            scope.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted scanning patterns", e);
        }

        MutableList<PatternResult> results = Lists.mutable.empty();
        for (StructuredTaskScope.Subtask<PatternResult> subtask : subtasks) {
            results.add(subtask.get());
        }
        return new Report(results.toImmutable());
    }

    private PatternResult scanCollected(PatternEntity<?> patternEntity, MutableIntList allNids,
                                        AtomicLong processedNids, ProgressListener listener) {
        AtomicInteger nullCount = new AtomicInteger();
        MutableIntObjectMap<MutableIntList> componentToSemantics =
                parallelGroupByComponent(allNids, nullCount, processedNids, listener);

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
                wrongPatternSkipped.get(),
                nullCount.get());
    }

    private MutableIntObjectMap<MutableIntList> parallelGroupByComponent(
            MutableIntList allNids, AtomicInteger nullCount,
            AtomicLong processedNids, ProgressListener listener) {
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
                        Entity<?> entity = EntityService.get().getEntityFast(nid);
                        if (entity == null) {
                            nullCount.incrementAndGet();
                            continue;
                        }
                        if (!(entity instanceof SemanticEntity<?> semantic)) {
                            // Index says semantic-of-pattern but bytes decode to something else. Treat as wrong-type.
                            LOG.error("Index says nid {} is a semantic of pattern but bytes decode as {}",
                                    nid, entity.getClass().getSimpleName());
                            continue;
                        }
                        local.getIfAbsentPut(semantic.referencedComponentNid(), IntLists.mutable::empty).add(nid);
                    }
                    long now = processedNids.addAndGet(chunkEnd - chunkStart);
                    listener.onNidsProcessed(now);
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
        // ~4 chunks per core so work-stealing handles uneven chunks; floor at 1024 to amortize fork overhead.
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
