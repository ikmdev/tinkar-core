package org.hl7.tinkar.entity.forremoval;

import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.component.Stamp;
import org.hl7.tinkar.component.Version;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.EntityService;
import org.hl7.tinkar.entity.EntityVersion;
import org.hl7.tinkar.entity.StampEntity;
import org.hl7.tinkar.entity.transaction.Transaction;
import org.hl7.tinkar.terms.ConceptFacade;
import org.hl7.tinkar.terms.State;

public abstract class EntityVersionClass
        implements EntityVersion {
    public static final int DEFAULT_VERSION_SIZE = 1024;
    protected EntityClass chronology;
    protected int stampNid;

    protected EntityVersionClass() {
    }

    @Override
    public final int nid() {
        return chronology.nid();
    }

    @Override
    public Entity entity() {
        return chronology;
    }

    @Override
    public int stampNid() {
        return stampNid;
    }

    @Override
    public Entity chronology() {
        return chronology;
    }

    @Override
    public PublicId publicId() {
        return chronology.publicId();
    }

    public final byte[] getBytes() {
        // TODO: write directly to a single ByteBuf, rather that the approach below.
        boolean complete = false;
        int versionSize = DEFAULT_VERSION_SIZE;
        while (!complete) {
            try {
                ByteBuf byteBuf = ByteBufPool.allocate(versionSize);
                versionSize = byteBuf.writeRemaining();
                byteBuf.writeByte(dataType().token); //ensure that the chronicle byte array sorts first.
                byteBuf.writeInt(stampNid);
                writeVersionFields(byteBuf);
                return byteBuf.asArray();
            } catch (RuntimeException e) {
                e.printStackTrace();
                versionSize = versionSize * 2;
            }
        }
        throw new IllegalStateException("Should never reach here. ");
    }

    public abstract FieldDataType dataType();

    protected abstract void writeVersionFields(ByteBuf writeBuf);

    protected final void fill(EntityClass chronology, ByteBuf readBuf, byte formatVersion) {
        this.chronology = chronology;
        int versionArraySize = readBuf.readInt();
        byte token = readBuf.readByte();
        if (dataType().token != token) {
            throw new IllegalStateException("Wrong token for type: " + token);
        }
        this.stampNid = readBuf.readInt();
        finishVersionFill(readBuf, formatVersion);
    }

    protected abstract void finishVersionFill(ByteBuf readBuf, byte formatVersion);

    protected final void fill(EntityClass chronology, Version version) {
        this.chronology = chronology;
        Stamp stamp = version.stamp();
        this.stampNid = EntityService.get().nidForComponent(stamp);
    }

    protected final void fill(EntityClass chronology, int stampNid) {
        this.chronology = chronology;
        this.stampNid = stampNid;
    }

    @Override
    public String toString() {
        return stamp().describe();
    }

    @Override
    public State state() {
        return stamp().state();
    }

    @Override
    public StampEntity stamp() {
        return EntityService.get().getStampFast(stampNid);
    }

    @Override
    public long time() {
        return stamp().time();
    }


    @Override
    public ConceptFacade author() {
        return stamp().author();
    }

    @Override
    public ConceptFacade module() {
        return stamp().module();
    }

    @Override
    public ConceptFacade path() {
        return stamp().path();
    }

    @Override
    public int authorNid() {
        return stamp().authorNid();
    }

    @Override
    public int moduleNid() {
        return stamp().moduleNid();
    }

    @Override
    public int pathNid() {
        return stamp().pathNid();
    }

    @Override
    public boolean active() {
        return stamp().state().nid() == State.ACTIVE.nid();
    }

    @Override
    public boolean committed() {
        return !uncommitted();
    }

    @Override
    public boolean uncommitted() {
        StampEntity stamp = stamp();
        if (stamp.time() == Long.MAX_VALUE) {
            return true;
        }
        if (Transaction.forStamp(stamp).isPresent()) {
            // Participating in an active transaction...
            return true;
        }
        return false;
    }


}
