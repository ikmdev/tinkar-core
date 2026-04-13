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
package dev.ikm.tinkar.common.service;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Provides access to the various thread pools used throughout the application.
 */
public interface ExecutorService {

    /**
     * Returns the fork-join thread pool.
     *
     * @return the {@link ForkJoinPool}
     */
    ForkJoinPool forkJoinThreadPool();

    /**
     * Returns the thread pool for blocking operations.
     *
     * @return the blocking {@link ThreadPoolExecutor}
     */
    ThreadPoolExecutor blockingThreadPool();

    /**
     * Returns the general-purpose thread pool.
     *
     * @return the {@link ThreadPoolExecutor}
     */
    ThreadPoolExecutor threadPool();

    /**
     * Returns the thread pool for I/O operations.
     *
     * @return the I/O {@link ThreadPoolExecutor}
     */
    ThreadPoolExecutor ioThreadPool();

    /**
     * Returns the scheduled executor service.
     *
     * @return the {@link ScheduledExecutorService}
     */
    ScheduledExecutorService scheduled();

}
