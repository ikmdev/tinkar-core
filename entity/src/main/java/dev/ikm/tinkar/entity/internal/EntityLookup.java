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
package dev.ikm.tinkar.entity.internal;

import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;

/**
 * The entity provider's lookup by nid, behind {@link EntityHandle}.
 *
 * <p>The running {@link EntityService} implements this interface. {@link EntityHandle} is its
 * only caller; every other module looks entities up through {@link EntityHandle}, which says
 * what the caller expects to find and fails with a description of what was missing.
 *
 * <p>The package is exported only to the entity provider's module, so code in any other module
 * on the module path cannot compile against it, and reaching it reflectively takes an explicit
 * {@code --add-exports}. Code on the class path is not held back by the module system; the build
 * check reports a reference from anywhere else.
 */
public interface EntityLookup {

    /**
     * Returns the entity with this nid.
     *
     * @param nid the entity's nid
     * @return the entity, or {@code null} if the store holds none for the nid
     */
    Entity<?> entityOrNull(int nid);

    /**
     * Returns the lookup of the running entity provider.
     *
     * @return the running {@link EntityService} as its lookup
     * @throws IllegalStateException if the running {@link EntityService} does not implement
     *                               this interface
     */
    static EntityLookup current() {
        EntityService service = EntityService.get();
        if (service instanceof EntityLookup lookup) {
            return lookup;
        }
        throw new IllegalStateException("The running EntityService does not implement EntityLookup: "
                + service.getClass().getName());
    }
}
