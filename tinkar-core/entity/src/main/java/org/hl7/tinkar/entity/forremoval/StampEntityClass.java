package org.hl7.tinkar.entity.forremoval;

import io.activej.bytebuf.ByteBuf;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.util.time.DateTimeUtil;
import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.component.Stamp;
import org.hl7.tinkar.component.Version;
import org.hl7.tinkar.dto.StampDTO;
import org.hl7.tinkar.entity.StampEntity;
import org.hl7.tinkar.entity.StampEntityVersion;
import org.hl7.tinkar.terms.ConceptFacade;
import org.hl7.tinkar.terms.State;

import java.util.UUID;

import static org.hl7.tinkar.component.FieldDataType.STAMP;

public class StampEntityClass<T extends StampEntityVersion> extends EntityClass<T>
        implements StampEntity<T> {

    protected StampEntityClass() {
    }

    protected StampEntityClass(UUID uuid) {
        super(uuid);
    }

    protected StampEntityClass(UUID[] uuids) {
        super(uuids);
    }

    public static StampEntityClass make(UUID stampUuid, State state, long time, PublicId authorId, PublicId moduleId, PublicId pathId) {
        StampEntityClass stampEntity = new StampEntityClass(stampUuid);
        StampEntityVersion stampEntityVersion = StampEntityVersionClass.make(stampEntity, state.nid(), time, PrimitiveData.nid(authorId),
                PrimitiveData.nid(moduleId), PrimitiveData.nid(pathId));
        stampEntity.versions.add(stampEntityVersion);
        return stampEntity;
    }

    public static StampEntityClass make(UUID stampUuid, State state, long time, int authorNid, int moduleNid, int pathNid) {
        StampEntityClass stampEntity = new StampEntityClass(stampUuid);
        StampEntityVersion stampEntityVersion = StampEntityVersionClass.make(stampEntity, state.nid(), time, authorNid,
                moduleNid, pathNid);
        stampEntity.versions.add(stampEntityVersion);
        return stampEntity;
    }

    public static StampEntityClass make(ByteBuf readBuf, byte entityFormatVersion) {
        StampEntityClass stampEntity = new StampEntityClass();
        stampEntity.fill(readBuf, entityFormatVersion);
        return stampEntity;
    }

    public static StampEntityClass make(Stamp other) {
        StampEntityClass stampEntity = new StampEntityClass();
        stampEntity.fill(other);
        return stampEntity;
    }

    public StampEntityVersion addVersion(State state, long time, int authorNid, int moduleNid, int pathNid) {
        StampEntityVersionClass entityVersion = StampEntityVersionClass.make(this, state.nid(), time, authorNid, moduleNid, pathNid);
        this.versions.add((T) entityVersion);
        return entityVersion;
    }

    @Override
    public State state() {
        return State.fromConceptNid(lastVersion().stateNid());
    }

    @Override
    public long time() {
        return lastVersion().time();
    }

    @Override
    public ConceptFacade author() {
        return lastVersion().author();
    }

    @Override
    public ConceptFacade module() {
        return lastVersion().module();
    }

    @Override
    public ConceptFacade path() {
        return lastVersion().path();
    }

    @Override
    public Stamp stamp() {
        return this;
    }

    @Override
    public int pathNid() {
        return lastVersion().pathNid();
    }

    @Override
    public int moduleNid() {
        return lastVersion().moduleNid();
    }

    @Override
    public int authorNid() {
        return lastVersion().authorNid();
    }

    @Override
    public StampEntityVersion lastVersion() {
        if (versions.size() == 1) {
            return versions.get(0);
        }
        StampEntityVersion latest = null;
        for (StampEntityVersion version : versions) {
            if (version.time() == Long.MIN_VALUE) {
                // if canceled (Long.MIN_VALUE), latest is canceled.
                return version;
            } else if (latest == null || latest.time() < version.time()) {
                latest = version;
            }
        }
        return latest;
    }

    @Override
    public int stateNid() {
        return lastVersion().stateNid();
    }

    @Override
    public String describe() {
        return "s:" + PrimitiveData.text(stateNid()) +
                " t:" + DateTimeUtil.format(time()) +
                " a:" + PrimitiveData.text(authorNid()) +
                " m:" + PrimitiveData.text(moduleNid()) +
                " p:" + PrimitiveData.text(pathNid());
    }

    @Override
    public FieldDataType dataType() {
        return STAMP;
    }

    @Override
    protected void finishEntityWrite(ByteBuf byteBuf) {

    }

    @Override
    protected void finishEntityRead(ByteBuf readBuf, byte formatVersion) {

    }

    @Override
    protected StampEntityVersion makeVersion(ByteBuf readBuf, byte formatVersion) {
        return StampEntityVersionClass.make(this, readBuf, formatVersion);
    }

    @Override
    protected void finishEntityRead(Chronology<Version> chronology) {

    }

    @Override
    protected StampEntityVersion makeVersion(Version version) {
        return StampEntityVersionClass.make(this, (StampDTO) version);
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
