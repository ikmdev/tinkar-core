package org.hl7.tinkar.common.util.id.impl;

import org.hl7.tinkar.common.util.id.IntIdSet;


public enum IntIdSetFactory {
    INSTANCE;

    public IntIdSet empty()
    {
        return IntId0.INSTANCE;
    }

    public IntIdSet of()
    {
        return this.empty();
    }

    public IntIdSet of(int one)
    {
        return new IntId1(one);
    }


    public IntIdSet of(int one, int two)
    {
        return new IntId2Set(one, two);
    }

    public IntIdSet of(int... elements)
    {
        if (elements == null || elements.length == 0)
        {
            return empty();
        }
        if (elements.length == 1)
        {
            return this.of(elements[0]);
        }
        if (elements.length == 2)
        {
            return this.of(elements[0], elements[1]);
        }
        if (elements.length < 1024) {
            return IntIdSetArray.newIntIdSet(elements);
        }
        return IntIdSetRoaring.newIntIdSet(elements);
    }

    public IntIdSet ofAlreadySorted(int... elements)
    {
        if (elements == null || elements.length == 0)
        {
            return empty();
        }
        if (elements.length == 1)
        {
            return this.of(elements[0]);
        }
        if (elements.length == 2)
        {
            return this.of(elements[0], elements[1]);
        }
        if (elements.length < 1024) {
            return IntIdSetArray.newIntIdSetAlreadySorted(elements);
        }
        return IntIdSetRoaring.newIntIdSetAlreadySorted(elements);
    }
}
