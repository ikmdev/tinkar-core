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
package dev.ikm.tinkar.entity.graph.adaptor.axiom;

import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.common.service.DiagnosticText;
import dev.ikm.tinkar.terms.ConceptFacade;

/**
 * Provide a standard means to connect implementation classes and terminology.
 *
 * TODO: Not sure if this class is redundant given the sealed logical axiom interfaces. Consider consolidation.
 */
public enum LogicalAxiomSemantic {
    AND(KernelConcept.AND, LogicalAxiom.Atom.Connective.And.class),
    CONCEPT(KernelConcept.CONCEPT_REFERENCE, LogicalAxiom.Atom.ConceptAxiom.class),
    DEFINITION_ROOT(KernelConcept.DEFINITION_ROOT, LogicalAxiom.Atom.DefinitionRoot.class),
    NECESSARY_SET(KernelConcept.NECESSARY_SET, LogicalAxiom.Atom.LogicalSet.NecessarySet.class),
    OR(KernelConcept.OR, LogicalAxiom.Atom.Connective.Or.class),
    PROPERTY_SEQUENCE_IMPLICATION(KernelConcept.PROPERTY_SEQUENCE_IMPLICATION, LogicalAxiom.Atom.PropertySequenceImplication.class),
    PROPERTY_SET(KernelConcept.PROPERTY_SET, LogicalAxiom.Atom.LogicalSet.PropertySet.class),
    DATA_PROPERTY_SET(KernelConcept.DATA_PROPERTY_SET, LogicalAxiom.Atom.LogicalSet.DataPropertySet.class),
    INTERVAL_PROPERTY_SET(KernelConcept.INTERVAL_PROPERTY_SET, LogicalAxiom.Atom.LogicalSet.IntervalPropertySet.class),
    ROLE(KernelConcept.ROLE, LogicalAxiom.Atom.TypedAtom.Role.class),
    INTERVAL_ROLE(KernelConcept.INTERVAL_ROLE, LogicalAxiom.Atom.TypedAtom.IntervalRole.class),
    SUFFICIENT_SET(KernelConcept.SUFFICIENT_SET, LogicalAxiom.Atom.LogicalSet.SufficientSet.class),
    DISJOINT_WITH(KernelConcept.DISJOINT_WITH, LogicalAxiom.Atom.DisjointWithAxiom.class),
    FEATURE(KernelConcept.FEATURE, LogicalAxiom.Atom.TypedAtom.Feature.class),
    INCLUSION_SET(KernelConcept.INCLUSION_SET, LogicalAxiom.Atom.LogicalSet.InclusionSet.class);

    public final long nid;
    public final Class<? extends LogicalAxiom> axiomClass;

    LogicalAxiomSemantic(ConceptFacade meaningFacade, Class<? extends LogicalAxiom> axiom) {
        this.nid = meaningFacade.nid();
        this.axiomClass = axiom;
    }

    public static LogicalAxiomSemantic get(ConceptFacade meaningFacade) {
        return get(meaningFacade.nid());
    }

    public static LogicalAxiomSemantic get(long meaningNid) {
        for (LogicalAxiomSemantic meaning : LogicalAxiomSemantic.values()) {
            if (meaning.nid == meaningNid) {
                return meaning;
            }
        }
        throw new IllegalStateException("No meaning for: " + DiagnosticText.component(meaningNid));
    }

}
