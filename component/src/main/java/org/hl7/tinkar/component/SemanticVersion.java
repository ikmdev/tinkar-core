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
import org.hl7.tinkar.dto.IdentifiedThingDTO;
import org.hl7.tinkar.dto.SemanticVersionDTO;

/**
 *
 * @author kec
 */
public interface SemanticVersion extends Version, Semantic {

    Object[] getFields();

    default SemanticVersionDTO toChangeSetThing() {
        Object[] convertedFields = new Object[getFields().length];
        for (int i = 0; i < convertedFields.length; i++) {
            Object objectToConvert = getFields()[i];
            if (objectToConvert instanceof IdentifiedThing identifiedThing) {
                convertedFields[i] = new IdentifiedThingDTO(identifiedThing.getComponentUuids());
            } else if (objectToConvert instanceof Number number) {
                convertedFields[i] = number;
            } else if (objectToConvert instanceof String string) {
                convertedFields[i] = string;
            } else {
                throw new UnsupportedOperationException("Can't convert:\n  " + objectToConvert + "\nin\n  " + this);
            }
        }
        return new SemanticVersionDTO(getComponentUuids(),
                getReferencedComponent().getComponentUuids(),
                getDefinitionForSemantic().getComponentUuids(),
                getStamp().toChangeSetThing(), Lists.immutable.of(convertedFields));
    }

}
