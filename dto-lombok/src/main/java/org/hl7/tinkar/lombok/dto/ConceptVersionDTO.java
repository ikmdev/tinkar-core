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
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.util.id.PublicId;
import org.hl7.tinkar.component.ConceptVersion;
import org.hl7.tinkar.lombok.dto.binary.*;
import org.hl7.tinkar.lombok.dto.json.*;

import java.io.Writer;
import java.util.UUID;


@Value
@Accessors(fluent = true)
@ToString(callSuper = true)
public class ConceptVersionDTO extends VersionDTO
        implements ConceptVersion, DTO, JsonMarshalable, Marshalable {

    private static final int localMarshalVersion = 3;

    public ConceptVersionDTO(@NonNull PublicId componentPublicId, @NonNull StampDTO stamp) {
        super(componentPublicId, stamp);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConceptVersionDTO)) return false;
        ConceptVersionDTO that = (ConceptVersionDTO) o;
        return super.equals(that);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public static ConceptVersionDTO make(ConceptVersion conceptVersion) {
        return new ConceptVersionDTO(conceptVersion.publicId(), StampDTO.make(conceptVersion.stamp()));
    }

    /**
     * Marshaler for ConceptVersionDTO using JSON
     * @param writer
     */
    @JsonMarshaler
    @Override
    public void jsonMarshal(Writer writer) {
        final JSONObject json = new JSONObject();
        json.put(ComponentFieldForJson.STAMP, stamp);
        json.writeJSONString(writer);
    }

    /**
     * Version unmarshaler for ConceptVersionDTO using JSON
     * @param jsonObject
     * @param publicId
     * @return
     */
    @JsonVersionUnmarshaler
    public static ConceptVersionDTO make(JSONObject jsonObject, PublicId publicId) {
        return new ConceptVersionDTO(
                publicId,
                StampDTO.make((JSONObject) jsonObject.get(ComponentFieldForJson.STAMP)));
    }

    /**
     * Version unmarshaler for ConceptVersionDTO.
     * @param in
     * @param publicId
     * @return new instance of ConceptVersionDTO created from the input.
     */
    @VersionUnmarshaler
    public static ConceptVersionDTO make(TinkarInput in, PublicId publicId) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            return new ConceptVersionDTO(publicId, StampDTO.make(in));
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + in.getTinkerFormatVersion());
        }
    }

    /**
     * Version marshaler for ConceptVersionDTO
     * @param out
     */
    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        // note that componentUuids are not written redundantly here,
        // they are written with the ConceptChronologyDTO...
        stamp.marshal(out);
    }
}
