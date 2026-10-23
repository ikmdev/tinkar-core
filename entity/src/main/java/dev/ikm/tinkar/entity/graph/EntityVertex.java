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
package dev.ikm.tinkar.entity.graph;

import dev.ikm.tinkar.common.id.Nid;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.VertexId;
import dev.ikm.tinkar.common.service.DiagnosticText;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.component.Concept;
import dev.ikm.tinkar.component.FieldDataType;
import dev.ikm.tinkar.component.Pattern;
import dev.ikm.tinkar.component.Semantic;
import dev.ikm.tinkar.component.graph.Vertex;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityRecordFactory;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.PublicIdentifierRecord;
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampEntityVersion;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.entity.StampRecordBuilder;
import dev.ikm.tinkar.entity.StampVersionRecord;
import dev.ikm.tinkar.entity.StampVersionRecordBuilder;
import dev.ikm.tinkar.terms.*;
import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;
import org.eclipse.collections.api.map.primitive.ImmutableLongObjectMap;
import org.eclipse.collections.api.map.primitive.LongObjectMap;
import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.eclipse.collections.impl.factory.primitive.LongObjectMaps;
import org.eclipse.collections.impl.list.mutable.primitive.ByteArrayList;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongConsumer;

import dev.ikm.tinkar.common.service.EntityRecordFormat3;
import dev.ikm.tinkar.entity.EntityCodec3;

import static dev.ikm.tinkar.entity.EntityRecordFactory.ENTITY_FORMAT_VERSION;

public class EntityVertex implements Vertex, VertexId {
	private static final Logger LOG = LoggerFactory.getLogger(EntityVertex.class);
	private static final int DEFAULT_SIZE = 128;
	protected long mostSignificantBits;
	protected long leastSignificantBits;
	protected int vertexIndex = -1;
	protected long meaningNid;
	private ImmutableLongObjectMap<Object> properties;
	private MutableLongObjectMap<Object> uncommittedProperties;

	protected EntityVertex() {
	}

	protected EntityVertex(UUID uuid, long meaningNid) {
		this.mostSignificantBits = uuid.getMostSignificantBits();
		this.leastSignificantBits = uuid.getLeastSignificantBits();
		this.meaningNid = meaningNid;
	}

	@Override
	public ImmutableList<UUID> asUuidList() {
		return Lists.immutable.of(new UUID(mostSignificantBits, leastSignificantBits));
	}

	/**
	 * A copy constructor
	 * @param another the vertex to copy
	 */
	public EntityVertex(EntityVertex another) {
		fill(another);
	}

	public void setMeaningNid(long meaningNid) {
		this.meaningNid = meaningNid;
	}

	public EntityVertex copyWithUnassignedIndex() {
		EntityVertex newVertex = new EntityVertex(this);
		newVertex.vertexIndex = -1;
		return newVertex;
	}

	public EntityVertex copyWithNewIndex(int newIndex) {
		EntityVertex newVertex = new EntityVertex(this);
		newVertex.vertexIndex = newIndex;
		return newVertex;
	}

	public static EntityVertex make(Vertex vertex) {
		EntityVertex entityVertex = new EntityVertex();
		entityVertex.fill(vertex);
		return entityVertex;
	}

	public ImmutableLongObjectMap<Object> properties() {
		if (properties == null) {
			return LongObjectMaps.immutable.empty();
		}
		return properties;
	}

	private void fill(Vertex another) {
		VertexId anotherId = another.vertexId();
		this.mostSignificantBits = anotherId.mostSignificantBits();
		this.leastSignificantBits = anotherId.leastSignificantBits();
		this.vertexIndex = another.vertexIndex();
		if (another.meaning() instanceof ConceptFacade) {
			this.meaningNid = ((ConceptFacade) another.meaning()).nid();
		} else {
			this.meaningNid = Entity.nid(another.meaning());
		}
		MutableLongObjectMap<Object> mutableProperties = new LongObjectHashMap<>(another.propertyKeys().size());
		another.propertyKeys().forEach(concept -> {
			mutableProperties.put(Entity.nid(concept), abstractObject(another.propertyFast(concept)));
		});
		this.properties = mutableProperties.toImmutable();
	}

