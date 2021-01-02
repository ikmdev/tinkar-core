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
import org.hl7.tinkar.component.*;
import org.hl7.tinkar.lombok.dto.binary.*;
import org.hl7.tinkar.lombok.dto.json.ComponentFieldForJson;
import org.hl7.tinkar.lombok.dto.json.JSONObject;
import org.hl7.tinkar.lombok.dto.json.JsonMarshalable;
import org.hl7.tinkar.lombok.dto.json.JsonSemanticVersionUnmarshaler;

import java.io.Writer;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

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
    protected final ImmutableList<UUID> definitionForSemanticUuids;
    @NonNull
    protected final ImmutableList<UUID> referencedComponentUuids;
    @NonNull
    protected final ImmutableList<Object> fields;

    public SemanticVersionDTO(@NonNull ImmutableList<UUID> componentUuids,
                              @NonNull ImmutableList<UUID> definitionForSemanticUuids,
                              @NonNull ImmutableList<UUID> referencedComponentUuids,
                              @NonNull StampDTO stamp,
                               @NonNull ImmutableList<Object> fields) {
        super(componentUuids, stamp);
        this.definitionForSemanticUuids = definitionForSemanticUuids;
        this.referencedComponentUuids = referencedComponentUuids;
        this.fields = fields;
    }

    public SemanticVersionDTO(@NonNull ImmutableList<UUID> componentUuids,
                              @NonNull DefinitionForSemantic definitionForSemantic,
                              @NonNull Component referencedComponent,
                              @NonNull Stamp stamp,
                              @NonNull ImmutableList<Object> fields) {
        this(componentUuids,
             definitionForSemantic.componentUuids(),
             referencedComponent.componentUuids(),
             StampDTO.make(stamp),
             fields);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SemanticVersionDTO)) return false;
        if (!super.equals(o)) return false;
        SemanticVersionDTO that = (SemanticVersionDTO) o;
        return definitionForSemanticUuids.equals(that.definitionForSemanticUuids) && referencedComponentUuids.equals(that.referencedComponentUuids) && fields.equals(that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), definitionForSemanticUuids, referencedComponentUuids, fields);
    }

    public static SemanticVersionDTO make(SemanticVersion semanticVersion) {
        MutableList<Object> convertedFields = Lists.mutable.empty();
        semanticVersion.fields().forEach(objectToConvert -> {
            if (objectToConvert instanceof Concept) {
                Concept concept = (Concept) objectToConvert;
                convertedFields.add(new ConceptDTO(concept.componentUuids()));
            } else if (objectToConvert instanceof DefinitionForSemantic) {
                DefinitionForSemantic definitionForSemantic = (DefinitionForSemantic) objectToConvert;
                convertedFields.add(new DefinitionForSemanticDTO(definitionForSemantic.componentUuids()));
            } else if (objectToConvert instanceof Semantic) {
                Semantic semantic = (Semantic) objectToConvert;
                convertedFields.add(new SemanticDTO(semantic.componentUuids(), semantic.definitionForSemantic(),
                        semantic.referencedComponent()));
            } else if (objectToConvert instanceof Component) {
                Component component = (Component) objectToConvert;
                convertedFields.add(new ComponentDTO(component.componentUuids()));
            } else if (objectToConvert instanceof Number) {
                Number number = (Number) objectToConvert;
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
        return new SemanticVersionDTO(semanticVersion.componentUuids(),
                semanticVersion.definitionForSemantic(),
                semanticVersion.referencedComponent(),
                StampDTO.make(semanticVersion.stamp()), convertedFields.toImmutable());
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
        json.put(ComponentFieldForJson.STAMP, stamp);
        json.put(FIELDS, fields);
        json.writeJSONString(writer);
    }

    @JsonSemanticVersionUnmarshaler
    public static SemanticVersionDTO make(JSONObject jsonObject,
                                          ImmutableList<UUID> componentUuids,
                                          ImmutableList<UUID> definitionForSemanticUuids,
                                          ImmutableList<UUID> referencedComponentUuids) {
        JSONObject jsonStampObject = (JSONObject) jsonObject.get(ComponentFieldForJson.STAMP);
        return new SemanticVersionDTO(componentUuids,
                definitionForSemanticUuids,
                referencedComponentUuids,
                StampDTO.make(jsonStampObject),
                jsonObject.asImmutableObjectList(FIELDS));
    }

    @SemanticVersionUnmarshaler
    public static SemanticVersionDTO make(TinkarInput in,
                                          ImmutableList<UUID> componentUuids,
                                          ImmutableList<UUID> definitionForSemanticUuids,
                                          ImmutableList<UUID> referencedComponentUuids) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            return new SemanticVersionDTO(componentUuids,
                    definitionForSemanticUuids,
                    referencedComponentUuids,
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
