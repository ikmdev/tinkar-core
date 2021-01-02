package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.component.Component;
import org.hl7.tinkar.component.Version;
import org.hl7.tinkar.entity.internal.Get;
import org.hl7.tinkar.lombok.dto.FieldDataType;
import org.hl7.tinkar.util.UuidUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.UUID;

public abstract class Entity<T extends EntityVersion>
        implements Chronology<T>, Component {

    protected int nid;

    // TODO this should be the definition?
    protected int setNid;

    protected long[] uuidLongArray;

    protected final MutableList<T> versions = Lists.mutable.empty();

    protected Entity() {
    }

    protected int entitySize() {
        int size = 12 + subclassFieldBytesSize();
        size += 4 + 8 * uuidLongArray.length;
        for (T version: versions) {
            size += version.versionSize();
        }
        return size;
    }

    protected abstract int subclassFieldBytesSize();


    protected final void fill(ByteBuf readBuf) {
        this.nid = readBuf.readInt();
        this.setNid = readBuf.readInt();
        this.uuidLongArray = new long[readBuf.readInt()];
        for (int i = 0; i < this.uuidLongArray.length; i++) {
            this.uuidLongArray[i] = readBuf.readLong();
        }

        finishEntityRead(readBuf);
        versions.clear();
        int versionCount = readBuf.readInt();
        for (int i = 0; i < versionCount; i++) {
            readBuf.readInt(); // size of version array, not used here.
            versions.add(makeVersion(readBuf));
        }
    }

    protected abstract void finishEntityRead(ByteBuf readBuf);

    protected abstract void finishEntityRead(Chronology<Version> chronology);

    protected final void fill(Chronology<Version> chronology) {
        this.nid = Get.entityService().nidForUuids(chronology.componentUuids());
        this.setNid = Get.entityService().nidForUuids(chronology.chronologySet().componentUuids());
        this.uuidLongArray = UuidUtil.asArray(chronology.componentUuids());
        versions.clear();
        for (Version version: chronology.versions()) {
            versions.add(makeVersion(version));
        }
    }

    public abstract FieldDataType dataType();

    public final byte[] getBytes() {

        ByteBuf byteBuf = ByteBufPool.allocate(entitySize());
        byteBuf.writeByte(dataType().token); //ensure that the chronicle byte array sorts first.
        byteBuf.writeInt(nid);
        byteBuf.writeInt(setNid);
        byteBuf.writeInt(this.uuidLongArray.length);
        for (int i = 0; i < this.uuidLongArray.length; i++) {
            byteBuf.writeLong(this.uuidLongArray[i]);
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

    protected abstract T makeVersion(ByteBuf readBuf);

    protected abstract T makeVersion(Version version);

    @Override
    public Component chronologySet() {
        return null;
    }

    @Override
    public ImmutableList<T> versions() {
        return versions.toImmutable();
    }

    protected MutableList<T> mutableVersions() {
        return versions;
    }

    @Override
    public ImmutableList<UUID> componentUuids() {
        return UuidUtil.toList(this.uuidLongArray);
    }

    public int nid() {
        return nid;
    }

    public int setNid() {
        return setNid;
    }

    /**
     * Merge bytes from concurrently created entities. Method is idempotent.
     * Versions will not be duplicated as a result of calling method multiple times.
     *
     * Used for map.merge functions in concurrent maps.
     * @param bytes1
     * @param bytes2
     * @return
     */
    public static byte[] merge(byte[] bytes1, byte[] bytes2) {
        if (Arrays.equals(bytes1, bytes2)) {
            return bytes1;
        }
        try {
            MutableSet<byte[]> byteArraySet = Sets.mutable.empty();
            addToSet(bytes1, byteArraySet);
            addToSet(bytes2, byteArraySet);
            MutableList<byte[]> byteArrayList = byteArraySet.toList();
            byteArrayList.sort(Arrays::compare);

            ByteBuf byteBuf = ByteBufPool.allocate(bytes1.length + bytes2.length);
            byteBuf.writeInt(byteArrayList.size());
            for (byte[] byteArray: byteArrayList) {
                byteBuf.writeInt(byteArray.length);
                byteBuf.put(byteArray);
            }
            return byteBuf.asArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void addToSet(byte[] bytes, MutableSet<byte[]> byteArraySet) throws IOException {
        ByteBuf readBuf = ByteBuf.wrapForReading(bytes);
        int arrayCount = readBuf.readInt();
        for (int i = 0; i < arrayCount; i++) {
            int arraySize = readBuf.readInt();
            byte[] newArray = new byte[arraySize];
            readBuf.read(newArray);
            byteArraySet.add(newArray);
        }
    }
}