	private Object abstractObject(Object object) {
		return switch (object) {
		case ConceptFacade con -> con;
		case Concept concept -> EntityProxy.Concept.make(concept.publicId());
		case EntityProxy.Semantic semantic -> semantic;
		case Semantic semantic -> EntityProxy.Semantic.make(semantic.publicId());
		case PatternFacade pattern -> pattern;
		case Pattern pattern -> EntityProxy.Pattern.make(pattern.publicId());
		case StampEntity stamp -> createStampRecord(stamp);
		case Double double_ -> double_.floatValue();
		case Integer integer -> integer;
		case byte[] byteArray -> new ByteArrayList(byteArray);
		default -> object;
		};
	}

	private static StampEntity<? extends StampEntityVersion> createStampRecord(StampEntity stamp) {
		if (stamp.publicId() == null) {
			throw new RuntimeException("Exception thrown, STAMP Public id is null.");
		}
		PublicId stampPublicId = stamp.publicId();
		RecordListBuilder<StampVersionRecord> stampVersions = RecordListBuilder.make();
		StampRecord stampRecord;

		if (stamp.publicId().uuidCount() > 0) {
			long conceptNid = Entity.nid(stampPublicId);
			PublicIdentifierRecord publicIdRecord = PublicIdentifierRecord.make(stampPublicId);
			stampRecord = StampRecordBuilder.builder()
					.leastSignificantBits(publicIdRecord.leastSignificantBits())
					.mostSignificantBits(publicIdRecord.mostSignificantBits())
					.additionalUuidLongs(publicIdRecord.additionalUuidLongs())
					.nid(conceptNid)
					.versions(stampVersions)
					.build();
		} else {
			throw new IllegalStateException("STAMP public id has no UUID");
		}
		StampVersionRecord stampVersionRecord = StampVersionRecordBuilder.builder()
                .stateNid(EntityService.get().nidForPublicId(stamp.state().publicId()))
                .time(stamp.time())
				.authorNid(EntityService.get().nidForPublicId(stamp.author().publicId()))
				.moduleNid(EntityService.get().nidForPublicId(stamp.module().publicId()))
                .pathNid(EntityService.get().nidForPublicId(stamp.path().publicId()))
                .build();
		stampVersions.add(stampVersionRecord);

        StampEntity<? extends StampEntityVersion> stampEntity = StampRecordBuilder.builder(stampRecord).versions(stampVersions).build();

		return stampEntity;
	}

	public static EntityVertex make(ConceptFacade conceptFacade) {
		return EntityVertex.make(conceptFacade.nid());
	}

	public static EntityVertex make(UUID vertexUuid, ConceptFacade conceptFacade) {
		return EntityVertex.make(vertexUuid, conceptFacade.nid());
	}

	public static EntityVertex make(long meaningNid) {
		EntityVertex entityVertex = new EntityVertex(UUID.randomUUID(), meaningNid);
		return entityVertex;
	}

	public static EntityVertex make(UUID vertexUuid, long meaningNid) {
		EntityVertex entityVertex = new EntityVertex(vertexUuid, meaningNid);
		return entityVertex;
	}

	public static EntityVertex make(ByteBuf readBuf, byte entityFormatVersion) {
		if (entityFormatVersion == EntityCodec3.FORMAT) {
			return read3(readBuf, -1);
		}
		if (entityFormatVersion == ENTITY_FORMAT_VERSION) {
			EntityVertex entityVertex = new EntityVertex();
			entityVertex.fill(readBuf, entityFormatVersion);
			return entityVertex;
		} else {
			throw new UnsupportedOperationException("Unsupported version: " + entityFormatVersion);
		}
	}

