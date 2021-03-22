package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.component.Component;
import org.hl7.tinkar.component.Version;
import org.hl7.tinkar.entity.internal.Get;
import org.hl7.tinkar.component.FieldDataType;

import java.util.Optional;
import java.util.UUID;

public abstract class Entity<T extends EntityVersion> extends PublicIdForEntity
        implements Chronology<T>, EntityFacade {

    public static final byte ENTITY_FORMAT_VERSION = 1;

    public static EntityService provider() {
        return Get.entityService();
    }

    public static int nid(Component component) {
        return Get.entityService().nidForComponent(component);
    }

    public static <T extends Entity<V>, V extends EntityVersion> Optional<T> get(int nid) {
        return Get.entityService().getEntity(nid);
    }

    public static <T extends Entity<V>, V extends EntityVersion> T getFast(int nid) {
        return Get.entityService().getEntityFast(nid);
    }

    public static StampEntity getStamp(int nid) {
        return Get.entityService().getStampFast(nid);
    }



    protected int nid;
    protected final MutableList<T> versions = Lists.mutable.empty();

    protected Entity() {
    }

    protected int entitySize() {
        int size = 12 + subclassFieldBytesSize();
        size += 4 + 16; //4 = UUID count, 16 = primordial uuid bytes.
        if (additionalUuidLongs != null) {
            size += 8 * additionalUuidLongs.length;
        }
        for (T version: versions) {
            size += version.versionSize();
        }
        return size + 32; // extra 32 to make it less brittle.
    }

    protected abstract int subclassFieldBytesSize();


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
            versions.add(makeVersion(readBuf, entityFormatVersion));
        }
    }

    protected abstract void finishEntityRead(ByteBuf readBuf, byte formatVersion);

    protected abstract void finishEntityRead(Chronology<Version> chronology);

    protected final void fill(Chronology<Version> chronology) {
        this.nid = Get.entityService().nidForPublicId(chronology.publicId());
        ImmutableList<UUID> componentUuids = chronology.publicId().asUuidList();
        UUID firstUuid = componentUuids.get(0);

        this.mostSignificantBits = firstUuid.getMostSignificantBits();
        this.leastSignificantBits = firstUuid.getLeastSignificantBits();
        if (componentUuids.size() > 1) {
            this.additionalUuidLongs = new long[(componentUuids.size() - 1) * 2];
            for (int i = 0; i < additionalUuidLongs.length; i+=2) {
                int listIndex = i * 2;
                this.additionalUuidLongs[i] = componentUuids.get(listIndex).getMostSignificantBits();
                this.additionalUuidLongs[i+1] = componentUuids.get(listIndex).getLeastSignificantBits();
            }
        }
        finishEntityRead(chronology);
        versions.clear();
        for (Version version: chronology.versions()) {
            versions.add(makeVersion(version));
        }
    }

    public abstract FieldDataType dataType();

    public final byte[] getBytes() {

        ByteBuf byteBuf = ByteBufPool.allocate(entitySize() + 1); // one byte for version...
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
        for (EntityVersion version: versions) {
            entityArray[chronicleFieldIndex++] = version.getBytes();
        }
        int totalSize = 0;
        totalSize += 4; // Integer for the number of arrays
        for (byte[] arrayBytes: entityArray) {
            totalSize += 4; // integer for size of array
            totalSize += arrayBytes.length;
        }
        ByteBuf finalByteBuf = ByteBufPool.allocate(totalSize);
        finalByteBuf.writeInt(entityArray.length);
        for (byte[] arrayBytes: entityArray) {
            finalByteBuf.writeInt(arrayBytes.length);
            finalByteBuf.write(arrayBytes);
        }
        return finalByteBuf.asArray();
    }

    protected abstract void finishEntityWrite(ByteBuf byteBuf);

    protected abstract T makeVersion(ByteBuf readBuf, byte formatVersion);

    protected abstract T makeVersion(Version version);

    @Override
    public ImmutableList<T> versions() {
        return versions.toImmutable();
    }

    protected MutableList<T> mutableVersions() {
        return versions;
    }

    @Override
    public PublicId publicId() {
        return this;
    }

    public int nid() {
        return nid;
    }

}
