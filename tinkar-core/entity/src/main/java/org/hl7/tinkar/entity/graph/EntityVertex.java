package org.hl7.tinkar.entity.graph;

import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.map.primitive.ImmutableIntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.eclipse.collections.impl.list.mutable.primitive.ByteArrayList;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import org.hl7.tinkar.common.id.VertexId;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.component.*;
import org.hl7.tinkar.component.graph.Vertex;
import org.hl7.tinkar.dto.*;
import org.hl7.tinkar.entity.EntityRecordFactory;
import org.hl7.tinkar.entity.EntityService;
import org.hl7.tinkar.terms.ConceptFacade;
import org.hl7.tinkar.terms.EntityProxy;
import org.hl7.tinkar.terms.PatternFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongConsumer;

import static org.hl7.tinkar.entity.EntityRecordFactory.ENTITY_FORMAT_VERSION;

public class EntityVertex implements Vertex, VertexId {
    private static final Logger LOG = LoggerFactory.getLogger(EntityVertex.class);
    private static final int DEFAULT_SIZE = 64;
    protected long mostSignificantBits;
    protected long leastSignificantBits;
    protected int vertexIndex = -1;
    protected int meaningNid;
    private ImmutableIntObjectMap<Object> properties;
    private MutableIntObjectMap<Object> uncommittedProperties;

    protected EntityVertex() {
    }

    public static EntityVertex make(Vertex vertex) {
        EntityVertex entityVertex = new EntityVertex();
        entityVertex.fill(vertex);
        return entityVertex;
    }

    private void fill(Vertex another) {
        VertexId anotherId = another.vertexId();
        this.mostSignificantBits = anotherId.mostSignificantBits();
        this.leastSignificantBits = anotherId.leastSignificantBits();
        this.vertexIndex = another.vertexIndex();
        if (another.meaning() instanceof ConceptFacade) {
            this.meaningNid = ((ConceptFacade) another.meaning()).nid();
        } else {
            this.meaningNid = EntityService.get().nidForComponent(another.meaning());
        }
        MutableIntObjectMap<Object> mutableProperties = new IntObjectHashMap(another.propertyKeys().size());
        another.propertyKeys().forEach(concept -> {
            mutableProperties.put(EntityService.get().nidForComponent(concept), abstractObject(another.propertyFast(concept)));
        });
        this.properties = mutableProperties.toImmutable();
    }

