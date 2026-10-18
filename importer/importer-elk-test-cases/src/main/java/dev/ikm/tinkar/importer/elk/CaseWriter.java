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

import dev.ikm.tinkar.common.id.LongIds;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiom;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpressionBuilder;
import dev.ikm.tinkar.importer.StoreWriter;
import dev.ikm.tinkar.importer.elk.ElkCase.And;
import dev.ikm.tinkar.importer.elk.ElkCase.Expr;
import dev.ikm.tinkar.importer.elk.ElkCase.HasValue;
import dev.ikm.tinkar.importer.elk.ElkCase.Named;
import dev.ikm.tinkar.importer.elk.ElkCase.Some;
import dev.ikm.tinkar.importer.elk.ElkCase.Taxonomy;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Writes lifted ELK cases into the store.
 * <p>
 * Every case is its own module, {@code <case> (ELK test case module)}, marked by an identifier
 * semantic whose source is {@link ReasonerTestKb#CASE_IDENTIFIER_SOURCE} and whose value is the
 * case's path; its versions are written under it. In it:
 * <ul>
 *   <li>the case's top, {@code Thing (<case>)}, a kind of {@link ReasonerTestKb#ROOT}, and, when the
 *   case has object properties, its top role, {@code topObjectProperty (<case>)}, a sub-property of
 *   {@code Role type};</li>
 *   <li>each class, {@code <name> (<case>)}: necessarily a kind of the case's top and of its
 *   {@code SubClassOf} superclasses, with a sufficient set per equivalent class expression and an
 *   inclusion set per class expression it subsumes;</li>
 *   <li>each object property, a sub-property of the top role and its stated super-properties,
 *   transitive and reflexive as stated, with each property chain implying it; each data property
 *   with its super-properties;</li>
 *   <li>the expected results: for each entity of an expected taxonomy, an inferred navigation
 *   semantic with its expected children and parents, and an inferred equivalence semantic when it
 *   has equivalents — under the identities the reasoner writes its own results under, so a
 *   classification in Komet versions them, and the case module still holds what was expected.</li>
 * </ul>
 * Identities are type 5 UUIDs in the namespace, from the case's path and each entity's IRI, so a
 * second lift writes nothing new.
 */
final class CaseWriter {

    /** The reasoner test knowledge base's namespace. */
    static final String DEFAULT_NAMESPACE = ReasonerTestKb.NAMESPACE;

    private static final EntityProxy.Concept MODULE_SOLOR =
            EntityProxy.Concept.make("Module (SOLOR)", PublicIds.of("40d1c869-b509-32f8-b735-836eac577a67"));
    private static final EntityProxy.Concept IDENTIFIER_SOURCE =
            EntityProxy.Concept.make("Identifier Source", PublicIds.of("5a87935c-d654-548f-82a2-0c06e3801162"));

    private final StoreWriter writer;
    private final UUID namespace;
    private final long time;
    private final ConceptFacade author;
    private boolean sharedWritten;

    CaseWriter(StoreWriter writer, UUID namespace, long time) {
        this.writer = writer;
        this.namespace = namespace;
        this.time = time;
        this.author = EntityProxy.Concept.make(PublicIds.of(shared("author")));
    }

    private UUID shared(String key) {
        return UuidT5Generator.get(namespace, "shared|" + key);
    }

    /** The knowledge base's own components, in its own module: written before the first case. */
    private void writeShared() {
        if (sharedWritten) {
            return;
        }
        sharedWritten = true;
        ConceptFacade module = EntityProxy.Concept.make(PublicIds.of(shared("module")));
        Stamp stamp = new ActiveStamp(time, author, module, KernelConcept.DEVELOPMENT_PATH);
        concept(stamp, shared("module"), "Reasoner test KB module (ReasonerTestKb)", "Reasoner test KB module",
                MODULE_SOLOR.publicId());
        concept(stamp, shared("author"), "ELK test corpus (ReasonerTestKb)", "ELK test corpus",
                KernelConcept.USER.publicId());
        concept(stamp, shared("root"), "ELK reasoner test cases (ReasonerTestKb)", "ELK reasoner test cases",
                KernelConcept.ROOT_VERTEX.publicId());
        concept(stamp, shared("case identifier"), "ELK test case (ReasonerTestKb)", "ELK test case",
                IDENTIFIER_SOURCE.publicId());
    }

    void write(ElkCase elkCase) {
        writeShared();
        String id = elkCase.id();
        UUID moduleUuid = caseUuid(id, "module");
        ConceptFacade module = EntityProxy.Concept.make(PublicIds.of(moduleUuid));
        Stamp stamp = new ActiveStamp(time, author, module, KernelConcept.DEVELOPMENT_PATH);

        long moduleNid = concept(stamp, moduleUuid, id + " (ELK test case module)", id, MODULE_SOLOR.publicId());
        writer.semantic(stamp, PublicIds.of(UuidT5Generator.get(moduleUuid, "case identifier")),
                KernelPattern.IDENTIFIER_PATTERN, moduleNid,
                Lists.immutable.of(EntityProxy.Concept.make(PublicIds.of(shared("case identifier"))), id));

        Map<String, Long> nids = new HashMap<>();
        nids.put(ElkCase.NOTHING, KernelConcept.BOTTOM.nid());
        UUID topUuid = caseUuid(id, "top");
        nids.put(ElkCase.THING, concept(stamp, topUuid, "Thing (" + id + ")", "Thing", PublicIds.of(shared("root"))));
        for (String iri : elkCase.classes()) {
            nids.put(iri, describedConcept(stamp, id, "class:" + iri, iri));
        }
        if (!elkCase.objectProperties().isEmpty()) {
            UUID roleTopUuid = caseUuid(id, "role top");
            long roleTopNid = describedConcept(stamp, id, "role top", ElkCase.TOP_OBJECT_PROPERTY);
            nids.put(ElkCase.TOP_OBJECT_PROPERTY, roleTopNid);
            statedAxioms(stamp, roleTopUuid, roleTopNid,
                    leb -> leb.PropertySet(leb.And(leb.ConceptAxiom(KernelConcept.ROLE_TYPE))));
        }
        for (String iri : elkCase.objectProperties()) {
            nids.put(iri, describedConcept(stamp, id, "role:" + iri, iri));
        }
        for (String iri : elkCase.dataProperties()) {
            nids.put(iri, describedConcept(stamp, id, "data:" + iri, iri));
        }

        for (String iri : elkCase.classes()) {
            statedAxioms(stamp, caseUuid(id, "class:" + iri), nids.get(iri), leb -> {
                List<LogicalAxiom.Atom> necessary = new ArrayList<>();
                necessary.add(leb.ConceptAxiom(concept(nids, ElkCase.THING)));
                for (Expr conjunct : elkCase.necessary().getOrDefault(iri, List.of())) {
                    necessary.add(atom(leb, nids, conjunct));
                }
                for (String disjoint : elkCase.disjoint().getOrDefault(iri, Set.of())) {
                    necessary.add(leb.DisjointWithAxiom(concept(nids, disjoint)));
                }
                leb.NecessarySet(leb.And(necessary.toArray(LogicalAxiom.Atom[]::new)));
                for (List<Expr> set : elkCase.sufficient().getOrDefault(iri, List.of())) {
                    leb.SufficientSet(leb.And(atoms(leb, nids, set)));
                }
                for (List<Expr> set : elkCase.inclusions().getOrDefault(iri, List.of())) {
                    leb.InclusionSet(leb.And(atoms(leb, nids, set)));
                }
            });
        }
        for (String iri : elkCase.objectProperties()) {
            statedAxioms(stamp, caseUuid(id, "role:" + iri), nids.get(iri), leb -> {
                List<LogicalAxiom.Atom> elements = new ArrayList<>();
                elements.add(leb.ConceptAxiom(concept(nids, ElkCase.TOP_OBJECT_PROPERTY)));
                for (String sup : elkCase.superProperties().getOrDefault(iri, Set.of())) {
                    elements.add(leb.ConceptAxiom(concept(nids, sup)));
                }
                if (elkCase.transitive().contains(iri)) {
                    elements.add(leb.ConceptAxiom(KernelConcept.TRANSITIVE_PROPERTY));
                }
                if (elkCase.reflexive().contains(iri)) {
                    elements.add(leb.ConceptAxiom(KernelConcept.REFLEXIVE_PROPERTY));
                }
                for (List<String> chain : elkCase.chains().getOrDefault(iri, List.of())) {
                    ImmutableList<ConceptFacade> sequence = Lists.immutable.fromStream(
                            chain.stream().map(link -> (ConceptFacade) concept(nids, link)));
                    elements.add(leb.PropertySequenceImplicationAxiom(sequence, concept(nids, iri)));
                }
                leb.PropertySet(leb.And(elements.toArray(LogicalAxiom.Atom[]::new)));
            });
        }
        for (String iri : elkCase.dataProperties()) {
            Set<String> supers = elkCase.superDataProperties().getOrDefault(iri, Set.of());
            if (!supers.isEmpty()) {
                statedAxioms(stamp, caseUuid(id, "data:" + iri), nids.get(iri), leb -> leb.DataPropertySet(leb.And(
                        supers.stream().map(sup -> leb.ConceptAxiom(concept(nids, sup))).toArray(LogicalAxiom.Atom[]::new))));
            }
        }

        elkCase.classTaxonomy().ifPresent(taxonomy -> expected(stamp, taxonomy, nids, ElkCase.THING,
                PublicIds.of(shared("root"))));
        elkCase.propertyTaxonomy().ifPresent(taxonomy -> expected(stamp, taxonomy, nids, ElkCase.TOP_OBJECT_PROPERTY,
                KernelConcept.ROLE_TYPE.publicId()));
    }

    /**
     * Writes a taxonomy's expected navigation and equivalence semantics, the top's included: its
     * parent is what the case's top is stated to be a kind of.
     */
    private void expected(Stamp stamp, Taxonomy taxonomy, Map<String, Long> nids, String top, PublicId topParent) {
        Map<String, Set<String>> parents = new LinkedHashMap<>(taxonomy.parents());
        Map<String, Set<String>> children = new LinkedHashMap<>();
        children.put(top, new LinkedHashSet<>());
        parents.forEach((child, childParents) -> {
            children.computeIfAbsent(child, _ -> new LinkedHashSet<>());
            childParents.forEach(parent -> children.computeIfAbsent(parent, _ -> new LinkedHashSet<>()).add(child));
        });
        long topParentNid = dev.ikm.tinkar.entity.EntityService.get().nidForConcept(topParent);
        for (Map.Entry<String, Set<String>> entry : children.entrySet()) {
            String iri = entry.getKey();
            long nid = nids.get(iri);
            // An unsatisfiable entity is placed where the bottom concept is stated, under the root.
            long[] parentNids = iri.equals(top) ? new long[]{topParentNid}
                    : taxonomy.unsatisfiable().contains(iri) ? new long[]{KernelConcept.ROOT_VERTEX.nid()}
                    : parents.get(iri).stream().mapToLong(nids::get).toArray();
            long[] childNids = entry.getValue().stream().mapToLong(nids::get).toArray();
            PublicId conceptId = dev.ikm.tinkar.common.service.PrimitiveData.publicId(nid);
            writer.semantic(stamp, PublicIds.of(UuidT5Generator.singleSemanticUuid(
                            KernelPattern.INFERRED_NAVIGATION_PATTERN.publicId(), conceptId)),
                    KernelPattern.INFERRED_NAVIGATION_PATTERN, nid,
                    Lists.immutable.of(LongIds.set.of(childNids), LongIds.set.of(parentNids)));
            Set<String> equivalents = taxonomy.equivalents().get(iri);
            if (taxonomy.unsatisfiable().contains(iri)) {
                // Equivalent to the bottom concept, as well as to the case's other unsatisfiable entities.
                equivalents = new LinkedHashSet<>(equivalents == null ? Set.of() : equivalents);
                equivalents.add(ElkCase.NOTHING);
            }
            if (equivalents != null) {
                writer.semantic(stamp, PublicIds.of(UuidT5Generator.singleSemanticUuid(
                                KernelPattern.INFERRED_EQUIVALENCE_PATTERN.publicId(), conceptId)),
                        KernelPattern.INFERRED_EQUIVALENCE_PATTERN, nid,
                        Lists.immutable.of(LongIds.set.of(equivalents.stream().mapToLong(nids::get).toArray())));
            }
        }
    }

    private LogicalAxiom.Atom[] atoms(LogicalExpressionBuilder leb, Map<String, Long> nids, List<Expr> exprs) {
        return exprs.stream().map(expr -> atom(leb, nids, expr)).toArray(LogicalAxiom.Atom[]::new);
    }

    private LogicalAxiom.Atom atom(LogicalExpressionBuilder leb, Map<String, Long> nids, Expr expr) {
        return switch (expr) {
            case Named named -> leb.ConceptAxiom(concept(nids, named.iri()));
            case Some some -> leb.SomeRole(concept(nids, some.property()), atom(leb, nids, some.filler()));
            case And and -> leb.And(atoms(leb, nids, and.conjuncts()));
            case HasValue hasValue -> leb.FeatureAxiom(concept(nids, hasValue.property()), KernelConcept.EQUAL_TO,
                    hasValue.value());
        };
    }

    private static EntityProxy.Concept concept(Map<String, Long> nids, String iri) {
        Long nid = nids.get(iri);
        if (nid == null) {
            throw new IllegalStateException("No concept for " + iri);
        }
        return EntityProxy.Concept.make(nid);
    }

    private UUID caseUuid(String caseId, String key) {
        return UuidT5Generator.get(namespace, caseId + "|" + key);
    }

    /** A case entity, named {@code <local name> (<case>)}; its axioms are written separately. */
    private long describedConcept(Stamp stamp, String caseId, String key, String iri) {
        UUID uuid = caseUuid(caseId, key);
        String name = ElkCase.localName(iri);
        long nid = writer.concept(stamp, PublicIds.of(uuid));
        describe(stamp, uuid, nid, name + " (" + caseId + ")", name);
        return nid;
    }

    /** A concept with its names and one stated parent. */
    private long concept(Stamp stamp, UUID uuid, String fullyQualifiedName, String name, PublicId parent) {
        long nid = writer.concept(stamp, PublicIds.of(uuid));
        describe(stamp, uuid, nid, fullyQualifiedName, name);
        writer.statedParent(stamp, PublicIds.of(uuid), parent);
        return nid;
    }

    private void describe(Stamp stamp, UUID uuid, long nid, String fullyQualifiedName, String name) {
        writer.describe(stamp, nid, UuidT5Generator.get(uuid, "fqn"), fullyQualifiedName,
                KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE);
        writer.describe(stamp, nid, UuidT5Generator.get(uuid, "name"), name,
                KernelConcept.REGULAR_NAME_DESCRIPTION_TYPE);
    }

    /** Writes a concept's stated axioms, built with vertex identities derived from its own. */
    private void statedAxioms(Stamp stamp, UUID conceptUuid, long conceptNid, Consumer<LogicalExpressionBuilder> sets) {
        int[] ordinal = {0};
        LogicalExpressionBuilder builder = new LogicalExpressionBuilder(
                UuidT5Generator.get(conceptUuid, "definition root"),
                () -> UuidT5Generator.get(conceptUuid, "axiom vertex " + ordinal[0]++));
        sets.accept(builder);
        DiTreeEntity tree = (DiTreeEntity) builder.build().sourceGraph();
        writer.semantic(stamp, PublicIds.of(UuidT5Generator.get(conceptUuid, "stated axioms")),
                KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN, conceptNid, Lists.immutable.of(tree));
    }
}
