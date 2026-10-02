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

import dev.ikm.tinkar.common.id.IntIds;
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
import dev.ikm.tinkar.terms.TinkarTerm;
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
 * Store-backed tests for {@link DiTreeText} against the Tinkar starter data
 * ({@code IKE-Network/ike-issues#1177}): the layout of the text, that every component in it is
 * named by the caller's function and by nothing else, and that no nid is in it — for a tree
 * built here with every kind of property value, and for the stated and inferred definitions
 * the starter data holds.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DiTreeTextIT {

    /** Concepts of the starter data whose stated and inferred definitions are written. */
    private static final List<EntityFacade> DEFINED = List.of(
            TinkarTerm.ENGLISH_LANGUAGE, TinkarTerm.LANGUAGE, TinkarTerm.PART_OF,
            TinkarTerm.ROLE_TYPE, TinkarTerm.NECESSARY_SET, TinkarTerm.DESCRIPTION_TYPE);

    /** The form {@code PrimitiveData.text} writes for a component with no description. */
    private static final Pattern ANGLE_BRACKET_NID = Pattern.compile("<-?\\d+>");

    /**
     * A nid of the test store in decimal. The store numbers components upward from the bottom
     * of the int range, so every nid it assigns is a minus sign and ten digits beginning
     * {@code 21474} or {@code 21473}.
     */
    private static final Pattern STORE_NID = Pattern.compile("-2147[34]\\d{5}(?!\\d)");

    /** Names a component by its first UUID, as text for another store does when it has no description. */
    private static final IntFunction<String> BY_UUID = nid -> PrimitiveData.publicId(nid).asUuidArray()[0].toString();

    private ViewCalculator view;

    /** Names a component by the description the default view selects. */
    private IntFunction<String> byDescription;

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
                name(TinkarTerm.ROLE_TYPE) + ": " + name(TinkarTerm.PART_OF),
                name(TinkarTerm.ROLE_OPERATOR) + ": " + name(TinkarTerm.EXISTENTIAL_RESTRICTION)));
        roleProperties.sort(null);

        String expected = "   [0]➞[1] " + name(TinkarTerm.DEFINITION_ROOT) + "\n"
                + "     [1]➞[2] " + name(TinkarTerm.NECESSARY_SET) + "\n"
                + "       [2]➞[3,4,5] " + name(TinkarTerm.AND) + "\n"
                + "         [3] " + name(TinkarTerm.CONCEPT_REFERENCE) + ": " + name(TinkarTerm.LANGUAGE) + "\n"
                + "         [4] " + name(TinkarTerm.ROLE) + "\n"
                + "            •" + roleProperties.get(0) + "\n"
                + "            •" + roleProperties.get(1) + "\n"
                + "         [5] " + name(TinkarTerm.PROPERTY_SET) + "\n"
                + "            •" + name(TinkarTerm.PROPERTY_SEQUENCE) + ": ["
                + name(TinkarTerm.PART_OF) + ", " + name(TinkarTerm.ROLE_TYPE) + "]\n";

        String text = DiTreeText.tree(tree, byDescription);
        assertEquals(expected, text);
        assertNoNid("the tree", text);
    }

    @Test
    void everyComponentIsNamedByTheCallersFunctionAndByNothingElse() {
        DiTreeEntity tree = aTreeWithEveryKindOfPropertyValue();
        Set<Integer> named = new TreeSet<>();

        String text = DiTreeText.tree(tree, nid -> {
            named.add(nid);
            return "X";
        });

        Set<Integer> components = new TreeSet<>();
        for (EntityFacade component : List.of(TinkarTerm.DEFINITION_ROOT, TinkarTerm.NECESSARY_SET, TinkarTerm.AND,
                TinkarTerm.CONCEPT_REFERENCE, TinkarTerm.LANGUAGE, TinkarTerm.ROLE, TinkarTerm.ROLE_TYPE,
                TinkarTerm.PART_OF, TinkarTerm.ROLE_OPERATOR, TinkarTerm.EXISTENTIAL_RESTRICTION,
                TinkarTerm.PROPERTY_SET, TinkarTerm.PROPERTY_SEQUENCE)) {
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
        int partOf = TinkarTerm.PART_OF.nid();

        assertTrue(tree.toString().contains("<" + partOf + ">"),
                "toString() writes the nid of each element of an id list");

        String text = DiTreeText.tree(tree, BY_UUID);
        assertNoNid("the tree named by UUID", text);
        assertFalse(text.contains(Integer.toString(partOf)), text);
        assertTrue(text.contains("[" + BY_UUID.apply(partOf) + ", " + BY_UUID.apply(TinkarTerm.ROLE_TYPE.nid()) + "]"),
                "an id list is the list of its components' names");
        assertTrue(text.startsWith("   [0]➞[1] " + BY_UUID.apply(TinkarTerm.DEFINITION_ROOT.nid()) + "\n"),
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
                for (IntFunction<String> nameOf : List.of(byDescription, BY_UUID)) {
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
        EntityVertex reference = EntityVertex.make(TinkarTerm.CONCEPT_REFERENCE);
        setProperty(reference, TinkarTerm.CONCEPT_REFERENCE, TinkarTerm.LANGUAGE);
        assertEquals(name(TinkarTerm.CONCEPT_REFERENCE) + ": " + name(TinkarTerm.LANGUAGE),
                DiTreeText.vertex(reference, byDescription));

        EntityVertex bare = EntityVertex.make(TinkarTerm.AND);
        assertEquals(name(TinkarTerm.AND), DiTreeText.vertex(bare, byDescription));

        int partOf = TinkarTerm.PART_OF.nid();
        EntityVertex role = EntityVertex.make(TinkarTerm.ROLE);
        setProperty(role, TinkarTerm.ROLE_TYPE, TinkarTerm.PART_OF);
        String text = DiTreeText.vertex(role, byDescription);
        assertEquals(name(TinkarTerm.ROLE) + " {" + name(TinkarTerm.ROLE_TYPE) + "=" + name(TinkarTerm.PART_OF) + "}",
                text);
        assertNoNid("the vertex", text);

        // The vertex's own toString() writes the nid of a component it holds as a value.
        String ownText = role.toString();
        assertTrue(ownText.contains("<" + partOf + ">"), ownText);
    }

    @Test
    void aVertexOrATreeHeldAsAPropertyValueIsWrittenTheSameWay() {
        EntityVertex held = EntityVertex.make(TinkarTerm.CONCEPT_REFERENCE);
        setProperty(held, TinkarTerm.CONCEPT_REFERENCE, TinkarTerm.LANGUAGE);
        EntityVertex holder = EntityVertex.make(TinkarTerm.ROLE);
        setProperty(holder, TinkarTerm.ROLE_TYPE, held);

        String text = DiTreeText.vertex(holder, BY_UUID);
        assertEquals(BY_UUID.apply(TinkarTerm.ROLE.nid()) + " {" + BY_UUID.apply(TinkarTerm.ROLE_TYPE.nid()) + "="
                        + BY_UUID.apply(TinkarTerm.CONCEPT_REFERENCE.nid()) + ": " + BY_UUID.apply(TinkarTerm.LANGUAGE.nid()) + "}",
                text);
        assertNoNid("a vertex that holds a vertex", text);

        EntityVertex treeHolder = EntityVertex.make(TinkarTerm.ROLE);
        setProperty(treeHolder, TinkarTerm.ROLE_TYPE, aTreeWithEveryKindOfPropertyValue());
        String treeText = DiTreeText.vertex(treeHolder, BY_UUID);
        assertNoNid("a vertex that holds a tree", treeText);
        assertTrue(treeText.contains(DiTreeText.tree(aTreeWithEveryKindOfPropertyValue(), BY_UUID).lines().findFirst().orElseThrow().strip()),
                "the held tree is written as a tree is");
    }

    @Test
    void aPropertyValueThatIsNotAComponentIsWrittenAsItsOwnText() {
        EntityVertex vertex = EntityVertex.make(TinkarTerm.ROLE);
        MutableIntObjectMap<Object> properties = IntObjectMaps.mutable.empty();
        properties.put(TinkarTerm.ROLE_TYPE.nid(), "a string");
        properties.put(TinkarTerm.ROLE_OPERATOR.nid(), 42);
        properties.put(TinkarTerm.PROPERTY_SEQUENCE.nid(), true);
        vertex.setProperties(properties);

        List<String> expected = new ArrayList<>(List.of(
                name(TinkarTerm.ROLE_TYPE) + "=a string",
                name(TinkarTerm.ROLE_OPERATOR) + "=42",
                name(TinkarTerm.PROPERTY_SEQUENCE) + "=true"));
        expected.sort(null);

        assertEquals(name(TinkarTerm.ROLE) + " {" + String.join(", ", expected) + "}",
                DiTreeText.vertex(vertex, byDescription));
    }

    /**
     * A small definition: a root, a necessary set, and under an And a concept reference (a
     * component held under the vertex's own meaning), a role (two component properties), and a
     * vertex that holds an id list.
     */
    private static DiTreeEntity aTreeWithEveryKindOfPropertyValue() {
        EntityVertex root = EntityVertex.make(TinkarTerm.DEFINITION_ROOT);
        EntityVertex necessarySet = EntityVertex.make(TinkarTerm.NECESSARY_SET);
        EntityVertex and = EntityVertex.make(TinkarTerm.AND);

        EntityVertex reference = EntityVertex.make(TinkarTerm.CONCEPT_REFERENCE);
        setProperty(reference, TinkarTerm.CONCEPT_REFERENCE, TinkarTerm.LANGUAGE);

        EntityVertex role = EntityVertex.make(TinkarTerm.ROLE);
        MutableIntObjectMap<Object> roleProperties = IntObjectMaps.mutable.empty();
        roleProperties.put(TinkarTerm.ROLE_TYPE.nid(), TinkarTerm.PART_OF);
        roleProperties.put(TinkarTerm.ROLE_OPERATOR.nid(), TinkarTerm.EXISTENTIAL_RESTRICTION);
        role.setProperties(roleProperties);

        EntityVertex propertySet = EntityVertex.make(TinkarTerm.PROPERTY_SET);
        setProperty(propertySet, TinkarTerm.PROPERTY_SEQUENCE,
                IntIds.list.of(TinkarTerm.PART_OF.nid(), TinkarTerm.ROLE_TYPE.nid()));

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
        MutableIntObjectMap<Object> properties = IntObjectMaps.mutable.empty();
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
