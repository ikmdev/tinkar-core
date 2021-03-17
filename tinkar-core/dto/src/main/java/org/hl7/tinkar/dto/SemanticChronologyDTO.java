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
import org.hl7.tinkar.component.Component;
import org.hl7.tinkar.component.SemanticChronology;
import org.hl7.tinkar.component.TypePattern;
import org.hl7.tinkar.dto.binary.*;

/**
 *
 * @author kec
 */
public record SemanticChronologyDTO(PublicId publicId,
                                    PublicId typePatternPublicId,
                                    PublicId referencedComponentPublicId,
                                    ImmutableList<SemanticVersionDTO> semanticVersions)
    implements SemanticChronology<SemanticVersionDTO>, DTO, Marshalable {

    private static final int localMarshalVersion = 3;

    public SemanticChronologyDTO(PublicId componentUuids,
                                 TypePattern typePatternForSemantic,
                                 Component referencedComponent,
                                 ImmutableList<SemanticVersionDTO> semanticVersions) {
        this(componentUuids,
                typePatternForSemantic.publicId(),
                referencedComponent.publicId(),
                semanticVersions);
    }

    public static SemanticChronologyDTO make(SemanticChronology<SemanticVersionDTO> semanticChronology) {
        MutableList<SemanticVersionDTO> changeSetVersions = Lists.mutable.ofInitialCapacity(semanticChronology.versions().size());
        for (SemanticVersionDTO semanticVersion : semanticChronology.versions()) {
            changeSetVersions.add(SemanticVersionDTO.make(semanticVersion));
        }
        return new SemanticChronologyDTO(semanticChronology.publicId(),
                semanticChronology.typePattern(),
                semanticChronology.referencedComponent(),
                changeSetVersions.toImmutable());
    }

    @Override
    public Component referencedComponent() {
        return new ComponentDTO(referencedComponentPublicId);
    }

    @Override
    public TypePattern typePattern() {
        return new TypePatternDTO(typePatternPublicId);
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
        out.putPublicId(typePatternPublicId);
        out.putPublicId(referencedComponentPublicId);
        out.writeSemanticVersionList(semanticVersions);
    }

    @Override
    public ImmutableList<SemanticVersionDTO> versions() {
        return semanticVersions.collect(semanticVersionDTO ->  semanticVersionDTO);
    }
}

