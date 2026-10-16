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
package dev.ikm.tinkar.ext.lang.owl;


import dev.ikm.tinkar.common.service.TinkExecutor;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.common.util.time.Stopwatch;
import dev.ikm.tinkar.coordinate.logic.PremiseType;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.PatternFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

public class Rf2OwlToLogicAxiomTransformer extends TrackingCallable<Void> {
    private static final Logger LOG = LoggerFactory.getLogger(Rf2OwlToLogicAxiomTransformer.class);
    final int transformSize = 10240;
    private final Transaction transaction;

    private final PatternFacade rf2OwlPattern;
    private final PatternFacade logicalAxiomPattern;
    private long authorNid = KernelConcept.USER.nid();
    private long moduleNid = Integer.MAX_VALUE;
    private long pathNid = Integer.MAX_VALUE;

    public Rf2OwlToLogicAxiomTransformer(Transaction transaction,
                                         PatternFacade rf2OwlPattern,
                                         PatternFacade logicalAxiomPattern) {
        super(false, true);
        this.transaction = transaction;
        this.rf2OwlPattern = rf2OwlPattern;
        this.logicalAxiomPattern = logicalAxiomPattern;
        updateTitle("Converting RF2 OWL to expressions");
    }

    public Rf2OwlToLogicAxiomTransformer(Transaction transaction,
                                         PatternFacade rf2OwlPattern,
                                         PatternFacade logicalAxiomPattern,
                                         long authorNid, long moduleNid, long pathNid) {
        this(transaction, rf2OwlPattern, logicalAxiomPattern);
        this.authorNid = authorNid;
        this.moduleNid = moduleNid;
        this.pathNid = pathNid;
    }

    /**
     * Transforms only the concepts a predicate accepts: those an import wrote OWL axioms for.
     * Transforming every concept after a layered import rewrites the stated axioms of the
     * layers beneath it under the import's author, a new version on each.
     *
     * @param concepts which concepts to transform, by nid
     */
    public Rf2OwlToLogicAxiomTransformer(Transaction transaction,
                                         PatternFacade rf2OwlPattern,
                                         PatternFacade logicalAxiomPattern,
                                         long authorNid, long moduleNid, long pathNid,
                                         java.util.function.LongPredicate concepts) {
        this(transaction, rf2OwlPattern, logicalAxiomPattern, authorNid, moduleNid, pathNid);
        this.concepts = concepts;
    }

    private java.util.function.LongPredicate concepts = nid -> true;

    @Override
    protected Void compute() throws Exception {
        Stopwatch stopwatch = new Stopwatch();
        updateMessage("Computing stated OWL expressions...");
        LOG.info("Computing stated OWL expressions...");
        addToTotalWork(4);
        completedUnitOfWork();
        updateMessage("Transforming stated OWL RF2 expressions...");
        List<TransformationGroup> statedTransformList = new ArrayList<>();
        AtomicInteger count = new AtomicInteger();
        long rf2OwlPatternNid = rf2OwlPattern.nid();

        EntityService.get().beginLoadPhase();
        try {
            List<Callable<Void>> tasks = new ArrayList<>();

            EntityService.get().forEachConceptEntity(concept -> {
                long conceptNid = concept.nid();
                if (!concepts.test(conceptNid)) {
                    return;
                }
                long[] semanticNids = EntityService.get().semanticsForComponentOfPattern(conceptNid, rf2OwlPatternNid)
                        .mapToLong(SemanticEntity::nid).toArray();
                if (semanticNids != null) {
                    if (semanticNids.length > 0) {
                        TransformationGroup tg = new TransformationGroup(conceptNid, semanticNids, PremiseType.STATED);
                        statedTransformList.add(tg);
                        count.incrementAndGet();
                    }
                }
                if (statedTransformList.size() == transformSize) {
                    List<TransformationGroup> listForTask = List.copyOf(statedTransformList);
                    OwlToLogicAxiomTransformerAndWriter transformer = new OwlToLogicAxiomTransformerAndWriter(
                            transaction, listForTask, logicalAxiomPattern.nid(), authorNid, moduleNid, pathNid);
                    tasks.add(transformer);
                    statedTransformList.clear();
                }
            });

            // pickup any items remaining in the list.
            if (!statedTransformList.isEmpty()) {
                OwlToLogicAxiomTransformerAndWriter remainingStatedtransformer = new OwlToLogicAxiomTransformerAndWriter(
                        transaction, statedTransformList, logicalAxiomPattern.nid(), authorNid, moduleNid, pathNid);
                tasks.add(remainingStatedtransformer);
            }

            LOG.info("Started processing {} batches", tasks.size());

            TinkExecutor.threadPool().invokeAll(tasks);

            completedUnitOfWork();

            transaction.commit();
            completedUnitOfWork();
            updateMessage("Completed transformation");
        } finally {
            EntityService.get().endLoadPhase();
            LOG.info("Completed processing of {} stated OWL expressions in {}", count.get(), stopwatch.durationString());
        }
        return null;
    }
}
