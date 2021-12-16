package org.hl7.tinkar.coordinate.edit;

import com.google.auto.service.AutoService;
import org.hl7.tinkar.collection.ConcurrentReferenceHashMap;
import org.hl7.tinkar.common.binary.Decoder;
import org.hl7.tinkar.common.binary.DecoderInput;
import org.hl7.tinkar.common.binary.Encoder;
import org.hl7.tinkar.common.binary.EncoderOutput;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.coordinate.ImmutableCoordinate;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.terms.ConceptFacade;

import java.util.Objects;


public class EditCoordinateImmutable implements EditCoordinate, ImmutableCoordinate {

    private static final ConcurrentReferenceHashMap<EditCoordinateImmutable, EditCoordinateImmutable> SINGLETONS =
            new ConcurrentReferenceHashMap<>(ConcurrentReferenceHashMap.ReferenceType.WEAK,
                    ConcurrentReferenceHashMap.ReferenceType.WEAK);

    private final int authorNid;
    private final int defaultModuleNid;
    private final int promotionPathNid;
    private final int defaultPathNid;
    private final int destinationModuleNid;

    private EditCoordinateImmutable() {
        // No arg constructor for HK2 managed instance
        // This instance just enables reset functionality...
        this.authorNid = Integer.MAX_VALUE;
        this.defaultModuleNid = Integer.MAX_VALUE;
        this.promotionPathNid = Integer.MAX_VALUE;
        this.defaultPathNid = Integer.MAX_VALUE;
        this.destinationModuleNid = Integer.MAX_VALUE;
    }

    private EditCoordinateImmutable(int authorNid, int defaultModuleNid, int destinationModuleNid, int defaultPathNid, int promotionPathNid) {
        this.authorNid = authorNid;
        this.defaultModuleNid = defaultModuleNid;
        this.destinationModuleNid = destinationModuleNid;
        this.defaultPathNid = defaultPathNid;
        this.promotionPathNid = promotionPathNid;
    }

    /**
     * @param authorNid
     * @param moduleNid Used for both developing. and modularizing activities
     * @param pathNid
     * @return
     */
    public static EditCoordinateImmutable make(int authorNid, int moduleNid, int pathNid) {
        return SINGLETONS.computeIfAbsent(new EditCoordinateImmutable(authorNid, moduleNid, moduleNid, pathNid, pathNid),
                editCoordinateImmutable -> editCoordinateImmutable);
    }

    /**
     * @param author
     * @param defaultModule     The default module is the module for new content when developing.
     * @param destinationModule The destination module is the module that existing content is moved to when Modularizing
     * @param promotionPath
     * @return
     */
    public static EditCoordinateImmutable make(ConceptFacade author, ConceptFacade defaultModule, ConceptFacade destinationModule,
                                               ConceptFacade defaultPath, ConceptFacade promotionPath) {
        return make(Entity.nid(author), Entity.nid(defaultModule), Entity.nid(destinationModule), Entity.nid(defaultPath), Entity.nid(promotionPath));
    }

    /**
     * @param authorNid
     * @param defaultModuleNid     The default module is the module for new content when developing.
     * @param destinationModuleNid The destination module is the module that existing content is moved to when Modularizing
     * @param promotionPathNid
     * @return
     */
    public static EditCoordinateImmutable make(int authorNid, int defaultModuleNid, int destinationModuleNid, int defaultPathNid, int promotionPathNid) {
        return SINGLETONS.computeIfAbsent(new EditCoordinateImmutable(authorNid, defaultModuleNid, destinationModuleNid, defaultPathNid, promotionPathNid),
                editCoordinateImmutable -> editCoordinateImmutable);
    }

    @Decoder
    public static EditCoordinateImmutable decode(DecoderInput in) {
        switch (in.encodingFormatVersion()) {
            case MARSHAL_VERSION:
                return SINGLETONS.computeIfAbsent(new EditCoordinateImmutable(in.readNid(), in.readNid(), in.readNid(), in.readNid(), in.readNid()),
                        editCoordinateImmutable -> editCoordinateImmutable);
            default:
                throw new UnsupportedOperationException("Unsupported version: " + in.encodingFormatVersion());
        }
    }

    @Override
    @Encoder
    public void encode(EncoderOutput out) {
        out.writeNid(this.authorNid);
        out.writeNid(this.defaultModuleNid);
        out.writeNid(this.destinationModuleNid);
        out.writeNid(this.defaultPathNid);
        out.writeNid(this.promotionPathNid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getAuthorNidForChanges(), getDefaultModuleNid(), getPromotionPathNid(), getDestinationModuleNid());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EditCoordinateImmutable that)) return false;
        return getAuthorNidForChanges() == that.getAuthorNidForChanges() &&
                getDefaultModuleNid() == that.getDefaultModuleNid() &&
                getDestinationModuleNid() == that.getDestinationModuleNid() &&
                getDefaultPathNid() == that.getDefaultPathNid() &&
                getPromotionPathNid() == that.getPromotionPathNid();
    }

    @Override
    public String toString() {
        return "EditCoordinateImmutable{" +
                toUserString() +
                '}';
    }

    @Override
    public int getAuthorNidForChanges() {
        return this.authorNid;
    }

    @Override
    public int getDefaultModuleNid() {
        return this.defaultModuleNid;
    }

    @Override
    public int getDestinationModuleNid() {
        return this.destinationModuleNid;
    }

    @Override
    public int getDefaultPathNid() {
        return this.defaultPathNid;
    }

    @Override
    public int getPromotionPathNid() {
        return this.promotionPathNid;
    }

    @Override
    public EditCoordinateImmutable toEditCoordinateImmutable() {
        return this;
    }

    @AutoService(CachingService.class)
    public static class CacheProvider implements CachingService {
        // TODO: this has implicit assumption that no one will hold on to a calculator... Should we be defensive?
        @Override
        public void reset() {
            SINGLETONS.clear();
        }
    }
}
