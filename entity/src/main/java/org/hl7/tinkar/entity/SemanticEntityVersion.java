package org.hl7.tinkar.entity;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.component.SemanticVersion;
import org.hl7.tinkar.terms.EntityFacade;

public interface SemanticEntityVersion extends EntityVersion, SemanticVersion {
    /**
     * TODO: Do we need both entity() and chronology() ?
     *
     * @return
     */
    @Override
    default SemanticEntity entity() {
        return chronology();
    }

    @Override
    SemanticEntity chronology();

    default EntityFacade referencedComponent() {
        return Entity.provider().getEntityFast(referencedComponentNid());
    }

    default int referencedComponentNid() {
        return chronology().referencedComponentNid();
    }

    default PatternEntity pattern() {
        return Entity.provider().getEntityFast(patternNid());
    }

    default int patternNid() {
        return chronology().patternNid();
    }

    default FieldDataType fieldDataType(int fieldIndex) {
        return FieldDataType.getFieldDataType(fieldValues().get(fieldIndex));
    }

    @Override
    ImmutableList<Object> fieldValues();

    ImmutableList<? extends Field> fields(PatternEntityVersion patternVersion);

}
