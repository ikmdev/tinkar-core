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
package dev.ikm.tinkar.integration.graph;

import java.util.function.LongFunction;
import org.eclipse.collections.impl.factory.primitive.LongObjectMaps;
import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;
import network.ike.foundation.ike.bindings.IkeTerms;
import dev.ikm.tinkar.terms.KernelTerm;
import dev.ikm.tinkar.common.id.LongIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.DiTreeText;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.EntityFacade;
import org.eclipse.collections.api.factory.primitive.IntObjectMaps;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Store-backed tests for {@link DiTreeText} against the IKE starter set
 * ({@code IKE-Network/ike-issues#1177}): the layout of the text, that every component in it is
 * named by the caller's function and by nothing else, and that no nid is in it — for a tree
 * built here with every kind of property value, and for the stated and inferred definitions
 * the starter data holds.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DiTreeTextIT {

    /** Concepts of the starter data whose stated and inferred definitions are written. */
    private static final List<EntityFacade> DEFINED = List.of(
            KernelTerm.ENGLISH_LANGUAGE, KernelTerm.LANGUAGE, IkeTerms.PART_OF,
            KernelTerm.ROLE_TYPE, KernelTerm.NECESSARY_SET, KernelTerm.DESCRIPTION_TYPE);

    /** The form {@code PrimitiveData.text} writes for a component with no description. */
    private static final Pattern ANGLE_BRACKET_NID = Pattern.compile("<-?\\d+>");

    /**
     * A nid of the test store in decimal. The store numbers components upward from the bottom
     * of the int range, so every nid it assigns is a minus sign and ten digits beginning
     * {@code 21474} or {@code 21473}.
     */
    private static final Pattern STORE_NID = Pattern.compile("-2147[34]\\d{5}(?!\\d)");

    /** Names a component by its UUIDs, every one, as text for another store does when it has no description. */
    private static final LongFunction<String> BY_UUID =
            nid -> PrimitiveData.publicId(nid).asUuidList().collect(java.util.UUID::toString).makeString(",");

    private ViewCalculator view;

    /** Names a component by the description the default view selects. */
    private LongFunction<String> byDescription;

    @BeforeAll
    void startStore() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
        view = Calculators.View.Default();
        byDescription = nid -> view.getDescriptionText(nid).orElseThrow();
    }

    @AfterAll
    void stopStore() {
        TestHelper.stopDatabase();
    }

    @Test
    void aTreeIsWrittenOneVertexPerLineWithItsPropertiesBeneathInNameOrder() {
        DiTreeEntity tree = aTreeWithEveryKindOfPropertyValue();

        // Role's two properties are written in the order of their names, whatever order the
        // vertex holds them in.
        List<String> roleProperties = new ArrayList<>(List.of(
                name(KernelTerm.ROLE_TYPE) + ": " + name(IkeTerms.PART_OF),
                name(KernelTerm.ROLE_OPERATOR) + ": " + name(KernelTerm.EXISTENTIAL_RESTRICTION)));
        roleProperties.sort(null);

        String expected = "   [0]➞[1] " + name(KernelTerm.DEFINITION_ROOT) + "\n"
                + "     [1]➞[2] " + name(KernelTerm.NECESSARY_SET) + "\n"
                + "       [2]➞[3,4,5] " + name(KernelTerm.AND) + "\n"
                + "         [3] " + name(KernelTerm.CONCEPT_REFERENCE) + ": " + name(KernelTerm.LANGUAGE) + "\n"
                + "         [4] " + name(KernelTerm.ROLE) + "\n"
                + "            •" + roleProperties.get(0) + "\n"
                + "            •" + roleProperties.get(1) + "\n"
                + "         [5] " + name(KernelTerm.PROPERTY_SET) + "\n"
                + "            •" + name(KernelTerm.PROPERTY_SEQUENCE) + ": ["
                + name(IkeTerms.PART_OF) + ", " + name(KernelTerm.ROLE_TYPE) + "]\n";

        String text = DiTreeText.tree(tree, byDescription);
        assertEquals(expected, text);
        assertNoNid("the tree", text);
    }

    @Test
    void everyComponentIsNamedByTheCallersFunctionAndByNothingElse() {
        DiTreeEntity tree = aTreeWithEveryKindOfPropertyValue();
        Set<Long> named = new TreeSet<>();

        String text = DiTreeText.tree(tree, nid -> {
            named.add(nid);
            return "X";
        });

        Set<Long> components = new TreeSet<>();
        for (EntityFacade component : List.of(KernelTerm.DEFINITION_ROOT, KernelTerm.NECESSARY_SET, KernelTerm.AND,
                KernelTerm.CONCEPT_REFERENCE, KernelTerm.LANGUAGE, KernelTerm.ROLE, KernelTerm.ROLE_TYPE,
                IkeTerms.PART_OF, KernelTerm.ROLE_OPERATOR, KernelTerm.EXISTENTIAL_RESTRICTION,
                KernelTerm.PROPERTY_SET, KernelTerm.PROPERTY_SEQUENCE)) {
            components.add(component.nid());
        }
        assertEquals(components, named,
                "the function is asked for each vertex meaning, property key, component value, and id list element");

        // What is left when the layout is taken away — indentation, vertex indexes, arrows,
        // bullets, and separators — is the names alone: nothing else is written for a component.
        String namesOnly = text.replaceAll("\\[\\d+(,\\d+)*]", "").replaceAll("[\\s➞•:,\\[\\]]", "");
        assertTrue(namesOnly.matches("X+"), "only the names remain: " + namesOnly);
        assertEquals(14, namesOnly.length(),
                "six meanings, one value under a meaning, two property keys with a value each,"
                        + " and one key with a list of two");
    }

    @Test
    void toStringWritesNidsForTheSameTreeAndThisTextDoesNot() {
        // The reason DiTreeText exists. DiTreeEntity.toString() writes the nid of every element
        // of an id list, whatever the store describes; the assertion on it fails when that
        // changes, which is the moment to reconsider this class.
        DiTreeEntity tree = aTreeWithEveryKindOfPropertyValue();
        long partOf = IkeTerms.PART_OF.nid();

        assertTrue(tree.toString().contains("<" + partOf + ">"),
                "toString() writes the nid of each element of an id list");

        String text = DiTreeText.tree(tree, BY_UUID);
        assertNoNid("the tree named by UUID", text);
        assertFalse(text.contains(Long.toString(partOf)), text);
        assertTrue(text.contains("[" + BY_UUID.apply(partOf) + ", " + BY_UUID.apply(KernelTerm.ROLE_TYPE.nid()) + "]"),
                "an id list is the list of its components' names");
        assertTrue(text.startsWith("   [0]➞[1] " + BY_UUID.apply(KernelTerm.DEFINITION_ROOT.nid()) + "\n"),
                "a vertex is named for its meaning");
    }

    @Test
    void theDefinitionsOfTheStarterDataAreWrittenWithoutANid() {
        int written = 0;
        for (EntityFacade concept : DEFINED) {
            for (Latest<DiTreeEntity> definition : List.of(
                    view.logicCalculator().getStatedLogicalExpressionForEntity(concept.nid(), view.stampCalculator()),
                    view.logicCalculator().getInferredLogicalExpressionForEntity(concept.nid(), view.stampCalculator()))) {
                if (!definition.isPresent()) {
                    continue;
                }
                DiTreeEntity tree = definition.get();
                for (LongFunction<String> nameOf : List.of(byDescription, BY_UUID)) {
                    String text = DiTreeText.tree(tree, nameOf);
                    assertNoNid("the definition of " + concept.description(), text);
                    // toString() has the same lines between "DiTreeEntity{" and "}": one per
                    // vertex and one per property that is not the vertex's own value.
                    assertEquals(tree.toString().lines().count() - 2, text.lines().count(),
                            "the layout follows DiTreeEntity.toString() line for line");
                    assertEquals(tree.vertexCount(),
                            text.lines().filter(line -> line.stripLeading().startsWith("[")).count(),
                            "one line per vertex");
                }
                written++;
            }
        }
        assertTrue(written >= DEFINED.size(), "the starter data holds definitions for these concepts");
    }

    @Test
    void aVertexOnItsOwnIsOneLineOfNames() {
        EntityVertex reference = EntityVertex.make(KernelTerm.CONCEPT_REFERENCE);
        setProperty(reference, KernelTerm.CONCEPT_REFERENCE, KernelTerm.LANGUAGE);
        assertEquals(name(KernelTerm.CONCEPT_REFERENCE) + ": " + name(KernelTerm.LANGUAGE),
                DiTreeText.vertex(reference, byDescription));

        EntityVertex bare = EntityVertex.make(KernelTerm.AND);
        assertEquals(name(KernelTerm.AND), DiTreeText.vertex(bare, byDescription));

        long partOf = IkeTerms.PART_OF.nid();
        EntityVertex role = EntityVertex.make(KernelTerm.ROLE);
        setProperty(role, KernelTerm.ROLE_TYPE, IkeTerms.PART_OF);
        String text = DiTreeText.vertex(role, byDescription);
        assertEquals(name(KernelTerm.ROLE) + " {" + name(KernelTerm.ROLE_TYPE) + "=" + name(IkeTerms.PART_OF) + "}",
                text);
        assertNoNid("the vertex", text);

        // The vertex's own toString() writes the nid of a component it holds as a value.
        String ownText = role.toString();
        assertTrue(ownText.contains("<" + partOf + ">"), ownText);
    }

    @Test
    void aVertexOrATreeHeldAsAPropertyValueIsWrittenTheSameWay() {
        EntityVertex held = EntityVertex.make(KernelTerm.CONCEPT_REFERENCE);
        setProperty(held, KernelTerm.CONCEPT_REFERENCE, KernelTerm.LANGUAGE);
        EntityVertex holder = EntityVertex.make(KernelTerm.ROLE);
        setProperty(holder, KernelTerm.ROLE_TYPE, held);

        String text = DiTreeText.vertex(holder, BY_UUID);
        assertEquals(BY_UUID.apply(KernelTerm.ROLE.nid()) + " {" + BY_UUID.apply(KernelTerm.ROLE_TYPE.nid()) + "="
                        + BY_UUID.apply(KernelTerm.CONCEPT_REFERENCE.nid()) + ": " + BY_UUID.apply(KernelTerm.LANGUAGE.nid()) + "}",
                text);
        assertNoNid("a vertex that holds a vertex", text);

        EntityVertex treeHolder = EntityVertex.make(KernelTerm.ROLE);
        setProperty(treeHolder, KernelTerm.ROLE_TYPE, aTreeWithEveryKindOfPropertyValue());
        String treeText = DiTreeText.vertex(treeHolder, BY_UUID);
        assertNoNid("a vertex that holds a tree", treeText);
        assertTrue(treeText.contains(DiTreeText.tree(aTreeWithEveryKindOfPropertyValue(), BY_UUID).lines().findFirst().orElseThrow().strip()),
                "the held tree is written as a tree is");
    }

    @Test
    void aPropertyValueThatIsNotAComponentIsWrittenAsItsOwnText() {
        EntityVertex vertex = EntityVertex.make(KernelTerm.ROLE);
        MutableLongObjectMap<Object> properties = LongObjectMaps.mutable.empty();
        properties.put(KernelTerm.ROLE_TYPE.nid(), "a string");
        properties.put(KernelTerm.ROLE_OPERATOR.nid(), 42);
        properties.put(KernelTerm.PROPERTY_SEQUENCE.nid(), true);
        vertex.setProperties(properties);

        List<String> expected = new ArrayList<>(List.of(
                name(KernelTerm.ROLE_TYPE) + "=a string",
                name(KernelTerm.ROLE_OPERATOR) + "=42",
                name(KernelTerm.PROPERTY_SEQUENCE) + "=true"));
        expected.sort(null);

        assertEquals(name(KernelTerm.ROLE) + " {" + String.join(", ", expected) + "}",
                DiTreeText.vertex(vertex, byDescription));
    }

    /**
     * A small definition: a root, a necessary set, and under an And a concept reference (a
     * component held under the vertex's own meaning), a role (two component properties), and a
     * vertex that holds an id list.
     */
    private static DiTreeEntity aTreeWithEveryKindOfPropertyValue() {
        EntityVertex root = EntityVertex.make(KernelTerm.DEFINITION_ROOT);
        EntityVertex necessarySet = EntityVertex.make(KernelTerm.NECESSARY_SET);
        EntityVertex and = EntityVertex.make(KernelTerm.AND);

        EntityVertex reference = EntityVertex.make(KernelTerm.CONCEPT_REFERENCE);
        setProperty(reference, KernelTerm.CONCEPT_REFERENCE, KernelTerm.LANGUAGE);

        EntityVertex role = EntityVertex.make(KernelTerm.ROLE);
        MutableLongObjectMap<Object> roleProperties = LongObjectMaps.mutable.empty();
        roleProperties.put(KernelTerm.ROLE_TYPE.nid(), IkeTerms.PART_OF);
        roleProperties.put(KernelTerm.ROLE_OPERATOR.nid(), KernelTerm.EXISTENTIAL_RESTRICTION);
        role.setProperties(roleProperties);

        EntityVertex propertySet = EntityVertex.make(KernelTerm.PROPERTY_SET);
        setProperty(propertySet, KernelTerm.PROPERTY_SEQUENCE,
                LongIds.list.of(IkeTerms.PART_OF.nid(), KernelTerm.ROLE_TYPE.nid()));

        DiTreeEntity.Builder builder = DiTreeEntity.builder();
        builder.setRoot(root);
        builder.addEdge(necessarySet, root);
        builder.addEdge(and, necessarySet);
        builder.addEdge(reference, and);
        builder.addEdge(role, and);
        builder.addEdge(propertySet, and);
        return builder.build();
    }

    /** Gives a vertex one property. */
    private static void setProperty(EntityVertex vertex, EntityFacade key, Object value) {
        MutableLongObjectMap<Object> properties = LongObjectMaps.mutable.empty();
        properties.put(key.nid(), value);
        vertex.setProperties(properties);
    }

    /** The description the default view selects for a component. */
    private String name(EntityFacade component) {
        return byDescription.apply(component.nid());
    }

    /** Fails when the text holds a nid in either of the forms one is written in. */
    private static void assertNoNid(String what, String text) {
        for (Pattern form : new Pattern[]{ANGLE_BRACKET_NID, STORE_NID}) {
            Matcher matcher = form.matcher(text);
            if (matcher.find()) {
                fail(what + " holds a nid: \"" + matcher.group() + "\" in:\n" + text);
            }
        }
    }
}
