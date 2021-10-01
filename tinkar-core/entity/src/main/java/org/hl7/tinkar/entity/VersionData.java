package org.hl7.tinkar.entity;

import org.hl7.tinkar.component.Stamp;
import org.hl7.tinkar.component.Version;
import org.hl7.tinkar.entity.transaction.Transaction;
import org.hl7.tinkar.terms.ConceptFacade;
import org.hl7.tinkar.terms.State;

public interface VersionData extends Version, Stamp<StampEntityVersion> {

    Entity entity();

    default State state() {
        return stamp().state();
    }

    default StampEntity stamp() {
        return Entity.getStamp(stampNid());
    }

    int stampNid();

    default long time() {
        return stamp().time();
    }

    default ConceptFacade author() {
        return stamp().author();
    }

    default ConceptFacade module() {
        return stamp().module();
    }

    default ConceptFacade path() {
        return stamp().path();
    }

    default int authorNid() {
        return stamp().authorNid();
    }

    default int moduleNid() {
        return stamp().moduleNid();
    }

    default int pathNid() {
        return stamp().pathNid();
    }

    default boolean isActive() {
        return stamp().state().nid() == State.ACTIVE.nid();
    }

    default boolean committed() {
        return !uncommitted();
    }

    default boolean uncommitted() {
        StampEntity stamp = stamp();
        if (stamp.time() == Long.MAX_VALUE) {
            return true;
        }
        if (Transaction.forStamp(stamp).isPresent()) {
            // Participating in an active transaction...
            return true;
        }
        return false;
    }

    Entity chronology();
}
