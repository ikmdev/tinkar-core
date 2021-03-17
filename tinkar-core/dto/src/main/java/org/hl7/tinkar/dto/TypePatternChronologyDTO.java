/*
 * Copyright 2020 kec.
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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.component.TypePatternChronology;
import org.hl7.tinkar.dto.binary.*;

/**
 *
 * @author kec
 */
public record TypePatternChronologyDTO(PublicId publicId,
                                      ImmutableList<TypePatternVersionDTO> typePatternVersions)
        implements TypePatternChronology<TypePatternVersionDTO>, DTO, Marshalable {

    private static final int localMarshalVersion = 3;

    public static TypePatternChronologyDTO make(TypePatternChronology<TypePatternVersionDTO> typePatternChronology) {
        MutableList<TypePatternVersionDTO> versions = Lists.mutable.ofInitialCapacity(typePatternChronology.versions().size());
        for (TypePatternVersionDTO definitionVersion : typePatternChronology.versions()) {
            versions.add(TypePatternVersionDTO.make(definitionVersion));
        }
        return new TypePatternChronologyDTO(typePatternChronology.publicId(),
                versions.toImmutable());
    }

    @Override
    public ImmutableList<TypePatternVersionDTO> versions() {
        return typePatternVersions.collect(typePatternVersionDTO -> typePatternVersionDTO);
    }

    @Unmarshaler
    public static TypePatternChronologyDTO make(TinkarInput in) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            PublicId publicId = in.getPublicId();
            return new TypePatternChronologyDTO(
                    publicId, in.readTypePatternVersionList(publicId));
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + in.getTinkerFormatVersion());
        }
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        out.putPublicId(publicId());
        out.writeTypePatternVersionList(typePatternVersions);
    }
}
