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


import io.soabase.recordbuilder.core.RecordBuilder;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.component.Stamp;
import org.hl7.tinkar.lombok.dto.binary.*;

@RecordBuilder
public record StampDTO(PublicId publicId,
                      PublicId statusPublicId,
                      long time,
                      PublicId authorPublicId,
                      PublicId modulePublicId,
                      PublicId pathPublicId)
        implements Marshalable, Stamp, DTO {
    private static final int localMarshalVersion = 3;


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
