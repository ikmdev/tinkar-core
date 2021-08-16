package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.component.Stamp;
import org.hl7.tinkar.component.Version;
import org.hl7.tinkar.terms.State;

public abstract class EntityVersion
        implements Version, Stamp {
    protected Entity chronology;
    protected int stampNid;

    protected EntityVersion() {
    }

    public final int nid() {
        return chronology.nid();
    }

    public String toXmlFragment() {
        return VersionProxyFactory.toXmlFragment(this);
    }

    protected final void fill(Entity chronology, ByteBuf readBuf, byte formatVersion) {
        this.chronology = chronology;
        int versionArraySize = readBuf.readInt();
        byte token = readBuf.readByte();
        if (dataType().token != token) {
            throw new IllegalStateException("Wrong token for type: " + token);
        }
        this.stampNid = readBuf.readInt();
        finishVersionFill(readBuf, formatVersion);
    }

    public abstract FieldDataType dataType();

    protected abstract void finishVersionFill(ByteBuf readBuf, byte formatVersion);

    protected final void fill(Entity chronology, Version version) {
        this.chronology = chronology;
        Stamp stamp = version.stamp();
        this.stampNid = EntityService.get().nidForComponent(stamp);
    }

    protected final byte[] getBytes() {
        ByteBuf byteBuf = ByteBufPool.allocate(versionSize());
        byteBuf.writeByte(dataType().token); //ensure that the chronicle byte array sorts first.
        byteBuf.writeInt(stampNid);
        writeVersionFields(byteBuf);
        return byteBuf.asArray();
    }

    protected int versionSize() {
        return 9 + subclassFieldBytesSize(); // token, stamp, field count
    }

    protected abstract void writeVersionFields(ByteBuf writeBuf);

    protected abstract int subclassFieldBytesSize();

    public Entity chronology() {
        return chronology;
    }

    @Override
    public PublicId publicId() {
        return chronology.publicId();
    }

    public int stampNid() {
        return stampNid;
    }

    @Override
    public String toString() {
        return stamp().describe();
    }

    @Override
    public StampEntity stamp() {
        return EntityService.get().getStampFast(stampNid);
    }

    @Override
    public Concept state() {
        return stamp().state();
    }

    @Override
    public long time() {
        return stamp().time();
    }

    @Override
    public Concept author() {
        return stamp().author();
    }

    @Override
    public Concept module() {
        return stamp().module();
    }

    @Override
    public Concept path() {
        return stamp().path();
    }

    public boolean isActive() {
        return stamp().state().nid() == State.ACTIVE.nid();
    }
}
