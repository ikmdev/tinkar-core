package dev.ikm.tinkar.common.id.impl;


import dev.ikm.tinkar.common.id.IntIdSet;
import dev.ikm.tinkar.common.service.PrimitiveData;

public class IntId1Set extends IntId1 implements IntIdSet {
    public IntId1Set(int element) {
        super(element);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof IntIdSet intIdSet) {
            if (intIdSet.size() == 1 && intIdSet.toArray()[0] == element) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "IntIdSet[" + PrimitiveData.textWithNid(element) + "]";
    }

}
