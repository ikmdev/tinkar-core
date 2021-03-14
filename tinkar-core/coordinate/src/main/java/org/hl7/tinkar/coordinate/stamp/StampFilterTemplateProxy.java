package org.hl7.tinkar.coordinate.stamp;

import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.set.primitive.ImmutableIntSet;

public interface StampFilterTemplateProxy extends StampFilterTemplate {
    StampFilterTemplate getStampFilterTemplate();

    @Override
    default StateSet getAllowedStates() {
        return getStampFilterTemplate().getAllowedStates();
    }

    @Override
    default ImmutableIntSet getModuleNids() {
        return getStampFilterTemplate().getModuleNids();
    }

    @Override
    default ImmutableIntList getModulePriorityOrder() {
        return getStampFilterTemplate().getModulePriorityOrder();
    }

    @Override
    default ImmutableIntSet getExcludedModuleNids() {
        return getStampFilterTemplate().getExcludedModuleNids();
    }
}
