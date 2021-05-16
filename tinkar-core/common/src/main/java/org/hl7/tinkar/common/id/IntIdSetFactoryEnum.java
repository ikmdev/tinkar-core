package org.hl7.tinkar.common.id;


import org.hl7.tinkar.common.id.impl.*;


enum IntIdSetFactoryEnum implements IntIdSetFactory {
    INSTANCE;

    @Override
    public IntIdSet empty()
    {
        return IntId0Set.INSTANCE;
    }

    @Override
    public IntIdSet of()
    {
        return this.empty();
    }

    @Override
    public IntIdSet of(int one)
    {
        return new IntId1Set(one);
    }


    @Override
    public IntIdSet of(int one, int two)
    {
        return new IntId2Set(one, two);
    }

    @Override
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

    @Override
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
