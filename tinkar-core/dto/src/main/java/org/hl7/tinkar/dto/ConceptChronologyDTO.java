/*
 * Copyright 2020 HL7.
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

import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.component.ConceptChronology;
import org.hl7.tinkar.component.ConceptVersion;
import org.hl7.tinkar.dto.binary.*;

/**
 * @author kec
 */
@RecordBuilder
public record ConceptChronologyDTO(PublicId publicId,
                                   ImmutableList<ConceptVersionDTO> versions)
        implements Marshalable, ConceptChronology<ConceptVersionDTO>, DTO {

    private static final int localMarshalVersion = 3;

    public static ConceptChronologyDTO make(ConceptChronology<? extends ConceptVersion> conceptChronology) {
        MutableList<ConceptVersionDTO> versions = Lists.mutable.ofInitialCapacity(conceptChronology.versions().size());
        for (ConceptVersion conceptVersion : conceptChronology.versions()) {
            versions.add(ConceptVersionDTO.make(conceptVersion));
        }
        return new ConceptChronologyDTO(conceptChronology.publicId(),
                versions.toImmutable());
    }

    @Unmarshaler
    public static ConceptChronologyDTO make(TinkarInput in) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            PublicId publicId = in.getPublicId();
            return new ConceptChronologyDTO(
                    publicId, in.readConceptVersionList(publicId));
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + in.getTinkerFormatVersion());
        }
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        out.putPublicId(publicId);
        // Note that the componentIds are not written redundantly
        // in writeConceptVersionList...
        out.writeConceptVersionList(versions);
    }
}
