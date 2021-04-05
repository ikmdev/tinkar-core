package org.hl7.tinkar.entity.calculator;

import org.hl7.tinkar.component.PatternVersion;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.EntityVersion;
import org.hl7.tinkar.entity.SemanticEntityVersion;
import org.hl7.tinkar.entity.StampEntity;
import org.hl7.tinkar.terms.EntityFacade;
import org.hl7.tinkar.terms.PatternFacade;

import java.util.function.BiConsumer;

public interface VersionCalculator {

    <V extends EntityVersion> Latest<V> latest(Entity<V> chronicle);

    RelativePosition relativePosition(int stampNid, int stampNid2);

    default RelativePosition relativePosition(EntityVersion v1, EntityVersion v2) {
        return relativePosition(v1.stampNid(), v2.stampNid());
    }

    default RelativePosition relativePosition(StampEntity stamp1, StampEntity stamp2) {
        return relativePosition(stamp1.nid(), stamp2.nid());
    }


    default <V extends EntityVersion> Latest<V> latest(EntityFacade entityFacade) {
        return latest(entityFacade.nid());
    }
    <V extends EntityVersion> Latest<V> latest(int nid);

    default void forEachSemanticVersionOfPattern(PatternFacade patternFacade,
                                                 BiConsumer<SemanticEntityVersion, PatternVersion> procedure) {
        forEachSemanticVersionOfPattern(patternFacade.nid(), procedure);

    }

    void forEachSemanticVersionOfPattern(int patternNid, BiConsumer<SemanticEntityVersion, PatternVersion> procedure);


    default void forEachSemanticVersionForComponent(EntityFacade component,
                                                    BiConsumer<SemanticEntityVersion, EntityVersion> procedure) {
        forEachSemanticVersionForComponent(component.nid(), procedure);
    }

    void forEachSemanticVersionForComponent(int componentNid,
                                            BiConsumer<SemanticEntityVersion, EntityVersion> procedure);

    default void forEachSemanticVersionForComponentOfPattern(EntityFacade component,
                                                             PatternFacade patternFacade,
                                                             TriConsumer<SemanticEntityVersion, EntityVersion, PatternVersion> procedure) {
        forEachSemanticVersionForComponentOfPattern(component.nid(), patternFacade.nid(), procedure);
    }

    void forEachSemanticVersionForComponentOfPattern(int componentNid, int patternNid, TriConsumer<SemanticEntityVersion, EntityVersion, PatternVersion> procedure);

}
