package org.hl7.tinkar.entity;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.component.ConceptChronology;
import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.terms.ConceptFacade;

public interface ConceptEntity<V extends ConceptEntityVersion>
        extends Entity<V>,
        ConceptChronology<V>,
        ConceptFacade,
        IdentifierData {

    @Override
    ImmutableList<V> versions();

    default FieldDataType entityDataType() {
        return FieldDataType.CONCEPT_CHRONOLOGY;
    }

    default FieldDataType versionDataType() {
        return FieldDataType.CONCEPT_VERSION;
    }
}