	/**
	 * Reads what {@link #getBytes(byte)} wrote in format 1: the nids as four bytes and the
	 * property count as an int.
	 */
	private void fill(ByteBuf readBuf, byte formatVersion) {
		this.mostSignificantBits = readBuf.readLong();
		this.leastSignificantBits = readBuf.readLong();
		this.vertexIndex = readBuf.readInt();
		this.meaningNid = readBuf.readInt();
		int propertyCount = readBuf.readInt();
		if (propertyCount > 0) {
			MutableLongObjectMap<Object> mutableProperties = LongObjectMaps.mutable.ofInitialCapacity(propertyCount);
			for (int i = 0; i < propertyCount; i++) {
				long conceptNid = readBuf.readInt();
				FieldDataType dataType = FieldDataType.fromToken(readBuf.readByte());
				Object value = EntityRecordFactory.readFieldData(readBuf, dataType, formatVersion);
				mutableProperties.put(conceptNid, value);
			}
			this.properties = mutableProperties.toImmutable();
		} else {
			this.properties = LongObjectMaps.immutable.empty();
		}

	}

	public String toGraphFormatString(String prepend, String idSuffix, DiGraphAbstract diGraph) {
		StringBuilder sb = new StringBuilder();
		sb.append(prepend);
		sb.append(" [").append(vertexIndex).append(idSuffix).append("]");
		Optional<ImmutableIntList> optionalSuccessorNids = diGraph.successorNids(this.vertexIndex); // vertex indexes, despite the name
		optionalSuccessorNids.ifPresent(successorNids -> {
			sb.append("➞[");
			successorNids.forEach(successorNid -> sb.append(successorNid).append(idSuffix).append(","));
			sb.deleteCharAt(sb.length() - 1);
			sb.append("]");
		});
		sb.append(" ");

		if (properties != null && properties.containsKey(meaningNid)) {
			Object property = properties.get(meaningNid);
			sb.append(PrimitiveData.text(meaningNid));
			sb.append(": ");
			propertyToString(sb, property);

		} else {
			sb.append(PrimitiveData.text(meaningNid));
		}

		sb.append("\n");
		if (properties != null) {
			long[] propertyKeys = properties.keySet().toArray();
			for (int i = 0; i < propertyKeys.length; i++) {
				if (propertyKeys[i] != meaningNid) {
					sb.append(prepend);
					sb.append("    •").append(PrimitiveData.text(propertyKeys[i]));
					sb.append(": ");
					Object value = properties.get(propertyKeys[i]);
					if (value instanceof dev.ikm.tinkar.terms.ConceptFacade conceptFacade) {
						sb.append(PrimitiveData.text(conceptFacade.nid()));
					} else if (value instanceof PatternFacade patternFacade) {
						sb.append(PrimitiveData.text(patternFacade.nid()));
					} else {
						sb.append(value.toString());
						sb.append(" <" + value.getClass().getSimpleName() + ">");
					}
					sb.append("\n");
				}
			}
		}

		return sb.toString();
	}

	private void propertyToString(StringBuilder sb, Object property) {
		if (property instanceof dev.ikm.tinkar.terms.ConceptFacade conceptFacade) {
			sb.append(PrimitiveData.text(conceptFacade.nid()));
		} else if (property instanceof PatternFacade patternFacade) {
			sb.append(PrimitiveData.text(patternFacade.nid()));
		} else {
			sb.append(property.toString());
		}
	}

	/**
	 * TODO: Not thread safe...
	 */
	public void commitProperties() {
		if (uncommittedProperties != null & !uncommittedProperties.isEmpty()) {
			if (this.properties != null) {
				for (long key : this.properties.keySet().toArray()) {
					if (!this.uncommittedProperties.containsKey(key)) {
						this.uncommittedProperties.put(key, this.properties.get(key));
					}
				}
			}
			this.properties = this.uncommittedProperties.toImmutable();
			this.uncommittedProperties = null;
		}
	}

	public String toString(String nodeIdSuffix) {
		StringBuilder sb = new StringBuilder();
		sb.append(PrimitiveData.text(meaningNid));
		sb.append("[").append(vertexIndex).append(nodeIdSuffix).append("]");

		int uncommittedPropertyCount = uncommittedProperties == null ? 0 : uncommittedProperties.size();
		int totalPropertyCount = properties == null ? 0 : properties.size() + uncommittedPropertyCount;
		AtomicInteger builtPropertyCount = new AtomicInteger();

		if (totalPropertyCount > 0) {
			sb.append(", properties={");

			if (properties != null) {
				properties.forEachKeyValue((keyNid, value) -> {
					builtPropertyCount.getAndIncrement();
					appendProperty(keyNid, value, "", sb, builtPropertyCount.get() < totalPropertyCount);
				});
			}

			if (uncommittedProperties != null) {
				uncommittedProperties.forEachKeyValue((keyNid, value) -> {
					builtPropertyCount.getAndIncrement();
					appendProperty(keyNid, value, "~", sb, builtPropertyCount.get() < totalPropertyCount);
				});
			}
			sb.append("}");
		}

		return sb.toString();
	}

