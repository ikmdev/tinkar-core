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
package dev.ikm.tinkar.provider.entity;

import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ServiceKeys;
import dev.ikm.tinkar.common.service.ServiceLifecycle;
import dev.ikm.tinkar.common.service.ServiceLifecyclePhase;
import dev.ikm.tinkar.common.service.ServiceProperties;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.transaction.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Gives open transactions back their uncommitted versions when a store opens
 * (IKE-Network/ike-issues#1308). Starts in the entities phase right after the
 * {@link EntityProvider}, whose reads it needs, and before anything else reads the store:
 *
 * <ol>
 *   <li>rebuilds each transaction whose record in the store says it is open;</li>
 *   <li>then, when the deployment asks for it ({@link ServiceKeys#CANCEL_UNCOMMITTED_STAMPS_AT_STARTUP}),
 *       cancels the uncommitted stamps no transaction owns, so an open transaction's are spared;</li>
 *   <li>and registers a save step, ahead of the others, that brings each open transaction's
 *       record up to date with the components it has written, which saving the store runs before
 *       it stops.</li>
 * </ol>
 *
 * When the store stops, the transactions held in memory for it are let go; their records remain.
 */
public class TransactionRecordsController implements ServiceLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionRecordsController.class);

    /** For the service loader. */
    public TransactionRecordsController() {
    }

    @Override
    public void startup() {
        EntityService entities = EntityService.get();
        int restored = Transaction.restoreOpen(entities);
        if (restored > 0) {
            LOG.info("Rebuilt {} open transaction(s) from their records", restored);
        }
        if (ServiceProperties.get(ServiceKeys.CANCEL_UNCOMMITTED_STAMPS_AT_STARTUP, Boolean.FALSE)
                && entities instanceof EntityProvider provider) {
            provider.cancelUncommittedStamps();
        }
        // First among the save steps: the record versions it writes must reach the change-set
        // writer before that writer's own save flushes, or they are written as the store stops.
        PrimitiveData.getStatesToSave().add(0, () -> {
            int checkpointed = Transaction.checkpointOpen(EntityService.get());
            if (checkpointed > 0) {
                LOG.info("Recorded {} open transaction(s) as the store was saved", checkpointed);
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    @Override
    public void shutdown() {
        Transaction.forgetAll();
    }

    @Override
    public ServiceLifecyclePhase getLifecyclePhase() {
        return ServiceLifecyclePhase.ENTITIES;
    }

    @Override
    public int getSubPriority() {
        return 20; // After the EntityProvider (10), before the next phase reads the store
    }
}
