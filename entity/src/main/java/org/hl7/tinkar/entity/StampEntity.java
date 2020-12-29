package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.component.Component;
import org.hl7.tinkar.component.Stamp;
import org.hl7.tinkar.dto.StampDTO;
import org.hl7.tinkar.entity.internal.Get;
import org.hl7.tinkar.util.UuidT5Generator;

import java.time.Instant;
import java.util.UUID;

public class StampEntity
        implements Stamp, Component {

    int nid;

    // Object + fields takes > 224 native bits + 16 bytes.
    // Minimum object size is 16 bytes for modern 64-bit JDK since the object has 12-byte header,
    // padded to a multiple of 8 bytes. In 32-bit JDK, the overhead is 8 bytes, padded to a multiple
    // of 4 bytes.
    int statusNid;
    Instant time;
    int authorNid;
    int moduleNid;
    int pathNid;

    protected final void fill(ByteBuf readBuf) {
        this.nid = readBuf.readInt();
        this.statusNid = readBuf.readInt();
        this.time = Instant.ofEpochMilli(readBuf.readLong());
        this.authorNid = readBuf.readInt();
        this.moduleNid = readBuf.readInt();
        this.pathNid = readBuf.readInt();
    }

    protected final void fill(Stamp other) {
        if (other instanceof StampEntity otherEntity) {
            this.nid = otherEntity.nid;
            this.statusNid = otherEntity.statusNid;
            this.time = otherEntity.time();
            this.authorNid = otherEntity.authorNid;
            this.moduleNid = otherEntity.moduleNid;
            this.pathNid = otherEntity.pathNid;
        } else {
            this.nid = Get.entityService().nidForUuids(other.componentUuids());
            this.statusNid = Get.entityService().nidForUuids(other.status().componentUuids());
            this.time = other.time();
            this.authorNid = Get.entityService().nidForUuids(other.author().componentUuids());
            this.moduleNid = Get.entityService().nidForUuids(other.module().componentUuids());
            this.pathNid = Get.entityService().nidForUuids(other.path().componentUuids());
        }
    }

    public int getNid() {
        return nid;
    }

    public int getStatusNid() {
        return statusNid;
    }

    public Instant getTime() {
        return time;
    }

    public int getAuthorNid() {
        return authorNid;
    }

    public int getModuleNid() {
        return moduleNid;
    }

    public int getPathNid() {
        return pathNid;
    }

    @Override
    public ConceptEntity status() {
        return Get.entityService().getEntityFast(this.statusNid);
    }

    @Override
    public Instant time() {
        return time;
    }

    @Override
    public ConceptEntity author() {
        return Get.entityService().getEntityFast(this.authorNid);
    }

    @Override
    public ConceptEntity module() {
        return Get.entityService().getEntityFast(this.moduleNid);
    }

    @Override
    public ConceptEntity path() {
        return Get.entityService().getEntityFast(this.pathNid);
    }

    @Override
    public ImmutableList<UUID> componentUuids() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.statusNid).append(this.time.getEpochSecond());
        sb.append(this.time.getNano()).append(this.authorNid);
        sb.append(this.moduleNid).append(this.pathNid);
        return Lists.immutable.of(UuidT5Generator.get(UuidT5Generator.STAMP_NAMESPACE, sb.toString()));
    }

    public static StampEntity make(ByteBuf readBuf) {
        StampEntity stampEntity = new StampEntity();
        stampEntity.fill(readBuf);
        return stampEntity;
    }
    public static StampEntity make(StampDTO stamp) {
        StampEntity stampEntity = new StampEntity();
        stampEntity.fill(stamp);
        return stampEntity;
    }
}
