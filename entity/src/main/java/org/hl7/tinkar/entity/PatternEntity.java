package org.hl7.tinkar.entity;

import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.component.PatternChronology;
import org.hl7.tinkar.terms.PatternFacade;

public interface PatternEntity<T extends PatternEntityVersion>
        extends Entity<T>,
                PatternChronology<T>,
                PatternFacade {

    @Override
    default FieldDataType entityDataType() {
        return FieldDataType.PATTERN_CHRONOLOGY;
    }

    @Override
    default FieldDataType versionDataType() {
        return FieldDataType.PATTERN_VERSION;
    }
}
