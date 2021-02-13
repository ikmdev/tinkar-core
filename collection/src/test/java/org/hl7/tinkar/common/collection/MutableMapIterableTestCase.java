package org.hl7.tinkar.common.collection;

import java.util.*;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.map.MutableMapIterable;
import org.eclipse.collections.impl.bag.mutable.HashBag;
import org.eclipse.collections.impl.block.factory.Predicates2;
import org.eclipse.collections.impl.block.function.PassThruFunction0;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.factory.Sets;
import org.eclipse.collections.impl.list.Interval;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.map.AbstractSynchronizedMapIterable;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.eclipse.collections.impl.set.mutable.UnifiedSet;
import org.eclipse.collections.impl.tuple.ImmutableEntry;
import org.eclipse.collections.impl.tuple.Tuples;
import org.eclipse.collections.impl.utility.Iterate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.eclipse.collections.impl.factory.Iterables.iMap;
import static org.eclipse.collections.impl.factory.Iterables.mList;

/**
 * Abstract JUnit TestCase for {@link MutableMapIterable}s.
 */
public abstract class MutableMapIterableTestCase extends MapIterableTestCase
{

    protected abstract ConcurrentUuidIntHashMap newMapWithKeyValue(UUID key, Integer value);

    @Override
    protected abstract ConcurrentUuidIntHashMap newMapWithKeysValues(UUID key1, Integer value1, UUID key2, Integer value2);

    @Override
    protected abstract ConcurrentUuidIntHashMap newMapWithKeysValues(
            UUID key1, Integer value1,
            UUID key2, Integer value2,
            UUID key3, Integer value3);

    @Override
    protected abstract ConcurrentUuidIntHashMap newMapWithKeysValues(
            UUID key1, Integer value1,
            UUID key2, Integer value2,
            UUID key3, Integer value3,
            UUID key4, Integer value4);

    @Test
    public void toImmutable()
    {
        ConcurrentUuidIntHashMap map = this.newMapWithKeyValue(uuid("One"), 1);
        ImmutableMap<UUID, Integer> immutable = map.toImmutable();
        Assertions.assertEquals(Maps.immutable.with(uuid("One"), 1), immutable);
    }

    @Test
    public void clear()
    {
        MutableMap<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3);
        map.clear();
        Verify.assertEmpty(map);

