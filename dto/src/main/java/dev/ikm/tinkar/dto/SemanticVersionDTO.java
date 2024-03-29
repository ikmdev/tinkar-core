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
import dev.ikm.tinkar.component.*;
import dev.ikm.tinkar.dto.binary.*;

import java.time.Instant;

/**
 * 
 */
@RecordBuilder
public record SemanticVersionDTO(PublicId publicId,
                                 StampDTO stamp,
                                 ImmutableList<Object> fieldValues)
        implements SemanticVersion, Marshalable {

    private static final int localMarshalVersion = 3;


    public SemanticVersionDTO(PublicId publicId,
                              Stamp stamp,
                              ImmutableList<Object> fields) {
        this(publicId,
                StampDTO.make(stamp),
                fields);
    }

    public static SemanticVersionDTO make(SemanticVersion semanticVersion) {
        MutableList<Object> convertedFields = Lists.mutable.empty();
        semanticVersion.fieldValues().forEach(objectToConvert -> {
            if (objectToConvert instanceof Concept concept) {
                convertedFields.add(new ConceptDTO(concept.publicId()));
            } else if (objectToConvert instanceof Pattern pattern) {
                convertedFields.add(new PatternDTO(pattern.publicId()));
            } else if (objectToConvert instanceof Semantic semantic) {
                convertedFields.add(new SemanticDTO(semantic.publicId()));
            } else if (objectToConvert instanceof Component component) {
                convertedFields.add(new ComponentDTO(component.publicId()));
            } else if (objectToConvert instanceof Number number) {
                if (number instanceof Long) {
                    convertedFields.add(number.intValue());
                } else if (number instanceof Double) {
                    convertedFields.add(number.floatValue());
                } else {
                    convertedFields.add(number);
                }
            } else if (objectToConvert instanceof String) {
                convertedFields.add(objectToConvert);
            } else if (objectToConvert instanceof Instant) {
                convertedFields.add(objectToConvert);
            } else {
                throw new UnsupportedOperationException("Can't convert:\n  " + objectToConvert + "\nin\n  " + semanticVersion);
            }
        });
        return new SemanticVersionDTO(semanticVersion.publicId(),
                StampDTO.make(semanticVersion.stamp()), convertedFields.toImmutable());
    }

    @SemanticVersionUnmarshaler
    public static SemanticVersionDTO make(TinkarInput in,
                                          PublicId componentPublicId) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            return new SemanticVersionDTO(componentPublicId,
                    StampDTO.make(in),
                    in.readImmutableObjectList());
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + in.getTinkerFormatVersion());
        }
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        stamp.marshal(out);
        out.writeObjectList(fieldValues);
    }
}
