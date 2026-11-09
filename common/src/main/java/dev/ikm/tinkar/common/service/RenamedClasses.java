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

import java.util.Optional;

/**
 * Classes a module renamed or moved, so a name stored before the change still resolves. Komet
 * keeps the factory class of each saved window, card and area by name; when a plugin moves its
 * classes to another package, the saved layouts still hold the former names, and
 * {@link PluggableService#forName} asks the providers of this service for the class each now is.
 *
 * <p>Asked only for a name no class has, so a provider costs nothing while names resolve.
 * Provided as a service ({@code provides} in a module descriptor, or
 * {@code META-INF/services} on the class path).</p>
 */
public interface RenamedClasses {

    /**
     * The binary name a class has now, when this provider renamed it.
     *
     * @param formerName the binary name the class had, as a saved layout holds it, nested classes
     *                   with {@code $}
     * @return its binary name now; empty when this provider did not rename it
     */
    Optional<String> current(String formerName);
}
