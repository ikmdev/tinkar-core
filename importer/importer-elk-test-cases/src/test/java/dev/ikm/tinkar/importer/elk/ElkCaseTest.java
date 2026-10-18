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
package dev.ikm.tinkar.importer.elk;

import dev.ikm.tinkar.importer.elk.ElkCase.Named;
import dev.ikm.tinkar.importer.elk.ElkCase.Some;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ElkCaseTest {

    private static final String PREFIXES = """
            Prefix(: = <http://example.org/>)
            Prefix(owl: = <http://www.w3.org/2002/07/owl#>)
            Prefix(xsd: = <http://www.w3.org/2001/XMLSchema#>)
            """;

    private static String iri(String local) {
        return "http://example.org/" + local;
    }

    @Test
    @DisplayName("A class's SubClassOf superclasses are its necessary conjuncts; a GCI is an inclusion set")
    void readsExistentials() throws Exception {
        ElkCase elkCase = ElkCase.read("classification/Existentials", PREFIXES + """
                Ontology(
                EquivalentClasses(:B :C)
                SubClassOf(:A ObjectSomeValuesFrom(:R :B))
                SubClassOf(ObjectSomeValuesFrom(:S :C) :D)
                SubObjectPropertyOf(:R :S)
                )""", Optional.of(PREFIXES + """
                Ontology(
                EquivalentClasses(:B :C)
                SubClassOf(:A :D)
                )"""), Optional.empty());

        assertEquals(List.of(new Some(iri("R"), new Named(iri("B")))), elkCase.necessary().get(iri("A")));
        assertEquals(List.of(List.of(new Some(iri("S"), new Named(iri("C"))))), elkCase.inclusions().get(iri("D")));
        assertEquals(List.of(List.of(new Named(iri("C")))), elkCase.sufficient().get(iri("B")));
        assertEquals(Set.of(iri("S")), elkCase.superProperties().get(iri("R")));

        ElkCase.Taxonomy taxonomy = elkCase.classTaxonomy().orElseThrow();
        assertEquals(Set.of(iri("D")), taxonomy.parents().get(iri("A")));
        assertEquals(Set.of(ElkCase.THING), taxonomy.parents().get(iri("B")));
        assertEquals(Set.of(iri("C")), taxonomy.equivalents().get(iri("B")));
        assertFalse(taxonomy.equivalents().containsKey(iri("A")));
    }

    @Test
    @DisplayName("A parent node with equivalent members contributes every member as a parent")
    void parentNodeMembersAreParents() throws Exception {
        ElkCase elkCase = ElkCase.read("x", PREFIXES + """
                Ontology(
                SubClassOf(:A :B)
                EquivalentClasses(:B :C)
                )""", Optional.of(PREFIXES + """
                Ontology(
                EquivalentClasses(:B :C)
                SubClassOf(:A :B)
                )"""), Optional.empty());
        assertEquals(Set.of(iri("B"), iri("C")), elkCase.classTaxonomy().orElseThrow().parents().get(iri("A")));
    }

    @Test
    @DisplayName("Individuals and negation put a case out of scope")
    void outOfScope() {
        ElkCase.OutOfScope individuals = assertThrows(ElkCase.OutOfScope.class, () -> ElkCase.read("x",
                PREFIXES + "Ontology(ClassAssertion(:A :a))", Optional.empty(), Optional.empty()));
        assertTrue(individuals.construct().contains("ClassAssertion"), individuals.construct());
        assertThrows(ElkCase.OutOfScope.class, () -> ElkCase.read("x",
                PREFIXES + "Ontology(SubClassOf(:A ObjectComplementOf(:B)))", Optional.empty(), Optional.empty()));
    }

    @Test
    @DisplayName("Disjoint classes are disjoint pairwise; the Nothing node is unsatisfiable")
    void disjointnessAndTheBottom() throws Exception {
        ElkCase elkCase = ElkCase.read("x", PREFIXES + """
                Ontology(
                SubClassOf(:A :C)
                SubClassOf(:B :C)
                DisjointClasses(:A :B :C)
                SubClassOf(:D owl:Nothing)
                )""", Optional.of(PREFIXES + """
                Ontology(
                EquivalentClasses(owl:Nothing :A :B :D)
                )"""), Optional.empty());
        assertEquals(Set.of(iri("B"), iri("C")), elkCase.disjoint().get(iri("A")));
        assertEquals(Set.of(iri("C")), elkCase.disjoint().get(iri("B")));
        assertTrue(elkCase.necessary().get(iri("D")).contains(new Named(ElkCase.NOTHING)));
        ElkCase.Taxonomy taxonomy = elkCase.classTaxonomy().orElseThrow();
        assertEquals(Set.of(iri("A"), iri("B"), iri("D")), taxonomy.unsatisfiable());
        assertEquals(Set.of(iri("B"), iri("D")), taxonomy.equivalents().get(iri("A")));
        assertEquals(Set.of(ElkCase.THING), taxonomy.parents().get(iri("C")));
    }

    @Test
    @DisplayName("DataHasValue is a feature equal to the literal, typed as the reasoner reads it")
    void dataHasValue() throws Exception {
        ElkCase elkCase = ElkCase.read("x", PREFIXES + """
                Ontology(
                SubClassOf(:A DataHasValue(:P "3"^^xsd:integer))
                )""", Optional.empty(), Optional.empty());
        assertEquals(List.of(new ElkCase.HasValue(iri("P"), 3)), elkCase.necessary().get(iri("A")));
    }

    @Test
    @DisplayName("The corpus reads: every case is in scope or names what puts it out")
    void readsTheCorpus() throws Exception {
        Path corpus = Path.of("../../../ikm-reasoner/elk-reasoner/src/test/resources/test_input");
        assumeTrue(Files.isDirectory(corpus), "the ELK corpus is beside this module in the working set");
        int lifted = 0;
        Map<String, Integer> outOfScope = new TreeMap<>();
        for (ElkTestCaseImporter.CaseFiles files : ElkTestCaseImporter.read(corpus)) {
            if (files.taxonomy().isEmpty() && files.propertyTaxonomy().isEmpty()) {
                continue;
            }
            try {
                ElkCase.read(files.id(), files.owl(), files.taxonomy(), files.propertyTaxonomy());
                lifted++;
            } catch (ElkCase.OutOfScope e) {
                outOfScope.merge(e.construct(), 1, Integer::sum);
            }
        }
        System.out.println("Lifted " + lifted + "; out of scope " + outOfScope);
        assertTrue(lifted >= 30, "the in-scope classification cases still read; lifted " + lifted);
    }
}
