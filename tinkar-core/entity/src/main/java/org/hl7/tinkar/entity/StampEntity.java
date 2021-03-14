package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.component.Component;
import org.hl7.tinkar.component.Stamp;
import org.hl7.tinkar.entity.internal.Get;
import org.hl7.tinkar.lombok.dto.StampDTO;

import java.time.Instant;

public class StampEntity extends PublicIdForEntity
        implements Stamp, Component {

    int nid;

    // Object + fields takes > 224 native bits + 16 bytes.
    // Minimum object size is 16 bytes for modern 64-bit JDK since the object has 12-byte header,
    // padded to a multiple of 8 bytes. In 32-bit JDK, the overhead is 8 bytes, padded to a multiple
    // of 4 bytes.
    int statusNid;
    long time;
    int authorNid;
    int moduleNid;
    int pathNid;

    protected final void fill(ByteBuf readBuf) {
        this.nid = readBuf.readInt();
        this.statusNid = readBuf.readInt();
        this.time = readBuf.readLong();
        this.authorNid = readBuf.readInt();
        this.moduleNid = readBuf.readInt();
        this.pathNid = readBuf.readInt();
        super.fillId(readBuf);
    }

    protected final void fill(Stamp other) {
        if (other instanceof StampEntity) {
            StampEntity otherEntity = (StampEntity) other;
            this.nid = otherEntity.nid;
            this.statusNid = otherEntity.statusNid;
            this.time = otherEntity.time();
            this.authorNid = otherEntity.authorNid;
            this.moduleNid = otherEntity.moduleNid;
            this.pathNid = otherEntity.pathNid;
        } else {
            this.nid = Get.entityService().nidForComponent(other);
            this.statusNid = Get.entityService().nidForComponent(other.state());
            this.time = other.time();
            this.authorNid = Get.entityService().nidForComponent(other.author());
            this.moduleNid = Get.entityService().nidForComponent(other.module());
            this.pathNid = Get.entityService().nidForComponent(other.path());
        }
    }

    public int nid() {
        return nid;
    }

    public int statusNid() {
        return statusNid;
    }

    public int authorNid() {
        return authorNid;
    }

    public int moduleNid() {
        return moduleNid;
    }

    public int pathNid() {
        return pathNid;
    }

    @Override
    public ConceptEntity state() {
        return Get.entityService().getEntityFast(this.statusNid);
    }

    @Override
    public long time() {
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
    public PublicId publicId() {
        return this;
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
