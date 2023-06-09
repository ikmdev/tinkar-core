package dev.ikm.tinkar.provider.executor;

import dev.ikm.tinkar.common.alert.AlertObject;
import dev.ikm.tinkar.common.alert.AlertStreams;
import dev.ikm.tinkar.common.service.TrackingCallable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

public class TinkarScheduledExecutor extends ScheduledThreadPoolExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(TinkarScheduledExecutor.class);

    public TinkarScheduledExecutor(int corePoolSize) {
        super(corePoolSize);
    }

    public TinkarScheduledExecutor(int corePoolSize, ThreadFactory threadFactory) {
        super(corePoolSize, threadFactory);
    }

    public TinkarScheduledExecutor(int corePoolSize, RejectedExecutionHandler handler) {
        super(corePoolSize, handler);
    }

    public TinkarScheduledExecutor(int corePoolSize, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, threadFactory, handler);
    }

    @Override
    protected <V> RunnableScheduledFuture<V> decorateTask(Runnable runnable, RunnableScheduledFuture<V> task) {
        if (runnable instanceof TrackingCallable) {

        }
        return super.decorateTask(runnable, task);
    }

    @Override
    protected <V> RunnableScheduledFuture<V> decorateTask(Callable<V> callable, RunnableScheduledFuture<V> task) {
        if (callable instanceof TrackingCallable) {

        }
        return super.decorateTask(callable, task);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        if (t != null) {
            AlertStreams.getRoot().dispatch(AlertObject.makeError(t));
        }
    }
}