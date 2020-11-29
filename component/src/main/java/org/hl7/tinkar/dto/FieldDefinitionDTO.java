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
import java.util.UUID;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.binary.Marshalable;
import org.hl7.tinkar.binary.Marshaler;
import org.hl7.tinkar.binary.TinkarInput;
import org.hl7.tinkar.binary.TinkarOutput;
import org.hl7.tinkar.binary.Unmarshaler;
import org.hl7.tinkar.changeset.ChangeSetThing;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.component.FieldDefinition;
import org.hl7.tinkar.json.ComponentFieldForJson;
import org.hl7.tinkar.json.JSONObject;
import org.hl7.tinkar.json.JsonMarshalable;
import org.hl7.tinkar.json.JsonChronologyUnmarshaler;

public record FieldDefinitionDTO(ImmutableList<UUID> dataTypeUuids,
                                 ImmutableList<UUID>  purposeUuids,
                                 ImmutableList<UUID>  useUuids)
        implements FieldDefinition, ChangeSetThing, JsonMarshalable, Marshalable {

    private static final int marshalVersion = 1;

    @Override
    public void jsonMarshal(Writer writer) {
        final JSONObject json = new JSONObject();
        json.put(ComponentFieldForJson.CLASS, this.getClass().getCanonicalName());
        json.put(ComponentFieldForJson.DATATYPE_UUIDS, dataTypeUuids);
        json.put(ComponentFieldForJson.PURPOSE_UUIDS, purposeUuids);
        json.put(ComponentFieldForJson.USE_UUIDS, useUuids);
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
        try {
            int objectMarshalVersion = in.readInt();
            if (objectMarshalVersion == marshalVersion) {
                return new FieldDefinitionDTO(
                        in.readImmutableUuidList(),
                        in.readImmutableUuidList(),
                        in.readImmutableUuidList());
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
            out.writeUuidList(dataTypeUuids);
            out.writeUuidList(purposeUuids);
            out.writeUuidList(useUuids);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
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
    public Concept getUse() {
        return new ConceptDTO(useUuids);
    }
}
