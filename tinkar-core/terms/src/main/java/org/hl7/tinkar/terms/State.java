package org.hl7.tinkar.terms;


import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.component.Concept;

public enum State implements Concept, ComponentWithNid {
    ACTIVE(TinkarTerm.ACTIVE_STATE),
    INACTIVE(TinkarTerm.INACTIVE_STATE),
    WITHDRAWN(TinkarTerm.WITHDRAWN_STATE),
    CANCELED(TinkarTerm.CANCELED_STATE),
    PRIMORDIAL(TinkarTerm.PRIMORDIAL_STATE);

    final ConceptProxy proxyForState;

    State(ConceptProxy proxyForState) {
        this.proxyForState = proxyForState;
    }

    @Override
    public PublicId publicId() {
        return proxyForState;
    }

    @Override
    public int nid() {
        return proxyForState.nid();
    }

    public static State fromConcept(ConceptFacade concept) {
        if (nidStateMap.size() < 5) {
            for (State state: State.values()) {
                nidStateMap.put(state.nid(), state);
            }
        }
        return nidStateMap.get(concept.nid());

    }
    public static State fromConceptNid(int conceptNid) {
        if (nidStateMap.size() < 5) {
            for (State state: State.values()) {
                nidStateMap.put(state.nid(), state);
            }
        }
        return nidStateMap.get(conceptNid);

    }

    private static MutableIntObjectMap<State> nidStateMap = IntObjectMaps.mutable.ofInitialCapacity(5);
}
