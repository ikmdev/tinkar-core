/*
 * Copyright 2020-2021 HL7.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */package org.hl7.tinkar.component;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.dto.DefinitionForSemanticVersionDTO;
import org.hl7.tinkar.dto.FieldDefinitionDTO;

/**
 *
 * @author kec
 */
public interface DefinitionForSemanticVersion extends Version, DefinitionForSemantic {

    ImmutableList<FieldDefinition> getFieldDefinitions();

    Concept getReferencedComponentPurpose();


    default DefinitionForSemanticVersionDTO toChangeSetThing() {

        MutableList<FieldDefinitionDTO> fields = Lists.mutable.ofInitialCapacity(getFieldDefinitions().size());
        for (FieldDefinition fieldDefinition : getFieldDefinitions()) {
            fields.add(fieldDefinition.toChangeSetThing());
        }

        return new DefinitionForSemanticVersionDTO(
                getComponentUuids(),
                getStamp().toChangeSetThing(),
                getReferencedComponentPurpose().getComponentUuids(),
                fields.toImmutable());
    }

}
