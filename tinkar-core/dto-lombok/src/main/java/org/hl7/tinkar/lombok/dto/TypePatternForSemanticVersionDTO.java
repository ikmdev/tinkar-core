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
package org.hl7.tinkar.lombok.dto;

import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.component.FieldDefinition;
import org.hl7.tinkar.component.TypePatternForSemanticVersion;
import org.hl7.tinkar.lombok.dto.binary.*;
import org.hl7.tinkar.lombok.dto.json.JSONObject;
import org.hl7.tinkar.lombok.dto.json.JsonMarshalable;
import org.hl7.tinkar.lombok.dto.json.JsonVersionUnmarshaler;
import org.hl7.tinkar.lombok.dto.json.ComponentFieldForJson;

import java.io.Writer;
import java.util.Objects;

import static org.hl7.tinkar.lombok.dto.json.ComponentFieldForJson.*;

/**
 *
 * @author kec
 */
@Value
@Accessors(fluent = true)
@ToString(callSuper = true)
public class TypePatternForSemanticVersionDTO
    extends VersionDTO
        implements TypePatternForSemanticVersion<FieldDefinitionDTO>, JsonMarshalable, Marshalable {

    private static final int localMarshalVersion = 3;

    @NonNull private final PublicId referencedComponentPurposePublicId;
    @NonNull private final PublicId referencedComponentMeaningPublicId;
    @NonNull private final ImmutableList<FieldDefinitionDTO> fieldDefinitionDTOS;

    public TypePatternForSemanticVersionDTO(@NonNull PublicId componentPublicId,
                                            @NonNull StampDTO stamp,
                                            @NonNull PublicId referencedComponentPurposePublicId,
                                            @NonNull PublicId referencedComponentMeaningPublicId,
                                            @NonNull ImmutableList<FieldDefinitionDTO> fieldDefinitionDTOS) {
        super(componentPublicId, stamp);
        this.referencedComponentPurposePublicId = referencedComponentPurposePublicId;
        this.referencedComponentMeaningPublicId = referencedComponentMeaningPublicId;
        this.fieldDefinitionDTOS = fieldDefinitionDTOS;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TypePatternForSemanticVersionDTO that)) return false;
        if (!super.equals(o)) return false;
        return referencedComponentPurposePublicId.equals(that.referencedComponentPurposePublicId) && fieldDefinitionDTOS.equals(that.fieldDefinitionDTOS);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), referencedComponentPurposePublicId, fieldDefinitionDTOS);
    }

    public static TypePatternForSemanticVersionDTO make(TypePatternForSemanticVersion<FieldDefinitionDTO> definitionForSemanticVersion) {

        MutableList<FieldDefinitionDTO> fields = Lists.mutable.ofInitialCapacity(definitionForSemanticVersion.fieldDefinitions().size());
        for (FieldDefinition fieldDefinition : definitionForSemanticVersion.fieldDefinitions()) {
            fields.add(FieldDefinitionDTO.make(fieldDefinition));
        }

        return new TypePatternForSemanticVersionDTO(
                definitionForSemanticVersion.publicId(),
                StampDTO.make(definitionForSemanticVersion.stamp()),
                definitionForSemanticVersion.referencedComponentPurpose().publicId(),
                definitionForSemanticVersion.referencedComponentMeaning().publicId(),
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
     * Marshal method for TypePatternForSemanticVersionDTO using JSON
     * @param writer
     */
    @Override
    public void jsonMarshal(Writer writer) {
        final JSONObject json = new JSONObject();
        json.put(ComponentFieldForJson.STAMP, stamp);
        json.put(REFERENCED_COMPONENT_PURPOSE_PUBLIC_ID, referencedComponentPurposePublicId);
        json.put(REFERENCED_COMPONENT_MEANING_PUBLIC_ID, referencedComponentPurposePublicId);
        json.put(FIELD_DEFINITIONS, fieldDefinitionDTOS);
        json.writeJSONString(writer);
    }

    /**
     * Unmarshal method for TypePatternForSemanticVersionDTO using JSON
     * @param jsonObject
     * @param componentPublicId
     * @return
     */
    @JsonVersionUnmarshaler
    public static TypePatternForSemanticVersionDTO make(JSONObject jsonObject, PublicId componentPublicId) {
        return new TypePatternForSemanticVersionDTO(componentPublicId,
                StampDTO.make((JSONObject) jsonObject.get(ComponentFieldForJson.STAMP)),
                jsonObject.asPublicId(REFERENCED_COMPONENT_PURPOSE_PUBLIC_ID),
                jsonObject.asPublicId(REFERENCED_COMPONENT_MEANING_PUBLIC_ID),
                jsonObject.asFieldDefinitionList(FIELD_DEFINITIONS));
    }

    /**
     * Unmarshal method for TypePatternForSemanticVersionDTO
     * @param in
     * @param componentPublicId
     * @return
     */
    @VersionUnmarshaler
    public static TypePatternForSemanticVersionDTO make(TinkarInput in, PublicId componentPublicId) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            return new TypePatternForSemanticVersionDTO(componentPublicId,
                    StampDTO.make(in),
                    in.getPublicId(),
                    in.getPublicId(),
                    in.readFieldDefinitionList());
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + in.getTinkerFormatVersion());
        }
    }

    /**
     * Marshal method for TypePatternForSemanticVersionDTO
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
