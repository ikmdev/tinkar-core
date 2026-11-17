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
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.importer.ImportException;
import dev.ikm.tinkar.importer.gsrs.GsrsTerms.Shared;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading substance records, choosing the names written, and the identities minted for them. The
 * records are made up, in the shape the GSRS public dump holds them.
 */
class SubstanceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String UUID_TEXT = "0b9a3e55-6f0c-4c1d-9a3e-2d6c3f1e8a10";
    /** Per test: JUnit makes a new instance for each. */
    private int nameCount;

    @Test
    void readsAnApprovedSubstance() throws Exception {
        Substance substance = Substance.of(record("""
                {"uuid":"%s","definitionType":"PRIMARY","substanceClass":"chemical","status":"approved",
                 "approvalID":"ABC123XYZ9","names":[%s,%s]}
                """.formatted(UUID_TEXT, name("EXAMPLE ACID, SODIUM SALT", "cn", false),
                name("EXAMPLE SODIUM", "of", true))));
        assertEquals(UUID.fromString(UUID_TEXT), substance.uuid());
        assertTrue(substance.primary());
        assertEquals("chemical", substance.substanceClass());
        assertEquals(Optional.of("ABC123XYZ9"), substance.unii());
        assertEquals(2, substance.names().size());
        assertEquals("EXAMPLE SODIUM", substance.name(), "the display name");
        assertTrue(substance.named());
        Substance.Name shown = substance.shown().orElseThrow();
        assertEquals("of", shown.type());
        assertEquals(List.of("en"), shown.languages());
        assertEquals(UUID.fromString("00000000-0000-4000-8000-000000000002"), shown.uuid());
    }

    @Test
    void fallsBackToTheFirstNameThenTheUniiThenTheUuid() throws Exception {
        Substance byFirst = Substance.of(record("""
                {"uuid":"%s","definitionType":"PRIMARY","substanceClass":"protein","names":[%s,%s]}
                """.formatted(UUID_TEXT, name("FIRST", "cn", false), name("SECOND", "cn", false))));
        assertEquals("FIRST", byFirst.name());
        assertEquals(List.of("SECOND"), texts(byFirst.otherNames()), "the first is shown, so not repeated");

        Substance byUnii = Substance.of(record("""
                {"uuid":"%s","definitionType":"PRIMARY","substanceClass":"chemical","approvalID":"ABC123XYZ9","names":[]}
                """.formatted(UUID_TEXT)));
        assertEquals("ABC123XYZ9", byUnii.name());
        assertFalse(byUnii.named());
        assertEquals(Optional.empty(), byUnii.shown());
        assertEquals(List.of(), byUnii.otherNames());

        Substance byUuid = Substance.of(record("""
                {"uuid":"%s","definitionType":"PRIMARY","substanceClass":"concept","names":[]}
                """.formatted(UUID_TEXT)));
        assertEquals(UUID_TEXT, byUuid.name());
        assertEquals(Optional.empty(), byUuid.unii());
    }

    @Test
    void writesEveryOtherNameButCodesAndExactDuplicates() throws Exception {
        Substance substance = Substance.of(record("""
                {"uuid":"%s","definitionType":"PRIMARY","substanceClass":"chemical","names":[%s,%s,%s,%s,%s,%s,%s,%s]}
                """.formatted(UUID_TEXT,
                name("FYTATE SODIUM", "cn", false),
                name("PHYTATE SODIUM", "of", true),
                name("PHYTATE SODIUM [USAN]", "cn", false),
                name("SODIUM PHYTATE", "of", false),
                name("RENCAL", "bn", false),
                name("SQ 9343", "cd", false),
                name("PHYTATE SODIUM", "cn", false),
                name("Sodium phytate", "cn", false))));
        assertEquals("PHYTATE SODIUM", substance.name());
        assertEquals(List.of("FYTATE SODIUM", "PHYTATE SODIUM [USAN]", "SODIUM PHYTATE", "RENCAL", "Sodium phytate"),
                texts(substance.otherNames()),
                "in record order; the code and the exact repeat of the shown name left out; case and"
                        + " bracketed sources kept");
        assertEquals(1, substance.codeNames());
    }

    @Test
    void showsACodeNameWhenGsrsShowsItByOne() throws Exception {
        Substance substance = Substance.of(record("""
                {"uuid":"%s","definitionType":"PRIMARY","substanceClass":"chemical","names":[%s,%s]}
                """.formatted(UUID_TEXT, name("AB-1234", "cd", true), name("AB 1234", "cd", false))));
        assertEquals("AB-1234", substance.name(), "the display name, though a code");
        assertEquals(List.of(), substance.otherNames());
        assertEquals(1, substance.codeNames(), "only the code not shown");
    }

    @Test
    void tellsNamesTaggedOtherThanEnglish() throws Exception {
        Substance substance = Substance.of(record("""
                {"uuid":"%s","definitionType":"PRIMARY","substanceClass":"chemical","names":[
                 {"uuid":"00000000-0000-4000-8000-0000000000a1","name":"ACIDE","type":"cn","languages":["fr"]},
                 {"uuid":"00000000-0000-4000-8000-0000000000a2","name":"ACID","type":"cn","languages":["English"]},
                 {"uuid":"00000000-0000-4000-8000-0000000000a3","name":"ACIDUM"}]}
                """.formatted(UUID_TEXT)));
        assertEquals(List.of(true, false, false),
                substance.names().stream().map(Substance.Name::taggedOtherThanEnglish).toList());
        assertEquals("", substance.names().get(2).type(), "no type given");
        assertEquals(List.of(), substance.names().get(2).languages(), "no language given");
    }

    @Test
    void readsAnAlternativeDefinition() throws Exception {
        Substance alternative = Substance.of(record("""
                {"uuid":"%s","definitionType":"ALTERNATIVE","substanceClass":"protein","status":"alternative","names":[]}
                """.formatted(UUID_TEXT)));
        assertFalse(alternative.primary());
    }

    @Test
    void refusesAnIncompleteRecord() throws Exception {
        assertThrows(ImportException.class, () -> Substance.of(record("""
                {"definitionType":"PRIMARY","substanceClass":"chemical","names":[]}""")));
        assertThrows(ImportException.class, () -> Substance.of(record("""
                {"uuid":"not a uuid","definitionType":"PRIMARY","substanceClass":"chemical","names":[]}""")));
        assertThrows(ImportException.class, () -> Substance.of(record("""
                {"uuid":"%s","definitionType":"SECONDARY","substanceClass":"chemical","names":[]}
                """.formatted(UUID_TEXT))));
        assertThrows(ImportException.class, () -> Substance.of(record("""
                {"uuid":"%s","definitionType":"PRIMARY","names":[]}""".formatted(UUID_TEXT))));
        assertThrows(ImportException.class, () -> Substance.of(record("""
                {"uuid":"%s","definitionType":"PRIMARY","substanceClass":"chemical",
                 "names":[{"uuid":"00000000-0000-4000-8000-0000000000b1","displayName":true}]}
                """.formatted(UUID_TEXT))), "a name with no text");
        assertThrows(ImportException.class, () -> Substance.of(record("""
                {"uuid":"%s","definitionType":"PRIMARY","substanceClass":"chemical","names":[{"name":"NO UUID"}]}
                """.formatted(UUID_TEXT))), "a name with no uuid");
        assertThrows(ImportException.class, () -> Substance.of(record("""
                {"uuid":"%s","definitionType":"PRIMARY","substanceClass":"chemical",
                 "names":[{"uuid":"not a uuid","name":"BAD UUID"}]}
                """.formatted(UUID_TEXT))), "a name whose uuid is not a UUID");
    }

    @Test
    void knowsEveryClassTheAugust2026ReleaseHolds() {
        for (String substanceClass : new String[]{"chemical", "structurallyDiverse", "protein", "concept", "mixture",
                "polymer", "nucleicAcid", "specifiedSubstanceG1"}) {
            assertTrue(Shared.ofSubstanceClass(substanceClass).isPresent(), substanceClass);
        }
        assertEquals(Optional.empty(), Shared.ofSubstanceClass("specifiedSubstanceG2"));
    }

    @Test
    void mintsIdentitiesInTheGsrsNamespace() {
        UUID urlNamespace = UUID.fromString("6ba7b811-9dad-11d1-80b4-00c04fd430c8");
        assertEquals(UUID.fromString(GsrsTerms.DEFAULT_NAMESPACE),
                UuidT5Generator.get(urlNamespace, "https://gsrs.ncats.nih.gov/"));
        UUID namespace = UUID.fromString(GsrsTerms.DEFAULT_NAMESPACE);
        UUID gsrsUuid = UUID.fromString(UUID_TEXT);
        assertEquals(GsrsTerms.substanceUuid(namespace, gsrsUuid), GsrsTerms.substanceUuid(namespace, gsrsUuid));
        assertFalse(GsrsTerms.substanceUuid(namespace, gsrsUuid).equals(gsrsUuid),
                "minted in the namespace, not GSRS's own UUID");
    }

    /** A name in the dump's shape, English, with the next made-up UUID: ...0001, ...0002, .... */
    private String name(String text, String type, boolean displayName) {
        return """
                {"uuid":"00000000-0000-4000-8000-%012d","name":"%s","type":"%s","languages":["en"],"displayName":%s}"""
                .formatted(++nameCount, text, type, displayName);
    }

    private static List<String> texts(List<Substance.Name> names) {
        return names.stream().map(Substance.Name::name).toList();
    }

    private static JsonNode record(String json) throws Exception {
        return MAPPER.readTree(json);
    }
}
