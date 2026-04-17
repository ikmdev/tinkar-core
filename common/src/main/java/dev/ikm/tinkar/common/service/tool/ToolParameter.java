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

import java.util.Optional;

/**
 * Describes one parameter of a tool.
 *
 * @param name parameter name (JSON object key in invocation)
 * @param schema JSON-Schema-compatible type description
 * @param description human-readable description for the LLM
 * @param required whether the parameter must be supplied
 * @param defaultValue default if not supplied; meaningful only when
 *                     {@code required} is {@code false}
 */
public record ToolParameter(
        String name,
        ToolSchema schema,
        String description,
        boolean required,
        Optional<Object> defaultValue) {

    /**
     * Canonical constructor validating required fields. Null
     * {@code defaultValue} is normalized to {@link Optional#empty()}.
     *
     * @throws IllegalArgumentException if {@code name} is null or blank,
     *         or {@code schema} is null
     */
    public ToolParameter {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("parameter name required");
        }
        if (schema == null) {
            throw new IllegalArgumentException("parameter schema required");
        }
        if (defaultValue == null) {
            defaultValue = Optional.empty();
        }
    }

    /**
     * Factory for a required parameter with no default value.
     *
     * @param name parameter name
     * @param schema parameter type description
     * @param description human-readable description for the LLM
     * @return a required parameter
     */
    public static ToolParameter requiredParam(String name, ToolSchema schema, String description) {
        return new ToolParameter(name, schema, description, true, Optional.empty());
    }

    /**
     * Factory for an optional parameter with no default value.
     *
     * @param name parameter name
     * @param schema parameter type description
     * @param description human-readable description for the LLM
     * @return an optional parameter without default
     */
    public static ToolParameter optionalParam(String name, ToolSchema schema, String description) {
        return new ToolParameter(name, schema, description, false, Optional.empty());
    }

    /**
     * Factory for an optional parameter carrying a default value applied
     * when the caller supplies none.
     *
     * @param name parameter name
     * @param schema parameter type description
     * @param description human-readable description for the LLM
     * @param defaultValue value applied when the parameter is omitted
     * @return an optional parameter with default
     */
    public static ToolParameter optionalParamWithDefault(String name, ToolSchema schema,
                                                         String description, Object defaultValue) {
        return new ToolParameter(name, schema, description, false, Optional.of(defaultValue));
    }
}
