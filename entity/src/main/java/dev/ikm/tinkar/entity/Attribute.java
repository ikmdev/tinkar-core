package dev.ikm.tinkar.entity;

import dev.ikm.tinkar.component.AttributeDefinition;
import dev.ikm.tinkar.component.FieldDataType;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.ConceptToDataType;
import dev.ikm.tinkar.terms.EntityProxy;

public interface Attribute<DT> extends AttributeDefinition {

    Attribute<DT> with(DT value);

    DT value();

    default FieldDataType attributeDataType() {
        return ConceptToDataType.convert(dataType());
    }

    default ConceptFacade dataType() {
        return EntityProxy.Concept.make(dataTypeNid());
    }

    default ConceptFacade purpose() {
        return EntityProxy.Concept.make(purposeNid());
    }

    default ConceptFacade meaning() {
        return EntityProxy.Concept.make(meaningNid());
    }

    int meaningNid();

    int purposeNid();

    int dataTypeNid();

    int fieldIndex();
}
