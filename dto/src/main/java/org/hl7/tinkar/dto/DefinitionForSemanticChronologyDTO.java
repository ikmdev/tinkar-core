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
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.component.DefinitionForSemanticChronology;
import org.hl7.tinkar.dto.binary.*;
import org.hl7.tinkar.dto.json.ComponentFieldForJson;
import org.hl7.tinkar.dto.json.JSONObject;
import org.hl7.tinkar.dto.json.JsonChronologyUnmarshaler;
import org.hl7.tinkar.dto.json.JsonMarshalable;

import java.io.Writer;
import java.util.UUID;

/**
 *
 * @author kec
 */
public record DefinitionForSemanticChronologyDTO(ImmutableList<UUID> componentUuids,
                                                 ImmutableList<UUID> chronologySetUuids,
                                                 ImmutableList<DefinitionForSemanticVersionDTO> definitionVersions)
        implements DefinitionForSemanticChronology<DefinitionForSemanticVersionDTO>, DTO, JsonMarshalable, Marshalable {

    private static final int localMarshalVersion = 3;


    public static DefinitionForSemanticChronologyDTO make(DefinitionForSemanticChronology<DefinitionForSemanticVersionDTO> definitionForSemanticChronology) {
        MutableList<DefinitionForSemanticVersionDTO> versions = Lists.mutable.ofInitialCapacity(definitionForSemanticChronology.versions().size());
        for (DefinitionForSemanticVersionDTO definitionVersion : definitionForSemanticChronology.versions()) {
            versions.add(DefinitionForSemanticVersionDTO.make(definitionVersion));
        }
        return new DefinitionForSemanticChronologyDTO(definitionForSemanticChronology.componentUuids(),
                definitionForSemanticChronology.chronologySet().componentUuids(),
                versions.toImmutable());
    }

    @Override
    public ImmutableList<DefinitionForSemanticVersionDTO> versions() {
        return definitionVersions.collect(definitionForSemanticVersionDTO -> definitionForSemanticVersionDTO);
    }

    @Override
    public Concept chronologySet() {
        return new ConceptDTO(chronologySetUuids);
    }

    @Override
    public void jsonMarshal(Writer writer) {
        final JSONObject json = new JSONObject();
        json.put(ComponentFieldForJson.CLASS, this.getClass().getCanonicalName());
        json.put(ComponentFieldForJson.COMPONENT_UUIDS, componentUuids);
        json.put(ComponentFieldForJson.CHRONOLOGY_SET_UUIDS, chronologySetUuids);
        json.put(ComponentFieldForJson.DEFINITION_VERSIONS, definitionVersions);
        json.writeJSONString(writer);
    }
    
    @JsonChronologyUnmarshaler
    public static DefinitionForSemanticChronologyDTO make(JSONObject jsonObject) {
        ImmutableList<UUID> componentUuids = jsonObject.asImmutableUuidList(ComponentFieldForJson.COMPONENT_UUIDS);
        return new DefinitionForSemanticChronologyDTO(componentUuids,
                        jsonObject.asImmutableUuidList(ComponentFieldForJson.CHRONOLOGY_SET_UUIDS),
                        jsonObject.asDefinitionForSemanticVersionList(ComponentFieldForJson.DEFINITION_VERSIONS, componentUuids));
    }

    @Unmarshaler
    public static DefinitionForSemanticChronologyDTO make(TinkarInput in) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            ImmutableList<UUID> componentUuids = in.readImmutableUuidList();
            return new DefinitionForSemanticChronologyDTO(
                    componentUuids, in.readImmutableUuidList(), in.readDefinitionForSemanticVersionList(componentUuids));
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + in.getTinkerFormatVersion());
        }
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        out.writeUuidList(componentUuids);
        out.writeUuidList(chronologySetUuids);
        out.writeDefinitionForSemanticVersionList(definitionVersions);
    }
}
