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

import java.util.ServiceConfigurationError;

/**
 * Service provider interface for creating and stopping the application's
 * {@link ExecutorService}. Implementations are loaded via {@link java.util.ServiceLoader}.
 */
public interface ExecutorController {

    /**
     * Creates and returns a new {@link ExecutorService} instance.
     *
     * @return the newly created executor service
     * @throws ServiceConfigurationError if the executor service cannot be created
     */
    ExecutorService create() throws ServiceConfigurationError;

    /**
     * Stops the executor service, shutting down any running threads and
     * releasing associated resources.
     */
    void stop();
}
