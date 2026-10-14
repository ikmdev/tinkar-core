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
 * Writes the generated bindings of a composed {@link KnowledgeSet} as enums, one kind of
 * component per enum: {@code <base>Concept} for its concepts, {@code <base>Pattern} for its
 * patterns and {@code <base>Stamp} for its declared stamps. Each constant carries its birth FQN
 * and resolved UUIDs as literals and the declaration's definition as javadoc. A concept or
 * pattern constant passes wherever a {@code ConceptFacade} or {@code PatternFacade} is expected;
 * it holds its identity in a private proxy, so its nid is resolved once. The generated enums
 * depend only on {@code dev.ikm.tinkar.terms}, so consumers of a bindings artifact do not depend
 * on the ledger or this builder API.
 * <p>
 * A set generates one base for its own bindings (named by the caller, such as {@code Ike}) and
 * one per binding class it declares (named by the class, such as {@code Cql}); a binding class
 * named like the set's base adds to it, as the set's stamps do. A binding class with a package of
 * its own (the kernel, {@code Kernel}) is another product, written apart
 * ({@link #writeBindingClass}); its members stay in the set's own enums. A constant name derives
 * from the birth FQN unless the ledger bound it under another: the trailing semantic tag is
 * stripped, the rest upper-snake-cased. Two components with the same name in one enum fail
 * generation with both cited — bind one under another name, or rename its FQN.
 * <p>
 * An enum's constants are initialized in one static initializer, which the JVM caps at 64 KiB of
 * bytecode; past {@value #MAX_CONSTANTS} constants the generator fails rather than write a class
 * that does not compile. Split such a set with binding classes.
 */
public final class BindingsWriter {

    /**
     * The most constants one generated enum may hold. Each costs about 20 bytes of the static
     * initializer's 64 KiB, so this leaves a margin for the enum's own bookkeeping.
     */
    public static final int MAX_CONSTANTS = 2500;

    private BindingsWriter() {
    }

    /**
     * Writes the set's own bindings and returns its concept enum: see {@link #writeAll}.
     *
     * @param knowledgeSet the composed set to generate bindings for
     * @param packageName  the generated enums' package
     * @param baseName     the base of the set's own enums, such as {@code Ike}
     * @param outputDir    the generated-sources root; package directories are created below it
     * @return the path of the first enum written, the set's concept enum when it has concepts
     * @throws IOException           if a file cannot be written
     * @throws IllegalStateException if two components have the same name in one enum
     */
    public static Path write(KnowledgeSet knowledgeSet, String packageName, String baseName,
                             Path outputDir) throws IOException {
        return writeAll(knowledgeSet, packageName, baseName, outputDir).getFirst();
    }

    /**
     * Writes every bindings enum of the set: the set's own base first, then one base per binding
     * class the set declares, in declaration order; for each base its concept, pattern and stamp
     * enums, those that have constants.
     *
     * @param knowledgeSet the composed set to generate bindings for
     * @param packageName  the generated enums' package
     * @param baseName     the base of the set's own enums, such as {@code Ike}
     * @param outputDir    the generated-sources root; package directories are created below it
     * @return the paths of the written source files
     * @throws IOException           if a file cannot be written
     * @throws IllegalStateException if two components have the same name in one enum, or an enum
     *                               would exceed {@value #MAX_CONSTANTS} constants
     */
    public static List<Path> writeAll(KnowledgeSet knowledgeSet, String packageName, String baseName,
                                      Path outputDir) throws IOException {
        requireIdentifier(baseName);
        Map<String, Group> groups = new LinkedHashMap<>();
        groups.put(baseName, new Group());
        for (BindingClass bindingClass : knowledgeSet.bindingClasses()) {
            if (bindingClass.generatedWith(packageName)) {
                groups.putIfAbsent(bindingClass.name(), new Group());
            }
        }
        for (KnowledgeSet.StampBinding stampBinding : knowledgeSet.stampBindings()) {
            if (stampBinding.bindingClass().generatedWith(packageName)) {
                groups.get(stampBinding.bindingClass().name()).stamps.add(stampBinding);
            }
        }
        for (Declaration declaration : knowledgeSet.declarations()) {
            // A component bound only in classes generated elsewhere (the kernel) is still named
            // here: those classes are another product, not a home among the set's bindings.
            if (declaration.bindings().keySet().stream().noneMatch(bindingClass -> bindingClass.generatedWith(packageName))) {
                groups.get(baseName).add(new Binding(constantName(declaration.birthFqn()), declaration));
            }
            declaration.bindings().forEach((bindingClass, constant) -> {
                if (bindingClass.generatedWith(packageName)) {
                    groups.get(bindingClass.name()).add(new Binding(constant, declaration));
                }
            });
        }
        List<Path> files = new ArrayList<>();
        for (Map.Entry<String, Group> group : groups.entrySet()) {
            String description = group.getKey().equals(baseName)
                    ? "Generated bindings for the knowledge set"
                    : "Generated " + group.getKey() + " bindings of the knowledge set";
            files.addAll(writeGroup(knowledgeSet, packageName, group.getKey(), description, group.getValue(), outputDir));
        }
        return files;
    }

    /**
     * Writes one binding class into its own package: for a class generated apart from the set's
     * bindings, such as the kernel tinkar-core commits ({@code KernelConcept}, {@code KernelPattern}).
     *
     * @param knowledgeSet the composed set
     * @param bindingClass a binding class the set declares, with a package of its own
     * @param outputDir    the generated-sources root; package directories are created below it
     * @return the paths of the written source files
     * @throws IOException           if a file cannot be written
     * @throws IllegalStateException if two components have the same name in one enum
     */
    public static List<Path> writeBindingClass(KnowledgeSet knowledgeSet, BindingClass bindingClass, Path outputDir)
            throws IOException {
        if (bindingClass.packageName() == null) {
            throw new IllegalArgumentException(bindingClass.name() + " is generated with the set's bindings");
        }
        Group group = new Group();
        for (Declaration declaration : knowledgeSet.declarations()) {
            String constant = declaration.bindings().get(bindingClass);
            if (constant != null) {
                group.add(new Binding(constant, declaration));
            }
        }
        knowledgeSet.stampBindings().stream()
                .filter(stampBinding -> stampBinding.bindingClass().equals(bindingClass))
                .forEach(group.stamps::add);
        return writeGroup(knowledgeSet, bindingClass.packageName(), bindingClass.name(),
                "The " + bindingClass.name() + " bindings, generated apart and committed where they are used,"
                        + " of the knowledge set", group, outputDir);
    }

    /** A component's constant in one generated enum. */
    private record Binding(String constant, Declaration declaration) {
    }

    /** The components and stamps of one base, by kind. */
    private static final class Group {
        private final List<Binding> concepts = new ArrayList<>();
        private final List<Binding> patterns = new ArrayList<>();
        private final List<KnowledgeSet.StampBinding> stamps = new ArrayList<>();

        private void add(Binding binding) {
            switch (binding.declaration().kind()) {
                case CONCEPT -> concepts.add(binding);
                case PATTERN -> patterns.add(binding);
            }
        }
    }

    private static List<Path> writeGroup(KnowledgeSet knowledgeSet, String packageName, String base,
                                         String description, Group group, Path outputDir) throws IOException {
        List<Path> files = new ArrayList<>();
        if (!group.concepts.isEmpty()) {
            files.add(writeEnum(knowledgeSet, packageName, base + "Concept", description, Kind.CONCEPT,
                    group.concepts, outputDir));
        }
        if (!group.patterns.isEmpty()) {
            files.add(writeEnum(knowledgeSet, packageName, base + "Pattern", description, Kind.PATTERN,
                    group.patterns, outputDir));
        }
        if (!group.stamps.isEmpty()) {
            files.add(writeStampEnum(knowledgeSet, packageName, base + "Stamp", description, group.stamps, outputDir));
        }
        return files;
    }

    private enum Kind {
        CONCEPT("Concept", "ConceptEnumerationFacade", "conceptForEnum"),
        PATTERN("Pattern", "PatternEnumerationFacade", "patternForEnum");

        private final String proxy;
        private final String facade;
        private final String accessor;

        Kind(String proxy, String facade, String accessor) {
            this.proxy = proxy;
            this.facade = facade;
            this.accessor = accessor;
        }
    }

    private static Path writeEnum(KnowledgeSet knowledgeSet, String packageName, String enumName, String description,
                                  Kind kind, List<Binding> bindings, Path outputDir) throws IOException {
        checkSize(enumName, bindings.size());
        StringBuilder src = header(knowledgeSet, packageName, description);
        boolean inTerms = packageName.equals("dev.ikm.tinkar.terms");
        StringBuilder imports = new StringBuilder("import dev.ikm.tinkar.common.id.PublicId;\n");
        if (!inTerms) {
            imports.append("import dev.ikm.tinkar.terms.").append(kind.facade).append(";\n");
            imports.append("import dev.ikm.tinkar.terms.EntityProxy;\n");
        }
        imports.append("\nimport java.util.UUID;\n\n");
        src.insert(src.indexOf("/**"), imports);
        src.append("public enum ").append(enumName).append(" implements ").append(kind.facade)
                .append('<').append(enumName).append("> {\n");

        Map<String, String> constantToFqn = new HashMap<>();
        for (int i = 0; i < bindings.size(); i++) {
            Binding binding = bindings.get(i);
            Declaration declaration = binding.declaration();
            String prior = constantToFqn.putIfAbsent(binding.constant(), declaration.birthFqn());
            if (prior != null) {
                throw new IllegalStateException("Constant name collision in " + enumName + ": \"" + prior
                        + "\" and \"" + declaration.birthFqn() + "\" are both " + binding.constant());
            }
            src.append("\n    /**\n");
            src.append("     * ").append(javadocText(declaration)).append("\n");
            src.append("     */\n");
            src.append("    ").append(binding.constant()).append("(\"").append(escapeJava(declaration.birthFqn()))
                    .append("\", \"").append(uuidsText(declaration.publicId())).append("\")")
                    .append(i + 1 < bindings.size() ? ",\n" : ";\n");
        }
        String proxyType = "EntityProxy." + kind.proxy;
        src.append("\n    private final ").append(proxyType).append(" proxy;\n\n");
        src.append("    ").append(enumName).append("(String fullyQualifiedName, String uuids) {\n");
        src.append("        this.proxy = ").append(proxyType).append(".make(fullyQualifiedName, uuids(uuids));\n");
        src.append("    }\n\n");
        src.append("    @Override\n");
        src.append("    public ").append(proxyType).append(' ').append(kind.accessor).append("() {\n");
        src.append("        return proxy;\n    }\n\n");
        src.append("    @Override\n    public PublicId publicId() {\n        return proxy.publicId();\n    }\n\n");
        src.append("    /**\n     * @return the nid, resolved once and cached, as the proxy does\n     */\n");
        src.append("    @Override\n    public long nid() {\n        return proxy.nid();\n    }\n\n");
        src.append("    /**\n     * @return the fully qualified name at birth, with no store lookup\n     */\n");
        src.append("    @Override\n    public String description() {\n        return proxy.description();\n    }\n\n");
        src.append("    /**\n     * @return the proxy's text, so logs and debugging read as they did before enums\n     */\n");
        src.append("    @Override\n    public String toString() {\n        return proxy.toString();\n    }\n\n");
        src.append("    private static UUID[] uuids(String text) {\n");
        src.append("        String[] parts = text.split(\",\");\n");
        src.append("        UUID[] uuids = new UUID[parts.length];\n");
        src.append("        for (int i = 0; i < parts.length; i++) {\n");
        src.append("            uuids[i] = UUID.fromString(parts[i]);\n");
        src.append("        }\n");
        src.append("        return uuids;\n");
        src.append("    }\n");
        src.append("}\n");
        return writeFile(packageName, enumName, src, outputDir);
    }

    private static Path writeStampEnum(KnowledgeSet knowledgeSet, String packageName, String enumName,
                                       String description, List<KnowledgeSet.StampBinding> stampBindings,
                                       Path outputDir) throws IOException {
        checkSize(enumName, stampBindings.size());
        StringBuilder src = header(knowledgeSet, packageName, description);
        boolean inTerms = packageName.equals("dev.ikm.tinkar.terms");
        StringBuilder imports = new StringBuilder("import dev.ikm.tinkar.common.id.PublicIds;\n");
        if (!inTerms) {
            imports.append("import dev.ikm.tinkar.terms.DeclaredStamp;\n");
            imports.append("import dev.ikm.tinkar.terms.EntityProxy;\n");
            imports.append("import dev.ikm.tinkar.terms.State;\n");
        }
        imports.append("\nimport java.util.UUID;\n\n");
        src.insert(src.indexOf("/**"), imports);
        src.append("public enum ").append(enumName).append(" {\n");
        Map<String, String> constants = new HashMap<>();
        for (int i = 0; i < stampBindings.size(); i++) {
            KnowledgeSet.StampBinding stampBinding = stampBindings.get(i);
            Stamp stamp = stampBinding.stamp();
            if (constants.putIfAbsent(stampBinding.constant(), stampBinding.constant()) != null) {
                throw new IllegalStateException("Constant name collision in " + enumName + ": two stamps are both "
                        + stampBinding.constant());
            }
            src.append("\n    /**\n");
            src.append("     * The stamp ").append(stamp.state()).append(" at ").append(stamp.time())
                    .append(", by ").append(escapeJavadoc(nameOf(stamp.author()))).append(", in ")
                    .append(escapeJavadoc(nameOf(stamp.module()))).append(", on ")
                    .append(escapeJavadoc(nameOf(stamp.path()))).append(".\n");
            src.append("     */\n");
            src.append("    ").append(stampBinding.constant()).append("(new DeclaredStamp(EntityProxy.Stamp.make(\"")
                    .append(escapeJava(stampBinding.constant())).append("\", ").append(publicIdLiteral(stamp.publicId()))
                    .append("),\n");
            src.append("            State.").append(stamp.state().name()).append(", ").append(stamp.time()).append("L,\n");
            src.append("            ").append(conceptLiteral(stamp.author())).append(",\n");
            src.append("            ").append(conceptLiteral(stamp.module())).append(",\n");
            src.append("            ").append(conceptLiteral(stamp.path())).append("))")
                    .append(i + 1 < stampBindings.size() ? ",\n" : ";\n");
        }
        src.append("\n    private final DeclaredStamp stamp;\n\n");
        src.append("    ").append(enumName).append("(DeclaredStamp stamp) {\n        this.stamp = stamp;\n    }\n\n");
        src.append("    /**\n     * @return the declared stamp this constant stands for\n     */\n");
        src.append("    public DeclaredStamp stamp() {\n        return stamp;\n    }\n");
        src.append("}\n");
        return writeFile(packageName, enumName, src, outputDir);
    }

    private static StringBuilder header(KnowledgeSet knowledgeSet, String packageName, String description) {
        StringBuilder src = new StringBuilder();
        src.append("package ").append(packageName).append(";\n\n");
        src.append("/**\n");
        src.append(" * ").append(description).append(" {@code ").append(knowledgeSet.uuid())
                .append("} — DO NOT EDIT.\n");
        src.append(" * A component's identity is {@code T5(setUuid, fullyQualifiedNameAtBirth)} unless the ledger\n");
        src.append(" * adopted an established one; regenerate from the ledger.\n");
        src.append(" */\n");
        return src;
    }

    private static Path writeFile(String packageName, String className, StringBuilder src, Path outputDir)
            throws IOException {
        Path packageDir = outputDir.resolve(packageName.replace('.', '/'));
        Files.createDirectories(packageDir);
        Path file = packageDir.resolve(className + ".java");
        Files.writeString(file, src.toString());
        return file;
    }

    private static void checkSize(String enumName, int constants) {
        if (constants > MAX_CONSTANTS) {
            throw new IllegalStateException(enumName + " would hold " + constants + " constants, more than the "
                    + MAX_CONSTANTS + " one static initializer can carry; split the set with binding classes");
        }
    }

    private static void requireIdentifier(String baseName) {
        if (!BindingClass.isJavaIdentifier(baseName)) {
            throw new IllegalArgumentException("A bindings base name must be a Java identifier: \"" + baseName + "\"");
        }
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

    private static String conceptLiteral(dev.ikm.tinkar.terms.ConceptFacade concept) {
        return "EntityProxy.Concept.make(\"" + escapeJava(nameOf(concept)) + "\", "
                + uuidsLiteral(concept.publicId()) + ")";
    }

    /**
     * A component's UUIDs, comma-separated, as the generated constructor parses them: the proxy
     * keeps them sorted, so a generated enum lists them the same way whatever order the set
     * declared them in.
     */
    private static String uuidsText(dev.ikm.tinkar.common.id.PublicId publicId) {
        StringBuilder text = new StringBuilder();
        UUID[] uuids = publicId.asUuidArray();
        for (int i = 0; i < uuids.length; i++) {
            text.append(i == 0 ? "" : ",").append(uuids[i]);
        }
        return text.toString();
    }

    /**
     * A component's UUIDs as the arguments of {@code make(name, UUID...)}: the proxy keeps
     * them sorted, so a generated class lists them the same way whatever order the set
     * declared them in.
     */
    private static String uuidsLiteral(dev.ikm.tinkar.common.id.PublicId publicId) {
        StringBuilder literal = new StringBuilder();
        UUID[] uuids = publicId.asUuidArray();
        for (int i = 0; i < uuids.length; i++) {
            literal.append(i == 0 ? "" : ", ").append("UUID.fromString(\"").append(uuids[i]).append("\")");
        }
        return literal.toString();
    }

    private static String publicIdLiteral(dev.ikm.tinkar.common.id.PublicId publicId) {
        StringBuilder literal = new StringBuilder("PublicIds.of(");
        UUID[] uuids = publicId.asUuidArray();
        for (int i = 0; i < uuids.length; i++) {
            literal.append(i == 0 ? "" : ", ").append("UUID.fromString(\"").append(uuids[i]).append("\")");
        }
        return literal.append(")").toString();
    }

    private static String nameOf(dev.ikm.tinkar.terms.ConceptFacade concept) {
        if (concept instanceof dev.ikm.tinkar.terms.EntityProxy proxy && proxy.description() != null) {
            return proxy.description();
        }
        return concept.publicId().idString();
    }

    private static String escapeJavadoc(String text) {
        return text.replace("*/", "*&#47;");
    }

    private static String javadocText(Declaration declaration) {
        String text = declaration.definition().orElse(declaration.birthFqn());
        return text.replace("*/", "*&#47;");
    }

    private static String escapeJava(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
