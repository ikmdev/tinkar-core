package dev.ikm.tinkar.common.id.impl;

import dev.ikm.tinkar.common.id.IntIdList;
import dev.ikm.tinkar.common.service.PrimitiveData;

public class IntId1List extends IntId1 implements IntIdList {
    public IntId1List(int element) {
        super(element);
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
        if (obj instanceof IntIdList intIdList) {
            if (intIdList.size() == 1 && intIdList.get(0) == element) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "IntIdList[" + PrimitiveData.textWithNid(element) + "]";
    }
}
