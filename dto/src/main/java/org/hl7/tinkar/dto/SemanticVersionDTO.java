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
 */
package org.hl7.tinkar.dto;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.time.Instant;
import java.util.UUID;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.component.*;
import org.hl7.tinkar.dto.binary.*;
import org.hl7.tinkar.dto.json.*;

import static org.hl7.tinkar.dto.json.ComponentFieldForJson.FIELDS;

/**
 *
 * @author kec
 */
public record SemanticVersionDTO(ImmutableList<UUID> componentUuids,
                                 ImmutableList<UUID> definitionForSemanticUuids,
                                 ImmutableList<UUID> referencedComponentUuids,
                                 StampDTO stampDTO, ImmutableList<Object> fields)
        implements SemanticVersion, JsonMarshalable, Marshalable {

    private static final int localMarshalVersion = 3;

    public SemanticVersionDTO(ImmutableList<UUID> componentUuids, DefinitionForSemantic definitionForSemantic,
                              Component referencedComponent, Stamp stamp, ImmutableList<Object> fields) {
        this(componentUuids,
                definitionForSemantic.componentUuids(),
                referencedComponent.componentUuids(),
                StampDTO.make(stamp), fields);
    }

    public static SemanticVersionDTO make(SemanticVersion semanticVersion) {
        MutableList<Object> convertedFields = Lists.mutable.empty();
        semanticVersion.fields().forEach(objectToConvert -> {
            if (objectToConvert instanceof Concept concept) {
                convertedFields.add(new ConceptDTO(concept.componentUuids()));
            } else if (objectToConvert instanceof DefinitionForSemantic definitionForSemantic) {
                convertedFields.add(new DefinitionForSemanticDTO(definitionForSemantic.componentUuids()));
            } else if (objectToConvert instanceof Semantic semantic) {
                convertedFields.add(new SemanticDTO(semantic.componentUuids(), semantic.definitionForSemantic(),
                        semantic.referencedComponent()));
            } else if (objectToConvert instanceof Component component) {
                convertedFields.add(new ComponentDTO(component.componentUuids()));
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
                throw new UnsupportedOperationException("Can't convert:\n  " + objectToConvert + "\nin\n  " + semanticVersion);
            }
        });
        return new SemanticVersionDTO(semanticVersion.componentUuids(),
                semanticVersion.definitionForSemantic(),
                semanticVersion.referencedComponent(),
                StampDTO.make(semanticVersion.stamp()), convertedFields.toImmutable());
    }

    @Override
    public Component referencedComponent() {
        return new ComponentDTO(referencedComponentUuids);
    }

    @Override
    public DefinitionForSemantic definitionForSemantic() {
        return new DefinitionForSemanticDTO(definitionForSemanticUuids);
    }

    @Override
    public void jsonMarshal(Writer writer) {
        final JSONObject json = new JSONObject();
        json.put(ComponentFieldForJson.STAMP, stampDTO);
        json.put(FIELDS, fields);
        json.writeJSONString(writer);
    }

    @JsonSemanticVersionUnmarshaler
    public static SemanticVersionDTO make(JSONObject jsonObject,
                                          ImmutableList<UUID> componentUuids,
                                          ImmutableList<UUID> definitionForSemanticUuids,
                                          ImmutableList<UUID> referencedComponentUuids) {
        JSONObject jsonStampObject = (JSONObject) jsonObject.get(ComponentFieldForJson.STAMP);
        return new SemanticVersionDTO(componentUuids,
                definitionForSemanticUuids,
                referencedComponentUuids,
                StampDTO.make(jsonStampObject),
                jsonObject.asImmutableObjectList(FIELDS));
    }

    @SemanticVersionUnmarshaler
    public static SemanticVersionDTO make(TinkarInput in,
                                          ImmutableList<UUID> componentUuids,
                                          ImmutableList<UUID> definitionForSemanticUuids,
                                          ImmutableList<UUID> referencedComponentUuids) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            return new SemanticVersionDTO(componentUuids,
                    definitionForSemanticUuids,
                    referencedComponentUuids,
                    StampDTO.make(in),
                    in.readImmutableObjectList());
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + in.getTinkerFormatVersion());
        }
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        stampDTO.marshal(out);
        out.writeObjectList(fields);
    }

    @Override
    public Stamp stamp() {
        return stampDTO;
    }
}
