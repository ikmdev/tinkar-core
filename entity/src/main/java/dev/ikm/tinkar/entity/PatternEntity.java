package dev.ikm.tinkar.entity;

import dev.ikm.tinkar.component.FieldDataType;
import dev.ikm.tinkar.component.PatternChronology;
import dev.ikm.tinkar.terms.PatternFacade;

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
