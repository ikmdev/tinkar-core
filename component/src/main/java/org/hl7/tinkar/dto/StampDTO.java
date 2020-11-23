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
import java.io.Writer;
import java.time.Instant;
import java.util.UUID;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.binary.Marshalable;
import org.hl7.tinkar.binary.Marshaler;
import org.hl7.tinkar.binary.TinkarInput;
import org.hl7.tinkar.binary.TinkarOutput;
import org.hl7.tinkar.binary.Unmarshaler;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.component.Stamp;
import org.hl7.tinkar.json.JSONObject;
import org.hl7.tinkar.json.JsonMarshalable;
import org.hl7.tinkar.json.JsonChronologyUnmarshaler;

public record StampDTO(ImmutableList<UUID> statusUuids,
                       Instant time,
                       ImmutableList<UUID> authorUuids,
                       ImmutableList<UUID> moduleUuids,
                       ImmutableList<UUID> pathUuids)
        implements ChangeSetThing, JsonMarshalable, Marshalable, Stamp {

    private static final int marshalVersion = 1;

    @Override
    public Concept getStatus() {
        return new ConceptDTO(statusUuids);
    }

    @Override
    public Instant getTime() {
        return time;
    }

    @Override
    public Concept getAuthor() {
        return new ConceptDTO(authorUuids);
    }

    @Override
    public Concept getModule() {
        return new ConceptDTO(moduleUuids);
    }

    @Override
    public Concept getPath() {
        return new ConceptDTO(pathUuids);
    }

    @Override
    public void jsonMarshal(Writer writer) {
        final JSONObject json = new JSONObject();
        json.put("statusUuids", statusUuids);
        json.put("time", time);
        json.put("authorUuids", authorUuids);
        json.put("moduleUuids", moduleUuids);
        json.put("pathUuids", pathUuids);
        json.writeJSONString(writer);
    }

    @JsonChronologyUnmarshaler
    public static StampDTO make(JSONObject jsonObject) {
        return new StampDTO(jsonObject.asImmutableUuidList("statusUuids"),
                jsonObject.asInstant("time"),
                jsonObject.asImmutableUuidList("authorUuids"),
                jsonObject.asImmutableUuidList("moduleUuids"),
                jsonObject.asImmutableUuidList("pathUuids"));
    }

    @Unmarshaler
    public static StampDTO make(TinkarInput in) {
        try {
            int objectMarshalVersion = in.readInt();
            switch (objectMarshalVersion) {
                case marshalVersion -> {
                    return new StampDTO(
                            in.readImmutableUuidList(),
                            in.readInstant(),
                            in.readImmutableUuidList(),
                            in.readImmutableUuidList(),
                            in.readImmutableUuidList());
                }
                default -> throw new UnsupportedOperationException("Unsupported version: " + objectMarshalVersion);
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
            out.writeUuidList(statusUuids);
            out.writeInstant(time);
            out.writeUuidList(authorUuids);
            out.writeUuidList(moduleUuids);
            out.writeUuidList(pathUuids);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

}
