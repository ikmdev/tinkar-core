/*
 * Copyright 2020 kec.
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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.component.DefinitionForSemanticVersion;
import org.hl7.tinkar.component.FieldDefinition;
import org.hl7.tinkar.component.Stamp;
import org.hl7.tinkar.dto.binary.*;
import org.hl7.tinkar.dto.json.ComponentFieldForJson;
import org.hl7.tinkar.dto.json.JSONObject;
import org.hl7.tinkar.dto.json.JsonMarshalable;
import org.hl7.tinkar.dto.json.JsonVersionUnmarshaler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.UUID;

import static org.hl7.tinkar.dto.json.ComponentFieldForJson.FIELD_DEFINITIONS;
import static org.hl7.tinkar.dto.json.ComponentFieldForJson.REFERENCED_COMPONENT_PURPOSE_UUIDS;

/**
 *
 * @author kec
 */
public record DefinitionForSemanticVersionDTO(ImmutableList<UUID> componentUuids,
                                              StampDTO stampDTO, ImmutableList<UUID> referencedComponentPurposeUuids,
                                              ImmutableList<FieldDefinitionDTO> fieldDefinitionDTOS)
        implements DefinitionForSemanticVersion, JsonMarshalable, Marshalable {

    private static final int localMarshalVersion = 3;

    public static DefinitionForSemanticVersionDTO make(DefinitionForSemanticVersion definitionForSemanticVersion) {

        MutableList<FieldDefinitionDTO> fields = Lists.mutable.ofInitialCapacity(definitionForSemanticVersion.fieldDefinitions().size());
        for (FieldDefinition fieldDefinition : definitionForSemanticVersion.fieldDefinitions()) {
            fields.add(FieldDefinitionDTO.make(fieldDefinition));
        }

        return new DefinitionForSemanticVersionDTO(
                definitionForSemanticVersion.componentUuids(),
                StampDTO.make(definitionForSemanticVersion.stamp()),
                definitionForSemanticVersion.referencedComponentPurpose().componentUuids(),
                fields.toImmutable());
    }
    @Override
    public Concept referencedComponentPurpose() {
        return new ConceptDTO(referencedComponentPurposeUuids);
    }

    @Override
    public Stamp stamp() {
        return stampDTO;
    }

    @Override
    public ImmutableList<FieldDefinition> fieldDefinitions() {
        return fieldDefinitionDTOS.collect(fieldDefinitionDTO -> (FieldDefinition) fieldDefinitionDTO);
    }

    /**
     * Marshal method for DefinitionForSemanticVersionDTO using JSON
     * @param writer
     */
    @Override
    public void jsonMarshal(Writer writer) {
        final JSONObject json = new JSONObject();
        json.put(ComponentFieldForJson.STAMP, stampDTO);
        json.put(REFERENCED_COMPONENT_PURPOSE_UUIDS, referencedComponentPurposeUuids);
        json.put(FIELD_DEFINITIONS, fieldDefinitionDTOS);
        json.writeJSONString(writer);
    }

    /**
     * Unmarshal method for DefinitionForSemanticVersionDTO using JSON
     * @param jsonObject
     * @param componentUuids
     * @return
     */
    @JsonVersionUnmarshaler
    public static DefinitionForSemanticVersionDTO make(JSONObject jsonObject, ImmutableList<UUID> componentUuids) {
        return new DefinitionForSemanticVersionDTO(componentUuids,
                StampDTO.make((JSONObject) jsonObject.get(ComponentFieldForJson.STAMP)),
                jsonObject.asImmutableUuidList(REFERENCED_COMPONENT_PURPOSE_UUIDS),
                jsonObject.asFieldDefinitionList(FIELD_DEFINITIONS));
    }

    /**
     * Unmarshal method for DefinitionForSemanticVersionDTO
     * @param in
     * @param componentUuids
     * @return
     */
    @VersionUnmarshaler
    public static DefinitionForSemanticVersionDTO make(TinkarInput in, ImmutableList<UUID> componentUuids) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            return new DefinitionForSemanticVersionDTO(componentUuids,
                    StampDTO.make(in),
                    in.readImmutableUuidList(),
                    in.readFieldDefinitionList());
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + in.getTinkerFormatVersion());
        }
    }

    /**
     * Marshal method for DefinitionForSemanticVersionDTO
     * @param out
     */
    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        stampDTO.marshal(out);
        out.writeUuidList(referencedComponentPurposeUuids);
        out.writeFieldDefinitionList(fieldDefinitionDTOS);
    }
}
