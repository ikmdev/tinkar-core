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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    /** The GSRS type of a name that is a code, such as a manufacturer's research code. */
    public static final String CODE_NAME_TYPE = "cd";

    /**
     * One of a substance's names.
     *
     * @param uuid        the UUID GSRS gives the name, stable across releases
     * @param name        the name, as GSRS spells it
     * @param type        the GSRS name type: {@code of} official, {@code cn} common, {@code sys}
     *                    systematic, {@code bn} brand, {@code cd} code, ...; empty when the record
     *                    gives none
     * @param languages   the languages GSRS tags the name with, as it spells them ({@code en}, ...)
     * @param displayName true for the name GSRS shows the substance by
     */
    public record Name(UUID uuid, String name, String type, List<String> languages, boolean displayName) {

        /**
         * Validates and copies.
         */
        public Name {
            Objects.requireNonNull(uuid, "uuid");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            languages = List.copyOf(Objects.requireNonNull(languages, "languages"));
        }

        /**
         * Whether GSRS types the name as a code.
         *
         * @return true for a code name
         */
        public boolean code() {
            return CODE_NAME_TYPE.equals(type);
        }

        /**
         * Whether GSRS tags the name with a language other than English. The importer writes every
         * name in English; this tells the few it should not have.
         *
         * @return true when a language other than English is tagged
         */
        public boolean taggedOtherThanEnglish() {
            return languages.stream().anyMatch(language -> !language.equals("en") && !language.equalsIgnoreCase("English"));
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
     * @throws ImportException when the record has no UUID, definition type or class, or a name has
     *                         no text or no UUID
     */
    public static Substance of(JsonNode record) {
        String uuid = text(record, "uuid").orElseThrow(() -> new ImportException("A GSRS record has no uuid"));
        String definitionType = text(record, "definitionType").orElseThrow(() ->
                new ImportException("GSRS record " + uuid + " has no definitionType"));
        String substanceClass = text(record, "substanceClass").orElseThrow(() ->
                new ImportException("GSRS record " + uuid + " has no substanceClass"));
        List<Name> names = new ArrayList<>();
        for (JsonNode name : record.path("names")) {
            String text = text(name, "name").orElseThrow(() ->
                    new ImportException("GSRS record " + uuid + " has a name with no text"));
            String nameUuid = text(name, "uuid").orElseThrow(() ->
                    new ImportException("GSRS record " + uuid + " has a name with no uuid: " + text));
            List<String> languages = new ArrayList<>();
            for (JsonNode language : name.path("languages")) {
                if (language.isTextual() && !language.asText().isBlank()) {
                    languages.add(language.asText().strip());
                }
            }
            try {
                names.add(new Name(UUID.fromString(nameUuid), text, text(name, "type").orElse(""), languages,
                        name.path("displayName").asBoolean(false)));
            } catch (IllegalArgumentException e) {
                throw new ImportException("GSRS record " + uuid + " has a name whose uuid is not a UUID: " + nameUuid, e);
            }
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
     * The name the substance is shown by: the name GSRS shows it by, else its first name. Some
     * records name no display name, and a few have no names at all.
     *
     * @return the name, empty when the substance has none
     */
    public Optional<Name> shown() {
        return names.stream().filter(Name::displayName).findFirst().or(() -> names.stream().findFirst());
    }

    /**
     * The name the substance is known by: the name it is {@linkplain #shown() shown by}, else its
     * UNII, else its GSRS UUID.
     *
     * @return the name
     */
    public String name() {
        return shown().map(Name::name).or(() -> unii).orElse(uuid.toString());
    }

    /**
     * Whether the name the substance is known by is one of its names; false when it falls back to
     * the UNII or UUID.
     *
     * @return true when the substance has a name
     */
    public boolean named() {
        return !names.isEmpty();
    }

    /**
     * The names written beside the name the substance is shown by, in record order: every other
     * name, except {@linkplain Name#code() codes}, which are identifiers rather than names, and a
     * name spelled exactly as one before it (or as the shown name). Names differing only in case,
     * or in a bracketed source such as {@code [USAN]}, are kept, as GSRS keeps them.
     *
     * @return the other names
     */
    public List<Name> otherNames() {
        Optional<Name> shown = shown();
        Set<String> spelled = new HashSet<>();
        shown.ifPresent(name -> spelled.add(name.name()));
        List<Name> others = new ArrayList<>();
        for (Name name : names) {
            if (shown.isPresent() && name == shown.get() || name.code()) {
                continue;
            }
            if (spelled.add(name.name())) {
                others.add(name);
            }
        }
        return others;
    }

    /**
     * How many names, other than the one the substance is shown by, GSRS types as codes. They are
     * not written as names.
     *
     * @return the count
     */
    public long codeNames() {
        Optional<Name> shown = shown();
        return names.stream().filter(name -> !(shown.isPresent() && name == shown.get())).filter(Name::code).count();
    }

    private static Optional<String> text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? Optional.empty()
                : Optional.of(value.asText().strip()).filter(text -> !text.isEmpty());
    }
}
