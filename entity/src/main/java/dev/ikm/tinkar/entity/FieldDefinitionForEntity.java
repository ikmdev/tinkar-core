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
package dev.ikm.tinkar.entity;

import dev.ikm.tinkar.component.FieldDefinition;

public interface FieldDefinitionForEntity extends FieldDefinition {

    /**
     * Underlying object type such as String or Integer.
     *
     * @return Concept designating the data type of the defined field.
     */
    default ConceptEntity dataType() {
        return Entity.getFast(dataTypeNid());
    }

    int dataTypeNid();

    /**
     * How this field is intended to be used. The objective to be reached; a target; an aim; a goal.
     * e.g. The purpose of an identifier may be "globally unique identification"
     * <br/>
     * Meaning is the symbolic value of something while purpose is an objective to be reached;
     * a target; an aim; a goal.
     * <br/>
     *
     * @return Concept designating the purpose of the defined field.
     */
    default ConceptEntity purpose() {
        return Entity.getFast(purposeNid());
    }

    int purposeNid();

    /**
     * The meaning of this field. Maybe it is the "SNOMED code" in a mapping.
     * This concept should be used to present to the user what this field "means" so they
     * can interpret what this field represents in user interfaces and similar.
     * <br/>
     * Meaning is the symbolic value of something while purpose is an objective to be reached;
     * a target; an aim; a goal.
     * <br/>
     *
     * @return Concept designating the meaning (symbolic value) of this field.
     */
    default ConceptEntity meaning() {
        return Entity.getFast(meaningNid());
    }

    int meaningNid();

    /**
     * The index of this field in the entity (patttern and semantic).
     * @return field index
     */
    int indexInPattern();
}
