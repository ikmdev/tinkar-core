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

import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.BindingClass;
import dev.ikm.tinkar.entity.builder.BindingsWriter;
import dev.ikm.tinkar.entity.builder.KnowledgeSet;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.terms.DeclaredStamp;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.State;
import dev.ikm.tinkar.terms.TinkarTerm;
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

        List<Path> files = BindingsWriter.writeAll(set, "test.binding.classes", "TestTerms", outputDir);

        assertEquals(List.of("TestTerms.java", "ElPlus.java", "Cql.java"),
                files.stream().map(file -> file.getFileName().toString()).toList());
        String defaults = Files.readString(files.get(0));
        String elPlusSource = Files.readString(files.get(1));
        String cqlSource = Files.readString(files.get(2));
        String conjunction = set.conceptRef("Conjunction (Test)").publicId().asUuidArray()[0].toString();

        assertTrue(defaults.contains("EntityProxy.Concept UNBOUND_CONCEPT ="), "unbound, named from its FQN");
        assertFalse(defaults.contains("CONJUNCTION"), "a bound component is not in the default class");
        assertTrue(elPlusSource.contains("EntityProxy.Concept AND =") && elPlusSource.contains(conjunction),
                "the shared concept, in ElPlus");
        assertTrue(cqlSource.contains("EntityProxy.Concept AND =") && cqlSource.contains(conjunction),
                "the shared concept, in Cql, under the same identity");
        assertTrue(elPlusSource.contains("EntityProxy.Concept SOME =") && !cqlSource.contains(" SOME ="),
                "a component bound in one class only");
        assertTrue(cqlSource.contains("EntityProxy.Concept RETRIEVE ="));
    }

    @Test
    @DisplayName("A bound stamp is generated in its class, with its identity and every dimension")
    void stampsBesideBindings() throws Exception {
        KnowledgeSet set = KnowledgeSet.of("3c8f1a2e-6b4d-5e7f-8a9b-0c1d2e3f4a56");
        BindingClass stamps = set.bindingClass("TestStamps");
        EntityProxy.Concept module = set.conceptRef("Test module (Test)");
        ActiveStamp inception = Stamp.active(PrimitiveData.INCEPTION_EPOCH, TinkarTerm.USER, module,
                TinkarTerm.DEVELOPMENT_PATH);
        set.bindStamp(inception, stamps, "INCEPTION");
        set.bindStamp(inception, stamps, "INCEPTION");
        assertThrows(IllegalStateException.class, () -> set.bindStamp(inception, stamps, "BIRTH"),
                "a second name for the stamp in the class");

        List<Path> files = BindingsWriter.writeAll(set, "test.binding.stamps", "TestTerms", outputDir);
        String source = Files.readString(files.get(1));

        assertTrue(source.contains("public static final DeclaredStamp INCEPTION ="));
        assertTrue(source.contains(inception.publicId().asUuidArray()[0].toString()), "the stamp's identity");
        assertTrue(source.contains("State.ACTIVE, " + PrimitiveData.INCEPTION_EPOCH + "L"), "status and time");
        assertTrue(source.contains(module.publicId().asUuidArray()[0].toString()), "the module");
        assertTrue(source.contains(TinkarTerm.DEVELOPMENT_PATH.publicId().asUuidArray()[0].toString()), "the path");
    }

    @Test
    @DisplayName("A declared stamp turns back into the builder stamp it was generated from")
    void declaredStampRoundTrips() {
        EntityProxy.Concept module = EntityProxy.Concept.make("Test module (Test)",
                dev.ikm.tinkar.common.id.PublicIds.of(java.util.UUID.fromString("7b0e6f3a-1c2d-5e4f-9a8b-7c6d5e4f3a21")));
        ActiveStamp derived = Stamp.active(PrimitiveData.INCEPTION_EPOCH, TinkarTerm.USER, module,
                TinkarTerm.DEVELOPMENT_PATH);
        Stamp back = Stamp.from(new DeclaredStamp(EntityProxy.Stamp.make("INCEPTION", derived.publicId()),
                State.ACTIVE, derived.time(), TinkarTerm.USER, module, TinkarTerm.DEVELOPMENT_PATH));
        assertEquals(derived, back, "a derived identity comes back derived");

        Stamp nonExistent = Stamp.nonExistent();
        Stamp primordial = Stamp.from(new DeclaredStamp(EntityProxy.Stamp.make("NON_EXISTENT", nonExistent.publicId()),
                State.PRIMORDIAL, nonExistent.time(), TinkarTerm.AUTHOR_FOR_VERSION,
                TinkarTerm.UNINITIALIZED_COMPONENT, TinkarTerm.UNINITIALIZED_COMPONENT));
        assertEquals(nonExistent.publicId(), primordial.publicId(), "a declared identity is kept");
        assertEquals(State.PRIMORDIAL, primordial.state());
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
                () -> BindingsWriter.writeAll(set, "test.binding.collision", "TestTerms", outputDir),
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
