package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.component.Stamp;
import org.hl7.tinkar.component.Version;
import org.hl7.tinkar.dto.FieldDataType;
import org.hl7.tinkar.entity.internal.Get;

import java.util.UUID;

public abstract class EntityVersion
        implements Version {
    protected Entity chronology;
    protected int stampNid;

    protected EntityVersion() { }

    public abstract FieldDataType dataType();

    protected final void fill(Entity chronology, ByteBuf readBuf) {
        this.chronology = chronology;
        this.stampNid = readBuf.readInt();
        finishVersionFill(readBuf);
    }

    protected final void fill(Entity chronology, Version version) {
        this.chronology = chronology;
        this.stampNid = Get.entityService().nidForUuids(version.stamp().componentUuids());
    }

    protected abstract void finishVersionFill(ByteBuf readBuf);
    
    protected int versionSize() {
        return 9 + subclassFieldBytesSize(); // token, stamp, field count
    }

    protected abstract int subclassFieldBytesSize();

    protected final byte[] getBytes() {
        ByteBuf byteBuf = ByteBufPool.allocate(versionSize());
        byteBuf.writeByte(dataType().token); //ensure that the chronicle byte array sorts first.
        byteBuf.writeInt(stampNid);
        writeVersionFields(byteBuf);
        return byteBuf.asArray();
    }

    protected abstract void writeVersionFields(ByteBuf writeBuf);

    @Override
    public ImmutableList<UUID> componentUuids() {
        return chronology.componentUuids();
    }

    @Override
    public Stamp stamp() {
        return Get.entityService().getStampFast(stampNid);
    }
}
