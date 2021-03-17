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
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.component.FieldDefinition;
import org.hl7.tinkar.component.TypePatternVersion;
import org.hl7.tinkar.dto.binary.*;

/**
 *
 * @author kec
 */

public record TypePatternVersionDTO(PublicId publicId,
                                    StampDTO stamp,
                                    PublicId referencedComponentPurposePublicId,
                                    PublicId referencedComponentMeaningPublicId,
                                    ImmutableList<FieldDefinitionDTO> fieldDefinitionDTOS)
        implements TypePatternVersion<FieldDefinitionDTO>, Marshalable {

    private static final int localMarshalVersion = 3;


    public static TypePatternVersionDTO make(TypePatternVersion<FieldDefinitionDTO> typePatternVersion) {

        MutableList<FieldDefinitionDTO> fields = Lists.mutable.ofInitialCapacity(typePatternVersion.fieldDefinitions().size());
        for (FieldDefinition fieldDefinition : typePatternVersion.fieldDefinitions()) {
            fields.add(FieldDefinitionDTO.make(fieldDefinition));
        }

        return new TypePatternVersionDTO(
                typePatternVersion.publicId(),
                StampDTO.make(typePatternVersion.stamp()),
                typePatternVersion.referencedComponentPurpose().publicId(),
                typePatternVersion.referencedComponentMeaning().publicId(),
                fields.toImmutable());
    }
    @Override
    public Concept referencedComponentPurpose() {
        return new ConceptDTO(referencedComponentPurposePublicId);
    }

    @Override
    public Concept referencedComponentMeaning() {
        return new ConceptDTO(referencedComponentMeaningPublicId);
    }

    @Override
    public ImmutableList<FieldDefinitionDTO> fieldDefinitions() {
        return fieldDefinitionDTOS.collect(fieldDefinitionDTO -> fieldDefinitionDTO);
    }

    /**
     * Unmarshal method for TypePatternVersionDTO
     * @param in
     * @param componentPublicId
     * @return
     */
    @VersionUnmarshaler
    public static TypePatternVersionDTO make(TinkarInput in, PublicId componentPublicId) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            return new TypePatternVersionDTO(componentPublicId,
                    StampDTO.make(in),
                    in.getPublicId(),
                    in.getPublicId(),
                    in.readFieldDefinitionList());
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + in.getTinkerFormatVersion());
        }
    }

    /**
     * Marshal method for TypePatternVersionDTO
     * @param out
     */
    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        stamp.marshal(out);
        out.putPublicId(referencedComponentPurposePublicId);
        out.putPublicId(referencedComponentMeaningPublicId);
        out.writeFieldDefinitionList(fieldDefinitionDTOS);
    }
}
