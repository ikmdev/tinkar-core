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
package dev.ikm.tinkar.common.service.tool;

import java.util.Map;
import java.util.Optional;

/**
 * A single call to a tool, containing parameter values and invocation context.
 *
 * @param parameters parameter values as a JSON-shaped map; keys match the
 *                   names declared by {@link ToolDescriptor#parameters()}
 * @param context per-invocation context (STAMP coordinate, session
 *                identifier, caller identity)
 */
public record ToolInvocation(
        Map<String, Object> parameters,
        ToolContext context) {

    /**
     * Canonical constructor normalizing null fields to sensible empty
     * defaults ({@link Map#of()} for parameters, {@link ToolContext#empty()}
     * for context).
     */
    public ToolInvocation {
        if (parameters == null) {
            parameters = Map.of();
        }
        if (context == null) {
            context = ToolContext.empty();
        }
    }

    /**
     * Returns a typed parameter value. Returns {@link Optional#empty()} if
     * the parameter is absent or is not an instance of the requested type.
     *
     * @param name parameter name
     * @param type expected runtime type
     * @param <T> expected type
     * @return the typed value, or empty if absent or not of the requested type
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String name, Class<T> type) {
        Object v = parameters.get(name);
        if (v == null || !type.isInstance(v)) {
            return Optional.empty();
        }
        return Optional.of((T) v);
    }

    /**
     * Convenience accessor for a string parameter.
     *
     * @param name parameter name
     * @return the string value, or empty if absent or not a string
     */
    public Optional<String> getString(String name) {
        return get(name, String.class);
    }

    /**
     * Convenience accessor for an integer parameter. Accepts any
     * {@link Number}-shaped value and narrows via {@link Number#intValue()}.
     *
     * @param name parameter name
     * @return the integer value, or empty if absent or not a number
     */
    public Optional<Integer> getInteger(String name) {
        Object v = parameters.get(name);
        if (v instanceof Integer i) {
            return Optional.of(i);
        }
        if (v instanceof Number n) {
            return Optional.of(n.intValue());
        }
        return Optional.empty();
    }
}
