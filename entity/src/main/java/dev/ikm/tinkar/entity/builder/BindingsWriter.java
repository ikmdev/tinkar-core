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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
 * A set generates one class per binding class it declares, holding the components bound in
 * it under the names the ledger gave them, and its default class, holding every component
 * bound in none. A default-class name derives from the birth FQN: the trailing semantic tag
 * is stripped, the rest upper-snake-cased. Two components with the same name in one class
 * fail generation with both cited — bind one under another name, or rename its FQN.
 */
public final class BindingsWriter {

    private BindingsWriter() {
    }

    /**
     * Writes the set's default bindings class: the components bound in no binding class.
     * The set's binding classes are written beside it; see {@link #writeAll}.
     *
     * @param knowledgeSet the composed set to generate bindings for
     * @param packageName  the generated classes' package
     * @param className    the default class's simple name
     * @param outputDir    the generated-sources root; package directories are created below it
     * @return the path of the default class's source file
     * @throws IOException           if a file cannot be written
     * @throws IllegalStateException if two components have the same name in one class
     */
    public static Path write(KnowledgeSet knowledgeSet, String packageName, String className,
                             Path outputDir) throws IOException {
        return writeAll(knowledgeSet, packageName, className, outputDir).getFirst();
    }

    /**
     * Writes every bindings class of the set: the default class first, then one per binding
     * class the set declares, in declaration order.
     *
     * @param knowledgeSet the composed set to generate bindings for
     * @param packageName  the generated classes' package
     * @param className    the default class's simple name
     * @param outputDir    the generated-sources root; package directories are created below it
     * @return the paths of the written source files, the default class's first
     * @throws IOException           if a file cannot be written
     * @throws IllegalStateException if two components have the same name in one class, or a
     *                               binding class has the default class's name
     */
    public static List<Path> writeAll(KnowledgeSet knowledgeSet, String packageName, String className,
                                      Path outputDir) throws IOException {
        Map<String, List<Binding>> classes = new LinkedHashMap<>();
        classes.put(className, new ArrayList<>());
        for (BindingClass bindingClass : knowledgeSet.bindingClasses()) {
            if (classes.putIfAbsent(bindingClass.name(), new ArrayList<>()) != null) {
                throw new IllegalStateException("The binding class " + bindingClass.name()
                        + " has the default bindings class's name");
            }
        }
        for (Declaration declaration : knowledgeSet.declarations()) {
            if (declaration.bindings().isEmpty()) {
                classes.get(className).add(new Binding(constantName(declaration.birthFqn()), declaration));
            }
            declaration.bindings().forEach((bindingClass, constant) ->
                    classes.get(bindingClass.name()).add(new Binding(constant, declaration)));
        }
        List<Path> files = new ArrayList<>();
        for (Map.Entry<String, List<Binding>> bindingsClass : classes.entrySet()) {
            String description = bindingsClass.getKey().equals(className)
                    ? "Generated bindings for the knowledge set"
                    : "Generated " + bindingsClass.getKey() + " bindings of the knowledge set";
            files.add(writeClass(knowledgeSet, packageName, bindingsClass.getKey(), description,
                    bindingsClass.getValue(), outputDir));
        }
        return files;
    }

    /** A component's constant in one generated class. */
    private record Binding(String constant, Declaration declaration) {
    }

    private static Path writeClass(KnowledgeSet knowledgeSet, String packageName, String className,
                                   String description, List<Binding> bindings, Path outputDir) throws IOException {
        StringBuilder src = new StringBuilder();
        src.append("package ").append(packageName).append(";\n\n");
        src.append("import dev.ikm.tinkar.common.id.PublicIds;\n");
        src.append("import dev.ikm.tinkar.terms.EntityProxy;\n\n");
        src.append("import java.util.UUID;\n\n");
        src.append("/**\n");
        src.append(" * ").append(description).append(" {@code ").append(knowledgeSet.uuid())
                .append("} — DO NOT EDIT.\n");
        src.append(" * Every identity is {@code T5(setUuid, fullyQualifiedNameAtBirth)}; regenerate from the ledger.\n");
        src.append(" */\n");
        src.append("public final class ").append(className).append(" {\n\n");
        src.append("    private ").append(className).append("() {\n    }\n");

        Map<String, String> constantToFqn = new HashMap<>();
        for (Binding binding : bindings) {
            Declaration declaration = binding.declaration();
            String prior = constantToFqn.putIfAbsent(binding.constant(), declaration.birthFqn());
            if (prior != null) {
                throw new IllegalStateException("Constant name collision in " + className + ": \"" + prior
                        + "\" and \"" + declaration.birthFqn() + "\" are both " + binding.constant());
            }
            UUID uuid = declaration.publicId().asUuidArray()[0];
            String proxyType = switch (declaration.kind()) {
                case CONCEPT -> "EntityProxy.Concept";
                case PATTERN -> "EntityProxy.Pattern";
            };
            src.append("\n    /**\n");
            src.append("     * ").append(javadocText(declaration)).append("\n");
            src.append("     */\n");
            src.append("    public static final ").append(proxyType).append(' ').append(binding.constant()).append(" =\n");
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
