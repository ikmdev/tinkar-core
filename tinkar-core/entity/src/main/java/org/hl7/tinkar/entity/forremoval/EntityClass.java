package org.hl7.tinkar.entity.forremoval;

import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.hl7.tinkar.common.id.IntIdSet;
import org.hl7.tinkar.common.id.IntIds;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.id.PublicIds;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.util.uuid.UuidUtil;
import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.component.Version;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.EntityRecordFactory;
import org.hl7.tinkar.entity.EntityService;
import org.hl7.tinkar.entity.EntityVersion;
import org.hl7.tinkar.terms.EntityFacade;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongConsumer;

public abstract class EntityClass<T extends EntityVersion>
        implements Entity<T> {
    public static final int DEFAULT_ENTITY_SIZE = 1024;
    public static final byte ENTITY_FORMAT_VERSION = 1;
    protected final MutableList<T> versions = Lists.mutable.empty();

    protected long mostSignificantBits;

    protected long leastSignificantBits;

    protected long[] additionalUuidLongs;

    protected int nid;

    protected EntityClass() {
    }

    public EntityClass(UUID uuid) {
        this.mostSignificantBits = uuid.getMostSignificantBits();
        this.leastSignificantBits = uuid.getLeastSignificantBits();
        this.additionalUuidLongs = null;
        nid = PrimitiveData.nid(uuid);
    }

    public EntityClass(UUID[] uuids) {
        this.mostSignificantBits = uuids[0].getMostSignificantBits();
        this.leastSignificantBits = uuids[0].getLeastSignificantBits();
        additionalUuidLongs = UuidUtil.asArray(Arrays.copyOfRange(uuids, 1, uuids.length));
        nid = PrimitiveData.nid(PublicIds.of(uuids));
    }

    @Override
    public long mostSignificantBits() {
        return mostSignificantBits;
    }

    @Override
    public long leastSignificantBits() {
        return leastSignificantBits;
    }

    @Override
    public long[] additionalUuidLongs() {
        return additionalUuidLongs;
    }

    @Override
    public UUID[] asUuidArray() {
        UUID[] uuidArray = new UUID[uuidCount()];
        uuidArray[0] = new UUID(mostSignificantBits, leastSignificantBits);
        if (additionalUuidLongs != null) {
            for (int i = 1; i < uuidArray.length; i++) {
                uuidArray[i] = new UUID(additionalUuidLongs[((i - 1) * 2)], additionalUuidLongs[((i - 1) * 2) + 1]);
            }
        }
        return uuidArray;
    }

    @Override
    public int uuidCount() {
        if (additionalUuidLongs != null) {
            return (additionalUuidLongs.length / 2) + 1;
        }
        return 1;
    }

    @Override
    public void forEach(LongConsumer consumer) {
        consumer.accept(mostSignificantBits);
        consumer.accept(leastSignificantBits);
        if (additionalUuidLongs != null) {
            for (long uuidPart : additionalUuidLongs) {
                consumer.accept(uuidPart);
            }
        }
    }

    @Override
    public IntIdSet stampNids() {
        MutableIntList stampNids = IntLists.mutable.withInitialCapacity(versions.size());
        for (EntityVersion version : versions) {
            stampNids.add(version.stampNid());
        }
        return IntIds.set.of(stampNids.toArray());
    }

    @Override
    public ImmutableList<T> versions() {
        return versions.toImmutable();
    }

    @Override
    public final byte[] getBytes() {
        // TODO: write directly to a single ByteBuf, rather that the approach below.
        boolean complete = false;
        int entitySize = DEFAULT_ENTITY_SIZE;
        while (!complete) {
            try {
                ByteBuf byteBuf = ByteBufPool.allocate(entitySize); // one byte for version...
                entitySize = byteBuf.writeRemaining();
                byteBuf.writeByte(ENTITY_FORMAT_VERSION);
                byteBuf.writeByte(dataType().token); //ensure that the chronicle byte array sorts first.
                byteBuf.writeInt(nid);
                byteBuf.writeLong(mostSignificantBits);
                byteBuf.writeLong(leastSignificantBits);
                if (this.additionalUuidLongs == null) {
                    byteBuf.writeByte((byte) 0);
                } else {
                    byteBuf.writeByte((byte) this.additionalUuidLongs.length);
                    for (int i = 0; i < this.additionalUuidLongs.length; i++) {
                        byteBuf.writeLong(this.additionalUuidLongs[i]);
                    }
                }
                finishEntityWrite(byteBuf);
                byteBuf.writeInt(versions.size());

                int chronicleArrayCount = versions.size() + 1;
                int chronicleFieldIndex = 0;
                byte[][] entityArray = new byte[chronicleArrayCount][];
                entityArray[chronicleFieldIndex++] = byteBuf.asArray();
                for (EntityVersion version : versions) {
                    entityArray[chronicleFieldIndex++] = EntityRecordFactory.getBytes(version);
                }
                int totalSize = 0;
                totalSize += 4; // Integer for the number of arrays
                for (byte[] arrayBytes : entityArray) {
                    totalSize += 4; // integer for size of array
                    totalSize += arrayBytes.length;
                }
                ByteBuf finalByteBuf = ByteBufPool.allocate(totalSize);
                finalByteBuf.writeInt(entityArray.length);
                for (byte[] arrayBytes : entityArray) {
                    finalByteBuf.writeInt(arrayBytes.length);
                    finalByteBuf.write(arrayBytes);
                }
                return finalByteBuf.asArray();
            } catch (Exception e) {
                e.printStackTrace();
                entitySize = entitySize * 2;
            }
        }
        throw new IllegalStateException("Should never reach here. ");
    }

    public abstract FieldDataType dataType();

    protected abstract void finishEntityWrite(ByteBuf byteBuf);

    @Override
    public int nid() {
        return nid;
    }

    @Override
    public PublicId publicId() {
        return this;
    }

    protected final void fill(ByteBuf readBuf, byte entityFormatVersion) {
        if (entityFormatVersion != ENTITY_FORMAT_VERSION) {
            throw new IllegalStateException("Unsupported entity format version: " + entityFormatVersion);
        }
        this.nid = readBuf.readInt();
        this.mostSignificantBits = readBuf.readLong();
        this.leastSignificantBits = readBuf.readLong();

        int additionalUuidLongCount = readBuf.readByte();
        if (additionalUuidLongCount > 0) {
            this.additionalUuidLongs = new long[additionalUuidLongCount];
            for (int i = 0; i < this.additionalUuidLongs.length; i++) {
                this.additionalUuidLongs[i] = readBuf.readLong();
            }
        } else {
            this.additionalUuidLongs = null;
        }

        finishEntityRead(readBuf, entityFormatVersion);
        versions.clear();
        int versionCount = readBuf.readInt();
        for (int i = 0; i < versionCount; i++) {
            versions.add((T) makeVersion(readBuf, entityFormatVersion));
        }
    }

    protected abstract void finishEntityRead(ByteBuf readBuf, byte formatVersion);

    protected abstract EntityVersion makeVersion(ByteBuf readBuf, byte formatVersion);

    protected final void fill(Chronology<Version> chronology) {
        this.nid = EntityService.get().nidForPublicId(chronology.publicId());
        ImmutableList<UUID> componentUuids = chronology.publicId().asUuidList();
        UUID firstUuid = componentUuids.get(0);

        this.mostSignificantBits = firstUuid.getMostSignificantBits();
        this.leastSignificantBits = firstUuid.getLeastSignificantBits();
        if (componentUuids.size() > 1) {
            this.additionalUuidLongs = new long[(componentUuids.size() - 1) * 2];
            for (int listIndex = 1; listIndex < componentUuids.size(); listIndex++) {
                int additionalUuidIndex = listIndex - 1;
                this.additionalUuidLongs[additionalUuidIndex * 2] = componentUuids.get(listIndex).getMostSignificantBits();
                this.additionalUuidLongs[additionalUuidIndex * 2 + 1] = componentUuids.get(listIndex).getLeastSignificantBits();
                // TODO remove debug check...
                if (this.additionalUuidLongs[additionalUuidIndex * 2] == this.mostSignificantBits ||
                        this.additionalUuidLongs[additionalUuidIndex * 2 + 1] == this.leastSignificantBits) {
                    throw new IllegalStateException("UUID error in: " + chronology);
                }
            }
        }
        finishEntityRead(chronology);
        versions.clear();
        for (Version version : chronology.versions()) {
            versions.add((T) makeVersion(version));
        }
    }

    protected abstract void finishEntityRead(Chronology<Version> chronology);

    protected abstract EntityVersion makeVersion(Version version);

    protected MutableList<T> mutableVersions() {
        return versions;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(nid);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof EntityFacade entityFacade) {
            return nid == entityFacade.nid();
        }
        if (o instanceof PublicId publicId) {
            UUID[] thisUuids = asUuidArray();
            return Arrays.stream(publicId.asUuidArray()).anyMatch(uuid -> {
                for (UUID thisUuid : thisUuids) {
                    if (uuid.equals(thisUuid)) {
                        return true;
                    }
                }
                return false;
            });
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName());
        sb.append("{");
        Optional<String> stringOptional = PrimitiveData.textOptional(this.nid);
        if (stringOptional.isPresent()) {
            sb.append(stringOptional.get());
            sb.append(' ');
        }
        sb.append("<");
        sb.append(nid);
        sb.append("> ");
        sb.append(Arrays.toString(publicId().asUuidArray()));
        sb.append(", v: ");
        sb.append(versions);
        sb.append('}');
        return sb.toString();
    }
}
