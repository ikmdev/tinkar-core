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
package dev.ikm.tinkar.integration.builder;

import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.BindingClass;
import dev.ikm.tinkar.entity.builder.BindingsWriter;
import dev.ikm.tinkar.entity.builder.KnowledgeSet;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.terms.*;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Binding classes: a set declares each once, a component is bound in each class it belongs
 * to with one name in each, and generation writes one class per binding class beside the
 * default class, which holds the components bound in none.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BindingClassesIT {

    private Path outputDir;

    @BeforeAll
    void start() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        outputDir = Path.of(System.getProperty("user.dir")).resolve("target").resolve("generated-binding-classes");
    }

    @AfterAll
    void stop() {
        TestHelper.stopDatabase();
    }

    @Test
    @DisplayName("A shared component is named in each class it is bound in; an unbound one in the default class")
    void oneClassPerBindingClass() throws Exception {
        KnowledgeSet set = KnowledgeSet.of("3c8f1a2e-6b4d-5e7f-8a9b-0c1d2e3f4a51");
        BindingClass elPlus = set.bindingClass("ElPlus");
        BindingClass cql = set.bindingClass("Cql");
        set.concept("Conjunction (Test)").binding(elPlus, "AND").binding(cql, "AND");
        set.concept("Existential restriction (Test)").binding(elPlus, "SOME");
        set.concept("Retrieve (Test)").binding(cql, "RETRIEVE");
        set.concept("Unbound concept (Test)");

        List<Path> files = BindingsWriter.writeAll(set, "test.binding.classes", "Test", outputDir);

        assertEquals(List.of("TestConcept.java", "ElPlusConcept.java", "CqlConcept.java"),
                files.stream().map(file -> file.getFileName().toString()).toList());
        String defaults = Files.readString(files.get(0));
        String elPlusSource = Files.readString(files.get(1));
        String cqlSource = Files.readString(files.get(2));
        PublicId conjunction = set.conceptRef("Conjunction (Test)").publicId();

        assertTrue(defaults.contains("    UNBOUND_CONCEPT(\"Unbound concept (Test)\""), "unbound, named from its FQN");
        assertFalse(defaults.contains("CONJUNCTION"), "a bound component is not in the default class");
        assertTrue(elPlusSource.contains("    AND(\"") && containsEveryUuid(elPlusSource, conjunction),
                "the shared concept, in ElPlus");
        assertTrue(cqlSource.contains("    AND(\"") && containsEveryUuid(cqlSource, conjunction),
                "the shared concept, in Cql, under the same identity");
        assertTrue(elPlusSource.contains("    SOME(\"") && !cqlSource.contains(" SOME(\""),
                "a component bound in one class only");
        assertTrue(cqlSource.contains("    RETRIEVE(\""));
    }

    @Test
    @DisplayName("A bound stamp is generated in its class, with its identity and every dimension")
    void stampsBesideBindings() throws Exception {
        KnowledgeSet set = KnowledgeSet.of("3c8f1a2e-6b4d-5e7f-8a9b-0c1d2e3f4a56");
        BindingClass stamps = set.bindingClass("Test");
        ConceptFacade module = set.conceptRef("Test module (Test)");
        ActiveStamp inception = Stamp.active(PrimitiveData.INCEPTION_EPOCH, KernelConcept.USER, module,
                KernelConcept.DEVELOPMENT_PATH);
        set.bindStamp(inception, stamps, "INCEPTION");
        set.bindStamp(inception, stamps, "INCEPTION");
        assertThrows(IllegalStateException.class, () -> set.bindStamp(inception, stamps, "BIRTH"),
                "a second name for the stamp in the class");

        List<Path> files = BindingsWriter.writeAll(set, "test.binding.stamps", "Test", outputDir);
        assertEquals(List.of("TestStamp.java"), files.stream().map(file -> file.getFileName().toString()).toList(),
                "a binding class named like the set's base adds its stamps to that base");
        String source = Files.readString(files.getFirst());

        assertTrue(source.contains("public enum TestStamp {"));
        assertTrue(source.contains("    INCEPTION(new DeclaredStamp("));
        assertTrue(containsEveryUuid(source, inception.publicId()), "the stamp's identity");
        assertTrue(source.contains("State.ACTIVE, " + PrimitiveData.INCEPTION_EPOCH + "L"), "status and time");
        assertTrue(containsEveryUuid(source, module.publicId()), "the module");
        assertTrue(containsEveryUuid(source, KernelConcept.DEVELOPMENT_PATH.publicId()), "the path");
    }

    /** Whether generated source names every UUID of a component: a binding carries them all. */
    private static boolean containsEveryUuid(String source, PublicId publicId) {
        for (java.util.UUID uuid : publicId.asUuidArray()) {
            if (!source.contains(uuid.toString())) {
                return false;
            }
        }
        return true;
    }

    @Test
    @DisplayName("A declared stamp turns back into the builder stamp it was generated from")
    void declaredStampRoundTrips() {
        ConceptFacade module = EntityProxy.Concept.make("Test module (Test)",
                dev.ikm.tinkar.common.id.PublicIds.of(java.util.UUID.fromString("7b0e6f3a-1c2d-5e4f-9a8b-7c6d5e4f3a21")));
        ActiveStamp derived = Stamp.active(PrimitiveData.INCEPTION_EPOCH, KernelConcept.USER, module,
                KernelConcept.DEVELOPMENT_PATH);
        Stamp back = Stamp.from(new DeclaredStamp(EntityProxy.Stamp.make("INCEPTION", derived.publicId()),
                State.ACTIVE, derived.time(), KernelConcept.USER, module, KernelConcept.DEVELOPMENT_PATH));
        assertEquals(derived, back, "a derived identity comes back derived");

        Stamp nonExistent = Stamp.nonExistent();
        Stamp primordial = Stamp.from(new DeclaredStamp(EntityProxy.Stamp.make("NON_EXISTENT", nonExistent.publicId()),
                State.PRIMORDIAL, nonExistent.time(), KernelConcept.AUTHOR_FOR_VERSION,
                KernelConcept.UNINITIALIZED_COMPONENT, KernelConcept.UNINITIALIZED_COMPONENT));
        assertEquals(nonExistent.publicId(), primordial.publicId(), "a declared identity is kept");
        assertEquals(State.PRIMORDIAL, primordial.state());
    }

    @Test
    @DisplayName("A binding class with a package of its own is written apart; its members stay in the default class")
    void classGeneratedApart() throws Exception {
        KnowledgeSet set = KnowledgeSet.of("3c8f1a2e-6b4d-5e7f-8a9b-0c1d2e3f4a57");
        BindingClass kernel = set.bindingClass("test.binding.kernel", "TestKernel");
        set.concept("Kernel concept (Test)");
        set.bind(set.conceptRef("Kernel concept (Test)").publicId(), kernel, "KERNEL_CONCEPT");
        assertThrows(IllegalArgumentException.class, () -> set.bind(
                dev.ikm.tinkar.common.id.PublicIds.of(java.util.UUID.fromString("00000000-0000-5000-8000-000000000001")),
                kernel, "ABSENT"), "an identity the set does not declare");

        List<Path> files = BindingsWriter.writeAll(set, "test.binding.apart", "Test", outputDir);
        assertEquals(List.of("TestConcept.java"), files.stream().map(file -> file.getFileName().toString()).toList(),
                "the class with a package of its own is not among the set's bindings");
        assertTrue(Files.readString(files.getFirst()).contains("    KERNEL_CONCEPT(\""),
                "its member stays in the set's own enum");

        Path apart = BindingsWriter.writeBindingClass(set, kernel, outputDir).getFirst();
        assertTrue(apart.endsWith(Path.of("test", "binding", "kernel", "TestKernelConcept.java")), "written into its own package");
        assertTrue(Files.readString(apart).contains("    KERNEL_CONCEPT(\""));
    }

    @Test
    @DisplayName("Generated enums compile, split by kind, and stand for their components")
    void generatedEnumsCompileAndResolve() throws Exception {
        KnowledgeSet set = KnowledgeSet.of("3c8f1a2e-6b4d-5e7f-8a9b-0c1d2e3f4a58");
        BindingClass stamps = set.bindingClass("Compiled");
        ActiveStamp birth = Stamp.active(PrimitiveData.INCEPTION_EPOCH, KernelConcept.USER,
                set.conceptRef("Test module (Test)"), KernelConcept.DEVELOPMENT_PATH);
        set.concept("Field meaning (Test)").at(birth);
        set.concept("Pattern meaning (Test)").at(birth);
        set.pattern("Sample pattern (Test)").at(birth)
                .meaning(set.conceptRef("Pattern meaning (Test)"))
                .purpose(set.conceptRef("Field meaning (Test)"));
        set.bindStamp(birth, stamps, "INCEPTION");

        Path sources = outputDir.resolve("compiled-src");
        List<Path> files = BindingsWriter.writeAll(set, "test.binding.compiled", "Compiled", sources);
        assertEquals(List.of("CompiledConcept.java", "CompiledPattern.java", "CompiledStamp.java"),
                files.stream().map(file -> file.getFileName().toString()).toList());

        Path classes = outputDir.resolve("compiled-classes");
        Files.createDirectories(classes);
        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        List<String> arguments = new java.util.ArrayList<>(List.of("--enable-preview", "--release",
                String.valueOf(Runtime.version().feature()), "-proc:none", "-d", classes.toString(),
                "-cp", System.getProperty("java.class.path"), "-p", System.getProperty("jdk.module.path", ""),
                "--add-modules", "ALL-MODULE-PATH"));
        files.forEach(file -> arguments.add(file.toString()));
        assertEquals(0, compiler.run(null, null, null, arguments.toArray(String[]::new)), "the generated enums compile");

        try (java.net.URLClassLoader loader = new java.net.URLClassLoader(new java.net.URL[] {classes.toUri().toURL()},
                getClass().getClassLoader())) {
            Class<?> concepts = loader.loadClass("test.binding.compiled.CompiledConcept");
            Object fieldMeaning = concepts.getField("FIELD_MEANING").get(null);
            assertTrue(fieldMeaning instanceof ConceptEnumerationFacade<?>, "a concept constant is a concept facade");
            assertEquals(set.conceptRef("Field meaning (Test)").publicId(),
                    ((ConceptEnumerationFacade<?>) fieldMeaning).publicId(), "its identity");
            assertEquals("Field meaning (Test)", ((ConceptEnumerationFacade<?>) fieldMeaning).description(),
                    "its birth FQN, with no store lookup");
            Object sample = loader.loadClass("test.binding.compiled.CompiledPattern").getField("SAMPLE_PATTERN").get(null);
            assertTrue(sample instanceof PatternEnumerationFacade<?>, "a pattern constant is a pattern facade");
            Object stamp = loader.loadClass("test.binding.compiled.CompiledStamp").getField("INCEPTION").get(null);
            assertTrue(stamp.getClass().getMethod("stamp").invoke(stamp) instanceof DeclaredStamp,
                    "a stamp constant gives its declared stamp");
        }
    }

    @Test
    @DisplayName("A binding class is declared once")
    void declaredOnce() {
        KnowledgeSet set = KnowledgeSet.of("3c8f1a2e-6b4d-5e7f-8a9b-0c1d2e3f4a52");
        set.bindingClass("Ecl");
        assertThrows(IllegalStateException.class, () -> set.bindingClass("Ecl"));
        assertThrows(IllegalArgumentException.class, () -> set.bindingClass("Not an identifier"));
    }

    @Test
    @DisplayName("Within one class, a component has one name and a name one component")
    void oneNamePerClass() {
        KnowledgeSet set = KnowledgeSet.of("3c8f1a2e-6b4d-5e7f-8a9b-0c1d2e3f4a53");
        BindingClass elPlus = set.bindingClass("ElPlus");
        var conjunction = set.concept("Conjunction (Test)").binding(elPlus, "AND");
        conjunction.binding(elPlus, "AND");
        assertThrows(IllegalStateException.class, () -> conjunction.binding(elPlus, "CONJUNCTION"),
                "a second name for the component in the class");

        set.concept("Logical and (Test)").binding(elPlus, "AND");
        assertThrows(IllegalStateException.class,
                () -> BindingsWriter.writeAll(set, "test.binding.collision", "Test", outputDir),
                "two components under one name in the class");
    }

    @Test
    @DisplayName("A component is bound only in a class its own set declares")
    void onlyTheSetsClasses() {
        KnowledgeSet set = KnowledgeSet.of("3c8f1a2e-6b4d-5e7f-8a9b-0c1d2e3f4a54");
        BindingClass foreign = KnowledgeSet.of("3c8f1a2e-6b4d-5e7f-8a9b-0c1d2e3f4a55").bindingClass("Cql");
        set.concept("Retrieve (Test)").binding(foreign, "RETRIEVE");
        assertThrows(IllegalStateException.class, set::declarations);
    }
}