    public static <T extends Object> T abstractObject(Object object) {
        if (object instanceof Concept concept) {
            if (object instanceof EntityProxy.Concept) {
                return (T) object;
            }
            int nid = PrimitiveData.get().nidForPublicId(concept.publicId());
            if (concept instanceof ConceptDTO) {
                return (T) EntityProxy.Concept.make(nid);
            }
            return (T) EntityProxy.Concept.make(nid);
        } else if (object instanceof Semantic semantic) {
            if (object instanceof EntityProxy.Semantic) {
                return (T) object;
            }
            int nid = PrimitiveData.get().nidForPublicId(semantic.publicId());
            if (semantic instanceof SemanticDTO) {
                return (T) EntityProxy.Semantic.make(nid);
            }
            return (T) EntityProxy.Semantic.make(nid);
        } else if (object instanceof Pattern pattern) {
            if (object instanceof EntityProxy.Pattern) {
                return (T) object;
            }
            int nid = PrimitiveData.get().nidForPublicId(pattern.publicId());
            if (pattern instanceof PatternDTO) {
                return (T) EntityProxy.Pattern.make(nid);
            }
            return (T) EntityProxy.Pattern.make(nid);
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
                Object value = EntityRecordFactory.readDataType(readBuf, dataType, formatVersion);
                mutableProperties.put(conceptNid, value);
            }
            this.properties = mutableProperties.toImmutable();
        } else {
            this.properties = IntObjectMaps.immutable.empty();
        }

    }

    public String toGraphFormatString(String prepend, DiGraphAbstract diGraph) {
        StringBuilder sb = new StringBuilder();
        sb.append(prepend);
        sb.append(" [").append(vertexIndex).append("]");
        Optional<ImmutableIntList> optionalSuccessorNids = diGraph.successorNids(this.vertexIndex);
        if (optionalSuccessorNids.isPresent()) {
            sb.append("➞");
            sb.append(optionalSuccessorNids.get().toString().replace(" ", ""));
        }
        sb.append(" ");

        if (properties.containsKey(meaningNid)) {
            Object property = properties.get(meaningNid);
            sb.append(PrimitiveData.text(meaningNid));
            sb.append(": ");
            propertyToString(sb, property);

        } else {
            sb.append(PrimitiveData.text(meaningNid));
        }

        sb.append("\n");
        int[] propertyKeys = properties.keySet().toArray();
        for (int i = 0; i < propertyKeys.length; i++) {
            if (propertyKeys[i] != meaningNid) {
                sb.append(prepend);
                sb.append("    •").append(PrimitiveData.text(propertyKeys[i]));
                sb.append(": ");
                Object value = properties.get(propertyKeys[i]);
                if (value instanceof org.hl7.tinkar.terms.ConceptFacade conceptFacade) {
                    sb.append(PrimitiveData.text(conceptFacade.nid()));
                } else if (value instanceof PatternFacade patternFacade) {
                    sb.append(PrimitiveData.text(patternFacade.nid()));
                } else {
                    sb.append(value.toString());
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private void propertyToString(StringBuilder sb, Object property) {
        if (property instanceof org.hl7.tinkar.terms.ConceptFacade conceptFacade) {
            sb.append(PrimitiveData.text(conceptFacade.nid()));
        } else if (property instanceof PatternFacade patternFacade) {
            sb.append(PrimitiveData.text(patternFacade.nid()));
        } else {
            sb.append(property.toString());
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("EntityVertex{").append(vertexId().asUuid());
        sb.append(", index: ").append(vertexIndex);
        sb.append(", meaning: ").append(PrimitiveData.text(meaningNid));
        sb.append(", properties=").append(properties).append('}');

        return sb.toString();
    }

    @Override
    public VertexId vertexId() {
        return this;
    }

    @Override
    public int vertexIndex() {
        return vertexIndex;
    }

    @Override
    public Concept meaning() {
        return EntityService.get().getEntityFast(meaningNid);
    }

    @Override
    public <T> Optional<T> property(Concept propertyConcept) {
        if (propertyConcept instanceof ConceptFacade) {
            return property((ConceptFacade) propertyConcept);
        }
        return Optional.ofNullable(propertyFast(propertyConcept));
    }

    @Override
    public <T> T propertyFast(Concept propertyConcept) {
        if (propertyConcept instanceof ConceptFacade) {
            return propertyFast((ConceptFacade) propertyConcept);
        }
        return (T) properties.get(EntityService.get().nidForComponent(propertyConcept));
    }

    @Override
    public RichIterable<Concept> propertyKeys() {
        return properties.keySet().collect(nid -> EntityProxy.Concept.make(nid));
    }

    public <T> Optional<T> property(ConceptFacade conceptFacade) {
        return Optional.ofNullable(propertyFast(conceptFacade));
    }

    public <T> T propertyFast(ConceptFacade conceptFacade) {
        return (T) properties.get(conceptFacade.nid());
    }

    public <T> Optional<T> property(int propertyConceptNid) {
        return Optional.ofNullable(propertyFast(propertyConceptNid));
    }

    public <T> T propertyFast(int propertyConceptNid) {
        return (T) properties.get(propertyConceptNid);
    }

    /**
     * TODO decide how to manage edits, temporary properties, and similar.
     *
     * @param propertyConceptNid
     * @param value
     */
    public void putUncommittedProperty(int propertyConceptNid, Object value) {
        if (this.uncommittedProperties == null) {
            this.uncommittedProperties = IntObjectMaps.mutable.empty();
        }
        this.uncommittedProperties.put(propertyConceptNid, value);
    }

    public <T> Optional<T> uncommittedProperty(int propertyConceptNid) {
        if (this.uncommittedProperties == null) {
            return Optional.empty();
        }
        return (Optional<T>) Optional.ofNullable(this.uncommittedProperties.get(propertyConceptNid));
    }


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
                        EntityRecordFactory.writeField(byteBuf, value);
                    });
                }
                return byteBuf.asArray();
            } catch (ArrayIndexOutOfBoundsException e) {
                byteBufRef.get().recycle();
                bufSize = bufSize + DEFAULT_SIZE;
                LOG.info("Growing Vertex size: " + bufSize);
                byteBufRef.set(ByteBufPool.allocate(bufSize));
            }
        }
    }

    protected void setVertexIndex(int vertexIndex) {
        this.vertexIndex = vertexIndex;
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
    public UUID[] asUuidArray() {
        return new UUID[]{asUuid()};
    }

    @Override
    public int uuidCount() {
        return 1;
    }

    @Override
    public void forEach(LongConsumer consumer) {
        consumer.accept(this.mostSignificantBits);
        consumer.accept(this.leastSignificantBits);
    }

    public int getMeaningNid() {
        return meaningNid;
    }
}
