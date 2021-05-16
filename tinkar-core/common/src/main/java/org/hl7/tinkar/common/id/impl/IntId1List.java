package org.hl7.tinkar.common.id.impl;

import org.hl7.tinkar.common.id.IntIdList;

public class IntId1List extends IntId1 implements IntIdList {
    public IntId1List(int element) {
        super(element);
    }

    @Override
    public boolean isEmpty() {
        return false;
    }
}
