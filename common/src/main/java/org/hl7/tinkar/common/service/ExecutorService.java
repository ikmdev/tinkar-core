package org.hl7.tinkar.common.service;

import org.hl7.tinkar.common.util.thread.PausableThreadPoolExecutor;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

public interface ExecutorService {

    /** The fork join executor. */
    ForkJoinPool forkJoinThreadPool();

    /** The blocking thread pool executor. */
    ThreadPoolExecutor blockingThreadPool();

    /** The thread pool executor. */
    ThreadPoolExecutor threadPool();

    /** The io thread pool executor. */
    ThreadPoolExecutor ioThreadPool();

    /** The scheduled executor. */
    ScheduledExecutorService scheduled();

}
