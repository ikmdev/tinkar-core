package org.hl7.tinkar.entity;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.hl7.tinkar.component.ConceptVersion;

@RecordBuilder
public record ConceptVersionRecord(ConceptEntity chronology, int stampNid)
        implements ConceptEntityVersion, ConceptVersionRecordBuilder.With {

    public ConceptVersionRecord(ConceptEntity chronology, ConceptVersion version) {
        this(chronology, EntityService.get().nidForComponent(version.stamp()));
    }

    @Override
    public ConceptEntity entity() {
        return chronology();
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(stampNid);
    }

    @Override
    public String toString() {
        return stamp().describe();
    }

}
