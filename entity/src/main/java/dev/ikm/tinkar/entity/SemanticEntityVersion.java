package dev.ikm.tinkar.entity;

import dev.ikm.tinkar.component.FieldDataType;
import dev.ikm.tinkar.component.SemanticVersion;
import dev.ikm.tinkar.terms.EntityFacade;
import org.eclipse.collections.api.list.ImmutableList;

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
