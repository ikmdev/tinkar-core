package org.hl7.tinkar.lombok.dto.graph;

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
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.component.TypePattern;
import org.hl7.tinkar.component.Semantic;
import org.hl7.tinkar.component.Stamp;
import org.hl7.tinkar.lombok.dto.*;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import io.soabase.recordbuilder.core.RecordBuilder;

@RecordBuilder
public final record VertexDTO(long vertexIdMsb, long vertexIdLsb, int vertexIndex,
                              ConceptDTO meaning, ImmutableMap<ConceptDTO, Object> properties)
        implements Vertex, Marshalable {
    private static final int localMarshalVersion = 3;


    public static <T extends Object> T abstractObject(Object object) {
        if (object instanceof Concept conceptValue) {
            if (object instanceof ConceptDTO & !(object instanceof ConceptChronologyDTO)) {
                return (T) object;
            }
            return (T) ConceptDTOBuilder.builder().publicId(conceptValue.publicId()).build();
        } else if (object instanceof Semantic semanticValue) {
            if (object instanceof SemanticDTO & !(object instanceof SemanticChronologyDTO)) {
                return (T) object;
            }
            return (T) SemanticDTOBuilder.builder().publicId(semanticValue.publicId()).build();
        } else if (object instanceof TypePattern typePatternValue) {
            if (object instanceof TypePatternDTO & !(object instanceof TypePatternChronologyDTO)) {
                return (T) object;
            }
            return (T) TypePatternDTOBuilder.builder().publicId(typePatternValue.publicId()).build();
        } else if (object instanceof Stamp & !(object instanceof StampDTO)) {
            Stamp stampValue = (Stamp) object;
            return (T) StampDTOBuilder.builder()
                    .publicId(stampValue.publicId())
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
        if (vertexIdLsb == vertexDTO.vertexIdLsb &&
                vertexIdMsb == vertexDTO.vertexIdMsb &&
                meaning.equals(vertexDTO.meaning) &&
        properties.size() == vertexDTO.properties.size()) {
            for (Concept concept: properties.keysView()) {
                Object value1 = properties.get(concept);
                Object value2 = properties.get(concept);
                if (value1.getClass().equals(value2.getClass())) {
                    if (value1.getClass().isArray()) {
                        if (!arrayEquals(value1, value2)) {
                            return false;
                        }
                    } else if (!value1.equals(value2)) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
            return true;
        }
        return false;

    }
    public static boolean arrayEquals(Object arr1, Object arr2) {
        try {
            Class<?> c = arr1.getClass();
            if (!c.getComponentType().isPrimitive()) {
                c = Object[].class;
            }
            Method m = Arrays.class.getMethod("equals", c, c);
            return (Boolean) m.invoke(null, arr1, arr2);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public int hashCode() {
        return Objects.hash(vertexIdMsb, vertexIdLsb, meaning);
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

            return VertexDTOBuilder.builder()
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
        out.putPublicId(meaning.publicId());
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