        ConcurrentUuidIntHashMap map2 = this.newUuidIntMap();
        map2.clear();
        Verify.assertEmpty(map2);
    }

    @Test
    public void removeObject()
    {
        MutableMap<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        map.remove(uuid("Two"));
        Verify.assertMapsEqual(UnifiedMap.newWithKeysValues(uuid("One"), 1, uuid("Three"), 3), map);
    }

    @Test
    public void removeFromEntrySet()
    {
        MutableMap<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        Assertions.assertTrue(map.entrySet().remove(ImmutableEntry.of(uuid("Two"), 2)));
        Assertions.assertEquals(UnifiedMap.newWithKeysValues(uuid("One"), 1, uuid("Three"), 3), map);

        Assertions.assertFalse(map.entrySet().remove(ImmutableEntry.of(uuid("Four"), 4)));
        Assertions.assertEquals(UnifiedMap.newWithKeysValues(uuid("One"), 1, uuid("Three"), 3), map);

        Assertions.assertFalse(map.entrySet().remove(null));

        MutableMap<UUID, Integer> mapWithNullKey = this.newMapWithKeysValues(uuid("One"), 1, null, 2, uuid("Three"), 3);
        Assertions.assertTrue(mapWithNullKey.entrySet().remove(new ImmutableEntry<String, Integer>(null, 2)));
    }

    @Test
    public void removeAllFromEntrySet()
    {
        MutableMap<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        Assertions.assertTrue(map.entrySet().removeAll(FastList.newListWith(
                ImmutableEntry.of(uuid("One"), 1),
                ImmutableEntry.of(uuid("Three"), 3))));
        Assertions.assertEquals(UnifiedMap.newWithKeysValues(uuid("Two"), 2), map);

        Assertions.assertFalse(map.entrySet().removeAll(FastList.newListWith(ImmutableEntry.of(uuid("Four"), 4))));
        Assertions.assertEquals(UnifiedMap.newWithKeysValues(uuid("Two"), 2), map);

        Assertions.assertFalse(map.entrySet().remove(null));

        MutableMap<UUID, Integer> mapWithNullKey = this.newMapWithKeysValues(uuid("One"), 1, null, 2, uuid("Three"), 3);
        Assertions.assertTrue(mapWithNullKey.entrySet().removeAll(FastList.newListWith(ImmutableEntry.of(null, 2))));
    }

    @Test
    public void retainAllFromEntrySet()
    {
        MutableMap<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        Assertions.assertFalse(map.entrySet().retainAll(FastList.newListWith(
                ImmutableEntry.of(uuid("One"), 1),
                ImmutableEntry.of(uuid("Two"), 2),
                ImmutableEntry.of(uuid("Three"), 3),
                ImmutableEntry.of(uuid("Four"), 4))));

        Assertions.assertTrue(map.entrySet().retainAll(FastList.newListWith(
                ImmutableEntry.of(uuid("One"), 1),
                ImmutableEntry.of(uuid("Three"), 3),
                ImmutableEntry.of(uuid("Four"), 4))));
        Assertions.assertEquals(UnifiedMap.newWithKeysValues(uuid("One"), 1, uuid("Three"), 3), map);

        MutableMapIterable<UUID, Integer> integers = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3);
        UUID copy = uuid(1);
        Assertions.assertTrue(integers.entrySet().retainAll(mList(ImmutableEntry.of(copy, toInt(copy)))));
        Assertions.assertEquals(iMap(copy, toInt(copy)), integers);
        Assertions.assertNotSame(copy, Iterate.getOnly(integers.entrySet()).getKey());
        Assertions.assertNotSame(copy, Iterate.getOnly(integers.entrySet()).getValue());
    }

    @Test
    public void clearEntrySet()
    {
        MutableMap<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        map.entrySet().clear();
        Verify.assertEmpty(map);
    }

    @Test
    public void entrySetEqualsAndHashCode()
    {
        MutableMap<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        Verify.assertEqualsAndHashCode(
                UnifiedSet.newSetWith(
                        ImmutableEntry.of(uuid("One"), 1),
                        ImmutableEntry.of(uuid("Two"), 2),
                        ImmutableEntry.of(uuid("Three"), 3)),
                map.entrySet());
    }

    @Test
    public void removeFromKeySet()
    {
        MutableMap<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        Assertions.assertFalse(map.keySet().remove(uuid("Four")));

        Assertions.assertTrue(map.keySet().remove(uuid("Two")));
        Assertions.assertEquals(UnifiedMap.newWithKeysValues(uuid("One"), 1, uuid("Three"), 3), map);
    }

    @Test
    public void removeAllFromKeySet()
    {
        MutableMap<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        Assertions.assertFalse(map.keySet().removeAll(FastList.newListWith(uuid("Four"))));

        Assertions.assertTrue(map.keySet().removeAll(FastList.newListWith(uuid("Two"), uuid("Four"))));
        Assertions.assertEquals(UnifiedMap.newWithKeysValues(uuid("One"), 1, uuid("Three"), 3), map);
    }

    @Test
    public void retainAllFromKeySet()
    {
        MutableMap<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        Assertions.assertFalse(map.keySet().retainAll(FastList.newListWith(uuid("One"), uuid("Two"), uuid("Three"), uuid("Four"))));

        Assertions.assertTrue(map.keySet().retainAll(FastList.newListWith(uuid("One"), uuid("Three"))));
        Assertions.assertEquals(UnifiedMap.newWithKeysValues(uuid("One"), 1, uuid("Three"), 3), map);
    }

    @Test
    public void clearKeySet()
    {
        MutableMapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        map.keySet().clear();
        Verify.assertEmpty(map);
    }

    @Test
    public void keySetEqualsAndHashCode()
    {
        MutableMapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, null, 4) ;
        Verify.assertEqualsAndHashCode(UnifiedSet.newSetWith(uuid("1"), uuid("2"), uuid("3"), null), map.keySet());
    }

    @Test
    public void keySetToArray()
    {
        MutableMapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        MutableList<UUID> expected = FastList.newListWith(uuid("One"), uuid("Two"), uuid("Three")).toSortedList();
        Set<UUID> keySet = map.keySet();
        Assertions.assertEquals(expected, FastList.newListWith(keySet.toArray()).toSortedList());
        Assertions.assertEquals(expected, FastList.newListWith(keySet.toArray(new UUID[keySet.size()])).toSortedList());
    }

    @Test
    public void removeFromValues()
    {
        MutableMapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        Assertions.assertFalse(map.values().remove(4));

        Assertions.assertTrue(map.values().remove(2));
        Assertions.assertEquals(UnifiedMap.newWithKeysValues(uuid("One"), 1, uuid("Three"), 3), map);
    }

    @Test
    public void removeAllFromValues()
    {
        MutableMapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        Assertions.assertFalse(map.values().removeAll(FastList.newListWith(4)));

        Assertions.assertTrue(map.values().removeAll(FastList.newListWith(2, 4)));
        Assertions.assertEquals(UnifiedMap.newWithKeysValues(uuid("One"), 1, uuid("Three"), 3), map);
    }

    @Test
    public void retainAllFromValues()
    {
        MutableMapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        Assertions.assertFalse(map.values().retainAll(FastList.newListWith(1, 2, 3, 4)));

        Assertions.assertTrue(map.values().retainAll(FastList.newListWith(1, 3)));
        Assertions.assertEquals(UnifiedMap.newWithKeysValues(uuid("One"), 1, uuid("Three"), 3), map);
    }

    @Test
    public void put()
    {
        ConcurrentUuidIntHashMap map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2);
        Assertions.assertNull(map.put(uuid("3"), 3));
        Assertions.assertEquals(UnifiedMap.newWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3), map);
    }

    @Test
    public void putAll()
    {
        ConcurrentUuidIntHashMap map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2);
        ConcurrentUuidIntHashMap toAdd = this.newMapWithKeysValues(uuid("2"), 2, uuid("3"), 3);

        map.putAll(toAdd);
        Verify.assertSize(3, map);
        Verify.assertContainsAllKeyValues(map, uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);

        //Testing JDK map
        ConcurrentUuidIntHashMap map2 = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2);
        HashMap<UUID, Integer> hashMaptoAdd = new HashMap<>(toAdd);
        map2.putAll(hashMaptoAdd);
        Verify.assertSize(3, map2);
        Verify.assertContainsAllKeyValues(map2, uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);
    }

    @Test
    public void removeKey()
    {
        ConcurrentUuidIntHashMap map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2);

        Assertions.assertEquals(Integer.valueOf(1), map.removeKey(uuid("1")));
        Verify.assertSize(1, map);
        Verify.denyContainsKey(1, map);

        Assertions.assertNull(map.removeKey(uuid("42")));
        Verify.assertSize(1, map);

        Assertions.assertEquals(Integer.valueOf(2), map.removeKey(uuid("2")));
        Verify.assertEmpty(map);
    }

    @Test
    public void removeAllKeys()
    {
        MutableMapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3);

        Verify.assertThrows(NullPointerException.class, () -> map.removeAllKeys(null));
        Assertions.assertFalse(map.removeAllKeys(Sets.mutable.with(uuid(4))));
        Assertions.assertFalse(map.removeAllKeys(Sets.mutable.with(uuid(4), uuid(5), uuid(6))));
        Assertions.assertFalse(map.removeAllKeys(Sets.mutable.with(uuid(4), uuid(5), uuid(6), uuid(7), uuid(8), uuid(9))));

        Assertions.assertTrue(map.removeAllKeys(Sets.mutable.with(uuid(1))));
        Verify.denyContainsKey(uuid(1), map);
        Assertions.assertTrue(map.removeAllKeys(Sets.mutable.with(uuid(3), uuid(4), uuid(5), uuid(6), uuid(7))));
        Verify.denyContainsKey(uuid(3), map);

        map.putAll(Maps.mutable.with(uuid(4), 4, uuid(5), 5, uuid(6), 6, uuid(7), 7));
        Assertions.assertTrue(map.removeAllKeys(Sets.mutable.with(uuid(2), uuid(3), uuid(9), uuid(10))));
        Verify.denyContainsKey(uuid(2), map);
        Assertions.assertTrue(map.removeAllKeys(Sets.mutable.with(uuid(5), uuid(3), uuid(7), uuid(8), uuid(9))));
        Assertions.assertEquals(Maps.mutable.with(uuid(4), 4, uuid(6), 6), map);
    }

    @Test
    public void removeIf()
    {
        ConcurrentUuidIntHashMap map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2);

        Assertions.assertFalse(map.removeIf(Predicates2.alwaysFalse()));
        Assertions.assertEquals(this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2), map);
        Assertions.assertTrue(map.removeIf(Predicates2.alwaysTrue()));
        Verify.assertEmpty(map);

        map.putAll(Maps.mutable.with(uuid(1), 1, uuid(2),  2,uuid(3),  3, uuid(4), 4));
        map.putAll(Maps.mutable.with(uuid(5), 5, uuid(6), 6, uuid(7), 7, uuid(8), 8));
        Assertions.assertTrue(map.removeIf((each, value) -> toInt(each) % 2 == 0 && numToText(value).length() < 4));
        Verify.denyContainsKey(uuid(2), map);
        Verify.denyContainsKey(uuid(6), map);
        MutableMapIterable<UUID, Integer> expected = this.newMapWithKeysValues(uuid(1), 1, uuid(3),  3, uuid(4), 4, uuid(5), 5);
        expected.put(uuid(7), 7);
        expected.put(uuid(8), 8);
        Assertions.assertEquals(expected, map);

        Assertions.assertTrue(map.removeIf((each, value) -> toInt(each) % 2 != 0 && numToText(value).equals("Three")));
        Verify.denyContainsKey(3, map);
        Verify.assertSize(5, map);

        Assertions.assertTrue(map.removeIf((each, value) -> toInt(each) % 2 != 0));
        Assertions.assertFalse(map.removeIf((each, value) -> toInt(each) % 2 != 0));
        Assertions.assertEquals(this.newMapWithKeysValues(uuid(4), 4, uuid(8), 8), map);
    }

    @Test
    public void getIfAbsentPut()
    {
        MutableMapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3);
        Assertions.assertNull(map.get(uuid(4)));
        Assertions.assertEquals(Integer.valueOf(4), map.getIfAbsentPut(uuid(4), new PassThruFunction0<>(4)));
        Assertions.assertEquals(Integer.valueOf(3), map.getIfAbsentPut(uuid(3), new PassThruFunction0<>(3)));
        Verify.assertContainsKeyValue(uuid(4), Integer.valueOf(4), map);
    }

    @Test
    public void getIfAbsentPutValue()
    {
        MutableMapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3);
        Assertions.assertNull(map.get(uuid(4)));
        Assertions.assertEquals(Integer.valueOf(4), map.getIfAbsentPut(uuid(4), 4));
        Assertions.assertEquals(Integer.valueOf(3), map.getIfAbsentPut(uuid(3), 5));
        Verify.assertContainsKeyValue(uuid(1), 1, map);
        Verify.assertContainsKeyValue(uuid(2), 2, map);
        Verify.assertContainsKeyValue(uuid(3), 3, map);
        Verify.assertContainsKeyValue(uuid(4), 4, map);
    }

    @Test
    public void getIfAbsentPutWithKey()
    {
        MutableMapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3);
        Assertions.assertNull(map.get(uuid(4)));
        Assertions.assertEquals(Integer.valueOf(4), map.getIfAbsentPutWithKey(uuid(4), MapIterableTestCase::toInt));
        Assertions.assertEquals(Integer.valueOf(3), map.getIfAbsentPutWithKey(uuid(3), MapIterableTestCase::toInt));
        Verify.assertContainsKeyValue(uuid(4), Integer.valueOf(4), map);
    }

    @Test
    public void getIfAbsentPutWith()
    {
        MutableMapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3);
        Assertions.assertNull(map.get(uuid(4)));
        Assertions.assertEquals(Integer.valueOf(4), map.getIfAbsentPutWith(uuid(4), Integer::valueOf, Integer.valueOf(4)));
        Assertions.assertEquals(Integer.valueOf(3), map.getIfAbsentPutWith(uuid(3), Integer::valueOf, Integer.valueOf(3)));
        Verify.assertContainsKeyValue(uuid(4), Integer.valueOf(4), map);
    }

    @Test
    public void getIfAbsentPut_block_throws()
    {
        MutableMapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3);
        Verify.assertThrows(RuntimeException.class, () -> map.getIfAbsentPut(uuid(4), () -> {
            throw new RuntimeException();
        }));
        Assertions.assertEquals(UnifiedMap.newWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3), map);
    }

    @Test
    public void getIfAbsentPutWith_block_throws()
    {
        MutableMapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3);
        Verify.assertThrows(RuntimeException.class, () -> map.getIfAbsentPutWith(uuid(4), object -> {
            throw new RuntimeException();
        }, null));
        Assertions.assertEquals(UnifiedMap.newWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3), map);
    }

    @Test
    public void getKeysAndGetValues()
    {
        MutableMapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3);
        Verify.assertContainsAll(map.keySet(), uuid(1), uuid(2), uuid(3));
        Verify.assertContainsAll(map.values(), 1, 2, 3);
    }

    @Test
    public void newEmpty()
    {
        MutableMapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2);
        Verify.assertEmpty(map.newEmpty());
    }

    @Test
    public void keysAndValues_toString()
    {
        MutableMapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2);
        Verify.assertContains(map.keySet().toString(),
                FastList.newListWith("[" +
                        uuid(1) +  ", " +
                        uuid(2) +"]", "[" +
                        uuid(2) +", " +
                        uuid(1) + "]"));
        Verify.assertContains(map.values().toString(), FastList.newListWith("[1, 2]", "[2, 1]"));
        Verify.assertContains(map.keysView().toString(), FastList.newListWith("[" +
                uuid(1) +  ", " +
                uuid(2) +"]", "[" +
                uuid(2) +", " +
                uuid(1) + "]"));
        Verify.assertContains(map.valuesView().toString(), FastList.newListWith("[1, 2]", "[2, 1]"));
    }

    @Test
    public void asUnmodifiable()
    {
        Verify.assertThrows(UnsupportedOperationException.class, () -> this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2).asUnmodifiable().put(uuid(3), 3));
    }

    @Test
    public void asSynchronized()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2).asSynchronized();
        Verify.assertInstanceOf(AbstractSynchronizedMapIterable.class, map);
    }

    @Test
    public void add()
    {
        ConcurrentUuidIntHashMap map = this.newMapWithKeyValue(uuid("A"), 1);

        Assertions.assertEquals(Integer.valueOf(1), map.add(Tuples.pair(uuid("A"), 3)));
        Assertions.assertNull(map.add(Tuples.pair(uuid("B"), 2)));
        Verify.assertMapsEqual(UnifiedMap.newWithKeysValues(uuid("A"), 3, uuid("B"), 2), map);
    }

    @Test
    public void putPair()
    {
        ConcurrentUuidIntHashMap map = this.newMapWithKeyValue(uuid("A"), 1);

        Assertions.assertEquals(Integer.valueOf(1), map.putPair(Tuples.pair(uuid("A"), 3)));
        Assertions.assertNull(map.putPair(Tuples.pair(uuid("B"), 2)));
        Verify.assertMapsEqual(UnifiedMap.newWithKeysValues(uuid("A"), 3, uuid("B"), 2), map);
    }

    @Test
    public void withKeyValue()
    {
        ConcurrentUuidIntHashMap map = this.newMapWithKeyValue(uuid("A"), 1);

        ConcurrentUuidIntHashMap mapWith = map.withKeyValue(uuid("B"), 2);
        Assertions.assertSame(map, mapWith);
        Verify.assertMapsEqual(UnifiedMap.newWithKeysValues(uuid("A"), 1, uuid("B"), 2), mapWith);

        ConcurrentUuidIntHashMap mapWith2 = mapWith.withKeyValue(uuid("A"), 11);
        Verify.assertMapsEqual(UnifiedMap.newWithKeysValues(uuid("A"), 11, uuid("B"), 2), mapWith);
    }

    @Test
    public void withMap()
    {
        MutableMapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("A"), 1, uuid("B"), 2);
        Map<UUID, Integer> simpleMap = Maps.mutable.with(uuid("B"), 22, uuid("C"), 3);
        map.putAll(simpleMap);
        MutableMapIterable<UUID, Integer> mapWith = map.withMap(simpleMap);
        Assertions.assertSame(map, mapWith);
        Verify.assertMapsEqual(UnifiedMap.newWithKeysValues(uuid("A"), 1, uuid("B"), 22, uuid("C"), 3), mapWith);
    }

    @Test
    public void withMapEmpty()
    {
        MutableMapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("A"), 1, uuid("B"), 2);
        MutableMapIterable<UUID, Integer> mapWith = map.withMap(Maps.mutable.empty());
        Assertions.assertSame(map, mapWith);
        Verify.assertMapsEqual(UnifiedMap.newWithKeysValues(uuid("A"), 1, uuid("B"), 2), mapWith);
    }

    @Test
    public void withMapTargetEmpty()
    {
        ConcurrentUuidIntHashMap map = this.newUuidIntMap();
        Map<UUID, Integer> simpleMap = Maps.mutable.with(uuid("A"), 1, uuid("B"), 2);
        MutableMapIterable<UUID, Integer> mapWith = map.withMap(simpleMap);
        Assertions.assertSame(map, mapWith);
        Verify.assertMapsEqual(UnifiedMap.newWithKeysValues(uuid("A"), 1, uuid("B"), 2), mapWith);
    }

    @Test
    public void withMapEmptyAndTargetEmpty()
    {
        ConcurrentUuidIntHashMap map = this.newUuidIntMap();
        ConcurrentUuidIntHashMap mapWith = map.withMap(Maps.mutable.empty());
        Assertions.assertSame(map, mapWith);
        Verify.assertMapsEqual(UnifiedMap.newMap(), mapWith);
    }

    @Test
    public void withMapNull()
    {
        Verify.assertThrows(NullPointerException.class, () -> this.newUuidIntMap().withMap(null));
    }

    @Test
    public void withAllKeyValues()
    {
        MutableMapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("A"), 1, uuid("B"), 2);
        MutableMapIterable<UUID, Integer> mapWith = map.withAllKeyValues(
                FastList.newListWith(Tuples.pair(uuid("B"), 22), Tuples.pair(uuid("C"), 3)));
        Assertions.assertSame(map, mapWith);
        Verify.assertMapsEqual(UnifiedMap.newWithKeysValues(uuid("A"), 1, uuid("B"), 22, uuid("C"), 3), mapWith);
    }

    @Test
    public void withAllKeyValueArguments()
    {
        MutableMapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("A"), 1, uuid("B"), 2);
        MutableMapIterable<UUID, Integer> mapWith = map.withAllKeyValueArguments(Tuples.pair(uuid("B"), 22), Tuples.pair(uuid("C"), 3));
        Assertions.assertSame(map, mapWith);
        Verify.assertMapsEqual(UnifiedMap.newWithKeysValues(uuid("A"), 1, uuid("B"), 22, uuid("C"), 3), mapWith);
    }

    @Test
    public void withoutKey()
    {
        MutableMapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("A"), 1, uuid("B"), 2);
        MutableMapIterable<UUID, Integer> mapWithout = map.withoutKey(uuid("B"));
        Assertions.assertSame(map, mapWithout);
        Verify.assertMapsEqual(UnifiedMap.newWithKeysValues(uuid("A"), 1), mapWithout);
    }

    @Test
    public void withoutAllKeys()
    {
        MutableMapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("A"), 1, uuid("B"), 2, uuid("C"), 3);
        MutableMapIterable<UUID, Integer> mapWithout = map.withoutAllKeys(FastList.newListWith(uuid("A"), uuid("C")));
        Assertions.assertSame(map, mapWithout);
        Verify.assertMapsEqual(UnifiedMap.newWithKeysValues(uuid("B"), 2), mapWithout);
    }

    @Test
    public void updateValue()
    {
        ConcurrentUuidIntHashMap map = this.newUuidIntMap();
        Iterate.forEach(Interval.oneTo(1000), each -> map.updateValue(new UUID(0, each % 10), () -> 0, integer -> integer + 1));
        Assertions.assertEquals(Interval.zeroTo(9).toSet(),
                Sets.mutable.of(map.keySet().stream().map(uuid -> toInt(uuid)).toArray()));
        Assertions.assertEquals(FastList.newList(Collections.nCopies(10, 100)), FastList.newList(map.values()));
    }

    @Test
    public void updateValue_collisions()
    {
        ConcurrentUuidIntHashMap map = this.newUuidIntMap();
        MutableList<Integer> list = Interval.oneTo(2000).toList().shuffleThis();
        Iterate.forEach(list, each -> map.updateValue(new UUID(0, each % 1000), () -> 0, integer -> integer + 1));
        Assertions.assertEquals(Interval.zeroTo(999).toSet(),
                Sets.mutable.of(map.keySet().stream().map(uuid -> toInt(uuid)).toArray()));
        Assertions.assertEquals(
                FastList.newList(Collections.nCopies(1000, 2)),
                FastList.newList(map.values()),
                HashBag.newBag(map.values()).toStringOfItemToCount()
                );
    }

    @Test
    public void updateValueWith()
    {
        ConcurrentUuidIntHashMap map = this.newUuidIntMap();
        Iterate.forEach(Interval.oneTo(1000), each -> map.updateValueWith(new UUID(0, each % 10), () -> 0, (integer, parameter) -> {
            Assertions.assertEquals("test", parameter);
            return integer + 1;
        }, "test"));
        Assertions.assertEquals(Interval.zeroTo(9).toSet(),
                Sets.mutable.of(map.keySet().stream().map(uuid -> toInt(uuid)).toArray()));
        Assertions.assertEquals(FastList.newList(Collections.nCopies(10, 100)), FastList.newList(map.values()));
    }

    @Test
    public void updateValueWith_collisions()
    {
        ConcurrentUuidIntHashMap map = this.newUuidIntMap();
        MutableList<Integer> list = Interval.oneTo(2000).toList().shuffleThis();
        Iterate.forEach(list, each -> map.updateValueWith(new UUID(0, each % 1000), () -> 0, (integer, parameter) -> {
            Assertions.assertEquals("test", parameter);
            return integer + 1;
        }, "test"));
        Assertions.assertEquals(Interval.zeroTo(999).toSet(),
                Sets.mutable.of(map.keySet().stream().map(uuid -> toInt(uuid)).toArray()));
        Assertions.assertEquals(
                FastList.newList(Collections.nCopies(1000, 2)),
                FastList.newList(map.values()),
                HashBag.newBag(map.values()).toStringOfItemToCount()
                );
    }
}
