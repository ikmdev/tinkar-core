package org.hl7.tinkar.common.id.impl;

import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.common.service.PrimitiveData;

import java.util.Arrays;

public class IntId2List extends IntId2 implements IntIdList {
    public IntId2List(int element, int element2) {
        super(element, element2);
    }

    @Override
    public int get(int index) {
        if (index == 0) {
            return element;
        }
        if (index == 1) {
            return element2;
        }
        throw new IndexOutOfBoundsException("Index: " + index);
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
            if (intIdList.size() == 2 && Arrays.equals(this.toArray(), intIdList.toArray())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "IntIdList[" + PrimitiveData.textWithNid(element) + ", " + PrimitiveData.textWithNid(element2) + "]";
    }
}
