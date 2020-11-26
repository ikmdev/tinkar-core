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
import java.io.Writer;
import java.util.UUID;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.binary.*;
import org.hl7.tinkar.component.DefinitionForSemantic;
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
                                              StampDTO stamp, ImmutableList<UUID> referencedComponentPurposeUuids,
                                              ImmutableList<FieldDefinitionDTO> fieldDefinitions)
        implements DefinitionForSemantic, ChangeSetThing, JsonMarshalable, Marshalable {

    private static final int marshalVersion = 1;

    @Override
    public ImmutableList<UUID> getComponentUuids() {
        return componentUuids;
    }

    @Override
    public void jsonMarshal(Writer writer) {
        final JSONObject json = new JSONObject();
        json.put(ComponentFieldForJson.STAMP, stamp);
        json.put(REFERENCED_COMPONENT_PURPOSE_UUIDS, referencedComponentPurposeUuids);
        json.put(FIELD_DEFINITIONS, fieldDefinitions);
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
            switch (objectMarshalVersion) {
                case marshalVersion -> {
                    return new DefinitionForSemanticVersionDTO(componentUuids,
                            StampDTO.make(in),
                            in.readImmutableUuidList(),
                            in.readFieldDefinitionList());
                }
                default ->
                    throw new UnsupportedOperationException("Unsupported version: " + objectMarshalVersion);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        try {
            out.writeInt(marshalVersion);
            stamp.marshal(out);
            out.writeUuidList(referencedComponentPurposeUuids);
            out.writeFieldDefinitionList(fieldDefinitions);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
