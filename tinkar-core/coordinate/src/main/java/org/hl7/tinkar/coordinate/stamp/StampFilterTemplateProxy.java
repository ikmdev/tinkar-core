package org.hl7.tinkar.coordinate.stamp;

import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.set.primitive.ImmutableIntSet;

public interface StampFilterTemplateProxy extends StampFilterTemplate {
    StampFilterTemplate getStampFilterTemplate();

    @Override
    default StateSet allowedStates() {
        return getStampFilterTemplate().allowedStates();
    }

    @Override
    default ImmutableIntSet moduleNids() {
        return getStampFilterTemplate().moduleNids();
    }

    @Override
    default ImmutableIntList modulePriorityOrder() {
        return getStampFilterTemplate().modulePriorityOrder();
    }

    @Override
    default ImmutableIntSet excludedModuleNids() {
        return getStampFilterTemplate().excludedModuleNids();
    }
}
