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
package dev.ikm.tinkar.coordinate.internal;

import dev.ikm.tinkar.coordinate.PathService;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.ServiceLoader;

import dev.ikm.tinkar.common.service.PluggableService;

public enum PathServiceFinder {
    INSTANCE;

    final PathService service;

    PathServiceFinder() {
        Class serviceClass = PathService.class;
        ServiceLoader<PathService> serviceLoader = PluggableService.load(serviceClass);
        Optional<PathService> optionalService = serviceLoader.findFirst();
        if (optionalService.isPresent()) {
            this.service = optionalService.get();
        } else {
            throw new NoSuchElementException("No " + serviceClass.getName() +
                    " found by PluggableService...");
        }
    }

    public PathService get() {
        return service;
    }
}
