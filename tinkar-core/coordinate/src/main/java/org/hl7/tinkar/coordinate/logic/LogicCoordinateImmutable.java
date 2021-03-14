package org.hl7.tinkar.coordinate.logic;


import java.util.Objects;

import org.hl7.tinkar.collection.ConcurrentReferenceHashMap;
import org.hl7.tinkar.common.binary.Decoder;
import org.hl7.tinkar.common.binary.DecoderInput;
import org.hl7.tinkar.common.binary.Encoder;
import org.hl7.tinkar.common.binary.EncoderOutput;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.coordinate.language.DefaultDescriptionText;
import org.hl7.tinkar.coordinate.ImmutableCoordinate;
import org.hl7.tinkar.entity.ConceptEntity;
import org.hl7.tinkar.entity.Entity;

//This class is not treated as a service, however, it needs the annotation, so that the reset() gets fired at appropriate times.
//@TODO Service annotation
public final class LogicCoordinateImmutable implements LogicCoordinate, ImmutableCoordinate, CachingService {

    private static final ConcurrentReferenceHashMap<LogicCoordinateImmutable, LogicCoordinateImmutable> SINGLETONS =
            new ConcurrentReferenceHashMap<>(ConcurrentReferenceHashMap.ReferenceType.WEAK,
                    ConcurrentReferenceHashMap.ReferenceType.WEAK);

    private static final int marshalVersion = 2;

    private final int classifierNid;
    private final int descriptionLogicProfileNid;
    private final int inferredAssemblageNid;
    private final int statedAssemblageNid;
    private final int conceptAssemblageNid;
    private final int digraphIdentityNid;
    private final int rootNid;

    private LogicCoordinateImmutable() {
        // No arg constructor for HK2 managed instance
        // This instance just enables reset functionality...
        this.classifierNid = Integer.MAX_VALUE;
        this.descriptionLogicProfileNid = Integer.MAX_VALUE;
        this.inferredAssemblageNid = Integer.MAX_VALUE;
        this.statedAssemblageNid = Integer.MAX_VALUE;
        this.conceptAssemblageNid = Integer.MAX_VALUE;
        this.digraphIdentityNid = Integer.MAX_VALUE;
        this.rootNid = Integer.MAX_VALUE;
    }
    
    @Override
    public void reset() {
        SINGLETONS.clear();
    }

    private LogicCoordinateImmutable(int classifierNid,
                                    int descriptionLogicProfileNid,
                                    int inferredAssemblageNid,
                                    int statedAssemblageNid,
                                    int conceptAssemblageNid,
                                    int digraphIdentityNid,
                                     int rootNid) {
        this.classifierNid = classifierNid;
        this.descriptionLogicProfileNid = descriptionLogicProfileNid;
        this.inferredAssemblageNid = inferredAssemblageNid;
        this.statedAssemblageNid = statedAssemblageNid;
        this.conceptAssemblageNid = conceptAssemblageNid;
        this.digraphIdentityNid = digraphIdentityNid;
        this.rootNid = rootNid;
    }

    private LogicCoordinateImmutable(DecoderInput in, int version) {
        this.classifierNid = in.readNid();
        this.descriptionLogicProfileNid = in.readNid();
        this.inferredAssemblageNid = in.readNid();
        this.statedAssemblageNid = in.readNid();
        this.conceptAssemblageNid = in.readNid();
        this.digraphIdentityNid = in.readNid();
        this.rootNid = in.readNid();
    }

    @Override
    @Encoder
    public void encode(EncoderOutput out) {
        out.writeNid(this.classifierNid);
        out.writeNid(this.descriptionLogicProfileNid);
        out.writeNid(this.inferredAssemblageNid);
        out.writeNid(this.statedAssemblageNid);
        out.writeNid(this.conceptAssemblageNid);
        out.writeNid(this.digraphIdentityNid);
        out.writeNid(this.rootNid);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LogicCoordinate that)) return false;
        return getClassifierNid() == that.getClassifierNid() &&
                getDescriptionLogicProfileNid() == that.getDescriptionLogicProfileNid() &&
                getInferredAssemblageNid() == that.getInferredAssemblageNid() &&
                getStatedAssemblageNid() == that.getStatedAssemblageNid() &&
                getConceptAssemblageNid() == that.getConceptAssemblageNid() &&
                getDigraphIdentityNid() == that.getDigraphIdentityNid() &&
                getRootNid() == that.getRootNid();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClassifierNid(), getDescriptionLogicProfileNid(), getInferredAssemblageNid(),
                getStatedAssemblageNid(), getConceptAssemblageNid(), getDigraphIdentityNid(), getRootNid());
    }

    public static LogicCoordinateImmutable make(int classifierNid,
                                                int descriptionLogicProfileNid,
                                                int inferredAssemblageNid,
                                                int statedAssemblageNid,
                                                int conceptAssemblageNid,
                                                int digraphIdentityNid,
                                                int rootNid)  {
        return SINGLETONS.computeIfAbsent(new LogicCoordinateImmutable(classifierNid, descriptionLogicProfileNid,
                        inferredAssemblageNid, statedAssemblageNid, conceptAssemblageNid, digraphIdentityNid, rootNid),
                logicCoordinateImmutable -> logicCoordinateImmutable);
    }

    public static LogicCoordinateImmutable make(ConceptEntity classifier,
                                                ConceptEntity descriptionLogicProfile,
                                                ConceptEntity inferredAssemblage,
                                                ConceptEntity statedAssemblage,
                                                ConceptEntity conceptAssemblage,
                                                ConceptEntity digraphIdentity,
                                                ConceptEntity root)  {
        return SINGLETONS.computeIfAbsent(new LogicCoordinateImmutable(classifier.nid(), descriptionLogicProfile.nid(),
                        inferredAssemblage.nid(), statedAssemblage.nid(), conceptAssemblage.nid(), digraphIdentity.nid(),
                        root.nid()),
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
    public int getInferredAssemblageNid() {
        return this.inferredAssemblageNid;
    }

    @Override
    public int getStatedAssemblageNid() {
        return this.statedAssemblageNid;
    }

    @Override
    public int getConceptAssemblageNid() {
        return this.conceptAssemblageNid;
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
    public int getDigraphIdentityNid() {
        return this.digraphIdentityNid;
    }

    @Override
    public String toString() {
        return "LogicCoordinateImpl{" +
                "stated axioms:" + DefaultDescriptionText.get(this.statedAssemblageNid) + "<" + this.statedAssemblageNid + ">,\n" +
                "inferred axioms:" + DefaultDescriptionText.get(this.inferredAssemblageNid) + "<" + this.inferredAssemblageNid + ">, \n" +
                "profile:" + DefaultDescriptionText.get(this.descriptionLogicProfileNid) + "<" + this.descriptionLogicProfileNid + ">, \n" +
                "classifier:" + DefaultDescriptionText.get(this.classifierNid) + "<" + this.classifierNid + ">, \n" +
                "concepts:" + DefaultDescriptionText.get(this.conceptAssemblageNid) + "<" + this.conceptAssemblageNid + ">, \n" +
                "digraph identity:" + DefaultDescriptionText.get(this.digraphIdentityNid) + "<" + this.digraphIdentityNid + ">, \n" +
                "root:" + DefaultDescriptionText.get(this.rootNid) + "<" + this.rootNid + ">,\n" +
        "}";
    }
    @Override
    public LogicCoordinateImmutable toLogicCoordinateImmutable() {
        return this;
    }


}
