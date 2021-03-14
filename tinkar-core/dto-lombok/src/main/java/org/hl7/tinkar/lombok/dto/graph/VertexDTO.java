package org.hl7.tinkar.lombok.dto.graph;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Accessors;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.list.mutable.primitive.ByteArrayList;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.id.VertexId;
import org.hl7.tinkar.common.id.VertexIds;
import org.hl7.tinkar.component.graph.Vertex;
import org.hl7.tinkar.lombok.dto.binary.*;
import org.hl7.tinkar.lombok.dto.json.*;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.component.TypePatternForSemantic;
import org.hl7.tinkar.component.Semantic;
import org.hl7.tinkar.component.Stamp;
import org.hl7.tinkar.lombok.dto.*;

import java.io.Writer;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Value
@Accessors(fluent = true)
public final class VertexDTO implements Vertex, JsonMarshalable, Marshalable {
    private static final int localMarshalVersion = 3;

    @NonNull
    private final long vertexIdMsb;

    @NonNull
    private final long vertexIdLsb;

    @NonNull
    private final int vertexIndex;

    @NonNull
    private final ConceptDTO meaning;

    @NonNull
    private final ImmutableMap<ConceptDTO, Object> properties;

    @Builder
    public VertexDTO(@NonNull long vertexIdMsb, @NonNull long vertexIdLsb, @NonNull int vertexIndex,
                     @NonNull ConceptDTO meaning, @NonNull ImmutableMap<ConceptDTO, Object> properties) {
        this.vertexIdMsb = vertexIdMsb;
        this.vertexIdLsb = vertexIdLsb;
        this.vertexIndex = vertexIndex;
        this.meaning = meaning;
        this.properties = abstractProperties(properties);
    }

    public static <T extends Object> T abstractObject(Object object) {
        if (object instanceof Concept conceptValue) {
            if (object instanceof ConceptDTO & !(object instanceof ConceptChronologyDTO)) {
                return (T) object;
            }
            return (T) ConceptDTO.builder().componentPublicId(conceptValue.publicId()).build();
        } else if (object instanceof Semantic semanticValue) {
            if (object instanceof SemanticDTO & !(object instanceof SemanticChronologyDTO)) {
                return (T) object;
            }
            return (T) SemanticDTO.builder().componentPublicId(semanticValue.publicId()).build();
        } else if (object instanceof TypePatternForSemantic typePatternForSemanticValue) {
            if (object instanceof TypePatternForSemanticDTO & !(object instanceof TypePatternForSemanticChronologyDTO)) {
                return (T) object;
            }
            return (T) TypePatternForSemanticDTO.builder().componentPublicId(typePatternForSemanticValue.publicId()).build();
        } else if (object instanceof Stamp & !(object instanceof StampDTO)) {
            Stamp stampValue = (Stamp) object;
            return (T) StampDTO.builder()
                    .componentPublicId(stampValue.publicId())
                    .statusPublicId(stampValue.state().publicId())
                    .time(stampValue.time())
                    .authorPublicId(stampValue.author().publicId())
                    .modulePublicId(stampValue.module().publicId())
                    .pathPublicId(stampValue.path().publicId()).build();
        } else if (object instanceof Double) {
            object = ((Double) object).floatValue();
        } else if (object instanceof Integer) {
            object = ((Integer) object).longValue();
        } else if (object instanceof byte[] byteArray) {
            object = new ByteArrayList(byteArray);
        }
        return (T) object;
    }

    public static ImmutableMap<ConceptDTO, Object> abstractProperties(ImmutableMap<ConceptDTO, Object> incoming) {
        MutableMap<ConceptDTO, Object> outgoing = Maps.mutable.ofInitialCapacity(incoming.size());
        incoming.forEachKeyValue((key, value) -> {
            outgoing.put(abstractObject(key), abstractObject(value));
        });
        return outgoing.toImmutable();
    }

    @Override
    public <T> Optional<T> property(Concept propertyConcept) {
        return Optional.ofNullable((T) properties.get(propertyConcept));
    }

