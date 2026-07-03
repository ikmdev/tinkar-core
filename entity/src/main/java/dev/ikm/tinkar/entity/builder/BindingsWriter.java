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
package dev.ikm.tinkar.entity.builder;

import dev.ikm.tinkar.entity.builder.KnowledgeSet.Declaration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Writes the generated bindings class for a composed {@link KnowledgeSet}: the
 * {@code TinkarTerm} idiom — one {@code EntityProxy} constant per declaration, its
 * identity embedded as a resolved UUID literal, its javadoc generated from the
 * declaration's definition text. The generated class depends only on
 * {@code dev.ikm.tinkar.terms}, so consumers of a bindings artifact do not depend on the
 * ledger or this builder API.
 * <p>
 * Constant names derive from birth FQNs: the trailing semantic tag is stripped, the rest
 * upper-snake-cased. Two declarations whose FQNs reduce to the same constant name fail
 * generation with both names cited — rename one, or accept the tag into the name by
 * changing the FQN.
 */
public final class BindingsWriter {

    private BindingsWriter() {
    }

    /**
     * Writes the bindings source file for the given knowledge set.
     *
     * @param knowledgeSet the composed set to generate bindings for
     * @param packageName  the generated class's package
     * @param className    the generated class's simple name
     * @param outputDir    the generated-sources root; package directories are created below it
     * @return the path of the written source file
     * @throws IOException           if the file cannot be written
     * @throws IllegalStateException if two declarations reduce to the same constant name
     */
    public static Path write(KnowledgeSet knowledgeSet, String packageName, String className,
                             Path outputDir) throws IOException {
        StringBuilder src = new StringBuilder();
        src.append("package ").append(packageName).append(";\n\n");
        src.append("import dev.ikm.tinkar.common.id.PublicIds;\n");
        src.append("import dev.ikm.tinkar.terms.EntityProxy;\n\n");
        src.append("import java.util.UUID;\n\n");
        src.append("/**\n");
        src.append(" * Generated bindings for the knowledge set {@code ").append(knowledgeSet.uuid())
                .append("} — DO NOT EDIT.\n");
        src.append(" * Every identity is {@code T5(setUuid, fullyQualifiedNameAtBirth)}; regenerate from the ledger.\n");
        src.append(" */\n");
        src.append("public final class ").append(className).append(" {\n\n");
        src.append("    private ").append(className).append("() {\n    }\n");

        Map<String, String> constantToFqn = new HashMap<>();
        for (Declaration declaration : knowledgeSet.declarations()) {
            String constant = constantName(declaration.birthFqn());
            String prior = constantToFqn.putIfAbsent(constant, declaration.birthFqn());
            if (prior != null) {
                throw new IllegalStateException("Constant name collision: \"" + prior + "\" and \""
                        + declaration.birthFqn() + "\" both reduce to " + constant);
            }
            UUID uuid = declaration.publicId().asUuidArray()[0];
            String proxyType = switch (declaration.kind()) {
                case CONCEPT -> "EntityProxy.Concept";
                case PATTERN -> "EntityProxy.Pattern";
            };
            src.append("\n    /**\n");
            src.append("     * ").append(javadocText(declaration)).append("\n");
            src.append("     */\n");
            src.append("    public static final ").append(proxyType).append(' ').append(constant).append(" =\n");
            src.append("            ").append(proxyType).append(".make(\"")
                    .append(escapeJava(declaration.birthFqn())).append("\",\n");
            src.append("                    PublicIds.of(UUID.fromString(\"").append(uuid).append("\")));\n");
        }
        src.append("}\n");

        Path packageDir = outputDir.resolve(packageName.replace('.', '/'));
        Files.createDirectories(packageDir);
        Path file = packageDir.resolve(className + ".java");
        Files.writeString(file, src.toString());
        return file;
    }

    /**
     * Derives the Java constant name for a birth FQN: the trailing parenthesized
     * semantic tag is stripped, remaining non-alphanumerics become underscores, and the
     * result is upper-cased. A name that would start with a digit is prefixed.
     *
     * @param birthFqn the fully qualified name at birth
     * @return the derived constant name
     */
    public static String constantName(String birthFqn) {
        String base = birthFqn.replaceAll("\\s*\\([^)]*\\)\\s*$", "").trim();
        String name = base.replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .toUpperCase();
        if (name.isEmpty()) {
            throw new IllegalStateException("Birth FQN reduces to an empty constant name: \"" + birthFqn + "\"");
        }
        if (Character.isDigit(name.charAt(0))) {
            name = "N_" + name;
        }
        return name;
    }

    private static String javadocText(Declaration declaration) {
        String text = declaration.definition().orElse(declaration.birthFqn());
        return text.replace("*/", "*&#47;");
    }

    private static String escapeJava(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
