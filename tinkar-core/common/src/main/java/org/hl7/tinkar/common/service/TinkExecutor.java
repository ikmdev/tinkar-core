package org.hl7.tinkar.common.service;

import com.google.auto.service.AutoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

public class TinkExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(TinkExecutor.class);
    private static TinkExecutor executor = new TinkExecutor();
    private static ExecutorService executorSingleton;
    private static ExecutorController executorController;

    ServiceLoader<ExecutorController> loader;

    private TinkExecutor() {
        this.loader = ServiceLoader.load(ExecutorController.class);
    }

    private static final int defaultParallelBatchSize = Runtime.getRuntime().availableProcessors() * 4;

    public static int defaultParallelBatchSize() {
        return defaultParallelBatchSize;
    }

    public static void stop() {
        executorController.stop();
        executorSingleton = null;
        executorController = null;
    }

    public static ForkJoinPool forkJoinThreadPool() {
        if (executorSingleton == null) {
            start();
        }
        return executorSingleton.forkJoinThreadPool();
    }

    public static void start() throws ServiceConfigurationError {
        if (executor == null) {
            executor = new TinkExecutor();
        }
        if (executorController == null) {
            List<ServiceLoader.Provider<ExecutorController>> controllers = executor.loader.stream().toList();
            if (controllers.isEmpty()) {
                throw new ServiceConfigurationError("No controllers found");
            }
            if (controllers.size() > 1) {
                throw new ServiceConfigurationError("More than one controller: " + controllers);
            }
            executorController = controllers.get(0).get();
        }
        if (executorSingleton == null) {
            executorSingleton = executorController.create();
        }
    }

    public static ThreadPoolExecutor blockingThreadPool() {
        if (executorSingleton == null) {
            start();
        }
        return executorSingleton.blockingThreadPool();
    }

    public static ThreadPoolExecutor threadPool() {
        if (executorSingleton == null) {
            start();
        }
        return executorSingleton.threadPool();
    }

    public static ThreadPoolExecutor ioThreadPool() {
        if (executorSingleton == null) {
            start();
        }
        return executorSingleton.ioThreadPool();
    }

    public static ScheduledExecutorService scheduled() {
        if (executorSingleton == null) {
            start();
        }
        return executorSingleton.scheduled();
    }


    @AutoService(CachingService.class)
    public static class CacheProvider implements CachingService {

        @Override
        public void reset() {
            executorController = null;
            executorSingleton = null;
            executor = null;
        }
    }

}