	private void appendProperty(long keyNid, Object value, String prefix, StringBuilder sb, boolean addSeparator) {
		sb.append(prefix).append(PrimitiveData.text(keyNid)).append("=");
		switch (value) {
//            case ConceptFacade conceptFacade -> sb.append(PrimitiveData.text(conceptFacade.nid()));
		default -> sb.append(value.toString());
		}
		if (addSeparator) {
			sb.append(", ");
		}
	}

	@Override
	public String toString() {
		return toString("");
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
		return EntityProxy.Concept.make(meaningNid);
	}

	@Override
	public <T> Optional<T> property(Concept propertyConcept) {
		if (propertyConcept instanceof ConceptFacade) {
			return property((ConceptFacade) propertyConcept);
		}
		return Optional.ofNullable(propertyFast(propertyConcept));
	}

	/** A property value that names a component — its nid or a facade — for a diagnostic message. */
	private static String componentText(Object propertyValue) {
		return switch (propertyValue) {
		case Long nid -> DiagnosticText.component(nid);
		case EntityFacade facade -> DiagnosticText.component(facade.nid());
		default -> String.valueOf(propertyValue);
		};
	}

	@Override
	public Optional<ConceptFacade> propertyAsConcept(Concept propertyConcept) {
		Optional<?> optionalPropertyValue = property(propertyConcept);

		if (optionalPropertyValue.isEmpty()) {
			return Optional.empty();
		}
		Optional<Entity<? extends EntityVersion>> optionalEntityValue = switch (optionalPropertyValue.get()) {
		case Long nid -> EntityHandle.get(nid).entity().filter(e -> !e.canceled());
		case EntityFacade facade -> EntityHandle.get(facade).entity().filter(e -> !e.canceled());
		case null -> throw new IllegalStateException("optionalPropertyValue is null");
            default -> throw new IllegalStateException("optionalPropertyValue is not an identifier or facade: " + optionalPropertyValue.get());
		};
		if (optionalEntityValue.isEmpty()) {
            throw new IllegalStateException("Entity specified by property is not in database:: " + componentText(optionalPropertyValue.get()));
		}
		if (optionalEntityValue.get() instanceof ConceptFacade conceptFacade) {
			return Optional.of(conceptFacade);
		}
		throw new IllegalStateException("Cannot convert property to concept. Property: " + componentText(optionalPropertyValue.get()));
	}

	@Override
	public <T> T propertyFast(Concept propertyConcept) {
		if (propertyConcept instanceof ConceptFacade) {
			return propertyFast((ConceptFacade) propertyConcept);
		}
		return (T) properties.get(Entity.nid(propertyConcept));
	}

	@Override
	public RichIterable<ConceptFacade> propertyKeys() {
		if (properties != null) {
			return properties.keySet().collect(nid -> EntityProxy.Concept.make(nid));
		}
		return Lists.immutable.empty();
	}

	public <T> Optional<T> property(ConceptFacade conceptFacade) {
		return Optional.ofNullable(propertyFast(conceptFacade));
	}

	public <T> T propertyFast(ConceptFacade conceptFacade) {
		return properties != null ? (T) properties.get(conceptFacade.nid()) : null;
	}

	public <T> Optional<T> property(long propertyConceptNid) {
		return Optional.ofNullable(propertyFast(propertyConceptNid));
	}

	public <T> T propertyFast(long propertyConceptNid) {
		return properties != null ? (T) properties.get(propertyConceptNid) : null;
	}

