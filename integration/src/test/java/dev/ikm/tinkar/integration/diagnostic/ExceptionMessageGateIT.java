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
package dev.ikm.tinkar.integration.diagnostic;

import dev.ikm.tinkar.common.service.DiagnosticText;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A gate on tinkar-core's own sources: no exception is given a message built from a nid
 * ({@code IKE-Network/ike-issues#1189}).
 *
 * <p>A nid means nothing outside the store that assigned it, and the message of an exception
 * leaves the store: a service copies it into an error response, and a person reads it beside
 * another store. A message identifies a component through {@link DiagnosticText}, which writes
 * the description and the UUID, and the nid only when the store has no public id for it. The
 * compiler has no objection to a nid in a message, and on a store whose components all have
 * descriptions the store's default text never shows one, so no test of behavior catches one
 * being added. This test reads the sources instead.
 *
 * <p>It reads the arguments of every {@code new …Exception(…)} and {@code new …Error(…)} for a
 * nid joined into the text and for the calls that write one. It reads text, not types, so it
 * does not see an entity, a version, a vertex, or a tree joined into a message with its own
 * {@code toString()}; {@code EntityText} and {@code DiTreeText} are the forms for those.
 *
 * <p>Three messages are about the nid itself and are allowed ({@link #KNOWN}). The gate fails
 * when their number changes in either direction, so the list stays true.
 */
class ExceptionMessageGateIT {

    /** The tinkar-core directory, from the module directory the tests run in. */
    private static final Path TINKAR_CORE = Path.of("..");

    /** The directory of main sources inside a module, as it reads in a path. */
    private static final String MAIN_SOURCES = "/src/main/java/";

    /** A way a nid gets into text, and what to use in its place. */
    private record Forbidden(String what, Pattern form, String instead) {
    }

    /** Calls that write a nid, wherever they are in the arguments. */
    private static final List<Forbidden> CALLS = List.of(
            new Forbidden("the store's default text, which is <nid> when there is no description",
                    Pattern.compile("PrimitiveData\\s*\\.\\s*text(?:Fast|WithNid|List)?\\s*\\("),
                    "DiagnosticText.component"),
            new Forbidden("a calculator method that answers with the nid when no description resolves",
                    Pattern.compile("\\w*OrNid\\s*\\("),
                    "DiagnosticText.component"),
            new Forbidden("an array of nids",
                    Pattern.compile("Arrays\\s*\\.\\s*toString\\s*\\(\\s*\\w*[nN]ids\\s*\\)"),
                    "DiagnosticText.name for each"),
            new Forbidden("an entity's own text, which holds its nid",
                    Pattern.compile("\\.\\s*(?:entityToString|toXmlFragment)\\s*\\("),
                    "EntityText.diagnostic or DiagnosticText.component"));

    /** A nid joined into the text directly: one of the operands of the concatenation. */
    private static final List<Forbidden> OPERANDS = List.of(
            new Forbidden("a nid variable joined into the message",
                    Pattern.compile("(?<![.\\w])\\w*[nN]id\\b(?!\\s*\\()"),
                    "DiagnosticText.component"),
            new Forbidden("a nid accessor joined into the message",
                    Pattern.compile("\\.\\s*\\w*[nN]id\\s*\\(\\)"),
                    "DiagnosticText.component"));

    /**
     * Messages the gate allows, by source file: how many, and why. Each is about the nid
     * itself, and says nothing about a component: a value that can never be a nid, and a nid
     * the store minted no public id for, written as a nid of this store.
     */
    private static final Map<String, Integer> KNOWN = Map.of(
            "common/src/main/java/dev/ikm/tinkar/common/id/impl/NidCodec6.java", 1,
            "common/src/main/java/dev/ikm/tinkar/common/id/impl/NidCodec8.java", 1,
            "provider/data-ephemeral-provider/src/main/java/dev/ikm/tinkar/provider/ephemeral/ProviderEphemeral.java", 1);

    /** The start of an exception being made; its arguments follow. */
    private static final Pattern NEW_EXCEPTION = Pattern.compile("new\\s+\\w*(?:Exception|Error)\\s*\\(");

    /** Block comments and javadoc, which may name the forbidden forms in prose. */
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    /** A line comment, from {@code //} to the end of the line, unless the slashes follow a colon (a URL). */
    private static final Pattern LINE_COMMENT = Pattern.compile("(?<!:)//[^\\n]*");

    @Test
    void noExceptionMessageIsBuiltFromANid() throws IOException {
        List<String> violations = new ArrayList<>();
        Map<String, Integer> found = new TreeMap<>();
        int scanned = 0;
        for (Path file : mainSources()) {
            scanned++;
            String name = TINKAR_CORE.relativize(file).toString().replace('\\', '/');
            String code = withoutComments(Files.readString(file, StandardCharsets.UTF_8));
            for (String finding : findings(code)) {
                found.merge(name, 1, Integer::sum);
                if (!KNOWN.containsKey(name)) {
                    violations.add(name + ":" + finding);
                }
            }
        }
        assertTrue(scanned > 500, "sources the gate read: " + scanned + "; too few means it is not reading tinkar-core");
        assertEquals(List.of(), violations, "Exception messages built from a nid");
        for (Map.Entry<String, Integer> known : KNOWN.entrySet()) {
            assertEquals(known.getValue(), found.getOrDefault(known.getKey(), 0),
                    "known messages in " + known.getKey() + "; update KNOWN when one is added or removed");
        }
    }

    @Test
    void theGateRecognizesEachForm() {
        // The gate is only as good as its patterns: each must match the form it stands for, and
        // none may match the replacement, a string that mentions a nid, or a comment.
        assertEquals(1, hits("throw new IllegalStateException(\"No path for: \" + stampPathNid);"));
        assertEquals(1, hits("throw new IllegalStateException(\"Concept: \" + conceptNid + \" definition: \" + text);"));
        assertEquals(1, hits("throw new RuntimeException(\"for stamp entity: \" + stampEntity.nid());"));
        assertEquals(1, hits("throw new IllegalStateException(\"No stated form: \" + PrimitiveData.text(conceptNid));"));
        assertEquals(1, hits("throw new IllegalStateException(\"Too many: \" + PrimitiveData.textWithNid(conceptNid));"));
        assertEquals(1, hits("throw new IllegalStateException(\"Index for \" + calc.getDescriptionTextOrNid(x) + \" is 1\");"));
        assertEquals(1, hits("throw new UnsupportedOperationException(\"Wrong nid count: \" + Arrays.toString(nids));"));
        assertEquals(1, hits("throw new IllegalStateException(\"No versions for: \" + chronicle.entityToString());"));
        assertEquals(1, hits("handle.orElseThrow(() -> new IllegalStateException(\"absent: \" + nid));"));
        assertEquals(2, hits("throw new RuntimeException(conceptNid + \" \" + PrimitiveData.text(conceptNid));"));

        assertEquals(0, hits("throw new IllegalStateException(\"No path for: \" + DiagnosticText.component(stampPathNid));"));
        assertEquals(0, hits("throw new IllegalStateException(\"Unexpected value: \" + EntityText.diagnostic(entity));"));
        assertEquals(0, hits("throw new IllegalStateException(\"Entity nid cannot = 0: \" + entity);"));
        assertEquals(0, hits("throw new IndexOutOfBoundsException(\"Index: \" + index + \", Size: 0\");"));
        assertEquals(0, hits("throw new IllegalStateException(message(conceptNid));"));
        assertEquals(0, hits("LOG.error(\"No definition for nid \" + conceptNid);"));
        assertEquals(0, hits("// throw new IllegalStateException(\"old: \" + conceptNid);"));
        assertEquals(0, hits("/* new IllegalStateException(\"old: \" + PrimitiveData.text(nid)) */ int x;"));
    }

    /** Every main source file of every module of tinkar-core. */
    private static List<Path> mainSources() throws IOException {
        try (Stream<Path> files = Files.walk(TINKAR_CORE)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().replace('\\', '/').contains(MAIN_SOURCES))
                    .filter(path -> !path.toString().replace('\\', '/').contains("/target/"))
                    .sorted()
                    .toList();
        }
    }

    /** How many findings the gate makes in a piece of source. */
    private static int hits(String source) {
        return findings(withoutComments(source)).size();
    }

    /** The findings in a source whose comments are blanked, each as {@code line  text  — what; use what}. */
    private static List<String> findings(String code) {
        List<String> findings = new ArrayList<>();
        Matcher exception = NEW_EXCEPTION.matcher(code);
        while (exception.find()) {
            String arguments = arguments(code, exception.end());
            int line = lineOf(code, exception.start());
            for (Forbidden forbidden : CALLS) {
                Matcher matcher = forbidden.form().matcher(arguments);
                while (matcher.find()) {
                    findings.add(finding(line, matcher, forbidden));
                }
            }
            String operands = withoutNestedArguments(arguments);
            if (operands.indexOf('+') < 0) {
                continue;
            }
            for (Forbidden forbidden : OPERANDS) {
                Matcher matcher = forbidden.form().matcher(operands);
                while (matcher.find()) {
                    findings.add(finding(line, matcher, forbidden));
                }
            }
        }
        return findings;
    }

    private static String finding(int line, Matcher matcher, Forbidden forbidden) {
        return line + "  " + matcher.group().strip() + "  — " + forbidden.what() + "; use " + forbidden.instead();
    }

    /**
     * The arguments of a call, from just after its opening parenthesis to the one that closes
     * it. A string or character literal is emptied, so that what it says is not read as code.
     */
    private static String arguments(String code, int start) {
        StringBuilder arguments = new StringBuilder();
        int depth = 1;
        int i = start;
        while (i < code.length()) {
            char c = code.charAt(i);
            if (c == '"' || c == '\'') {
                int end = i + 1;
                while (end < code.length() && code.charAt(end) != c) {
                    end += code.charAt(end) == '\\' ? 2 : 1;
                }
                arguments.append(c).append(c);
                i = end + 1;
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                break;
            }
            arguments.append(c);
            i++;
        }
        return arguments.toString();
    }

    /**
     * The arguments with the arguments of every call inside them replaced by {@code …}, which
     * leaves the operands that are joined into the message directly:
     * {@code "" + f(…) + entity.nid() + conceptNid}. A call that takes no argument keeps its
     * empty parentheses, so an accessor can be told from a call that is given a nid.
     */
    private static String withoutNestedArguments(String arguments) {
        StringBuilder operands = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < arguments.length(); i++) {
            char c = arguments.charAt(i);
            if (c == '(') {
                if (depth++ == 0) {
                    operands.append(c);
                }
            } else if (c == ')') {
                if (--depth == 0) {
                    operands.append(c);
                }
            } else if (depth == 0) {
                operands.append(c);
            } else if (depth == 1 && !Character.isWhitespace(c) && operands.charAt(operands.length() - 1) == '(') {
                operands.append('…');
            }
        }
        return operands.toString();
    }

    /**
     * The source with its comments blanked. Line breaks are kept, so a finding still reports
     * the line it is on.
     */
    private static String withoutComments(String source) {
        String withoutBlocks = BLOCK_COMMENT.matcher(source)
                .replaceAll(match -> match.group().replaceAll("[^\\n]", " "));
        return LINE_COMMENT.matcher(withoutBlocks).replaceAll("");
    }

    /** The one-based line of an offset. */
    private static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
