/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.dto;

import dev.ikm.tinkar.dto.binary.*;
import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.component.Component;
import dev.ikm.tinkar.component.Pattern;
import dev.ikm.tinkar.component.SemanticChronology;
import dev.ikm.tinkar.component.SemanticVersion;
import dev.ikm.tinkar.dto.binary.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 */
@RecordBuilder
public record SemanticChronologyDTO(PublicId publicId,
                                    PublicId patternPublicId,
                                    PublicId referencedComponentPublicId,
                                    ImmutableList<SemanticVersionDTO> semanticVersions)
        implements SemanticChronology<SemanticVersionDTO>, DTO, Marshalable {
    private static final Logger LOG = LoggerFactory.getLogger(SemanticChronologyDTO.class);
    private static final int localMarshalVersion = 3;

    public SemanticChronologyDTO(PublicId componentUuids,
                                 Pattern patternForSemantic,
                                 Component referencedComponent,
                                 ImmutableList<SemanticVersionDTO> semanticVersions) {
        this(componentUuids,
                patternForSemantic.publicId(),
                referencedComponent.publicId(),
                semanticVersions);
    }

    public static SemanticChronologyDTO make(SemanticChronology<? extends SemanticVersion> semanticChronology) {
        MutableList<SemanticVersionDTO> changeSetVersions = Lists.mutable.ofInitialCapacity(semanticChronology.versions().size());
        for (SemanticVersion semanticVersion : semanticChronology.versions()) {
            changeSetVersions.add(SemanticVersionDTO.make(semanticVersion));
        }
        return new SemanticChronologyDTO(semanticChronology.publicId(),
                semanticChronology.pattern(),
                semanticChronology.referencedComponent(),
                changeSetVersions.toImmutable());
    }

    @Unmarshaler
    public static SemanticChronologyDTO make(TinkarInput in) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            PublicId componentUuids = in.getPublicId();
            PublicId patternUuids = in.getPublicId();
            PublicId referencedComponentUuids = in.getPublicId();
            return new SemanticChronologyDTO(
                    componentUuids, patternUuids, referencedComponentUuids,
                    in.readSemanticVersionList(componentUuids, patternUuids, referencedComponentUuids));
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + in.getTinkerFormatVersion());
        }
    }

    @Override
    public Component referencedComponent() {
        return new ComponentDTO(referencedComponentPublicId);
    }

    @Override
    public Pattern pattern() {
        return new PatternDTO(patternPublicId);
    }

    @Override
    public ImmutableList<SemanticVersionDTO> versions() {
        return semanticVersions.collect(semanticVersionDTO -> semanticVersionDTO);
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        out.putPublicId(publicId());
        out.putPublicId(patternPublicId);
        out.putPublicId(referencedComponentPublicId);
        out.writeSemanticVersionList(semanticVersions);
    }
}

