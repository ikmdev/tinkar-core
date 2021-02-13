package org.hl7.tinkar.common.util.id.impl;

import org.hl7.tinkar.common.util.id.IntIdList;

/**
 *
 */
public enum IntIdListFactory {
    INSTANCE;

    public IntIdList empty()
    {
        return IntId0.INSTANCE;
    }

    public IntIdList of()
    {
        return this.empty();
    }

    public IntIdList of(int one)
    {
        return new IntId1(one);
    }

    public IntIdList of(int one, int two)
    {
        return new IntId2List(one, two);
    }

    public IntIdList of(int... elements)
    {
        if (elements == null || elements.length == 0)
        {
            return this.empty();
        }
        if (elements.length == 1)
        {
            return new IntId1(elements[0]);
        }
        if (elements.length == 2)
        {
            return new IntId2List(elements[0], elements[1]);
        }
        return new IntIdListArray(elements);
    }
}
