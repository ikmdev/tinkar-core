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
import org.hl7.tinkar.component.ConceptChronology;
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
public class ConceptChronologyDTO
    extends ConceptDTO
        implements JsonMarshalable, Marshalable, ConceptChronology<ConceptVersionDTO>, DTO {

    private static final int localMarshalVersion = 3;
    @NonNull
    private final PublicId chronologySetPublicId;
    @NonNull
    private final ImmutableList<ConceptVersionDTO> conceptVersions;

    public ConceptChronologyDTO(@NonNull PublicId componentPublicId,
                                @NonNull PublicId chronologySetPublicId,
                                @NonNull ImmutableList<ConceptVersionDTO> conceptVersions) {
        super(componentPublicId);
        this.chronologySetPublicId = chronologySetPublicId;
        this.conceptVersions = conceptVersions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConceptChronologyDTO)) return false;
        if (!super.equals(o)) return false;
        ConceptChronologyDTO that = (ConceptChronologyDTO) o;
        return chronologySetPublicId.equals(that.chronologySetPublicId) && conceptVersions.equals(that.conceptVersions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), chronologySetPublicId, conceptVersions);
    }

    public static ConceptChronologyDTO make(ConceptChronology<ConceptVersionDTO> conceptChronology) {
        MutableList<ConceptVersionDTO> versions = Lists.mutable.ofInitialCapacity(conceptChronology.versions().size());
        for (ConceptVersionDTO conceptVersion : conceptChronology.versions()) {
            versions.add(ConceptVersionDTO.make(conceptVersion));
        }
        return new ConceptChronologyDTO(conceptChronology.publicId(),
                conceptChronology.chronologySet().publicId(),
                versions.toImmutable());
    }

    @Override
    public Concept chronologySet() {
        return new ConceptDTO(chronologySetPublicId);
    }

    @Override
    public ImmutableList<ConceptVersionDTO> versions() {
        return conceptVersions.collect(conceptVersionDTO -> conceptVersionDTO);
    }

    @Override
    public void jsonMarshal(Writer writer) {
        final JSONObject json = new JSONObject();
        json.put(ComponentFieldForJson.CLASS, this.getClass().getCanonicalName());
        json.put(ComponentFieldForJson.COMPONENT_PUBLIC_ID, publicId());
        json.put(ComponentFieldForJson.CHRONOLOGY_SET_PUBLIC_ID, chronologySetPublicId);
        json.put(ComponentFieldForJson.CONCEPT_VERSIONS, conceptVersions);
        json.writeJSONString(writer);
    }

    @JsonChronologyUnmarshaler
    public static ConceptChronologyDTO make(JSONObject jsonObject) {
        PublicId publicId = jsonObject.asPublicId(ComponentFieldForJson.COMPONENT_PUBLIC_ID);
        return new ConceptChronologyDTO(publicId,
                        jsonObject.asPublicId(ComponentFieldForJson.CHRONOLOGY_SET_PUBLIC_ID),
                        jsonObject.asConceptVersionList(ComponentFieldForJson.CONCEPT_VERSIONS, publicId));
    }

    @Unmarshaler
    public static ConceptChronologyDTO make(TinkarInput in) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            PublicId publicId = in.getPublicId();
            return new ConceptChronologyDTO(
                    publicId, in.getPublicId(), in.readConceptVersionList(publicId));
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + in.getTinkerFormatVersion());
        }
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        out.putPublicId(publicId());
        out.putPublicId(chronologySetPublicId);
        // Note that the componentIds are not written redundantly
        // in writeConceptVersionList...
        out.writeConceptVersionList(conceptVersions);
    }
}
