package dev.ikm.tinkar.entity;

import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.component.Stamp;
import dev.ikm.tinkar.component.Version;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.State;

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

    default boolean inactive() {
        return !active();
    }

    default boolean active() {
        return stamp().state().nid() == State.ACTIVE.nid();
    }

    default boolean canceled() {
        return stamp().state().nid() == State.CANCELED.nid();
    }


    default boolean committed() {
        return !uncommitted();
    }

    default boolean uncommitted() {
        StampEntity stamp = stamp();
        if (stamp.time() == Long.MAX_VALUE) {
            return true;
        }
        if (stamp().state().nid() == State.CANCELED.nid()) {
            return false;
        }
        if (Transaction.forStamp(stamp).isPresent()) {
            // Participating in an active transaction...
            return true;
        }
        return false;
    }

    Entity chronology();
}
