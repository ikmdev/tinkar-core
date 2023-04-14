package dev.ikm.tinkar.entity;

import dev.ikm.tinkar.component.ConceptChronology;
import dev.ikm.tinkar.component.FieldDataType;
import dev.ikm.tinkar.terms.ConceptFacade;
import org.eclipse.collections.api.list.ImmutableList;

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
