package org.hl7.tinkar.entity;

import org.eclipse.collections.api.factory.Lists;
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
        return FieldDataType.getFieldDataType(fields().get(fieldIndex));
    }

    @Override
    ImmutableList<Object> fields();

    // TODO implement narrative value
    default ImmutableList<Field> fields(PatternEntityVersion patternVersion) {
        Field[] fieldArray = new Field[fields().size()];
        for (int i = 0; i < fieldArray.length; i++) {
            Object value = fields().get(i);
            FieldDefinitionForEntity fieldDef = patternVersion.fieldDefinitions().get(i);
            if (fieldDef.narrativeOptional().isPresent()) {
                fieldArray[i] = new FieldRecord(value, fieldDef.narrativeOptional().get(), fieldDef.dataTypeNid, fieldDef.purposeNid, fieldDef.meaningNid,
                        this);
            } else {
                fieldArray[i] = new FieldRecord(value, null, fieldDef.dataTypeNid, fieldDef.purposeNid, fieldDef.meaningNid,
                        this);
            }
        }
        return Lists.immutable.of(fieldArray);
    }

}
