package org.hl7.tinkar.entity.graph;

import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.map.primitive.ImmutableIntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.eclipse.collections.impl.list.mutable.primitive.ByteArrayList;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import org.hl7.tinkar.common.id.VertexId;
import org.hl7.tinkar.component.*;
import org.hl7.tinkar.component.graph.Vertex;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.entity.internal.Get;
import org.hl7.tinkar.lombok.dto.*;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.hl7.tinkar.entity.Entity.ENTITY_FORMAT_VERSION;

public class EntityVertex implements Vertex, VertexId {
    private static final int DEFAULT_SIZE = 64;
    protected long mostSignificantBits;
    protected long leastSignificantBits;
    protected int vertexIndex;
    protected int meaningNid;
    private ImmutableIntObjectMap<Object> properties;

    protected EntityVertex() {}

    public final byte[] getBytes() {
        int bufSize = DEFAULT_SIZE;
        AtomicReference<ByteBuf> byteBufRef =
                new AtomicReference<>(ByteBufPool.allocate(bufSize));
         while (true) {
            try {
                ByteBuf byteBuf = byteBufRef.get();
                byteBuf.writeLong(mostSignificantBits);
                byteBuf.writeLong(leastSignificantBits);
                byteBuf.writeInt(vertexIndex);
                byteBuf.writeInt(meaningNid);
                if (properties == null) {
                    byteBuf.writeInt(0);
                } else {
                    byteBuf.writeInt(properties.size());
                    properties.forEachKeyValue((nid, value) -> {
                        byteBuf.writeInt(nid);
                        SemanticEntityVersion.writeField(byteBuf, value);
                    });
                }
                return byteBuf.asArray();
            } catch (ArrayIndexOutOfBoundsException e) {
                byteBufRef.get().recycle();
                bufSize = bufSize + DEFAULT_SIZE;
                System.out.println("Growing Vertex size: " + bufSize);
                byteBufRef.set(ByteBufPool.allocate(bufSize));
           }
        }
    }

    private void fill(ByteBuf readBuf, byte formatVersion) {
        this.mostSignificantBits = readBuf.readLong();
        this.leastSignificantBits = readBuf.readLong();
        this.vertexIndex = readBuf.readInt();
        this.meaningNid = readBuf.readInt();
        int propertyCount = readBuf.readInt();
        if (propertyCount > 0) {
            MutableIntObjectMap<Object> mutableProperties = IntObjectMaps.mutable.ofInitialCapacity(propertyCount);
            for (int i = 0; i < propertyCount; i++) {
                int conceptNid = readBuf.readInt();
                FieldDataType dataType = FieldDataType.fromToken(readBuf.readByte());
                Object value = SemanticEntityVersion.readDataType(readBuf, dataType, formatVersion);
                mutableProperties.put(conceptNid, value);
            }
            this.properties = mutableProperties.toImmutable();
        } else {
            this.properties = IntObjectMaps.immutable.empty();
        }

    }

    public static <T extends Object> T abstractObject(Object object) {
        if (object instanceof Concept) {
            if (object instanceof ConceptProxy) {
                return (T) object;
            }
             return (T) ConceptProxy.make(Get.entityService().nidForComponent((Concept) object));
        } else if (object instanceof Semantic) {
            if (object instanceof SemanticProxy) {
                return (T) object;
            }
            return (T) SemanticProxy.make(Get.entityService().nidForComponent((Semantic) object));
        } else if (object instanceof TypePattern) {
            if (object instanceof TypePatternProxy) {
                return (T) object;
            }
            return (T) TypePatternProxy.make(Get.entityService().nidForComponent((TypePattern) object));
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

    private void fill(Vertex another) {
        VertexId anotherId = another.vertexId();
        this.mostSignificantBits = anotherId.mostSignificantBits();
        this.leastSignificantBits = anotherId.leastSignificantBits();
        this.vertexIndex = another.vertexIndex();
        if (another.meaning() instanceof ConceptEntity) {
            this.meaningNid = ((ConceptEntity) another.meaning()).nid();
        } else {
            this.meaningNid = Get.entityService().nidForComponent(another.meaning());
        }
        MutableIntObjectMap<Object> mutableProperties = new IntObjectHashMap(another.propertyKeys().size());
        another.propertyKeys().forEach(concept -> {
            mutableProperties.put(Get.entityService().nidForComponent(concept), abstractObject(another.propertyFast(concept)));
        });
        this.properties = mutableProperties.toImmutable();
    }

    @Override
    public VertexId vertexId() {
        return this;
    }

    protected void setVertexIndex(int vertexIndex) {
        this.vertexIndex = vertexIndex;
    }
    @Override
    public int vertexIndex() {
        return vertexIndex;
    }

    @Override
    public long mostSignificantBits() {
        return this.mostSignificantBits;
    }

    @Override
    public long leastSignificantBits() {
        return this.leastSignificantBits;
    }

    @Override
    public Concept meaning() {
        return Get.entityService().getEntityFast(meaningNid);
    }

    @Override
    public <T> Optional<T> property(Concept propertyConcept) {
        if (propertyConcept instanceof ConceptEntity) {
            return property((ConceptEntity) propertyConcept);
        }
        return Optional.ofNullable(propertyFast(propertyConcept));
    }

    @Override
    public <T> T propertyFast(Concept propertyConcept) {
        if (propertyConcept instanceof ConceptEntity) {
            return propertyFast((ConceptEntity) propertyConcept);
        }
        return (T) properties.get(Get.entityService().nidForComponent(propertyConcept));
    }
    public <T> T propertyFast(ConceptEntity conceptEntity) {
        return (T) properties.get(conceptEntity.nid());
    }

    public <T> Optional<T> property(ConceptEntity conceptEntity) {
        return Optional.ofNullable(propertyFast(conceptEntity));
    }

    @Override
    public RichIterable<Concept> propertyKeys() {
        return properties.keySet().collect(nid -> ConceptProxy.make(nid));
    }

    public static EntityVertex make(Vertex vertex) {
        EntityVertex entityVertex = new EntityVertex();
        entityVertex.fill(vertex);
        return entityVertex;
    }

    public static EntityVertex make() {
        EntityVertex entityVertex = new EntityVertex();
        return entityVertex;
    }

    public static EntityVertex make(ByteBuf readBuf, byte entityFormatVersion) {
        if (entityFormatVersion == ENTITY_FORMAT_VERSION) {
            EntityVertex entityVertex = new EntityVertex();
            entityVertex.fill(readBuf, entityFormatVersion);
            return entityVertex;
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + entityFormatVersion);
        }
    }
}
