package org.hl7.tinkar.provider.executor;

import com.google.auto.service.AutoService;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.common.service.ExecutorController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

@AutoService({ExecutorController.class, CachingService.class})
public class ExecutorProviderController implements ExecutorController, CachingService {
    private static final Logger LOG = LoggerFactory.getLogger(ExecutorProviderController.class);
    private AtomicReference<ExecutorProvider> providerReference = new AtomicReference<>();

    @Override
    public void reset() {
        stop();
    }

    @Override
    public ExecutorProvider create() {
        if (providerReference.get() == null) {
            providerReference.updateAndGet(executorProvider -> {
                if (executorProvider != null) {
                    return executorProvider;
                }
                return new ExecutorProvider();
            });
            providerReference.get().start();
        }
        return providerReference.get();
    }

    @Override
    public void stop() {
        providerReference.updateAndGet(executorProvider -> {
            if (executorProvider != null) {
                executorProvider.stop();
            }
            return null;
        });
    }
}
