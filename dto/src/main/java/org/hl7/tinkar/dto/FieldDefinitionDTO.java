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

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.component.FieldDefinition;
import org.hl7.tinkar.dto.binary.*;
import org.hl7.tinkar.dto.json.ComponentFieldForJson;
import org.hl7.tinkar.dto.json.JSONObject;
import org.hl7.tinkar.dto.json.JsonChronologyUnmarshaler;
import org.hl7.tinkar.dto.json.JsonMarshalable;

import java.io.Writer;
import java.util.UUID;

public record FieldDefinitionDTO(ImmutableList<UUID> dataTypeUuids,
                                 ImmutableList<UUID>  purposeUuids,
                                 ImmutableList<UUID> identityUuids)
        implements FieldDefinition, JsonMarshalable, Marshalable {

    private static final int localMarshalVersion = 3;


    public static FieldDefinitionDTO make(FieldDefinition fieldDefinition) {
        return new FieldDefinitionDTO(fieldDefinition.getDataType().componentUuids(),
                fieldDefinition.getPurpose().componentUuids(), fieldDefinition.getIdentity().componentUuids());
    }

    @Override
    public Concept getDataType() {
        return new ConceptDTO(dataTypeUuids);
    }

    @Override
    public Concept getPurpose() {
        return new ConceptDTO(purposeUuids);
    }

    @Override
    public Concept getIdentity() {
        return new ConceptDTO(identityUuids);
    }

    @Override
    public void jsonMarshal(Writer writer) {
        final JSONObject json = new JSONObject();
        json.put(ComponentFieldForJson.CLASS, this.getClass().getCanonicalName());
        json.put(ComponentFieldForJson.DATATYPE_UUIDS, dataTypeUuids);
        json.put(ComponentFieldForJson.PURPOSE_UUIDS, purposeUuids);
        json.put(ComponentFieldForJson.USE_UUIDS, identityUuids);
        json.writeJSONString(writer);
    }

    @JsonChronologyUnmarshaler
    public static FieldDefinitionDTO make(JSONObject jsonObject) {
        return new FieldDefinitionDTO(jsonObject.asImmutableUuidList(ComponentFieldForJson.DATATYPE_UUIDS),
                jsonObject.asImmutableUuidList(ComponentFieldForJson.PURPOSE_UUIDS),
                jsonObject.asImmutableUuidList(ComponentFieldForJson.USE_UUIDS));
    }

    @Unmarshaler
    public static FieldDefinitionDTO make(TinkarInput in) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            return new FieldDefinitionDTO(
                    in.readImmutableUuidList(),
                    in.readImmutableUuidList(),
                    in.readImmutableUuidList());
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + in.getTinkerFormatVersion());
        }
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        out.writeUuidList(dataTypeUuids);
        out.writeUuidList(purposeUuids);
        out.writeUuidList(identityUuids);
    }
}
