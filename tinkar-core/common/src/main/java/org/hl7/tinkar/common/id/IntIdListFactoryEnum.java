package org.hl7.tinkar.common.id;

import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.common.id.impl.*;

/**
 *
 */
enum IntIdListFactoryEnum implements IntIdListFactory {
    INSTANCE;

    @Override
    public IntIdList empty()
    {
        return IntId0List.INSTANCE;
    }

    @Override
    public IntIdList of()
    {
        return this.empty();
    }

    @Override
    public IntIdList of(int one)
    {
        return new IntId1List(one);
    }

    @Override
    public IntIdList of(int one, int two)
    {
        return new IntId2List(one, two);
    }

    @Override
    public IntIdList of(int... elements)
    {
        if (elements == null || elements.length == 0)
        {
            return this.empty();
        }
        if (elements.length == 1)
        {
            return new IntId1List(elements[0]);
        }
        if (elements.length == 2)
        {
            return new IntId2List(elements[0], elements[1]);
        }
        return new IntIdListArray(elements);
    }
}
