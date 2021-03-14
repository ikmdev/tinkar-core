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
import lombok.experimental.SuperBuilder;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.component.Stamp;
import org.hl7.tinkar.lombok.dto.binary.*;
import org.hl7.tinkar.lombok.dto.json.JSONObject;
import org.hl7.tinkar.lombok.dto.json.JsonMarshalable;
import org.hl7.tinkar.lombok.dto.json.ComponentFieldForJson;
import org.hl7.tinkar.lombok.dto.json.JsonChronologyUnmarshaler;

import java.io.Writer;
import java.time.Instant;
import java.util.Objects;

import static org.hl7.tinkar.lombok.dto.json.ComponentFieldForJson.*;

@Value
@Accessors(fluent = true)
@ToString(callSuper = true)
@SuperBuilder
public class StampDTO
    extends ComponentDTO
        implements JsonMarshalable, Marshalable, Stamp, DTO {
    private static final int localMarshalVersion = 3;

    @NonNull private final PublicId statusPublicId;
    @NonNull private final long time;
    @NonNull private final PublicId authorPublicId;
    @NonNull private final PublicId modulePublicId;
    @NonNull private final PublicId pathPublicId;

    public StampDTO(@NonNull PublicId publicId,
                    @NonNull PublicId statusPublicId,
                    @NonNull long time,
                    @NonNull PublicId authorPublicId,
                    @NonNull PublicId modulePublicId,
                    @NonNull PublicId pathPublicId) {
        super(publicId);
        this.statusPublicId = statusPublicId;
        this.time = time;
        this.authorPublicId = authorPublicId;
        this.modulePublicId = modulePublicId;
        this.pathPublicId = pathPublicId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StampDTO)) return false;
        if (!super.equals(o)) return false;
        StampDTO stampDTO = (StampDTO) o;
        return statusPublicId.equals(stampDTO.statusPublicId) &&
                time == stampDTO.time &&
                authorPublicId.equals(stampDTO.authorPublicId) &&
                modulePublicId.equals(stampDTO.modulePublicId) &&
                pathPublicId.equals(stampDTO.pathPublicId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), statusPublicId, time, authorPublicId, modulePublicId, pathPublicId);
    }

    public static StampDTO make(Stamp stamp) {
        return new StampDTO(stamp.publicId(),
                stamp.state().publicId(),
                stamp.time(),
                stamp.author().publicId(),
                stamp.module().publicId(),
                stamp.path().publicId());
    }

    @Override
    public Concept state() {
        return new ConceptDTO(statusPublicId);
    }

    @Override
    public long time() {
        return time;
    }

    @Override
    public Concept author() {
        return new ConceptDTO(authorPublicId);
    }

    @Override
    public Concept module() {
        return new ConceptDTO(modulePublicId);
    }

    @Override
    public Concept path() {
        return new ConceptDTO(pathPublicId);
    }

    @Override
    public void jsonMarshal(Writer writer) {
        final JSONObject json = new JSONObject();
        json.put(COMPONENT_PUBLIC_ID, publicId());
        json.put(STATUS_PUBLIC_ID, statusPublicId);
        json.put(TIME, time);
        json.put(ComponentFieldForJson.AUTHOR_PUBLIC_ID, authorPublicId);
        json.put(ComponentFieldForJson.MODULE_PUBLIC_ID, modulePublicId);
        json.put(ComponentFieldForJson.PATH_PUBLIC_ID, pathPublicId);
        json.writeJSONString(writer);
    }

    @JsonChronologyUnmarshaler
    public static StampDTO make(JSONObject jsonObject) {
        return new StampDTO(jsonObject.asPublicId(COMPONENT_PUBLIC_ID),
                jsonObject.asPublicId(STATUS_PUBLIC_ID),
                jsonObject.asLong(TIME),
                jsonObject.asPublicId(AUTHOR_PUBLIC_ID),
                jsonObject.asPublicId(MODULE_PUBLIC_ID),
                jsonObject.asPublicId(PATH_PUBLIC_ID));
    }

    @Unmarshaler
    public static StampDTO make(TinkarInput in) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            return new StampDTO(in.getPublicId(),
                    in.getPublicId(),
                    in.getLong(),
                    in.getPublicId(),
                    in.getPublicId(),
                    in.getPublicId());
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + localMarshalVersion);
        }
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        out.putPublicId(publicId());
        out.putPublicId(statusPublicId);
        out.putLong(time);
        out.putPublicId(authorPublicId);
        out.putPublicId(modulePublicId);
        out.putPublicId(pathPublicId);
    }

}
