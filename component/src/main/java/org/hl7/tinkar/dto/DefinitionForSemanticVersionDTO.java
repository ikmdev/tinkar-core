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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.UUID;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.binary.*;
import org.hl7.tinkar.changeset.ChangeSetThing;
import org.hl7.tinkar.component.*;
import org.hl7.tinkar.json.ComponentFieldForJson;
import org.hl7.tinkar.json.JSONObject;
import org.hl7.tinkar.json.JsonMarshalable;
import org.hl7.tinkar.json.JsonVersionUnmarshaler;

import static org.hl7.tinkar.json.ComponentFieldForJson.FIELD_DEFINITIONS;
import static org.hl7.tinkar.json.ComponentFieldForJson.REFERENCED_COMPONENT_PURPOSE_UUIDS;

/**
 *
 * @author kec
 */
public record DefinitionForSemanticVersionDTO(ImmutableList<UUID> componentUuids,
                                              StampDTO stampDTO, ImmutableList<UUID> referencedComponentPurposeUuids,
                                              ImmutableList<FieldDefinitionDTO> fieldDefinitionDTOS)
        implements DefinitionForSemanticVersion, ChangeSetThing, JsonMarshalable, Marshalable {

    private static final int marshalVersion = 1;

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

    @Override
    public void jsonMarshal(Writer writer) {
        final JSONObject json = new JSONObject();
        json.put(ComponentFieldForJson.STAMP, stampDTO);
        json.put(REFERENCED_COMPONENT_PURPOSE_UUIDS, referencedComponentPurposeUuids);
        json.put(FIELD_DEFINITIONS, fieldDefinitionDTOS);
        json.writeJSONString(writer);
    }

    @JsonVersionUnmarshaler
    public static DefinitionForSemanticVersionDTO make(JSONObject jsonObject, ImmutableList<UUID> componentUuids) {
        return new DefinitionForSemanticVersionDTO(componentUuids,
                StampDTO.make((JSONObject) jsonObject.get(ComponentFieldForJson.STAMP)),
                jsonObject.asImmutableUuidList(REFERENCED_COMPONENT_PURPOSE_UUIDS),
                jsonObject.asFieldDefinitionList(FIELD_DEFINITIONS));
    }

    @VersionUnmarshaler
    public static DefinitionForSemanticVersionDTO make(TinkarInput in, ImmutableList<UUID> componentUuids) {
        try {
            int objectMarshalVersion = in.readInt();
            if (objectMarshalVersion == marshalVersion) {
                return new DefinitionForSemanticVersionDTO(componentUuids,
                        StampDTO.make(in),
                        in.readImmutableUuidList(),
                        in.readFieldDefinitionList());
            } else {
                throw new UnsupportedOperationException("Unsupported version: " + objectMarshalVersion);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        try {
            out.writeInt(marshalVersion);
            stampDTO.marshal(out);
            out.writeUuidList(referencedComponentPurposeUuids);
            out.writeFieldDefinitionList(fieldDefinitionDTOS);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
