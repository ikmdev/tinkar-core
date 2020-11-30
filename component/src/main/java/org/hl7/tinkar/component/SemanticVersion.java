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
import org.hl7.tinkar.dto.*;

import java.time.Instant;

/**
 *
 * @author kec
 */
public interface SemanticVersion extends Version, Semantic {

    ImmutableList<Object> fields();

    default SemanticVersionDTO toChangeSetThing() {
        MutableList<Object> convertedFields = Lists.mutable.empty();
        fields().forEach(objectToConvert -> {
            if (objectToConvert instanceof Concept concept) {
                convertedFields.add(new ConceptDTO(concept.componentUuids()));
            } else if (objectToConvert instanceof DefinitionForSemantic definitionForSemantic) {
                convertedFields.add(new DefinitionForSemanticDTO(definitionForSemantic.componentUuids()));
            } else if (objectToConvert instanceof Semantic semantic) {
                convertedFields.add(new SemanticDTO(semantic.componentUuids(), semantic.definitionForSemantic(),
                        semantic.referencedComponent()));
            } else if (objectToConvert instanceof IdentifiedThing identifiedThing) {
                convertedFields.add(new IdentifiedThingDTO(identifiedThing.componentUuids()));
            } else if (objectToConvert instanceof Number number) {
                if (number instanceof Long) {
                    convertedFields.add(number.intValue());
                } else if (number instanceof Double) {
                    convertedFields.add(number.floatValue());
                } else {
                    convertedFields.add(number);
                }
            } else if (objectToConvert instanceof String string) {
                convertedFields.add(string);
            } else if (objectToConvert instanceof Instant instant) {
                convertedFields.add(instant);
            } else {
                throw new UnsupportedOperationException("Can't convert:\n  " + objectToConvert + "\nin\n  " + this);
            }
        });
        return new SemanticVersionDTO(componentUuids(),
                definitionForSemantic(),
                referencedComponent(),
                stamp().toChangeSetThing(), convertedFields.toImmutable());
    }

}
