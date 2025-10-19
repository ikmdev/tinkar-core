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
package dev.ikm.tinkar.provider.entity;

import java.util.concurrent.atomic.AtomicReference;

//@AutoService({EntityService.class})
public class EntityServiceFactory {
    protected static final AtomicReference<EntityProvider> provider = new AtomicReference<>();

    public static EntityProvider provider() {
        return provider.updateAndGet(entityProvider -> {
            if (entityProvider == null) {
                return new EntityProvider();
            }
            return entityProvider;
        });
    }


}