	/**
	 * TODO decide how to manage edits, temporary properties, and similar.
	 *
	 * @param propertyConceptNid
	 * @param value
	 */
	public void putUncommittedProperty(long propertyConceptNid, Object value) {
		if (this.uncommittedProperties == null) {
			this.uncommittedProperties = LongObjectMaps.mutable.empty();
		}
		this.uncommittedProperties.put(propertyConceptNid, value);
	}

	public <T> Optional<T> uncommittedProperty(long propertyConceptNid) {
		if (this.uncommittedProperties == null) {
			return Optional.empty();
		}
		return (Optional<T>) Optional.ofNullable(this.uncommittedProperties.get(propertyConceptNid));
	}

	public void setProperties(MutableLongObjectMap<Object> properties) {
		this.properties = properties.toImmutable();
	}

	/** The vertex in entity format 1. */
	public final byte[] getBytes() {
		return getBytes(ENTITY_FORMAT_VERSION);
	}

	/**
	 * The vertex's bytes in an entity format. Format 1: the UUID, the vertex index, the meaning's
	 * nid, and the properties, each a concept's nid and a field value, every nid four bytes and
	 * the count an int. Format 3: {@link #write3} with the index.
	 *
	 * @param entityFormatVersion the format, 1 or 3
	 * @return the bytes
	 */
	public final byte[] getBytes(byte entityFormatVersion) {
		boolean format3 = entityFormatVersion == EntityCodec3.FORMAT;
		int bufSize = DEFAULT_SIZE;
        AtomicReference<ByteBuf> byteBufRef =
                new AtomicReference<>(ByteBufPool.allocate(bufSize));
		while (true) {
			try {
				ByteBuf byteBuf = byteBufRef.get();
				if (format3) {
					write3(byteBuf, true);
					return byteBuf.asArray();
				}
				byteBuf.writeLong(mostSignificantBits);
				byteBuf.writeLong(leastSignificantBits);
				byteBuf.writeInt(vertexIndex);
				byteBuf.writeInt(Nid.narrowChecked(meaningNid));
				int propertyCount = properties == null ? 0 : properties.size();
				byteBuf.writeInt(propertyCount);
				if (properties != null) {
					properties.forEachKeyValue((nid, value) -> {
						byteBuf.writeInt(Nid.narrowChecked(nid));
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

	/**
	 * Writes the vertex in entity format 3 (IKE-Network/ike-issues#1313): its index when asked
	 * (a vertex standing alone as a field; within a graph its index is its position), the UUID,
	 * the meaning as a concept reference, the property count, and each property as a concept
	 * reference and a field value.
	 *
	 * @param buf       the buffer
	 * @param withIndex whether to write the vertex's index
	 */
	public final void write3(ByteBuf buf, boolean withIndex) {
		if (withIndex) {
			EntityRecordFormat3.writeSignedVarInt(buf, vertexIndex);
		}
		buf.writeLong(mostSignificantBits);
		buf.writeLong(leastSignificantBits);
		EntityRecordFormat3.writeConceptNid(buf, meaningNid);
		buf.writeVarInt(properties == null ? 0 : properties.size());
		if (properties != null) {
			properties.forEachKeyValue((nid, value) -> {
				EntityRecordFormat3.writeConceptNid(buf, nid);
				EntityCodec3.writeField(buf, value);
			});
		}
	}

	/**
	 * A vertex with every part given, as a reader of a retired record format decodes it
	 * ({@code EntityFormat2}).
	 *
	 * @param mostSignificantBits  the vertex UUID's most significant bits
	 * @param leastSignificantBits the vertex UUID's least significant bits
	 * @param vertexIndex          the vertex's index in its graph
	 * @param meaningNid           the meaning's nid
	 * @param properties           the properties, each a concept's nid and its value
	 * @return the vertex
	 */
	public static EntityVertex of(long mostSignificantBits, long leastSignificantBits, int vertexIndex,
								  long meaningNid, ImmutableLongObjectMap<Object> properties) {
		EntityVertex vertex = new EntityVertex();
		vertex.mostSignificantBits = mostSignificantBits;
		vertex.leastSignificantBits = leastSignificantBits;
		vertex.vertexIndex = vertexIndex;
		vertex.meaningNid = meaningNid;
		vertex.properties = properties;
		return vertex;
	}

	/**
	 * Reads a vertex {@link #write3} wrote.
	 *
	 * @param buf         the buffer
	 * @param vertexIndex the vertex's index, its position in a graph; negative to read the index written with it
	 * @return the vertex
	 */
	public static EntityVertex read3(ByteBuf buf, int vertexIndex) {
		EntityVertex vertex = new EntityVertex();
		vertex.vertexIndex = vertexIndex < 0 ? EntityRecordFormat3.readSignedVarInt(buf) : vertexIndex;
		vertex.mostSignificantBits = buf.readLong();
		vertex.leastSignificantBits = buf.readLong();
		vertex.meaningNid = EntityRecordFormat3.readConceptNid(buf);
		int propertyCount = buf.readVarInt();
		if (propertyCount > 0) {
			MutableLongObjectMap<Object> properties = LongObjectMaps.mutable.ofInitialCapacity(propertyCount);
			for (int i = 0; i < propertyCount; i++) {
				long conceptNid = EntityRecordFormat3.readConceptNid(buf);
				properties.put(conceptNid, EntityCodec3.readField(buf, FieldDataType.fromToken(buf.readByte())));
			}
			vertex.properties = properties.toImmutable();
		} else {
			vertex.properties = LongObjectMaps.immutable.empty();
		}
		return vertex;
	}

	public void setVertexIndex(int vertexIndex) {
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
		return new UUID[] { asUuid() };
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

	public long getMeaningNid() {
		return meaningNid;
	}

	public MutableLongObjectMap<Object> uncommittedProperties() {
		return uncommittedProperties;
	}

	@Override
	public int hashCode() {
		int result = (int) (mostSignificantBits ^ (mostSignificantBits >>> 32));
		result = 31 * result + (int) (leastSignificantBits ^ (leastSignificantBits >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof EntityVertex that) {
			if (this.leastSignificantBits != that.leastSignificantBits) {
				return false;
			}
			if (this.mostSignificantBits != that.mostSignificantBits) {
				return false;
			}
			if (this.meaningNid != that.meaningNid) {
				return false;
			}
			if (compareProperties(this.uncommittedProperties, that.uncommittedProperties) == false) {
				return false;
			}
			return compareProperties(this.properties, that.properties);
		}
		return false;
	}

	public boolean equivalent(EntityVertex that) {
		if (this.uncommittedProperties != null || that.uncommittedProperties != null) {
			throw new IllegalStateException("Cannot test for equivalence with uncommitted properties");
		}
		if (this.meaningNid != that.meaningNid) {
			return false;
		}
		return compareProperties(this.properties, that.properties);
	}

	private static boolean compareProperties(LongObjectMap theseProperties, LongObjectMap thoseProperties) {
		if (theseProperties == null || theseProperties.isEmpty()) {
			if (thoseProperties != null && thoseProperties.isEmpty() == false) {
				return false;
			}
			return true;
		} else {
			if (thoseProperties == null || thoseProperties.isEmpty()) {
				return false;
			}
			if (theseProperties.size() != thoseProperties.size()) {
				return false;
			}
			if (!theseProperties.keySet().equals(thoseProperties.keySet())) {
				return false;
			}
			for (long key : theseProperties.keySet().toArray()) {
                if (!Objects.equals(theseProperties.get(key),
                        thoseProperties.get(key))) {
					return false;
				}
			}
			return true;
		}
	}

	/**
     * Only considers meaning and committed property keys and values that are kinds of concept.
	 * @param conceptNidSet
	 */
	public void addConceptsReferencedByVertex(MutableLongSet conceptNidSet) {
		conceptNidSet.add(meaningNid);
		if (this.properties != null) {
			this.properties.keySet().forEach(keyNid -> conceptNidSet.add(keyNid));
			this.properties.values().forEach(propertyValue -> {
				switch (propertyValue) {
				case ConceptEntity concept -> conceptNidSet.add(concept.nid());
				case ConceptFacade conceptFacade -> conceptNidSet.add(conceptFacade.nid());
                    default -> { /* not a concept, so ignore */ }
				}
			});
		}
	}

}
