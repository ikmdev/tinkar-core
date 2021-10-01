package org.hl7.tinkar.entity.forremoval;

import io.activej.bytebuf.ByteBuf;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.util.time.DateTimeUtil;
import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.dto.StampDTO;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.StampEntityVersion;

public class StampEntityVersionClass implements StampEntityVersion {

    int stateNid;
    long time;
    int authorNid;
    int moduleNid;
    int pathNid;

    public static StampEntityVersionClass make(StampEntityClass stampEntity, ByteBuf readBuf, byte formatVersion) {
        StampEntityVersionClass version = new StampEntityVersionClass();
        //version.fill(stampEntity, readBuf, formatVersion);
        return version;
    }

    public static StampEntityVersionClass make(StampEntityClass stampEntity, StampDTO versionToCopy) {
        StampEntityVersionClass version = new StampEntityVersionClass();
        //version.fill(stampEntity, versionToCopy);
        version.stateNid = PrimitiveData.get().nidForPublicId(versionToCopy.statusPublicId());
        version.time = versionToCopy.time();
        version.authorNid = PrimitiveData.get().nidForPublicId(versionToCopy.authorPublicId());
        version.moduleNid = PrimitiveData.get().nidForPublicId(versionToCopy.modulePublicId());
        version.pathNid = PrimitiveData.get().nidForPublicId(versionToCopy.pathPublicId());
        return version;
    }

    public static StampEntityVersionClass make(StampEntityClass stampEntity, int stateNid, long time, int authorNid, int moduleNid, int pathNid) {
        StampEntityVersionClass version = new StampEntityVersionClass();
        //version.fill(stampEntity, stampEntity.nid);
        version.stateNid = stateNid;
        version.time = time;
        version.authorNid = authorNid;
        version.moduleNid = moduleNid;
        version.pathNid = pathNid;
        return version;
    }

    @Override
    public Entity entity() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int stampNid() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Entity chronology() {
        throw new UnsupportedOperationException();
    }

    @Override
    public PublicId publicId() {
        return chronology();
    }

    public FieldDataType dataType() {
        return FieldDataType.STAMP;
    }

    protected void finishVersionFill(ByteBuf readBuf, byte formatVersion) {
        stateNid = readBuf.readInt();
        time = readBuf.readLong();
        authorNid = readBuf.readInt();
        moduleNid = readBuf.readInt();
        pathNid = readBuf.readInt();
    }

    protected void writeVersionFields(ByteBuf writeBuf) {
        writeBuf.writeInt(stateNid);
        writeBuf.writeLong(time);
        writeBuf.writeInt(authorNid);
        writeBuf.writeInt(moduleNid);
        writeBuf.writeInt(pathNid);
    }

    @Override
    public StampEntityClass stamp() {
        return (StampEntityClass) chronology();
    }

    @Override
    public int stateNid() {
        return stateNid;
    }

    @Override
    public long time() {
        return time;
    }

    @Override
    public int authorNid() {
        return authorNid;
    }

    @Override
    public int moduleNid() {
        return moduleNid;
    }

    @Override
    public int pathNid() {
        return pathNid;
    }

    @Override
    public String toString() {
        return "sv{" + describe() + "}";
    }

    public String describe() {
        return "s:" + PrimitiveData.text(stateNid()) +
                " t:" + DateTimeUtil.format(time()) +
                " a:" + PrimitiveData.text(authorNid()) +
                " m:" + PrimitiveData.text(moduleNid()) +
                " p:" + PrimitiveData.text(pathNid());
    }

}
