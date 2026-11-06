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

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading substance records, and the identities minted for them. The records are made up, in
 * the shape the GSRS public dump holds them.
 */
class SubstanceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String UUID_TEXT = "0b9a3e55-6f0c-4c1d-9a3e-2d6c3f1e8a10";

    @Test
    void readsAnApprovedSubstance() throws Exception {
        Substance substance = Substance.of(record("""
                {"uuid":"%s","definitionType":"PRIMARY","substanceClass":"chemical","status":"approved",
                 "approvalID":"ABC123XYZ9","names":[
                  {"name":"EXAMPLE ACID, SODIUM SALT","displayName":false},
                  {"name":"EXAMPLE SODIUM","displayName":true}]}
                """.formatted(UUID_TEXT)));
        assertEquals(UUID.fromString(UUID_TEXT), substance.uuid());
        assertTrue(substance.primary());
        assertEquals("chemical", substance.substanceClass());
        assertEquals(Optional.of("ABC123XYZ9"), substance.unii());
        assertEquals(2, substance.names().size());
        assertEquals("EXAMPLE SODIUM", substance.name(), "the display name");
        assertTrue(substance.named());
    }

    @Test
    void fallsBackToTheFirstNameThenTheUniiThenTheUuid() throws Exception {
        assertEquals("FIRST", Substance.of(record("""
                {"uuid":"%s","definitionType":"PRIMARY","substanceClass":"protein",
                 "names":[{"name":"FIRST","displayName":false},{"name":"SECOND","displayName":false}]}
                """.formatted(UUID_TEXT))).name());

        Substance byUnii = Substance.of(record("""
                {"uuid":"%s","definitionType":"PRIMARY","substanceClass":"chemical","approvalID":"ABC123XYZ9","names":[]}
                """.formatted(UUID_TEXT)));
        assertEquals("ABC123XYZ9", byUnii.name());
        assertFalse(byUnii.named());

        Substance byUuid = Substance.of(record("""
                {"uuid":"%s","definitionType":"PRIMARY","substanceClass":"concept","names":[]}
                """.formatted(UUID_TEXT)));
        assertEquals(UUID_TEXT, byUuid.name());
        assertEquals(Optional.empty(), byUuid.unii());
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
                {"uuid":"%s","definitionType":"PRIMARY","substanceClass":"chemical","names":[{"displayName":true}]}
                """.formatted(UUID_TEXT))));
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

    private static JsonNode record(String json) throws Exception {
        return MAPPER.readTree(json);
    }
}
