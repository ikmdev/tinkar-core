package org.hl7.tinkar.provider.executor;

import com.google.auto.service.AutoService;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.common.service.ExecutorController;

import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

@AutoService({ExecutorController.class, CachingService.class})
public class ExecutorProviderController implements ExecutorController, CachingService {
    private static final Logger LOG = Logger.getLogger(ExecutorProviderController.class.getName());
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
