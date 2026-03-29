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
package dev.ikm.tinkar.entity;

import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.terms.EntityFacade;

/**
 * Utility methods for converting entities and their attached semantics to a string
 * by walking the semantic graph recursively.
 */
public final class EntityStringUtil {

    private EntityStringUtil() {
        // Utility class
    }

    /**
     * Build a recursive string for the provided nid that includes the entity and all
     * attached semantics.
     *
     * @param nid nid to render
     * @return recursive toString for the nid and its semantics
     */
    public static String recursiveEntityToString(int nid) {
        StringBuilder sb = new StringBuilder();
        appendRecursiveEntityToString(nid, sb);
        return sb.toString();
    }

    /**
     * Build a recursive string for the provided entity facade that includes the entity and all
     * attached semantics.
     *
     * @param entityFacade facade of the entity to render
     * @return recursive toString for the entity and its semantics
     */
    public static String recursiveEntityToString(EntityFacade entityFacade) {
        return recursiveEntityToString(entityFacade.nid());
    }

    private static void appendRecursiveEntityToString(int nid, StringBuilder sb) {
        EntityHandle.get(nid).ifPresent(entity -> {
            sb.append(entity);
            sb.append("\n\n");
            for (int semanticNid : PrimitiveData.get().semanticNidsForComponent(nid)) {
                appendRecursiveEntityToString(semanticNid, sb);
            }
        });
    }
}
