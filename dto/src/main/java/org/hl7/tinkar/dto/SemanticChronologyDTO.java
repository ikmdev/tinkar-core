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

import java.io.Writer;
import java.util.UUID;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.component.*;
import org.hl7.tinkar.dto.binary.*;
import org.hl7.tinkar.dto.json.ComponentFieldForJson;
import org.hl7.tinkar.dto.json.JSONObject;
import org.hl7.tinkar.dto.json.JsonChronologyUnmarshaler;
import org.hl7.tinkar.dto.json.JsonMarshalable;

/**
 *
 * @author kec
 */
public record SemanticChronologyDTO(ImmutableList<UUID> componentUuids,
                                    ImmutableList<UUID> definitionForSemanticUuids,
                                    ImmutableList<UUID> referencedComponentUuids,
                                    ImmutableList<SemanticVersionDTO> semanticVersions)
        implements SemanticChronology<SemanticVersionDTO>, DTO, JsonMarshalable, Marshalable {

    private static final int localMarshalVersion = 3;

    public SemanticChronologyDTO(ImmutableList<UUID> componentUuids, DefinitionForSemantic definitionForSemantic,
                                 Component referencedComponent, ImmutableList<SemanticVersionDTO> semanticVersions) {
        this(componentUuids,
                definitionForSemantic.componentUuids(),
                referencedComponent.componentUuids(),
                semanticVersions);
    }


    public static SemanticChronologyDTO make(SemanticChronology<SemanticVersionDTO> semanticChronology) {
        MutableList<SemanticVersionDTO> changeSetVersions = Lists.mutable.ofInitialCapacity(semanticChronology.versions().size());
        for (SemanticVersionDTO semanticVersion : semanticChronology.versions()) {
            changeSetVersions.add(SemanticVersionDTO.make(semanticVersion));
        }
        return new SemanticChronologyDTO(semanticChronology.componentUuids(),
                semanticChronology.definitionForSemantic(),
                semanticChronology.referencedComponent(),
                changeSetVersions.toImmutable());
    }

    @Override
    public Component referencedComponent() {
        return new ComponentDTO(referencedComponentUuids);
    }

    @Override
    public DefinitionForSemantic definitionForSemantic() {
        return new DefinitionForSemanticDTO(definitionForSemanticUuids);
    }

    @Override
    public void jsonMarshal(Writer writer) {
        final JSONObject json = new JSONObject();
        json.put(ComponentFieldForJson.CLASS, this.getClass().getCanonicalName());
        json.put(ComponentFieldForJson.COMPONENT_UUIDS, componentUuids);
        json.put(ComponentFieldForJson.DEFINITION_FOR_SEMANTIC_UUIDS, definitionForSemanticUuids);
        json.put(ComponentFieldForJson.REFERENCED_COMPONENT_UUIDS, referencedComponentUuids);
        json.put(ComponentFieldForJson.VERSIONS, semanticVersions);
        json.writeJSONString(writer);
    }
    
    @JsonChronologyUnmarshaler
    public static SemanticChronologyDTO make(JSONObject jsonObject) {
        ImmutableList<UUID> componentUuids = jsonObject.asImmutableUuidList(ComponentFieldForJson.COMPONENT_UUIDS);
        ImmutableList<UUID> definitionForSemanticUuids = jsonObject.asImmutableUuidList(ComponentFieldForJson.DEFINITION_FOR_SEMANTIC_UUIDS);
        ImmutableList<UUID> referencedComponentUuids = jsonObject.asImmutableUuidList(ComponentFieldForJson.REFERENCED_COMPONENT_UUIDS);
        return new SemanticChronologyDTO(componentUuids,
                definitionForSemanticUuids,
                referencedComponentUuids,
                        jsonObject.asSemanticVersionList(ComponentFieldForJson.VERSIONS,
                                componentUuids,
                                definitionForSemanticUuids,
                                referencedComponentUuids)
                );
    }

    @Unmarshaler
    public static SemanticChronologyDTO make(TinkarInput in) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            ImmutableList<UUID> componentUuids = in.readImmutableUuidList();
            ImmutableList<UUID> definitionForSemanticUuids = in.readImmutableUuidList();
            ImmutableList<UUID> referencedComponentUuids = in.readImmutableUuidList();
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
        out.writeUuidList(componentUuids);
        out.writeUuidList(definitionForSemanticUuids);
        out.writeUuidList(referencedComponentUuids);
        out.writeSemanticVersionList(semanticVersions);
    }

    @Override
    public ImmutableList<SemanticVersionDTO> versions() {
        return semanticVersions.collect(semanticVersionDTO ->  semanticVersionDTO);
    }

    @Override
    public DefinitionForSemanticDTO chronologySet() {
        return new DefinitionForSemanticDTO(definitionForSemanticUuids);
    }
}

