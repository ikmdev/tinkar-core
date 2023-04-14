package dev.ikm.tinkar.common.id.impl;

import dev.ikm.tinkar.common.id.IntIdList;

public class IntId0List extends IntId0 implements IntIdList {
    public static final IntId0List INSTANCE = new IntId0List();

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof IntIdList intIdList) {
            if (intIdList.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "IntIdList[]";
    }

}
