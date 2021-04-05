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
import org.hl7.tinkar.component.PatternVersion;
import org.hl7.tinkar.dto.binary.*;

/**
 *
 * @author kec
 */

public record PatternVersionDTO(PublicId publicId,
                                StampDTO stamp,
                                PublicId referencedComponentPurposePublicId,
                                PublicId referencedComponentMeaningPublicId,
                                ImmutableList<FieldDefinitionDTO> fieldDefinitionDTOS)
        implements PatternVersion<FieldDefinitionDTO>, Marshalable {

    private static final int localMarshalVersion = 3;


    public static PatternVersionDTO make(PatternVersion<FieldDefinitionDTO> patternVersion) {

        MutableList<FieldDefinitionDTO> fields = Lists.mutable.ofInitialCapacity(patternVersion.fieldDefinitions().size());
        for (FieldDefinition fieldDefinition : patternVersion.fieldDefinitions()) {
            fields.add(FieldDefinitionDTO.make(fieldDefinition));
        }

        return new PatternVersionDTO(
                patternVersion.publicId(),
                StampDTO.make(patternVersion.stamp()),
                patternVersion.referencedComponentPurpose().publicId(),
                patternVersion.referencedComponentMeaning().publicId(),
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
     * Unmarshal method for PatternVersionDTO
     * @param in
     * @param componentPublicId
     * @return
     */
    @VersionUnmarshaler
    public static PatternVersionDTO make(TinkarInput in, PublicId componentPublicId) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            return new PatternVersionDTO(componentPublicId,
                    StampDTO.make(in),
                    in.getPublicId(),
                    in.getPublicId(),
                    in.readFieldDefinitionList());
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + in.getTinkerFormatVersion());
        }
    }

    /**
     * Marshal method for PatternVersionDTO
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
