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
import org.hl7.tinkar.common.util.id.PublicId;
import org.hl7.tinkar.component.Component;
import org.hl7.tinkar.component.PatternForSemantic;
import org.hl7.tinkar.component.SemanticChronology;
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
public class SemanticChronologyDTO
    extends SemanticDTO
    implements SemanticChronology<SemanticVersionDTO>, DTO, JsonMarshalable, Marshalable {

    private static final int localMarshalVersion = 3;

    @NonNull
    protected final ImmutableList<SemanticVersionDTO> semanticVersions;

    public SemanticChronologyDTO(@NonNull PublicId componentUuids,
                                 @NonNull PublicId definitionForSemanticUuids,
                                 @NonNull PublicId referencedComponentUuids,
                                 @NonNull ImmutableList<SemanticVersionDTO> semanticVersions) {
        super(componentUuids, definitionForSemanticUuids, referencedComponentUuids);
        this.semanticVersions = semanticVersions;
    }

    public SemanticChronologyDTO(PublicId componentUuids,
                                 PatternForSemantic patternForSemantic,
                                 Component referencedComponent,
                                 ImmutableList<SemanticVersionDTO> semanticVersions) {
        this(componentUuids,
                patternForSemantic.publicId(),
                referencedComponent.publicId(),
                semanticVersions);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SemanticChronologyDTO)) return false;
        if (!super.equals(o)) return false;
        SemanticChronologyDTO that = (SemanticChronologyDTO) o;
        return semanticVersions.equals(that.semanticVersions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), semanticVersions);
    }

    public static SemanticChronologyDTO make(SemanticChronology<SemanticVersionDTO> semanticChronology) {
        MutableList<SemanticVersionDTO> changeSetVersions = Lists.mutable.ofInitialCapacity(semanticChronology.versions().size());
        for (SemanticVersionDTO semanticVersion : semanticChronology.versions()) {
            changeSetVersions.add(SemanticVersionDTO.make(semanticVersion));
        }
        return new SemanticChronologyDTO(semanticChronology.publicId(),
                semanticChronology.patternForSemantic(),
                semanticChronology.referencedComponent(),
                changeSetVersions.toImmutable());
    }

    @Override
    public Component referencedComponent() {
        return new ComponentDTO(referencedComponentPublicId);
    }

    @Override
    public PatternForSemantic patternForSemantic() {
        return new PatternForSemanticDTO(definitionForSemanticPublicId);
    }

    @Override
    public void jsonMarshal(Writer writer) {
        final JSONObject json = new JSONObject();
        json.put(ComponentFieldForJson.CLASS, this.getClass().getCanonicalName());
        json.put(ComponentFieldForJson.COMPONENT_PUBLIC_ID, publicId());
        json.put(ComponentFieldForJson.PATTERN_FOR_SEMANTIC_PUBLIC_ID, definitionForSemanticPublicId);
        json.put(ComponentFieldForJson.REFERENCED_COMPONENT_PUBLIC_ID, referencedComponentPublicId);
        json.put(ComponentFieldForJson.VERSIONS, semanticVersions);
        json.writeJSONString(writer);
    }
    
    @JsonChronologyUnmarshaler
    public static SemanticChronologyDTO make(JSONObject jsonObject) {
        PublicId componentPublicId = jsonObject.asPublicId(ComponentFieldForJson.COMPONENT_PUBLIC_ID);
        PublicId definitionForSemanticPublicId = jsonObject.asPublicId(ComponentFieldForJson.PATTERN_FOR_SEMANTIC_PUBLIC_ID);
        PublicId referencedComponentPublicId = jsonObject.asPublicId(ComponentFieldForJson.REFERENCED_COMPONENT_PUBLIC_ID);
        return new SemanticChronologyDTO(componentPublicId,
                definitionForSemanticPublicId,
                referencedComponentPublicId,
                        jsonObject.asSemanticVersionList(ComponentFieldForJson.VERSIONS,
                                componentPublicId,
                                definitionForSemanticPublicId,
                                referencedComponentPublicId)
                );
    }

    @Unmarshaler
    public static SemanticChronologyDTO make(TinkarInput in) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            PublicId componentUuids = in.getPublicId();
            PublicId definitionForSemanticUuids = in.getPublicId();
            PublicId referencedComponentUuids = in.getPublicId();
            return new SemanticChronologyDTO(
                    componentUuids, definitionForSemanticUuids, referencedComponentUuids,
                    in.readSemanticVersionList(componentUuids, definitionForSemanticUuids, referencedComponentUuids));
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + in.getTinkerFormatVersion());
        }
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        out.putPublicId(publicId());
        out.putPublicId(definitionForSemanticPublicId);
        out.putPublicId(referencedComponentPublicId);
        out.writeSemanticVersionList(semanticVersions);
    }

    @Override
    public ImmutableList<SemanticVersionDTO> versions() {
        return semanticVersions.collect(semanticVersionDTO ->  semanticVersionDTO);
    }

    @Override
    public PatternForSemanticDTO chronologySet() {
        return new PatternForSemanticDTO(definitionForSemanticPublicId);
    }
}

