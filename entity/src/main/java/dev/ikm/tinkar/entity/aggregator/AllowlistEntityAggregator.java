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
package dev.ikm.tinkar.entity.aggregator;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.EntityCountSummary;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.entity.PatternEntityVersion;
import dev.ikm.tinkar.entity.SemanticEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * Aggregates a change set by an <b>allowlist</b> over STAMP dimensions: a chronology is included when
 * any of its stamps is authored in an allowed <b>module</b> (and, when a path allowlist is supplied, on
 * an allowed <b>path</b>). An optional <b>purpose</b> predicate further refines semantics by their
 * pattern's purpose — a permitted, complementary classification, off by default.
 *
 * <p>This is the mirror of {@link TemporalEntityAggregator} on the module/path axes rather than time,
 * and it is <em>default-deny</em>: only the named modules cross the boundary, so a newly-added module is
 * invisible to a knowledge-distribution export until it is explicitly allowlisted — the failure mode is
 * "forgot to include" (loud), never "forgot to exclude" (a silent leak).
 *
 * <p>It emits <b>patterns before the semantics that reference them</b> (unlike
 * {@link DefaultEntityAggregator}, which emits semantics first), so a consumer can resolve a semantic's
 * pattern — and thus its pattern-purpose — before applying it. Referenced stamps of included entities
 * are always exported so entities resolve on import, even when a stamp's own module is not allowlisted.
 *
 * <p><b>v1 scope:</b> the whole chronology is exported when <em>any</em> of its stamps qualifies,
 * matching {@link TemporalEntityAggregator}'s any-stamp semantics. This is exact for single-module
 * content and for the layout-vs-fact case, where layout is a <em>separate</em> semantic in a different
 * module and is excluded outright. Partial-chronology export — dropping the disallowed-module
 * <em>versions</em> of an otherwise-included cross-module entity — is a follow-up.
 */
public class AllowlistEntityAggregator extends EntityAggregator {

    private final Set<PublicId> allowedModules;
    private final Set<PublicId> allowedPaths;
    private final IntPredicate purposeNidPredicate;

    /**
     * An allowlist by module only, any path, no purpose refinement.
     *
     * @param allowedModules the module concepts whose content may cross the boundary
     */
    public AllowlistEntityAggregator(Set<PublicId> allowedModules) {
        this(allowedModules, Set.of(), null);
    }

    /**
     * @param allowedModules      the module concepts whose content may cross the boundary; required,
     *                            non-empty for anything to be exported
     * @param allowedPaths        the path concepts allowed, or empty for any path
     * @param purposeNidPredicate an optional refinement applied to <em>semantics</em>, tested against
     *                            their pattern's purpose nid; {@code null} disables purpose filtering
     *                            (purpose is a permitted, complementary key — off by default)
     */
    public AllowlistEntityAggregator(Set<PublicId> allowedModules, Set<PublicId> allowedPaths,
                                     IntPredicate purposeNidPredicate) {
        this.allowedModules = Set.copyOf(Objects.requireNonNull(allowedModules, "allowedModules"));
        this.allowedPaths = allowedPaths == null ? Set.of() : Set.copyOf(allowedPaths);
        this.purposeNidPredicate = purposeNidPredicate;
    }

    @Override
    public EntityCountSummary aggregate(IntConsumer nidConsumer) {
        initCounts();

        Set<Integer> allowedModuleNids = new HashSet<>();
        allowedModules.forEach(publicId -> allowedModuleNids.add(PrimitiveData.nid(publicId)));
        Set<Integer> allowedPathNids = new HashSet<>();
        allowedPaths.forEach(publicId -> allowedPathNids.add(PrimitiveData.nid(publicId)));

        // The stamps whose module (and path, when constrained) is allowlisted.
        Set<Integer> allowedStampNids = new HashSet<>();
        PrimitiveData.get().forEachStampNid(stampNid ->
                EntityService.get().getStamp(stampNid).ifPresent(stampEntity -> {
                    boolean moduleOk = allowedModuleNids.contains(stampEntity.moduleNid());
                    boolean pathOk = allowedPathNids.isEmpty() || allowedPathNids.contains(stampEntity.pathNid());
                    if (moduleOk && pathOk) {
                        allowedStampNids.add(stampEntity.nid());
                    }
                }));

        List<Integer> referencedStampNids = new ArrayList<>();

        // Concepts included when any of their stamps is allowlisted.
        PrimitiveData.get().forEachConceptNid(conceptNid ->
                EntityService.get().getEntity(conceptNid).ifPresent(conceptEntity -> {
                    Set<Integer> stampNids = conceptEntity.stampNids().mapToSet(i -> i);
                    if (!Collections.disjoint(allowedStampNids, stampNids)) {
                        conceptsAggregatedCount.incrementAndGet();
                        nidConsumer.accept(conceptNid);
                        referencedStampNids.addAll(stampNids);
                    }
                }));

        // Patterns BEFORE semantics, so a consumer can resolve a semantic's pattern (and purpose) first.
        PrimitiveData.get().forEachPatternNid(patternNid ->
                EntityService.get().getEntity(patternNid).ifPresent(patternEntity -> {
                    Set<Integer> stampNids = patternEntity.stampNids().mapToSet(i -> i);
                    if (!Collections.disjoint(allowedStampNids, stampNids)) {
                        patternsAggregatedCount.incrementAndGet();
                        nidConsumer.accept(patternNid);
                        referencedStampNids.addAll(stampNids);
                    }
                }));

        // Semantics included when any stamp is allowlisted and the optional purpose refinement passes.
        PrimitiveData.get().forEachSemanticNid(semanticNid ->
                EntityService.get().getEntity(semanticNid).ifPresent(semanticEntity -> {
                    Set<Integer> stampNids = semanticEntity.stampNids().mapToSet(i -> i);
                    if (!Collections.disjoint(allowedStampNids, stampNids)
                            && purposeAllows(semanticEntity)) {
                        semanticsAggregatedCount.incrementAndGet();
                        nidConsumer.accept(semanticNid);
                        referencedStampNids.addAll(stampNids);
                    }
                }));

        Set<Integer> deduplicatedStampNids = new HashSet<>(referencedStampNids);
        stampsAggregatedCount.set(deduplicatedStampNids.size());
        deduplicatedStampNids.forEach(nidConsumer::accept);

        return summarize();
    }

    /**
     * Whether the optional purpose refinement admits {@code entity}. Non-semantics, and — when no
     * purpose predicate is set — all semantics pass. Purpose is a <em>complementary</em> refinement, so
     * it is <b>fail-open</b>: a semantic is excluded only when its pattern-purpose is positively
     * resolved and the predicate rejects it; a semantic whose pattern-purpose cannot be resolved (for
     * example a description on a {@code TinkarTerm} pattern absent from a replay-seeded store) is
     * <em>kept</em>. Purpose never drops content it cannot classify.
     */
    private boolean purposeAllows(Entity<?> entity) {
        if (purposeNidPredicate == null || !(entity instanceof SemanticEntity<?> semantic)) {
            return true;
        }
        Entity<?> pattern = EntityService.get().getEntityFast(semantic.patternNid());
        if (pattern instanceof PatternEntity<?> patternEntity && !patternEntity.versions().isEmpty()) {
            PatternEntityVersion latest = patternEntity.versions().getLast();
            return purposeNidPredicate.test(latest.semanticPurposeNid());
        }
        return true; // unresolvable purpose → keep (complementary refinement, fail-open)
    }
}
