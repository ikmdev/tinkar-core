package org.hl7.tinkar.common.id.impl;

import org.hl7.tinkar.common.id.IntIdList;

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
}
