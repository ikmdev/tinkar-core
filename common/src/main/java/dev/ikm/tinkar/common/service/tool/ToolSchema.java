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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * A simplified JSON-Schema-shaped type description for tool parameters and
 * results.
 * <p>
 * Tool consumers (e.g. LLM drivers) translate this to the target schema
 * format they need: JSON Schema Draft 2020-12 for Anthropic's Messages API,
 * Model Context Protocol's {@code Tool} input schema, OpenAI function
 * calling schema, etc.
 * <p>
 * Not all JSON Schema features are represented; this is the subset useful
 * for LLM tool parameters. Richer cases can extend the sealed hierarchy.
 */
public sealed interface ToolSchema {

    /**
     * String-valued parameter. Supports an optional enumeration of allowed
     * values, length bounds, and a regex pattern.
     *
     * @param description human-readable type description
     * @param enumValues if present, the allowed values (schema becomes an enum)
     * @param minLength minimum string length, or empty
     * @param maxLength maximum string length, or empty
     * @param pattern regex the value must match, or empty
     */
    record StringSchema(
            Optional<String> description,
            Optional<ImmutableList<String>> enumValues,
            OptionalInt minLength,
            OptionalInt maxLength,
            Optional<String> pattern) implements ToolSchema {}

    /**
     * Integer-valued parameter with optional range bounds.
     *
     * @param description human-readable type description
     * @param min minimum value, or empty
     * @param max maximum value, or empty
     */
    record IntegerSchema(
            Optional<String> description,
            OptionalInt min,
            OptionalInt max) implements ToolSchema {}

    /**
     * Floating-point-valued parameter with optional range bounds.
     *
     * @param description human-readable type description
     * @param min minimum value, or empty
     * @param max maximum value, or empty
     */
    record NumberSchema(
            Optional<String> description,
            Optional<Double> min,
            Optional<Double> max) implements ToolSchema {}

    /**
     * Boolean-valued parameter.
     *
     * @param description human-readable type description
     */
    record BooleanSchema(Optional<String> description) implements ToolSchema {}

    /**
     * Array-valued parameter with a single item type.
     *
     * @param itemSchema type description of each array element
     * @param minItems minimum array length, or empty
     * @param maxItems maximum array length, or empty
     * @param description human-readable type description
     */
    record ArraySchema(
            ToolSchema itemSchema,
            OptionalInt minItems,
            OptionalInt maxItems,
            Optional<String> description) implements ToolSchema {}

    /**
     * Object-valued parameter with nested typed properties.
     *
     * @param properties the object's properties
     * @param description human-readable type description
     */
    record ObjectSchema(
            ImmutableList<ToolParameter> properties,
            Optional<String> description) implements ToolSchema {}

    /**
     * Factory for a bare string parameter with no constraints.
     *
     * @return a string schema with all options empty
     */
    static StringSchema string() {
        return new StringSchema(Optional.empty(), Optional.empty(),
                OptionalInt.empty(), OptionalInt.empty(), Optional.empty());
    }

    /**
     * Factory for a string parameter constrained to a fixed set of values.
     *
     * @param values the allowed values
     * @return an enumerated string schema
     */
    static StringSchema stringEnum(String... values) {
        return new StringSchema(Optional.empty(), Optional.of(Lists.immutable.of(values)),
                OptionalInt.empty(), OptionalInt.empty(), Optional.empty());
    }

    /**
     * Factory for an unbounded integer parameter.
     *
     * @return an integer schema with no bounds
     */
    static IntegerSchema integer() {
        return new IntegerSchema(Optional.empty(), OptionalInt.empty(), OptionalInt.empty());
    }

    /**
     * Factory for an integer parameter with inclusive range bounds.
     *
     * @param min minimum value (inclusive)
     * @param max maximum value (inclusive)
     * @return a bounded integer schema
     */
    static IntegerSchema integerRange(int min, int max) {
        return new IntegerSchema(Optional.empty(), OptionalInt.of(min), OptionalInt.of(max));
    }

    /**
     * Factory for a boolean parameter.
     *
     * @return a boolean schema
     */
    static BooleanSchema bool() {
        return new BooleanSchema(Optional.empty());
    }

    /**
     * Factory for an unbounded array parameter with the given item type.
     *
     * @param itemSchema schema of each array element
     * @return an array schema with no length bounds
     */
    static ArraySchema arrayOf(ToolSchema itemSchema) {
        return new ArraySchema(itemSchema, OptionalInt.empty(), OptionalInt.empty(),
                Optional.empty());
    }
}
