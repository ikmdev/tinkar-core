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
package dev.ikm.tinkar.terms;

import dev.ikm.tinkar.common.service.DiagnosticText;
import dev.ikm.tinkar.component.Concept;
import dev.ikm.tinkar.component.FieldDataType;

public class ConceptToDataType {
    public static FieldDataType convert(Concept dataTypeConcept) {
        if (KernelTerm.STRING.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.STRING;
        }
        if (KernelTerm.COMPONENT_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.IDENTIFIED_THING;
        }
        if (KernelTerm.COMPONENT_ID_SET_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.COMPONENT_ID_SET;
        }
        if (KernelTerm.COMPONENT_ID_LIST_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.COMPONENT_ID_LIST;
        }
        if (KernelTerm.DITREE_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.DITREE;
        }
        if (KernelTerm.DIGRAPH_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.DIGRAPH;
        }
        if (KernelTerm.CONCEPT_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.CONCEPT;
        }
        if (KernelTerm.SEMANTIC_FIELD_TYPE.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.SEMANTIC;
        }
        if (KernelTerm.INTEGER_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.INTEGER;
        }
        if (KernelTerm.FLOAT_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.FLOAT;
        }
        if (KernelTerm.BOOLEAN_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.BOOLEAN;
        }
        if (KernelTerm.BYTE_ARRAY_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.BYTE_ARRAY;
        }
        if (KernelTerm.ARRAY_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.OBJECT_ARRAY;
        }
        if (KernelTerm.INSTANT_LITERAL.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.INSTANT;
        }
        if (KernelTerm.LONG.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.LONG;
        }
        if (KernelTerm.DECIMAL_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.DECIMAL;
        }

        throw new UnsupportedOperationException("Can't handle: " + DiagnosticText.component(dataTypeConcept.publicId()));
    }
}
