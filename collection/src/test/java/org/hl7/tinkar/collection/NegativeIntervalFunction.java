package org.hl7.tinkar.common.collection;

import org.eclipse.collections.api.block.function.Function;
import org.eclipse.collections.impl.list.Interval;

public class NegativeIntervalFunction implements Function<Integer, Iterable<Integer>>
{
    private static final long serialVersionUID = 1L;

    @Override
    public Iterable<Integer> valueOf(Integer object)
    {
        return Interval.fromTo(-1, -object);
    }
}
