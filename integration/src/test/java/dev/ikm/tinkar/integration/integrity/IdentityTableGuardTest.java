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
package dev.ikm.tinkar.integration.integrity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An identity table is one exhaustive switch over its vocabulary's enum with no {@code default}
 * branch (arch-vertex-identity-table, "Holding the Table Complete";
 * IKE-Network/ike-issues#1310). A switch expression over an enum fails to compile when a
 * constant has no entry, so a meaning added without its identity rule cannot build; a
 * {@code default} would silence that, and this check refuses one.
 *
 * <p>The check reads the source: a {@code default} label is gone from the bytecode of an
 * exhaustive enum switch, and present for any other.
 */
class IdentityTableGuardTest {

    /** The identity tables, by their path under the repository. */
    static final List<String> TABLES = List.of(
            "entity/src/main/java/dev/ikm/tinkar/entity/graph/adaptor/axiom/IdentityRule.java");

    /** A {@code default} label in a switch statement or expression. */
    static final Pattern DEFAULT_LABEL = Pattern.compile("\\bdefault\\s*(->|:)");

    @Test
    void noIdentityTableHasADefaultBranch() throws IOException {
        Path repository = PublicIdHashKeyGuardTest.repositoryRoot();
        List<String> found = new ArrayList<>();
        for (String table : TABLES) {
            Path file = repository.resolve(table);
            assertTrue(Files.exists(file), "the identity table is where this check looks: " + file);
            List<String> lines = Files.readAllLines(file);
            assertTrue(lines.stream().anyMatch(line -> line.contains("switch (")), "the table is a switch: " + table);
            for (int i = 0; i < lines.size(); i++) {
                String code = withoutComment(lines.get(i));
                if (DEFAULT_LABEL.matcher(code).find()) {
                    found.add(table + ":" + (i + 1) + ": " + lines.get(i).strip());
                }
            }
        }
        assertEquals(List.of(), found, "an identity table must stay an exhaustive switch with no default branch");
    }

    /** The check sees a default label in either switch form, and not in prose. */
    @Test
    void theCheckSeesADefaultLabel() {
        assertTrue(DEFAULT_LABEL.matcher(withoutComment("            default -> rule(Successors.NONE);")).find());
        assertTrue(DEFAULT_LABEL.matcher(withoutComment("        default:")).find());
        assertFalse(DEFAULT_LABEL.matcher(withoutComment(" * No default branch: one would silence the check")).find());
        assertFalse(DEFAULT_LABEL.matcher(withoutComment("    // a default -> here would be refused")).find());
    }

    /** The line without a trailing line comment, and nothing of a javadoc or block-comment line. */
    private static String withoutComment(String line) {
        String trimmed = line.strip();
        if (trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")) {
            return "";
        }
        int comment = line.indexOf("//");
        return comment < 0 ? line : line.substring(0, comment);
    }
}
