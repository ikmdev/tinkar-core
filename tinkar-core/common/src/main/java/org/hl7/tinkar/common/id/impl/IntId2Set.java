package org.hl7.tinkar.common.id.impl;

import org.hl7.tinkar.common.id.IntIdSet;

public class IntId2Set extends IntId2 implements IntIdSet {
    public IntId2Set(int element, int element2) {
        super(element, element2);
        if (element == element2) {
            throw new IllegalStateException("Duplicate values in set: " + element);
        }
    }

    @Override
    public boolean isEmpty() {
        return false;
    }
}
