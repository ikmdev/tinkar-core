package org.hl7.tinkar.coordinate.logic;


import java.util.Objects;

import com.google.auto.service.AutoService;
import org.hl7.tinkar.collection.ConcurrentReferenceHashMap;
import org.hl7.tinkar.common.binary.Decoder;
import org.hl7.tinkar.common.binary.DecoderInput;
import org.hl7.tinkar.common.binary.Encoder;
import org.hl7.tinkar.common.binary.EncoderOutput;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.coordinate.ImmutableCoordinate;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.terms.ConceptFacade;

public final class LogicCoordinateImmutable implements LogicCoordinate, ImmutableCoordinate, CachingService {


    @AutoService(CachingService.class)
    public static class CacheProvider implements CachingService {
        @Override
        public void reset() {
            SINGLETONS.clear();
        }
    }

    private static final ConcurrentReferenceHashMap<LogicCoordinateImmutable, LogicCoordinateImmutable> SINGLETONS =
            new ConcurrentReferenceHashMap<>(ConcurrentReferenceHashMap.ReferenceType.WEAK,
                    ConcurrentReferenceHashMap.ReferenceType.WEAK);

    private static final int marshalVersion = 2;

    private final int classifierNid;
    private final int descriptionLogicProfileNid;
    private final int inferredAxiomsPatternNid;
    private final int statedAxiomsPatternNid;
    private final int conceptMemberPatternNid;
    private final int statedNavigationPatternNid;
    private final int inferredNavigationPatternNid;
    private final int rootNid;

    @Override
    public void reset() {
        SINGLETONS.clear();
    }

    private LogicCoordinateImmutable(int classifierNid,
                                    int descriptionLogicProfileNid,
                                    int inferredAxiomsPatternNid,
                                    int statedAxiomsPatternNid,
                                    int conceptMemberPatternNid,
                                    int statedNavigationPatternNid,
                                     int inferredNavigationPatternNid,
                                     int rootNid) {
        this.classifierNid = classifierNid;
        this.descriptionLogicProfileNid = descriptionLogicProfileNid;
        this.inferredAxiomsPatternNid = inferredAxiomsPatternNid;
        this.statedAxiomsPatternNid = statedAxiomsPatternNid;
        this.conceptMemberPatternNid = conceptMemberPatternNid;
        this.statedNavigationPatternNid = statedNavigationPatternNid;
        this.inferredNavigationPatternNid = inferredNavigationPatternNid;
        this.rootNid = rootNid;
    }

    private LogicCoordinateImmutable(DecoderInput in, int version) {
        this.classifierNid = in.readNid();
        this.descriptionLogicProfileNid = in.readNid();
        this.inferredAxiomsPatternNid = in.readNid();
        this.statedAxiomsPatternNid = in.readNid();
        this.conceptMemberPatternNid = in.readNid();
        this.statedNavigationPatternNid = in.readNid();
        this.inferredNavigationPatternNid = in.readNid();
        this.rootNid = in.readNid();
    }

