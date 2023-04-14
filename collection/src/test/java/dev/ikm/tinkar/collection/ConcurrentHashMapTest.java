package dev.ikm.tinkar.collection;

import java.util.Collections;
import java.util.UUID;

import org.eclipse.collections.api.bag.MutableBag;
import org.eclipse.collections.api.block.function.Function;
import org.eclipse.collections.api.block.function.Function2;
import org.eclipse.collections.api.block.function.Function3;
import org.eclipse.collections.api.block.procedure.Procedure;
import org.eclipse.collections.api.block.procedure.Procedure2;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.ConcurrentMutableMap;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.partition.PartitionIterable;
import org.eclipse.collections.impl.bag.mutable.HashBag;
import org.eclipse.collections.impl.block.factory.Functions;
import org.eclipse.collections.impl.block.factory.IntegerPredicates;
import org.eclipse.collections.impl.block.factory.Predicates2;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.list.Interval;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.eclipse.collections.impl.parallel.ParallelIterate;
import org.eclipse.collections.impl.set.mutable.UnifiedSet;
import org.eclipse.collections.impl.tuple.ImmutableEntry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.eclipse.collections.impl.factory.Iterables.iSet;

/**
 * JUnit test for {@link ConcurrentHashMap}.
 */
public class ConcurrentHashMapTest extends ConcurrentHashMapTestCase
{
    public static final MutableMap<Integer, MutableBag<Integer>> SMALL_BAG_MUTABLE_MAP = Interval.oneTo(100).groupBy(each -> each % 10).toMap(HashBag::new);
    @Override
    protected ConcurrentUuidIntHashMap newUuidIntMap() {
        return ConcurrentUuidIntHashMap.newMap();
    }

    @Override
    public ConcurrentUuidIntHashMap newMapWithKeyValue(UUID key, Integer value)
    {
        return ConcurrentUuidIntHashMap.newMap().withKeyValue(key, value);
    }

    @Override
    public ConcurrentUuidIntHashMap newMapWithKeysValues(UUID key1, Integer value1, UUID key2, Integer value2)
    {
        return ConcurrentUuidIntHashMap.newMap().withKeyValue(key1, value1).withKeyValue(key2, value2);
    }

    @Override
    protected ConcurrentUuidIntHashMap newMapWithKeysValues(UUID key1, Integer value1, UUID key2, Integer value2, UUID key3, Integer value3) {
        return ConcurrentUuidIntHashMap.newMap().withKeyValue(key1, value1).withKeyValue(key2, value2).withKeyValue(key3, value3);
    }

    @Override
    protected ConcurrentUuidIntHashMap newMapWithKeysValues(
            UUID key1, Integer value1,
            UUID key2, Integer value2,
            UUID key3, Integer value3,
            UUID key4, Integer value4) {
        return ConcurrentUuidIntHashMap.newMap().withKeyValue(key1, value1).withKeyValue(key2, value2).
                withKeyValue(key3, value3).withKeyValue(key4, value4);
    }

    @Test
    public void doubleReverseTest()
    {
        FastList<String> source = FastList.newListWith("1", "2", "3");
        MutableList<String> expectedDoubleReverse = source.toReversed().collect(new Function<String, String>()
        {
            private String visited = "";

            public String valueOf(String object)
            {
                return this.visited += object;
            }
        }).toReversed();
        Assertions.assertEquals(FastList.newListWith("321", "32", "3"), expectedDoubleReverse);
        MutableList<String> expectedNormal = source.collect(new Function<String, String>()
        {
            private String visited = "";

            public String valueOf(String object)
            {
                return this.visited += object;
            }
        });
        Assertions.assertEquals(FastList.newListWith("1", "12", "123"), expectedNormal);
    }

