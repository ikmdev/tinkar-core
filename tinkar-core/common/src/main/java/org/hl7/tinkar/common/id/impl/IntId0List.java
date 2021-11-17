package org.hl7.tinkar.common.id.impl;

import org.hl7.tinkar.common.id.IntIdList;

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
}
