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
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.component.DefinitionForSemanticVersion;
import org.hl7.tinkar.component.FieldDefinition;
import org.hl7.tinkar.lombok.dto.binary.*;
import org.hl7.tinkar.lombok.dto.json.ComponentFieldForJson;
import org.hl7.tinkar.lombok.dto.json.JSONObject;
import org.hl7.tinkar.lombok.dto.json.JsonMarshalable;
import org.hl7.tinkar.lombok.dto.json.JsonVersionUnmarshaler;

import java.io.Writer;
import java.util.Objects;
import java.util.UUID;

import static org.hl7.tinkar.lombok.dto.json.ComponentFieldForJson.FIELD_DEFINITIONS;
import static org.hl7.tinkar.lombok.dto.json.ComponentFieldForJson.REFERENCED_COMPONENT_PURPOSE_UUIDS;

/**
 *
 * @author kec
 */
@Value
@Accessors(fluent = true)
@ToString(callSuper = true)
public class DefinitionForSemanticVersionDTO
    extends VersionDTO
        implements DefinitionForSemanticVersion<FieldDefinitionDTO>, JsonMarshalable, Marshalable {

    private static final int localMarshalVersion = 3;

    @NonNull private final ImmutableList<UUID> referencedComponentPurposeUuids;
    @NonNull private final ImmutableList<FieldDefinitionDTO> fieldDefinitionDTOS;

    public DefinitionForSemanticVersionDTO(@NonNull ImmutableList<UUID> componentUuids,
                                           @NonNull StampDTO stamp,
                                           @NonNull ImmutableList<UUID> referencedComponentPurposeUuids,
                                           @NonNull ImmutableList<FieldDefinitionDTO> fieldDefinitionDTOS) {
        super(componentUuids, stamp);
        this.referencedComponentPurposeUuids = referencedComponentPurposeUuids;
        this.fieldDefinitionDTOS = fieldDefinitionDTOS;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DefinitionForSemanticVersionDTO)) return false;
        if (!super.equals(o)) return false;
        DefinitionForSemanticVersionDTO that = (DefinitionForSemanticVersionDTO) o;
        return referencedComponentPurposeUuids.equals(that.referencedComponentPurposeUuids) && fieldDefinitionDTOS.equals(that.fieldDefinitionDTOS);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), referencedComponentPurposeUuids, fieldDefinitionDTOS);
    }

    public static DefinitionForSemanticVersionDTO make(DefinitionForSemanticVersion<FieldDefinitionDTO> definitionForSemanticVersion) {

        MutableList<FieldDefinitionDTO> fields = Lists.mutable.ofInitialCapacity(definitionForSemanticVersion.fieldDefinitions().size());
        for (FieldDefinition fieldDefinition : definitionForSemanticVersion.fieldDefinitions()) {
            fields.add(FieldDefinitionDTO.make(fieldDefinition));
        }

        return new DefinitionForSemanticVersionDTO(
                definitionForSemanticVersion.componentUuids(),
                StampDTO.make(definitionForSemanticVersion.stamp()),
                definitionForSemanticVersion.referencedComponentPurpose().componentUuids(),
                fields.toImmutable());
    }
    @Override
    public Concept referencedComponentPurpose() {
        return new ConceptDTO(referencedComponentPurposeUuids);
    }

    @Override
    public ImmutableList<FieldDefinitionDTO> fieldDefinitions() {
        return fieldDefinitionDTOS.collect(fieldDefinitionDTO -> fieldDefinitionDTO);
    }

    /**
     * Marshal method for DefinitionForSemanticVersionDTO using JSON
     * @param writer
     */
    @Override
    public void jsonMarshal(Writer writer) {
        final JSONObject json = new JSONObject();
        json.put(ComponentFieldForJson.STAMP, stamp);
        json.put(REFERENCED_COMPONENT_PURPOSE_UUIDS, referencedComponentPurposeUuids);
        json.put(FIELD_DEFINITIONS, fieldDefinitionDTOS);
        json.writeJSONString(writer);
    }

    /**
     * Unmarshal method for DefinitionForSemanticVersionDTO using JSON
     * @param jsonObject
     * @param componentUuids
     * @return
     */
    @JsonVersionUnmarshaler
    public static DefinitionForSemanticVersionDTO make(JSONObject jsonObject, ImmutableList<UUID> componentUuids) {
        return new DefinitionForSemanticVersionDTO(componentUuids,
                StampDTO.make((JSONObject) jsonObject.get(ComponentFieldForJson.STAMP)),
                jsonObject.asImmutableUuidList(REFERENCED_COMPONENT_PURPOSE_UUIDS),
                jsonObject.asFieldDefinitionList(FIELD_DEFINITIONS));
    }

    /**
     * Unmarshal method for DefinitionForSemanticVersionDTO
     * @param in
     * @param componentUuids
     * @return
     */
    @VersionUnmarshaler
    public static DefinitionForSemanticVersionDTO make(TinkarInput in, ImmutableList<UUID> componentUuids) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            return new DefinitionForSemanticVersionDTO(componentUuids,
                    StampDTO.make(in),
                    in.readImmutableUuidList(),
                    in.readFieldDefinitionList());
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + in.getTinkerFormatVersion());
        }
    }

    /**
     * Marshal method for DefinitionForSemanticVersionDTO
     * @param out
     */
    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        stamp.marshal(out);
        out.writeUuidList(referencedComponentPurposeUuids);
        out.writeFieldDefinitionList(fieldDefinitionDTOS);
    }
}
