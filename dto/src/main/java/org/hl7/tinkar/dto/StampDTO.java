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

import java.io.Writer;
import java.time.Instant;
import java.util.UUID;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.component.Stamp;
import org.hl7.tinkar.dto.binary.*;
import org.hl7.tinkar.dto.json.JSONObject;
import org.hl7.tinkar.dto.json.ComponentFieldForJson;
import org.hl7.tinkar.dto.json.JsonMarshalable;
import org.hl7.tinkar.dto.json.JsonChronologyUnmarshaler;

import static org.hl7.tinkar.dto.json.ComponentFieldForJson.*;

public record StampDTO(ImmutableList<UUID> componentUuids,
                       ImmutableList<UUID> statusUuids,
                       Instant time,
                       ImmutableList<UUID> authorUuids,
                       ImmutableList<UUID> moduleUuids,
                       ImmutableList<UUID> pathUuids)
        implements JsonMarshalable, Marshalable, Stamp, DTO {
    private static final int localMarshalVersion = 3;

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
        json.put(COMPONENT_UUIDS, componentUuids);
        json.put(STATUS_UUIDS, statusUuids);
        json.put(ComponentFieldForJson.TIME, time);
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
        out.writeUuidList(componentUuids);
        out.writeUuidList(statusUuids);
        out.writeInstant(time);
        out.writeUuidList(authorUuids);
        out.writeUuidList(moduleUuids);
        out.writeUuidList(pathUuids);
    }

}
