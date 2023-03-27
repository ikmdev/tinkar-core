package dev.ikm.tinkar.provider.executor;

import com.google.auto.service.AutoService;
import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.common.service.ExecutorController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

@AutoService({ExecutorController.class})
public class ExecutorProviderController implements ExecutorController {
    private static final Logger LOG = LoggerFactory.getLogger(ExecutorProviderController.class);
    private static AtomicReference<ExecutorProvider> providerReference = new AtomicReference<>();

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


    @AutoService(CachingService.class)
    public static class CacheProvider implements CachingService {
        @Override
        public void reset() {
            providerReference.updateAndGet(executorProvider -> {
                if (executorProvider != null) {
                    executorProvider.stop();
                }
                return null;
            });
        }
    }

}
