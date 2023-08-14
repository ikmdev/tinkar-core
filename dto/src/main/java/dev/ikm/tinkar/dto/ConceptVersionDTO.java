/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.dto;

import dev.ikm.tinkar.dto.binary.*;
import io.soabase.recordbuilder.core.RecordBuilder;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.component.ConceptVersion;
import dev.ikm.tinkar.dto.binary.*;


@RecordBuilder
public record ConceptVersionDTO(PublicId publicId, StampDTO stamp)
        implements ConceptVersion, DTO, Marshalable {

    private static final int localMarshalVersion = 3;

    public static ConceptVersionDTO make(ConceptVersion conceptVersion) {
        return new ConceptVersionDTO(conceptVersion.publicId(), StampDTO.make(conceptVersion.stamp()));
    }


    /**
     * Version unmarshaler for ConceptVersionDTO.
     *
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
     *
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
