package dev.ikm.tinkar.entity;

import dev.ikm.tinkar.component.FieldDataType;
import dev.ikm.tinkar.component.SemanticChronology;
import dev.ikm.tinkar.terms.SemanticFacade;
import org.eclipse.collections.api.list.ImmutableList;

public interface SemanticEntity<T extends SemanticEntityVersion> extends Entity<T>,
        SemanticFacade, SemanticChronology<T> {

    @Override
    ImmutableList<T> versions();

    @Override
    default FieldDataType entityDataType() {
        return FieldDataType.SEMANTIC_CHRONOLOGY;
    }

    @Override
    default FieldDataType versionDataType() {
        return FieldDataType.SEMANTIC_VERSION;
    }

    @Override
    default Entity referencedComponent() {
        return Entity.provider().getEntityFast(referencedComponentNid());
    }

    int referencedComponentNid();

    @Override
    default PatternEntity pattern() {
        return Entity.provider().getEntityFast(patternNid());
    }

    int patternNid();

    default int topEnclosingComponentNid() {
        return topEnclosingComponent().nid();
    }

    default Entity<? extends EntityVersion> topEnclosingComponent() {
        Entity<? extends EntityVersion> referencedComponent = Entity.getFast(referencedComponentNid());
        while (referencedComponent instanceof SemanticEntity parentSemantic) {
            referencedComponent = Entity.getFast(parentSemantic.referencedComponentNid());
        }
        return referencedComponent;
    }
}
