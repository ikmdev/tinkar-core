package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.util.time.DateTimeUtil;
import org.hl7.tinkar.component.*;
import org.hl7.tinkar.dto.StampDTO;

import static org.hl7.tinkar.component.FieldDataType.STAMP;

public class StampEntity extends Entity<StampEntityVersion>
         implements Stamp<StampEntityVersion>, Component, Version {

    private StampEntityVersion lastVersion() {
        if (versions.size() == 1) {
            return versions.get(0);
        }
        StampEntityVersion latest = null;
        for (StampEntityVersion version: versions) {
            if (latest == null || latest.time < version.time) {
                latest = version;
            }
        }
        return latest;
    }
    @Override
    public Concept state() {
        return lastVersion().state();
    }

    public int stateNid() {
        return lastVersion().statusNid;
    }

    @Override
    public long time() {
        return lastVersion().time;
    }

    @Override
    public Concept author() {
        return lastVersion().author();
    }

    public int authorNid() {
        return lastVersion().authorNid;
    }

    @Override
    public Concept module() {
        return lastVersion().module();
    }

    public int moduleNid() {
        return lastVersion().moduleNid;
    }

    @Override
    public Concept path() {
        return lastVersion().path();
    }

    public int pathNid() {
        return lastVersion().pathNid;
    }

    @Override
    protected int subclassFieldBytesSize() {
        return 0;
    }

    @Override
    protected void finishEntityRead(ByteBuf readBuf, byte formatVersion) {

    }

    @Override
    protected void finishEntityRead(Chronology<Version> chronology) {

    }

    @Override
    public FieldDataType dataType() {
        return STAMP;
    }

    @Override
    protected void finishEntityWrite(ByteBuf byteBuf) {

    }

    @Override
    protected StampEntityVersion makeVersion(ByteBuf readBuf, byte formatVersion) {
        return StampEntityVersion.make(this, readBuf, formatVersion);
    }

    public static StampEntity make(ByteBuf readBuf, byte entityFormatVersion) {
        StampEntity stampEntity = new StampEntity();
        stampEntity.fill(readBuf, entityFormatVersion);
        return stampEntity;
    }

    @Override
    public Stamp stamp() {
        return this;
    }

    public static StampEntity make(Stamp other) {
        StampEntity stampEntity = new StampEntity();
        stampEntity.fill(other);
        return stampEntity;
    }

    @Override
    protected StampEntityVersion makeVersion(Version version) {
        return StampEntityVersion.make(this, (StampDTO) version);
    }

    public String describe() {
        return "s:" + PrimitiveData.text(stateNid()) +
                " t:" + DateTimeUtil.format(time()) +
                " a:" + PrimitiveData.text(authorNid()) +
                " m:" + PrimitiveData.text(moduleNid()) +
                " p:" + PrimitiveData.text(pathNid());
    }

    @Override
    public String toString() {
        return "StampEntity{" +
                "<" + nid +
                "> , " + publicId().asUuidList() + " "
                + describe() +
                '}';
    }

}
