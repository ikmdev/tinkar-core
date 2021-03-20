package org.hl7.tinkar.coordinate.edit;

import java.util.Objects;

import com.google.auto.service.AutoService;
import org.hl7.tinkar.collection.ConcurrentReferenceHashMap;
import org.hl7.tinkar.common.binary.Decoder;
import org.hl7.tinkar.common.binary.DecoderInput;
import org.hl7.tinkar.common.binary.Encoder;
import org.hl7.tinkar.common.binary.EncoderOutput;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.coordinate.ImmutableCoordinate;
import org.hl7.tinkar.entity.Entity;


//This class is not treated as a service, however, it needs the annotation, so that the reset() gets fired at appropriate times.
@AutoService(CachingService.class)
public class EditCoordinateImmutable implements EditCoordinate, ImmutableCoordinate, CachingService {
    private static final int marshalVersion = 2;

    private static final ConcurrentReferenceHashMap<EditCoordinateImmutable, EditCoordinateImmutable> SINGLETONS =
            new ConcurrentReferenceHashMap<>(ConcurrentReferenceHashMap.ReferenceType.WEAK,
                    ConcurrentReferenceHashMap.ReferenceType.WEAK);

    private final int authorNid;
    private final int defaultModuleNid;
    private final int promotionPathNid;
    private final int destinationModuleNid;

    private EditCoordinateImmutable() {
        // No arg constructor for HK2 managed instance
        // This instance just enables reset functionality...
        this.authorNid = Integer.MAX_VALUE;
        this.defaultModuleNid = Integer.MAX_VALUE;
        this.promotionPathNid = Integer.MAX_VALUE;
        this.destinationModuleNid = Integer.MAX_VALUE;
    }

    @Override
    public void reset() {
        SINGLETONS.clear();
    }

    private EditCoordinateImmutable(int authorNid, int defaultModuleNid, int promotionPathNid, int destinationModuleNid) {
        this.authorNid = authorNid;
        this.defaultModuleNid = defaultModuleNid;
        this.promotionPathNid = promotionPathNid;
        this.destinationModuleNid = destinationModuleNid;
    }

    /**
     * 
     * @param authorNid
     * @param defaultModuleNid The default module is the module for new content when developing.
     * @param promotionPathNid
     * @param destinationModuleNid The destination module is the module that existing content is moved to when Modularizing
     * @return
     */
    public static EditCoordinateImmutable make(int authorNid, int defaultModuleNid, int promotionPathNid, int destinationModuleNid) {
        return SINGLETONS.computeIfAbsent(new EditCoordinateImmutable(authorNid, defaultModuleNid, promotionPathNid, destinationModuleNid),
                editCoordinateImmutable -> editCoordinateImmutable);
    }
    
    /**
     * 
     * @param authorNid
     * @param moduleNid Used for both developing. and modularizing activities
     * @param promotionPathNid
     * @return
     */
    public static EditCoordinateImmutable make(int authorNid, int moduleNid, int promotionPathNid) {
        return SINGLETONS.computeIfAbsent(new EditCoordinateImmutable(authorNid, moduleNid, promotionPathNid, moduleNid),
                editCoordinateImmutable -> editCoordinateImmutable);
    }

    /**
     * @param author
     * @param defaultModule The default module is the module for new content when developing.
     * @param promotionPath
     * @param destinationModule The destination module is the module that existing content is moved to when Modularizing
     * @return
     */
    public static EditCoordinateImmutable make(Concept author, Concept defaultModule, Concept promotionPath,
                                               Concept destinationModule) {
        return make(Entity.nid(author), Entity.nid(defaultModule), Entity.nid(promotionPath), Entity.nid(destinationModule));
    }

    @Decoder
    public static EditCoordinateImmutable decode(DecoderInput in) {
        int objectMarshalVersion = in.encodingFormatVersion();
        switch (objectMarshalVersion) {
            case marshalVersion:
                return SINGLETONS.computeIfAbsent(new EditCoordinateImmutable(in.readNid(), in.readNid(), in.readNid(), in.readNid()),
                        editCoordinateImmutable -> editCoordinateImmutable);
            default:
                throw new UnsupportedOperationException("Unsupported version: " + objectMarshalVersion);
        }
    }

    @Override
    @Encoder
    public void encode(EncoderOutput out) {
        out.writeNid(this.authorNid);
        out.writeNid(this.defaultModuleNid);
        out.writeNid(this.promotionPathNid);
        out.writeNid(this.destinationModuleNid);
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
    public int getPromotionPathNid() {
        return this.promotionPathNid;
    }

    @Override
    public int getDestinationModuleNid() {
        return this.destinationModuleNid;
    }

    @Override
    public EditCoordinateImmutable toEditCoordinateImmutable() {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EditCoordinateImmutable that)) return false;
        return getAuthorNidForChanges() == that.getAuthorNidForChanges() &&
                getDefaultModuleNid() == that.getDefaultModuleNid() &&
                getPromotionPathNid() == that.getPromotionPathNid() &&
                getDestinationModuleNid() == that.getDestinationModuleNid();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getAuthorNidForChanges(), getDefaultModuleNid(), getPromotionPathNid(), getDestinationModuleNid());
    }

    @Override
    public String toString() {
        return "EditCoordinateImmutable{" +
                toUserString() +
                '}';
    }
}