    @Test
    public void putIfAbsent()
    {
        ConcurrentMutableMap<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2);
        Assertions.assertEquals(Integer.valueOf(1), map.putIfAbsent(uuid(1), 1));
        Assertions.assertNull(map.putIfAbsent(uuid(3), 3));
    }

    @Test
    public void replace()
    {
        ConcurrentMutableMap<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2);
        Assertions.assertEquals(Integer.valueOf(1), map.replace(uuid(1), 7));
        Assertions.assertEquals(Integer.valueOf(7), map.get(uuid(1)));
        Assertions.assertNull(map.replace(uuid(3), 3));
    }

    @Test
    public void entrySetContains()
    {
        ConcurrentUuidIntHashMap map = this.newMapWithKeysValues(uuid("One"), Integer.valueOf(1), uuid("Two"), Integer.valueOf(2), uuid("Three"), Integer.valueOf(3));
        Assertions.assertFalse(map.entrySet().contains(null));

        ImmutableEntry<UUID, Integer> entry = ImmutableEntry.of(uuid("One"), Integer.valueOf(1));
        ConcurrentUuidIntHashMap.Entry anotherEntry = map.getEntry(uuid("One"));
        Assertions.assertTrue(anotherEntry.equals(entry));
        Assertions.assertTrue(anotherEntry.equals(anotherEntry));

        Assertions.assertFalse(anotherEntry.equals("fail"));
        Assertions.assertFalse(anotherEntry.equals(ImmutableEntry.of(uuid("zero"), Integer.valueOf(0))));


        Assertions.assertFalse(map.entrySet().contains(ImmutableEntry.of(uuid("Zero"), Integer.valueOf(0))));
        Assertions.assertTrue(map.entrySet().contains(ImmutableEntry.of(uuid("One"), Integer.valueOf(1))));
    }

    @Test
    public void entrySetRemove()
    {
        MutableMap<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), Integer.valueOf(1), uuid("Two"), Integer.valueOf(2), uuid("Three"), Integer.valueOf(3));
        Assertions.assertFalse(map.entrySet().remove(null));
        Assertions.assertFalse(map.entrySet().remove(ImmutableEntry.of(uuid("Zero"), Integer.valueOf(0))));
        Assertions.assertTrue(map.entrySet().remove(ImmutableEntry.of(uuid("One"), Integer.valueOf(1))));
    }

    @Test
    public void replaceWithOldValue()
    {
        ConcurrentMutableMap<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2);
        Assertions.assertTrue(map.replace(uuid(1), 1, 7));
        Assertions.assertEquals(Integer.valueOf(7), map.get(uuid(1)));
        Assertions.assertFalse(map.replace(uuid(2), 3, 3));
    }

    @Test
    public void removeWithKeyValue()
    {
        ConcurrentMutableMap<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2);
        Assertions.assertTrue(map.remove(uuid(1), 1));
        Assertions.assertFalse(map.remove(uuid(2), 3));
    }

    @Override
    @Test
    public void removeFromEntrySet()
    {
        MutableMap<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        Assertions.assertTrue(map.entrySet().remove(ImmutableEntry.of(uuid("Two"), 2)));
        Assertions.assertEquals(UnifiedMap.newWithKeysValues(uuid("One"), 1, uuid("Three"), 3), map);

        Assertions.assertFalse(map.entrySet().remove(ImmutableEntry.of(uuid("Four"), 4)));
        Verify.assertEqualsAndHashCode(UnifiedMap.newWithKeysValues(uuid("One"), 1, uuid("Three"), 3), map);
    }

    @Override
    @Test
    public void removeAllFromEntrySet()
    {
        MutableMap<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        Assertions.assertTrue(map.entrySet().removeAll(FastList.newListWith(
                ImmutableEntry.of(uuid("One"), 1),
                ImmutableEntry.of(uuid("Three"), 3))));
        Assertions.assertEquals(UnifiedMap.newWithKeysValues(uuid("Two"), 2), map);

        Assertions.assertFalse(map.entrySet().removeAll(FastList.newListWith(ImmutableEntry.of(uuid("Four"), 4))));
        Verify.assertEqualsAndHashCode(UnifiedMap.newWithKeysValues(uuid("Two"), 2), map);
    }

    @Override
    @Test
    public void keySetEqualsAndHashCode()
    {
        MutableMap<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        Verify.assertEqualsAndHashCode(UnifiedSet.newSetWith(uuid("One"), uuid("Two"), uuid("Three")), map.keySet());
    }

    @Test
    public void equalsEdgeCases()
    {
        Assertions.assertNotEquals(ConcurrentHashMap.newMap().withKeyValue(1, 1), ConcurrentHashMap.newMap());
        Assertions.assertNotEquals(ConcurrentHashMap.newMap().withKeyValue(1, 1), ConcurrentHashMap.newMap().withKeyValue(1, 1).withKeyValue(2, 2));
    }

    @Test
    public void negativeInitialSize()
    {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ConcurrentUuidIntHashMap().newMap(-1));
    }

    @Override
    @Test
    public void partition_value()
    {
        ConcurrentUuidIntHashMap map = this.newMapWithKeysValues(
                uuid(1), 1,
                uuid(2), 2,
                uuid(3), 3,
                uuid(4), 4);
        PartitionIterable<Integer> partition = map.partition(IntegerPredicates.isEven());
        Assertions.assertEquals(iSet(2, 4), partition.getSelected().toSet());
        Assertions.assertEquals(iSet(1, 3), partition.getRejected().toSet());
    }

    @Override
    @Test
    public void partitionWith_value()
    {
        ConcurrentUuidIntHashMap map = this.newMapWithKeysValues(
                uuid(1), 1,
                uuid(2), 2,
                uuid(3), 3,
                uuid(4), 4);
        PartitionIterable<Integer> partition = map.partitionWith(Predicates2.in(), map.select(IntegerPredicates.isEven()));
        Assertions.assertEquals(iSet(2, 4), partition.getSelected().toSet());
        Assertions.assertEquals(iSet(1, 3), partition.getRejected().toSet());
    }

    @Override
    @Test
    public void withMapNull()
    {
        Verify.assertThrows(IllegalArgumentException.class, () -> this.newUuidIntMap().withMap(null));
    }

    @Test
    public void parallelGroupByIntoConcurrentHashMap()
    {
        MutableMap<Integer, MutableBag<Integer>> actual = ConcurrentHashMap.newMap();
        ParallelIterate.forEach(Interval.oneTo(100), each -> actual.getIfAbsentPut(each % 10, () -> HashBag.<Integer>newBag().asSynchronized()).add(each), 10, this.executor);
        Verify.assertEqualsAndHashCode(SMALL_BAG_MUTABLE_MAP, actual);
    }

    @Test
    public void parallelForEachValue()
    {
        ConcurrentHashMap<Integer, Integer> source =
                ConcurrentHashMap.newMap(Interval.oneTo(100).toMap(Functions.getIntegerPassThru(), Functions.getIntegerPassThru()));
        MutableMap<Integer, MutableBag<Integer>> actual = ConcurrentHashMap.newMap();
        Procedure<Integer> procedure = each -> actual.getIfAbsentPut(each % 10, () -> HashBag.<Integer>newBag().asSynchronized()).add(each);
        source.parallelForEachValue(FastList.newList(Collections.nCopies(5, procedure)), this.executor);
        Verify.assertEqualsAndHashCode(SMALL_BAG_MUTABLE_MAP, actual);
    }

    @Test
    public void parallelForEachEntry()
    {
        ConcurrentHashMap<Integer, Integer> source =
                ConcurrentHashMap.newMap(Interval.oneTo(100).toMap(Functions.getIntegerPassThru(), Functions.getIntegerPassThru()));
        MutableMap<Integer, MutableBag<Integer>> actual = ConcurrentHashMap.newMap();
        Procedure2<Integer, Integer> procedure2 = (key, value) -> actual.getIfAbsentPut(value % 10, () -> HashBag.<Integer>newBag().asSynchronized()).add(value);
        source.parallelForEachKeyValue(FastList.newList(Collections.nCopies(5, procedure2)), this.executor);
        Verify.assertEqualsAndHashCode(SMALL_BAG_MUTABLE_MAP, actual);
    }

    @Test
    public void putAllInParallelSmallMap()
    {
        ConcurrentHashMap<Integer, Integer> source = ConcurrentHashMap.newMap(Interval.oneTo(100).toMap(Functions.getIntegerPassThru(), Functions.getIntegerPassThru()));
        ConcurrentHashMap<Integer, Integer> target = ConcurrentHashMap.newMap();
        target.putAllInParallel(source, 100, this.executor);
        Verify.assertEqualsAndHashCode(source, target);
    }

    @Test
    public void putAllInParallelLargeMap()
    {
        ConcurrentHashMap<Integer, Integer> source = ConcurrentHashMap.newMap(Interval.oneTo(600).toMap(Functions.getIntegerPassThru(), Functions.getIntegerPassThru()));
        ConcurrentHashMap<Integer, Integer> target = ConcurrentHashMap.newMap();
        target.putAllInParallel(source, 100, this.executor);
        Verify.assertEqualsAndHashCode(source, target);
    }

    @Test
    public void concurrentPutGetPutAllRemoveContainsKeyContainsValueGetIfAbsentPutTest()
    {
        ConcurrentHashMap<Integer, Integer> map1 = ConcurrentHashMap.newMap();
        ConcurrentHashMap<Integer, Integer> map2 = ConcurrentHashMap.newMap();
        ParallelIterate.forEach(Interval.oneTo(100), each -> {
            map1.put(each, each);
            Assertions.assertEquals(each, map1.get(each));
            map2.putAll(Maps.mutable.of(each, each));
            map1.remove(each);
            map1.putAll(Maps.mutable.of(each, each));
            Assertions.assertEquals(each, map2.get(each));
            map2.remove(each);
            Assertions.assertNull(map2.get(each));
            Assertions.assertFalse(map2.containsValue(each));
            Assertions.assertFalse(map2.containsKey(each));
            Assertions.assertEquals(each, map2.getIfAbsentPut(each, Functions.getIntegerPassThru()));
            Assertions.assertTrue(map2.containsValue(each));
            Assertions.assertTrue(map2.containsKey(each));
            Assertions.assertEquals(each, map2.getIfAbsentPut(each, Functions.getIntegerPassThru()));
            map2.remove(each);
            Assertions.assertEquals(each, map2.getIfAbsentPutWith(each, Functions.getIntegerPassThru(), each));
            Assertions.assertEquals(each, map2.getIfAbsentPutWith(each, Functions.getIntegerPassThru(), each));
            Assertions.assertEquals(each, map2.getIfAbsentPut(each, Functions.getIntegerPassThru()));
        }, 1, this.executor);
        Verify.assertEqualsAndHashCode(map1, map2);
    }

    @Test
    public void concurrentPutIfAbsentGetIfPresentPutTest()
    {
        ConcurrentHashMap<Integer, Integer> map1 = ConcurrentHashMap.newMap();
        ConcurrentHashMap<Integer, Integer> map2 = ConcurrentHashMap.newMap();
        ParallelIterate.forEach(Interval.oneTo(100), each -> {
            map1.put(each, each);
            map1.put(each, each);
            Assertions.assertEquals(each, map1.get(each));
            map2.putAll(Maps.mutable.of(each, each));
            map2.putAll(Maps.mutable.of(each, each));
            map1.remove(each);
            Assertions.assertNull(map1.putIfAbsentGetIfPresent(each, new KeyTransformer(), new ValueFactory(), null, null));
            Assertions.assertEquals(each, map1.putIfAbsentGetIfPresent(each, new KeyTransformer(), new ValueFactory(), null, null));
        }, 1, this.executor);
        Assertions.assertEquals(map1, map2);
    }

    @Test
    public void concurrentClear()
    {
        ConcurrentHashMap<Integer, Integer> map = ConcurrentHashMap.newMap();
        ParallelIterate.forEach(Interval.oneTo(100), each -> {
            for (int i = 0; i < 10; i++)
            {
                map.put(each + i * 1000, each);
            }
            map.clear();
        }, 1, this.executor);
        Verify.assertEmpty(map);
    }

    @Test
    public void concurrentRemoveAndPutIfAbsent()
    {
        ConcurrentHashMap<Integer, Integer> map1 = ConcurrentHashMap.newMap();
        ParallelIterate.forEach(Interval.oneTo(100), each -> {
            Assertions.assertNull(map1.put(each, each));
            map1.remove(each);
            Assertions.assertNull(map1.get(each));
            Assertions.assertEquals(each, map1.getIfAbsentPut(each, Functions.getIntegerPassThru()));
            map1.remove(each);
            Assertions.assertNull(map1.get(each));
            Assertions.assertEquals(each, map1.getIfAbsentPutWith(each, Functions.getIntegerPassThru(), each));
            map1.remove(each);
            Assertions.assertNull(map1.get(each));
            for (int i = 0; i < 10; i++)
            {
                Assertions.assertNull(map1.putIfAbsent(each + i * 1000, each));
            }
            for (int i = 0; i < 10; i++)
            {
                Assertions.assertEquals(each, map1.putIfAbsent(each + i * 1000, each));
            }
            for (int i = 0; i < 10; i++)
            {
                Assertions.assertEquals(each, map1.remove(each + i * 1000));
            }
        }, 1, this.executor);
    }

    @Test
    public void emptyToString()
    {
        ConcurrentHashMap<?, ?> empty = ConcurrentHashMap.newMap(0);
        Assertions.assertEquals("{}", empty.toString());
    }

    private static class KeyTransformer implements Function2<Integer, Integer, Integer>
    {
        private static final long serialVersionUID = 1L;

        @Override
        public Integer value(Integer key, Integer value)
        {
            return key;
        }
    }

    private static class ValueFactory implements Function3<Object, Object, Integer, Integer>
    {
        private static final long serialVersionUID = 1L;

        @Override
        public Integer value(Object argument1, Object argument2, Integer key)
        {
            return key;
        }
    }
}
