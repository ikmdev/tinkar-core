package org.hl7.tinkar.common.id.impl;

import org.hl7.tinkar.common.id.IntIdSet;
import org.hl7.tinkar.common.service.PrimitiveData;

import java.util.Arrays;

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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof IntIdSet intIdSet) {
            if (intIdSet.size() == 2 && Arrays.equals(this.toArray(), intIdSet.toArray())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "IntIdSet[" + PrimitiveData.textWithNid(element) + ", " + PrimitiveData.textWithNid(element2) + "]";
    }
}
