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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.component.ConceptChronology;
import org.hl7.tinkar.component.ConceptVersion;
import org.hl7.tinkar.dto.binary.*;
import org.hl7.tinkar.dto.json.ComponentFieldForJson;
import org.hl7.tinkar.dto.json.JSONObject;
import org.hl7.tinkar.dto.json.JsonChronologyUnmarshaler;
import org.hl7.tinkar.dto.json.JsonMarshalable;

import java.io.IOException;
import java.io.Writer;
import java.util.UUID;

/**
 *
 * @author kec
 */
public record ConceptChronologyDTO(ImmutableList<UUID> componentUuids,
                                   ImmutableList<UUID> chronologySetUuids,
                                   ImmutableList<ConceptVersionDTO> conceptVersions)
        implements JsonMarshalable, Marshalable, ConceptChronology<ConceptVersionDTO>, DTO {

    private static final int localMarshalVersion = 3;


    public static ConceptChronologyDTO make(ConceptChronology<ConceptVersionDTO> conceptChronology) {
        MutableList<ConceptVersionDTO> versions = Lists.mutable.ofInitialCapacity(conceptChronology.versions().size());
        for (ConceptVersionDTO conceptVersion : conceptChronology.versions()) {
            versions.add(ConceptVersionDTO.make(conceptVersion));
        }
        return new ConceptChronologyDTO(conceptChronology.componentUuids(),
                conceptChronology.chronologySet().componentUuids(),
                versions.toImmutable());
    }

    @Override
    public Concept chronologySet() {
        return new ConceptDTO(chronologySetUuids);
    }

    @Override
    public ImmutableList<ConceptVersionDTO> versions() {
        return conceptVersions.collect(conceptVersionDTO -> conceptVersionDTO);
    }

    @Override
    public void jsonMarshal(Writer writer) {
        final JSONObject json = new JSONObject();
        json.put(ComponentFieldForJson.CLASS, this.getClass().getCanonicalName());
        json.put(ComponentFieldForJson.COMPONENT_UUIDS, componentUuids);
        json.put(ComponentFieldForJson.CHRONOLOGY_SET_UUIDS, chronologySetUuids);
        json.put(ComponentFieldForJson.CONCEPT_VERSIONS, conceptVersions);
        json.writeJSONString(writer);
    }

    @JsonChronologyUnmarshaler
    public static ConceptChronologyDTO make(JSONObject jsonObject) {
        ImmutableList<UUID> componentUuids = jsonObject.asImmutableUuidList(ComponentFieldForJson.COMPONENT_UUIDS);
        return new ConceptChronologyDTO(componentUuids,
                        jsonObject.asImmutableUuidList(ComponentFieldForJson.CHRONOLOGY_SET_UUIDS),
                        jsonObject.asConceptVersionList(ComponentFieldForJson.CONCEPT_VERSIONS, componentUuids));
    }

    @Unmarshaler
    public static ConceptChronologyDTO make(TinkarInput in) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            ImmutableList<UUID> componentUuids = in.readImmutableUuidList();
            return new ConceptChronologyDTO(
                    componentUuids, in.readImmutableUuidList(), in.readConceptVersionList(componentUuids));
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + in.getTinkerFormatVersion());
        }
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        out.writeUuidList(componentUuids);
        out.writeUuidList(chronologySetUuids);
        // Note that the componentIds are not written redundantly
        // in writeConceptVersionList...
        out.writeConceptVersionList(conceptVersions);
    }
}
