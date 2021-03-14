/*
 * Copyright 2020-2021 HL7.
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
import org.hl7.tinkar.lombok.dto.binary.*;
import org.hl7.tinkar.lombok.dto.json.JSONObject;
import org.hl7.tinkar.lombok.dto.json.JsonMarshalable;
import org.hl7.tinkar.component.*;
import org.hl7.tinkar.lombok.dto.json.ComponentFieldForJson;
import org.hl7.tinkar.lombok.dto.json.JsonSemanticVersionUnmarshaler;

import java.io.Writer;
import java.time.Instant;
import java.util.Objects;

import static org.hl7.tinkar.lombok.dto.json.ComponentFieldForJson.FIELDS;

/**
 *
 * @author kec
 */
@Value
@Accessors(fluent = true)
@ToString(callSuper = true)
public class SemanticVersionDTO
    extends VersionDTO
        implements SemanticVersion, JsonMarshalable, Marshalable {

    private static final int localMarshalVersion = 3;
    @NonNull
    protected final PublicId definitionForSemanticPublicId;
    @NonNull
    protected final PublicId referencedComponentPublicId;
    @NonNull
    protected final ImmutableList<Object> fields;

    public SemanticVersionDTO(@NonNull PublicId componentUuids,
                              @NonNull PublicId definitionForSemanticPublicId,
                              @NonNull PublicId referencedComponentPublicId,
                              @NonNull StampDTO stamp,
                              @NonNull ImmutableList<Object> fields) {
        super(componentUuids, stamp);
        this.definitionForSemanticPublicId = definitionForSemanticPublicId;
        this.referencedComponentPublicId = referencedComponentPublicId;
        this.fields = fields;
    }

    public SemanticVersionDTO(@NonNull PublicId componentPublicId,
                              @NonNull PatternForSemantic patternForSemantic,
                              @NonNull Component referencedComponent,
                              @NonNull Stamp stamp,
                              @NonNull ImmutableList<Object> fields) {
        this(componentPublicId,
             patternForSemantic.publicId(),
             referencedComponent.publicId(),
             StampDTO.make(stamp),
             fields);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SemanticVersionDTO that)) return false;
        if (!super.equals(o)) return false;
        return definitionForSemanticPublicId.equals(that.definitionForSemanticPublicId) && referencedComponentPublicId.equals(that.referencedComponentPublicId) && fields.equals(that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), definitionForSemanticPublicId, referencedComponentPublicId, fields);
    }

    public static SemanticVersionDTO make(SemanticVersion semanticVersion) {
        MutableList<Object> convertedFields = Lists.mutable.empty();
        semanticVersion.fields().forEach(objectToConvert -> {
            if (objectToConvert instanceof Concept concept) {
                convertedFields.add(new ConceptDTO(concept.publicId()));
            } else if (objectToConvert instanceof PatternForSemantic patternForSemantic) {
                convertedFields.add(new PatternForSemanticDTO(patternForSemantic.publicId()));
            } else if (objectToConvert instanceof Semantic semantic) {
                convertedFields.add(new SemanticDTO(semantic.publicId(), semantic.patternForSemantic(),
                        semantic.referencedComponent()));
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
                semanticVersion.patternForSemantic(),
                semanticVersion.referencedComponent(),
                StampDTO.make(semanticVersion.stamp()), convertedFields.toImmutable());
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
        json.put(ComponentFieldForJson.STAMP, stamp);
        json.put(FIELDS, fields);
        json.writeJSONString(writer);
    }

    @JsonSemanticVersionUnmarshaler
    public static SemanticVersionDTO make(JSONObject jsonObject,
                                          PublicId componentPublicId,
                                          PublicId definitionForSemanticPublicId,
                                          PublicId referencedComponentPublicId) {
        JSONObject jsonStampObject = (JSONObject) jsonObject.get(ComponentFieldForJson.STAMP);
        return new SemanticVersionDTO(componentPublicId,
                definitionForSemanticPublicId,
                referencedComponentPublicId,
                StampDTO.make(jsonStampObject),
                jsonObject.asImmutableObjectList(FIELDS));
    }

    @SemanticVersionUnmarshaler
    public static SemanticVersionDTO make(TinkarInput in,
                                          PublicId componentPublicId,
                                          PublicId definitionForSemanticPublicId,
                                          PublicId referencedComponentPublicId) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            return new SemanticVersionDTO(componentPublicId,
                    definitionForSemanticPublicId,
                    referencedComponentPublicId,
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
        out.writeObjectList(fields);
    }
}
