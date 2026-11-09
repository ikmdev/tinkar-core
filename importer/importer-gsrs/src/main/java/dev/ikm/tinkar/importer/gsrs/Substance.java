/*
 * Copyright © 2026 IKE Network (support@ike.network)
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
package dev.ikm.tinkar.importer.gsrs;

import com.fasterxml.jackson.databind.JsonNode;
import dev.ikm.tinkar.importer.ImportException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What the importer reads of a GSRS substance record: its UUID, whether it is a primary
 * definition or an alternative definition of another substance, its class, its UNII when it has
 * one, and its names. The rest of the record (codes, structure, relationships, ...) is not read.
 *
 * @param uuid           the UUID GSRS gives the substance
 * @param primary        true for a primary definition; false for an alternative definition, which
 *                       defines another substance a second way
 * @param substanceClass the class as GSRS names it ({@code chemical}, {@code nucleicAcid}, ...)
 * @param unii           the UNII ({@code approvalID}), which only approved substances have
 * @param names          the names, in record order
 */
public record Substance(UUID uuid, boolean primary, String substanceClass, Optional<String> unii, List<Name> names) {

    /**
     * One of a substance's names.
     *
     * @param name        the name, as GSRS spells it
     * @param displayName true for the name GSRS shows the substance by
     */
    public record Name(String name, boolean displayName) {

        /**
         * Validates.
         */
        public Name {
            Objects.requireNonNull(name, "name");
        }
    }

    /**
     * Validates and copies.
     */
    public Substance {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(substanceClass, "substanceClass");
        Objects.requireNonNull(unii, "unii");
        names = List.copyOf(Objects.requireNonNull(names, "names"));
    }

    /**
     * Reads a record.
     *
     * @param record the record's JSON, as the dump holds it
     * @return the substance
     * @throws ImportException when the record has no UUID, definition type or class, or a name has no text
     */
    public static Substance of(JsonNode record) {
        String uuid = text(record, "uuid").orElseThrow(() -> new ImportException("A GSRS record has no uuid"));
        String definitionType = text(record, "definitionType").orElseThrow(() ->
                new ImportException("GSRS record " + uuid + " has no definitionType"));
        String substanceClass = text(record, "substanceClass").orElseThrow(() ->
                new ImportException("GSRS record " + uuid + " has no substanceClass"));
        List<Name> names = new ArrayList<>();
        for (JsonNode name : record.path("names")) {
            names.add(new Name(text(name, "name").orElseThrow(() ->
                    new ImportException("GSRS record " + uuid + " has a name with no text")),
                    name.path("displayName").asBoolean(false)));
        }
        try {
            return new Substance(UUID.fromString(uuid), switch (definitionType) {
                case "PRIMARY" -> true;
                case "ALTERNATIVE" -> false;
                default -> throw new ImportException("GSRS record " + uuid + " has definitionType " + definitionType
                        + ", neither PRIMARY nor ALTERNATIVE");
            }, substanceClass, text(record, "approvalID"), names);
        } catch (IllegalArgumentException e) {
            throw new ImportException("GSRS record has a uuid that is not a UUID: " + uuid, e);
        }
    }

    /**
     * The name the substance is known by: the name GSRS shows it by, else its first name, else
     * its UNII, else its GSRS UUID. Some records name no display name, and a few none at all.
     *
     * @return the name
     */
    public String name() {
        return names.stream().filter(Name::displayName).findFirst()
                .or(() -> names.stream().findFirst())
                .map(Name::name)
                .or(() -> unii)
                .orElse(uuid.toString());
    }

    /**
     * Whether the name the substance is known by is one of its names, the name GSRS shows it by
     * or the first; false when it falls back to the UNII or UUID.
     *
     * @return true when the substance has a name
     */
    public boolean named() {
        return !names.isEmpty();
    }

    private static Optional<String> text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? Optional.empty()
                : Optional.of(value.asText().strip()).filter(text -> !text.isEmpty());
    }
}