    @Override
    @Encoder
    public void encode(EncoderOutput out) {
        out.writeNid(this.classifierNid);
        out.writeNid(this.descriptionLogicProfileNid);
        out.writeNid(this.inferredAxiomsPatternNid);
        out.writeNid(this.statedAxiomsPatternNid);
        out.writeNid(this.conceptMemberPatternNid);
        out.writeNid(this.statedNavigationPatternNid);
        out.writeNid(this.inferredNavigationPatternNid);
        out.writeNid(this.rootNid);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LogicCoordinate that)) return false;
        return getClassifierNid() == that.getClassifierNid() &&
                getDescriptionLogicProfileNid() == that.getDescriptionLogicProfileNid() &&
                getInferredAxiomsPatternNid() == that.getInferredAxiomsPatternNid() &&
                getStatedAxiomsPatternNid() == that.getStatedAxiomsPatternNid() &&
                getConceptMemberPatternNid() == that.getConceptMemberPatternNid() &&
                getStatedNavigationPatternNid() == that.getStatedNavigationPatternNid() &&
                getInferredNavigationPatternNid() == that.getInferredNavigationPatternNid() &&
                getRootNid() == that.getRootNid();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClassifierNid(), getDescriptionLogicProfileNid(), getInferredAxiomsPatternNid(),
                getStatedAxiomsPatternNid(), getConceptMemberPatternNid(), getStatedNavigationPatternNid(),
                getInferredNavigationPatternNid(), getRootNid());
    }

    public static LogicCoordinateImmutable make(int classifierNid,
                                                int descriptionLogicProfileNid,
                                                int inferredAssemblageNid,
                                                int statedAssemblageNid,
                                                int conceptAssemblageNid,
                                                int statedNavigationPatternNid,
                                                int inferredNavigationPatternNid,
                                                int rootNid)  {
        return SINGLETONS.computeIfAbsent(new LogicCoordinateImmutable(classifierNid, descriptionLogicProfileNid,
                        inferredAssemblageNid, statedAssemblageNid, conceptAssemblageNid, statedNavigationPatternNid,
                        inferredNavigationPatternNid, rootNid),
                logicCoordinateImmutable -> logicCoordinateImmutable);
    }

    public static LogicCoordinateImmutable make(ConceptFacade classifier,
                                                ConceptFacade descriptionLogicProfile,
                                                ConceptFacade inferredAssemblage,
                                                ConceptFacade statedAssemblage,
                                                ConceptFacade conceptAssemblage,
                                                ConceptFacade statedNavigationPattern,
                                                ConceptFacade inferredNavigationPattern,
                                                ConceptFacade root)  {
        return SINGLETONS.computeIfAbsent(new LogicCoordinateImmutable(classifier.nid(), descriptionLogicProfile.nid(),
                        inferredAssemblage.nid(), statedAssemblage.nid(), conceptAssemblage.nid(), statedNavigationPattern.nid(),
                        inferredNavigationPattern.nid(), root.nid()),
                logicCoordinateImmutable -> logicCoordinateImmutable);
    }

    @Decoder
    public static LogicCoordinateImmutable decode(DecoderInput in) {
        int objectMarshalVersion = in.encodingFormatVersion();
        switch (objectMarshalVersion) {
            case 1:
            case marshalVersion:
                return SINGLETONS.computeIfAbsent(new LogicCoordinateImmutable(in, objectMarshalVersion),
                        logicCoordinateImmutable -> logicCoordinateImmutable);
            default:
                throw new UnsupportedOperationException("Unsupported version: " + objectMarshalVersion);
        }
    }

    @Override
    public int getClassifierNid() {
        return this.classifierNid;
    }

    @Override
    public int getDescriptionLogicProfileNid() {
        return this.descriptionLogicProfileNid;
    }

    @Override
    public int getInferredAxiomsPatternNid() {
        return this.inferredAxiomsPatternNid;
    }

    @Override
    public int getStatedAxiomsPatternNid() {
        return this.statedAxiomsPatternNid;
    }

    @Override
    public int getConceptMemberPatternNid() {
        return this.conceptMemberPatternNid;
    }

    @Override
    public int getRootNid() {
        return this.rootNid;
    }

    @Override
    public Concept getRoot() {
        return Entity.getFast(this.rootNid);
    }

    @Override
    public int getStatedNavigationPatternNid() {
        return this.statedNavigationPatternNid;
    }

    @Override
    public int getInferredNavigationPatternNid() {
        return this.inferredNavigationPatternNid;
    }

    @Override
    public String toString() {
        return "LogicCoordinateImpl{" +
                "stated axioms: " + PrimitiveData.text(this.statedAxiomsPatternNid) + "<" + this.statedAxiomsPatternNid + ">,\n" +
                "inferred axioms: " + PrimitiveData.text(this.inferredAxiomsPatternNid) + "<" + this.inferredAxiomsPatternNid + ">, \n" +
                "profile: " + PrimitiveData.text(this.descriptionLogicProfileNid) + "<" + this.descriptionLogicProfileNid + ">, \n" +
                "classifier: " + PrimitiveData.text(this.classifierNid) + "<" + this.classifierNid + ">, \n" +
                "concept members: " + PrimitiveData.text(this.conceptMemberPatternNid) + "<" + this.conceptMemberPatternNid + ">, \n" +
                "stated navigation: " + PrimitiveData.text(this.statedNavigationPatternNid) + "<" + this.statedNavigationPatternNid + ">, \n" +
                "inferred navigation: " + PrimitiveData.text(this.inferredNavigationPatternNid) + "<" + this.inferredNavigationPatternNid + ">, \n" +
                "root:" + PrimitiveData.text(this.rootNid) + "<" + this.rootNid + ">,\n" +
        "}";
    }
    @Override
    public LogicCoordinateImmutable toLogicCoordinateImmutable() {
        return this;
    }


}
