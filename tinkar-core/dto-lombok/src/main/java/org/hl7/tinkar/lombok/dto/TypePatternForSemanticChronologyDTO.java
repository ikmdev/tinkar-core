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
import org.hl7.tinkar.component.TypePatternForSemanticChronology;
import org.hl7.tinkar.lombok.dto.binary.*;
import org.hl7.tinkar.lombok.dto.json.JSONObject;
import org.hl7.tinkar.lombok.dto.json.JsonMarshalable;
import org.hl7.tinkar.lombok.dto.json.ComponentFieldForJson;
import org.hl7.tinkar.lombok.dto.json.JsonChronologyUnmarshaler;

import java.io.Writer;
import java.util.Objects;

/**
 *
 * @author kec
 */
@Value
@Accessors(fluent = true)
@ToString(callSuper = true)
public class TypePatternForSemanticChronologyDTO
    extends TypePatternForSemanticDTO
        implements TypePatternForSemanticChronology<TypePatternForSemanticVersionDTO>, DTO, JsonMarshalable, Marshalable {

    private static final int localMarshalVersion = 3;

    @NonNull protected final PublicId chronologySetPublicId;
    @NonNull protected final ImmutableList<TypePatternForSemanticVersionDTO> definitionVersions;

    public TypePatternForSemanticChronologyDTO(@NonNull PublicId componentPublicId,
                                               @NonNull PublicId chronologySetPublicId,
                                               @NonNull ImmutableList<TypePatternForSemanticVersionDTO> definitionVersions) {
        super(componentPublicId);
        this.chronologySetPublicId = chronologySetPublicId;
        this.definitionVersions = definitionVersions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TypePatternForSemanticChronologyDTO that)) return false;
        if (!super.equals(o)) return false;
        return chronologySetPublicId.equals(that.chronologySetPublicId) && definitionVersions.equals(that.definitionVersions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), chronologySetPublicId, definitionVersions);
    }

    public static TypePatternForSemanticChronologyDTO make(TypePatternForSemanticChronology<TypePatternForSemanticVersionDTO> definitionForSemanticChronology) {
        MutableList<TypePatternForSemanticVersionDTO> versions = Lists.mutable.ofInitialCapacity(definitionForSemanticChronology.versions().size());
        for (TypePatternForSemanticVersionDTO definitionVersion : definitionForSemanticChronology.versions()) {
            versions.add(TypePatternForSemanticVersionDTO.make(definitionVersion));
        }
        return new TypePatternForSemanticChronologyDTO(definitionForSemanticChronology.publicId(),
                definitionForSemanticChronology.chronologySet().publicId(),
                versions.toImmutable());
    }

    @Override
    public ImmutableList<TypePatternForSemanticVersionDTO> versions() {
        return definitionVersions.collect(definitionForSemanticVersionDTO -> definitionForSemanticVersionDTO);
    }

    @Override
    public Concept chronologySet() {
        return new ConceptDTO(chronologySetPublicId);
    }

    @Override
    public void jsonMarshal(Writer writer) {
        final JSONObject json = new JSONObject();
        json.put(ComponentFieldForJson.CLASS, this.getClass().getCanonicalName());
        json.put(ComponentFieldForJson.COMPONENT_PUBLIC_ID, publicId());
        json.put(ComponentFieldForJson.CHRONOLOGY_SET_PUBLIC_ID, chronologySetPublicId);
        json.put(ComponentFieldForJson.DEFINITION_VERSIONS, definitionVersions);
        json.writeJSONString(writer);
    }
    
    @JsonChronologyUnmarshaler
    public static TypePatternForSemanticChronologyDTO make(JSONObject jsonObject) {
        PublicId componentPublicId = jsonObject.asPublicId(ComponentFieldForJson.COMPONENT_PUBLIC_ID);
        return new TypePatternForSemanticChronologyDTO(componentPublicId,
                        jsonObject.asPublicId(ComponentFieldForJson.CHRONOLOGY_SET_PUBLIC_ID),
                        jsonObject.asDefinitionForSemanticVersionList(ComponentFieldForJson.DEFINITION_VERSIONS, componentPublicId));
    }

    @Unmarshaler
    public static TypePatternForSemanticChronologyDTO make(TinkarInput in) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            PublicId componentPublicId = in.getPublicId();
            return new TypePatternForSemanticChronologyDTO(
                    componentPublicId, in.getPublicId(), in.readDefinitionForSemanticVersionList(componentPublicId));
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + in.getTinkerFormatVersion());
        }
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        out.putPublicId(publicId());
        out.putPublicId(chronologySetPublicId);
        out.writeDefinitionForSemanticVersionList(definitionVersions);
    }
}
