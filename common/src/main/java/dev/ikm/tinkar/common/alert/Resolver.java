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
package dev.ikm.tinkar.common.alert;

import java.util.concurrent.FutureTask;

/**
 * Generic resolution interface providing a title, description, executable resolution
 * action, and persistence model. See also {@link AlertResolver} for alert-specific resolution.
 */
public interface Resolver {

    /**
     * Returns a short, human-readable title for this resolution strategy.
     *
     * @return the resolver title
     */
    String getTitle();

    /**
     * Returns a longer description explaining what this resolution will do.
     *
     * @return the resolver description
     */
    String getDescription();

    /**
     * Creates and returns a {@link FutureTask} that, when executed, performs the
     * resolution action.
     *
     * @return a {@link FutureTask} encapsulating the resolution logic
     */
    FutureTask<Void> resolve();

    /**
     * Returns the persistence model for this resolution, indicating whether it
     * is {@link ResolutionPersistence#TEMPORARY} or {@link ResolutionPersistence#PERMANENT}.
     *
     * @return the {@link ResolutionPersistence} of this resolver
     */
    ResolutionPersistence getPersistence();
}
