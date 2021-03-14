package org.hl7.tinkar.common.collection;

import org.eclipse.collections.api.block.function.Function;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.hl7.tinkar.collection.ConcurrentUuidIntHashMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * Abstract JUnit TestCase for {@link MutableMap}s.
 */
public abstract class MutableMapTestCase extends MutableMapIterableTestCase
{


    @Override
    protected abstract ConcurrentUuidIntHashMap newMapWithKeysValues(UUID key1, Integer value1, UUID key2, Integer value2);

    @Override
    protected abstract ConcurrentUuidIntHashMap newMapWithKeysValues(UUID key1, Integer value1, UUID key2, Integer value2, UUID key3, Integer value3);

    @Test
    public void collectKeysAndValues()
    {
        ConcurrentUuidIntHashMap map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2);
        MutableList<Integer> toAdd = FastList.newListWith(2, 3);
        map.collectKeysAndValues(toAdd, new UuidPassThruFunction(), Integer::valueOf);
        Verify.assertSize(3, map);
        Verify.assertContainsAllKeyValues(map, uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);
    }
    private static class UuidPassThruFunction implements Function<Integer, UUID>
    {
        private static final long serialVersionUID = 1L;

        @Override
        public UUID valueOf(Integer each)
        {
            return uuid(Integer.toString(each));
        }

        @Override
        public String toString()
        {
            return UuidPassThruFunction.class.getSimpleName();
        }
    }

    @Test
    public void testClone()
    {
        ConcurrentUuidIntHashMap map = this.newMapWithKeysValues(uuid("1"), 1, uuid("Two"), 2);
        MutableMap<UUID, Integer> clone = map.clone();
        Assertions.assertNotSame(map, clone);
        Verify.assertEqualsAndHashCode(map, clone);
    }
}
