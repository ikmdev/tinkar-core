package dev.ikm.tinkar.terms;


import dev.ikm.tinkar.common.id.PublicId;
import org.eclipse.collections.api.map.primitive.ImmutableIntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;

public enum State implements dev.ikm.tinkar.component.Concept, ComponentWithNid {
    ACTIVE(TinkarTerm.ACTIVE_STATE),
    INACTIVE(TinkarTerm.INACTIVE_STATE),
    WITHDRAWN(TinkarTerm.WITHDRAWN_STATE),
    CANCELED(TinkarTerm.CANCELED_STATE),
    PRIMORDIAL(TinkarTerm.PRIMORDIAL_STATE);

    final EntityProxy.Concept proxyForState;

    State(EntityProxy.Concept proxyForState) {
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

    private static ImmutableIntObjectMap<State> nidStateMap;

    static {
        MutableIntObjectMap<State> mutableNidStateMap = IntObjectMaps.mutable.ofInitialCapacity(5);
        for (State state: State.values()) {
            mutableNidStateMap.put(state.nid(), state);
        }
        nidStateMap = mutableNidStateMap.toImmutable();
    }

    public static State fromConceptNid(int conceptNid) {
        return nidStateMap.get(conceptNid);
    }
    public static State fromConcept(ConceptFacade concept) {
        return nidStateMap.get(concept.nid());
    }
}
