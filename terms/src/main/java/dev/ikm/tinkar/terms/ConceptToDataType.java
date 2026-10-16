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
        if (KernelConcept.STRING.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.STRING;
        }
        if (KernelConcept.COMPONENT_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.IDENTIFIED_THING;
        }
        if (KernelConcept.COMPONENT_ID_SET_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.COMPONENT_ID_SET;
        }
        if (KernelConcept.COMPONENT_ID_LIST_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.COMPONENT_ID_LIST;
        }
        if (KernelConcept.DITREE_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.DITREE;
        }
        if (KernelConcept.DIGRAPH_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.DIGRAPH;
        }
        if (KernelConcept.CONCEPT_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.CONCEPT;
        }
        if (KernelConcept.SEMANTIC_FIELD_TYPE.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.SEMANTIC;
        }
        if (KernelConcept.INTEGER_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.INTEGER;
        }
        if (KernelConcept.FLOAT_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.FLOAT;
        }
        if (KernelConcept.BOOLEAN_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.BOOLEAN;
        }
        if (KernelConcept.BYTE_ARRAY_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.BYTE_ARRAY;
        }
        if (KernelConcept.ARRAY_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.OBJECT_ARRAY;
        }
        if (KernelConcept.INSTANT_LITERAL.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.INSTANT;
        }
        if (KernelConcept.LONG.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.LONG;
        }
        if (KernelConcept.DECIMAL_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.DECIMAL;
        }

        throw new UnsupportedOperationException("Can't handle: " + DiagnosticText.component(dataTypeConcept.publicId()));
    }
}
