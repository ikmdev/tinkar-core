package org.hl7.tinkar.common.id.impl;


import org.hl7.tinkar.common.id.IntIdSet;

public class IntId0Set extends IntId0 implements IntIdSet {
    public static final IntId0Set INSTANCE = new IntId0Set();

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof IntIdSet intIdSet) {
            if (intIdSet.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "IntIdSet[]";
    }

}
