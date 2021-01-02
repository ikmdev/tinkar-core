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
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.component.Stamp;
import org.hl7.tinkar.lombok.dto.binary.*;
import org.hl7.tinkar.lombok.dto.json.ComponentFieldForJson;
import org.hl7.tinkar.lombok.dto.json.JSONObject;
import org.hl7.tinkar.lombok.dto.json.JsonChronologyUnmarshaler;
import org.hl7.tinkar.lombok.dto.json.JsonMarshalable;

import java.io.Writer;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static org.hl7.tinkar.lombok.dto.json.ComponentFieldForJson.*;

@Value
@Accessors(fluent = true)
@ToString(callSuper = true)
public class StampDTO
    extends ComponentDTO
        implements JsonMarshalable, Marshalable, Stamp, DTO {
    private static final int localMarshalVersion = 3;

    @NonNull private final ImmutableList<UUID> statusUuids;
    @NonNull private final Instant time;
    @NonNull private final ImmutableList<UUID> authorUuids;
    @NonNull private final ImmutableList<UUID> moduleUuids;
    @NonNull private final ImmutableList<UUID> pathUuids;

    public StampDTO(@NonNull ImmutableList<UUID> componentUuids,
                    @NonNull ImmutableList<UUID> statusUuids,
                    @NonNull Instant time,
                    @NonNull ImmutableList<UUID> authorUuids,
                    @NonNull ImmutableList<UUID> moduleUuids,
                    @NonNull ImmutableList<UUID> pathUuids) {
        super(componentUuids);
        this.statusUuids = statusUuids;
        this.time = time;
        this.authorUuids = authorUuids;
        this.moduleUuids = moduleUuids;
        this.pathUuids = pathUuids;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StampDTO)) return false;
        if (!super.equals(o)) return false;
        StampDTO stampDTO = (StampDTO) o;
        return statusUuids.equals(stampDTO.statusUuids) && time.equals(stampDTO.time) && authorUuids.equals(stampDTO.authorUuids) && moduleUuids.equals(stampDTO.moduleUuids) && pathUuids.equals(stampDTO.pathUuids);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), statusUuids, time, authorUuids, moduleUuids, pathUuids);
    }

    public static StampDTO make(Stamp stamp) {
        return new StampDTO(stamp.componentUuids(),
                stamp.status().componentUuids(),
                stamp.time(),
                stamp.author().componentUuids(),
                stamp.module().componentUuids(),
                stamp.path().componentUuids());
    }

    @Override
    public Concept status() {
        return new ConceptDTO(statusUuids);
    }

    @Override
    public Instant time() {
        return time;
    }

    @Override
    public Concept author() {
        return new ConceptDTO(authorUuids);
    }

    @Override
    public Concept module() {
        return new ConceptDTO(moduleUuids);
    }

    @Override
    public Concept path() {
        return new ConceptDTO(pathUuids);
    }

    @Override
    public void jsonMarshal(Writer writer) {
        final JSONObject json = new JSONObject();
        json.put(COMPONENT_UUIDS, componentUuids());
        json.put(STATUS_UUIDS, statusUuids);
        json.put(TIME, time);
        json.put(ComponentFieldForJson.AUTHOR_UUIDS, authorUuids);
        json.put(ComponentFieldForJson.MODULE_UUIDS, moduleUuids);
        json.put(ComponentFieldForJson.PATH_UUIDS, pathUuids);
        json.writeJSONString(writer);
    }

    @JsonChronologyUnmarshaler
    public static StampDTO make(JSONObject jsonObject) {
        return new StampDTO(jsonObject.asImmutableUuidList(COMPONENT_UUIDS),
                jsonObject.asImmutableUuidList(STATUS_UUIDS),
                jsonObject.asInstant(TIME),
                jsonObject.asImmutableUuidList(AUTHOR_UUIDS),
                jsonObject.asImmutableUuidList(MODULE_UUIDS),
                jsonObject.asImmutableUuidList(PATH_UUIDS));
    }

    @Unmarshaler
    public static StampDTO make(TinkarInput in) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            return new StampDTO(in.readImmutableUuidList(),
                    in.readImmutableUuidList(),
                    in.readInstant(),
                    in.readImmutableUuidList(),
                    in.readImmutableUuidList(),
                    in.readImmutableUuidList());
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + localMarshalVersion);
        }
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        out.writeUuidList(componentUuids());
        out.writeUuidList(statusUuids);
        out.writeInstant(time);
        out.writeUuidList(authorUuids);
        out.writeUuidList(moduleUuids);
        out.writeUuidList(pathUuids);
    }

}
