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
package dev.ikm.tinkar.provider.spinedarray.internal;

import dev.ikm.tinkar.component.Chronology;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityRecordFactory;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.provider.spinedarray.SpinedArrayProvider;

public class Put {
    public static SpinedArrayProvider singleton;

    public static void put(Chronology chronology) {
        Entity entity = EntityRecordFactory.make(chronology);
        if (entity instanceof SemanticEntity semanticEntity) {
            singleton.merge(entity.nid(),
                    semanticEntity.patternNid(),
                    semanticEntity.referencedComponentNid(),
                    entity.getBytes(), semanticEntity);
        } else {
            singleton.merge(entity.nid(), Integer.MAX_VALUE, Integer.MAX_VALUE, entity.getBytes(), entity);
        }
    }
}
