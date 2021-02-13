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
package org.hl7.tinkar.lombok.dto;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Accessors;
import org.hl7.tinkar.common.util.id.PublicId;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.component.FieldDefinition;
import org.hl7.tinkar.lombok.dto.binary.*;
import org.hl7.tinkar.lombok.dto.json.JSONObject;
import org.hl7.tinkar.lombok.dto.json.JsonMarshalable;
import org.hl7.tinkar.lombok.dto.json.ComponentFieldForJson;
import org.hl7.tinkar.lombok.dto.json.JsonChronologyUnmarshaler;

import java.io.Writer;

@Value
@Accessors(fluent = true)
public class FieldDefinitionDTO
        implements FieldDefinition, JsonMarshalable, Marshalable {

    private static final int localMarshalVersion = 3;

    @NonNull private final PublicId dataTypePublicId;
    @NonNull private final PublicId purposePublicId;
    @NonNull private final PublicId meaningPublicId;


    public static FieldDefinitionDTO make(FieldDefinition fieldDefinition) {
        return new FieldDefinitionDTO(fieldDefinition.dataType().publicId(),
                fieldDefinition.purpose().publicId(), fieldDefinition.meaning().publicId());
    }

    @Override
    public Concept dataType() {
        return new ConceptDTO(dataTypePublicId);
    }

    @Override
    public Concept purpose() {
        return new ConceptDTO(purposePublicId);
    }

    @Override
    public Concept meaning() {
        return new ConceptDTO(meaningPublicId);
    }

    @Override
    public void jsonMarshal(Writer writer) {
        final JSONObject json = new JSONObject();
        json.put(ComponentFieldForJson.CLASS, this.getClass().getCanonicalName());
        json.put(ComponentFieldForJson.DATATYPE_PUBLIC_ID, dataTypePublicId);
        json.put(ComponentFieldForJson.PURPOSE_PUBLIC_ID, purposePublicId);
        json.put(ComponentFieldForJson.MEANING_PUBLIC_ID, meaningPublicId);
        json.writeJSONString(writer);
    }

    @JsonChronologyUnmarshaler
    public static FieldDefinitionDTO make(JSONObject jsonObject) {
        return new FieldDefinitionDTO(jsonObject.asPublicId(ComponentFieldForJson.DATATYPE_PUBLIC_ID),
                jsonObject.asPublicId(ComponentFieldForJson.PURPOSE_PUBLIC_ID),
                jsonObject.asPublicId(ComponentFieldForJson.MEANING_PUBLIC_ID));
    }

    @Unmarshaler
    public static FieldDefinitionDTO make(TinkarInput in) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            return new FieldDefinitionDTO(
                    in.getPublicId(),
                    in.getPublicId(),
                    in.getPublicId());
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + in.getTinkerFormatVersion());
        }
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        out.putPublicId(dataTypePublicId);
        out.putPublicId(purposePublicId);
        out.putPublicId(meaningPublicId);
    }
}