    @Override
    public <T> T propertyFast(Concept propertyConcept) {
        return (T) properties.get(propertyConcept);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VertexDTO vertexDTO)) return false;
        return vertexIdLsb == vertexDTO.vertexIdLsb &&
                vertexIdMsb == vertexDTO.vertexIdMsb &&
                meaning.equals(vertexDTO.meaning) &&
                properties.equals(vertexDTO.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vertexIdMsb, vertexIdLsb, meaning);
    }

    @Override
    public void jsonMarshal(Writer writer) {
        final JSONObject json = new JSONObject();
        json.put(ComponentFieldForJson.CLASS, this.getClass().getCanonicalName());
        json.put(ComponentFieldForJson.VERTEX_ID, vertexId());
        json.put(ComponentFieldForJson.VERTEX_INDEX, vertexIndex());
        json.put(ComponentFieldForJson.VERTEX_MEANING, meaning());
        final JSONObject jsonPropertyMap = new JSONObject();
        json.put(ComponentFieldForJson.VERTEX_PROPERTIES, jsonPropertyMap);
        properties.forEachKeyValue((conceptKey, value) -> {
            jsonPropertyMap.put(conceptKey.componentPublicId().toString(), value);
        });
        json.writeJSONString(writer);
    }

    @JsonChronologyUnmarshaler
    public static VertexDTO make(JSONObject jsonObject) {
        JSONArray idParts = (JSONArray) jsonObject.get(ComponentFieldForJson.VERTEX_ID);
        UUID vertexUuid = (UUID) idParts.get(0);
        return VertexDTO.builder().vertexIdMsb(vertexUuid.getMostSignificantBits())
                .vertexIdLsb(vertexUuid.getLeastSignificantBits())
                .vertexIndex(((Long) jsonObject.get(ComponentFieldForJson.VERTEX_INDEX)).intValue())
                .meaning(jsonObject.asConcept(ComponentFieldForJson.VERTEX_MEANING))
                .properties(jsonObject.getConceptObjectMap(ComponentFieldForJson.VERTEX_PROPERTIES)).build();
    }

    @Unmarshaler
    public static VertexDTO make(TinkarInput in) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            UUID vertexUuid = in.getUuid();
            int vertexSequence = in.getInt();
            PublicId meaningId = in.getPublicId();
            final ImmutableMap<ConceptDTO, Object> immutableProperties;
            int propertyCount = in.getInt();
            if (propertyCount > 0) {
                MutableMap<ConceptDTO, Object> mutableProperties = Maps.mutable.ofInitialCapacity(propertyCount);
                for (int i = 0; i < propertyCount; i++) {
                    ConceptDTO conceptKey = new ConceptDTO(in.getPublicId());
                    Object object = in.getTinkarNativeObject();
                    mutableProperties.put(conceptKey, object);
                }
                immutableProperties = mutableProperties.toImmutable();
            } else {
                immutableProperties = Maps.immutable.empty();
            }

            return VertexDTO.builder()
                    .vertexIdMsb(vertexUuid.getMostSignificantBits())
                    .vertexIdLsb(vertexUuid.getLeastSignificantBits())
                    .vertexIndex(vertexSequence)
                    .meaning(new ConceptDTO(meaningId))
                    .properties(immutableProperties).build();
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + marshalVersion);
        }
    }

    @Override
    public RichIterable<ConceptDTO> propertyKeys() {
        return this.properties.keysView();
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        out.putLong(vertexIdMsb);
        out.putLong(vertexIdLsb);
        out.putInt(vertexIndex);
        out.putPublicId(meaning.componentPublicId());
        out.putInt(properties.size());
        properties.forEachKeyValue((conceptKey, object) -> {
            out.putPublicId(conceptKey);
            out.putTinkarNativeObject(object);
        });
    }

    @Override
    public VertexId vertexId() {
        return VertexIds.of(this.vertexIdMsb, this.vertexIdLsb);
    }
}
