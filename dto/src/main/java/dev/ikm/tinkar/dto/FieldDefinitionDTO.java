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
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.component.Concept;
import dev.ikm.tinkar.component.FieldDefinition;
import dev.ikm.tinkar.dto.binary.*;


@RecordBuilder
public record FieldDefinitionDTO(PublicId dataTypePublicId, PublicId purposePublicId, PublicId meaningPublicId)
        implements FieldDefinition, Marshalable {

    private static final int localMarshalVersion = 3;

    public static FieldDefinitionDTO make(FieldDefinition fieldDefinition) {
        return new FieldDefinitionDTO(fieldDefinition.dataType().publicId(),
                fieldDefinition.purpose().publicId(), fieldDefinition.meaning().publicId());
    }

    @Unmarshaler
    public static FieldDefinitionDTO make(TinkarInput in) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            return new FieldDefinitionDTO(
                    in.getPublicId(),
                    in.getPublicId(),
                    in.getPublicId());
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + in.getTinkerFormatVersion());
        }
    }

    @Override
    public Concept dataType() {
        return new ConceptDTO(dataTypePublicId);
    }

    @Override
    public Concept purpose() {
        return new ConceptDTO(purposePublicId);
    }

    @Override
    public Concept meaning() {
        return new ConceptDTO(meaningPublicId);
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        out.putPublicId(dataTypePublicId);
        out.putPublicId(purposePublicId);
        out.putPublicId(meaningPublicId);
    }
}
