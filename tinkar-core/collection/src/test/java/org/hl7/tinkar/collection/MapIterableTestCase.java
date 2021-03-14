package org.hl7.tinkar.collection;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.collections.api.BooleanIterable;
import org.eclipse.collections.api.ByteIterable;
import org.eclipse.collections.api.CharIterable;
import org.eclipse.collections.api.DoubleIterable;
import org.eclipse.collections.api.FloatIterable;
import org.eclipse.collections.api.IntIterable;
import org.eclipse.collections.api.LazyIterable;
import org.eclipse.collections.api.LongIterable;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.ShortIterable;
import org.eclipse.collections.api.bag.Bag;
import org.eclipse.collections.api.bag.MutableBag;
import org.eclipse.collections.api.bag.sorted.MutableSortedBag;
import org.eclipse.collections.api.block.function.Function;
import org.eclipse.collections.api.block.function.Function0;
import org.eclipse.collections.api.block.function.Function2;
import org.eclipse.collections.api.block.function.primitive.CharFunction;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.map.primitive.ObjectDoubleMap;
import org.eclipse.collections.api.map.primitive.ObjectLongMap;
import org.eclipse.collections.api.multimap.Multimap;
import org.eclipse.collections.api.multimap.MutableMultimap;
import org.eclipse.collections.api.partition.PartitionIterable;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.sorted.MutableSortedSet;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.bag.mutable.HashBag;
import org.eclipse.collections.impl.bag.mutable.primitive.BooleanHashBag;
import org.eclipse.collections.impl.bag.mutable.primitive.ByteHashBag;
import org.eclipse.collections.impl.bag.mutable.primitive.CharHashBag;
import org.eclipse.collections.impl.bag.mutable.primitive.DoubleHashBag;
import org.eclipse.collections.impl.bag.mutable.primitive.FloatHashBag;
import org.eclipse.collections.impl.bag.mutable.primitive.IntHashBag;
import org.eclipse.collections.impl.bag.mutable.primitive.LongHashBag;
import org.eclipse.collections.impl.bag.mutable.primitive.ShortHashBag;
import org.eclipse.collections.impl.bag.sorted.mutable.TreeBag;
import org.eclipse.collections.impl.block.factory.Comparators;
import org.eclipse.collections.impl.block.factory.Functions;
import org.eclipse.collections.impl.block.factory.IntegerPredicates;
import org.eclipse.collections.impl.block.factory.Predicates;
import org.eclipse.collections.impl.block.factory.Predicates2;
import org.eclipse.collections.impl.block.function.AddFunction;
import org.eclipse.collections.impl.block.function.PassThruFunction0;
import org.eclipse.collections.impl.block.procedure.CollectionAddProcedure;
import org.eclipse.collections.impl.factory.Bags;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.factory.Sets;
import org.eclipse.collections.impl.list.Interval;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.list.primitive.IntInterval;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.eclipse.collections.impl.multimap.list.FastListMultimap;
import org.eclipse.collections.impl.set.mutable.UnifiedSet;
import org.eclipse.collections.impl.set.sorted.mutable.TreeSortedSet;
import org.eclipse.collections.impl.string.immutable.CharAdapter;
import org.eclipse.collections.impl.tuple.Tuples;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.eclipse.collections.impl.factory.Iterables.iBag;
import static org.eclipse.collections.impl.factory.Iterables.iSet;

public abstract class MapIterableTestCase
{

    public static UUID uuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes());
    }

    public static int booleanStringToInt(String booleanStr) {
        if (Boolean.parseBoolean(booleanStr)) {
            return 1;
        }
        return 0;
    }

    public static boolean intToBoolean(Integer booleanInt) {
        if (booleanInt == 1) {
            return true;
        }
        return false;
    }



    public static UUID uuid(Integer value) {
        return new UUID(0, value);
    }

    public static String numToText(Integer num) {
        switch (num) {
            case 1:
                return "One";
            case 2:
                return "Two";
            case 3:
                return "Three";
            case 4:
                return "Four";
            case 5:
                return "Five";
            case 6:
                return "Six";
            case 7:
                return "Seven";
            case 8:
                return "Eight";
            case 9:
                return "Nine";
            default:
                return Integer.toString(num);
        }
    }

    public static int textToNum(String numString) {
        switch (numString) {
            case "One":
                return 1;
            case "Two":
                return 2;
            case "Three":
                return 3;
            case "Four":
                return 4;
            case "Five":
                return 5;
            case "Six":
                return 6;
            case "Seven":
                return 7;
            case "Eight":
                return 8;
            case "Nine":
                return 9;
            default:
                return Integer.parseInt(numString);
        }
    }

    public static Integer toInt(UUID value) {
        return (int) value.getLeastSignificantBits();
}

    protected abstract ConcurrentUuidIntHashMap newUuidIntMap();

    protected abstract ConcurrentUuidIntHashMap newMapWithKeyValue(UUID key, Integer value);

    protected abstract ConcurrentUuidIntHashMap newMapWithKeysValues(
            UUID key1, Integer value1,
            UUID key2, Integer value2);

    protected abstract ConcurrentUuidIntHashMap newMapWithKeysValues(
            UUID key1, Integer value1,
            UUID key2, Integer value2,
            UUID key3, Integer value3);

    protected abstract ConcurrentUuidIntHashMap newMapWithKeysValues(
            UUID key1, Integer value1,
            UUID key2, Integer value2,
            UUID key3, Integer value3,
            UUID key4, Integer value4);

    @Test
    public void stream()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);
        Assertions.assertEquals(
                "123",
                CharAdapter.adapt(map.stream().map(integer ->
                        Integer.toString(integer)).reduce("", (r, s) -> r + s)).toSortedList().makeString(""));
        Assertions.assertEquals(map.reduce((r, s) -> r + s), map.stream().reduce((r, s) -> r + s));
    }

    @Test
    public void parallelStream()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);
        Assertions.assertEquals(
                "123",
                CharAdapter.adapt(map.stream().map(integer ->
                        Integer.toString(integer)).reduce("", (r, s) -> r + s)).toSortedList().makeString(""));
        Assertions.assertEquals(map.reduce((r, s) -> r + s), map.stream().reduce((r, s) -> r + s));
    }

    @Test
    public void equalsAndHashCode()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);
        Verify.assertPostSerializedEqualsAndHashCode(map);
        Verify.assertEqualsAndHashCode(Maps.mutable.of(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3), map);
        Verify.assertEqualsAndHashCode(Maps.immutable.of(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3), map);

        Assertions.assertNotEquals(map, this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2));
        Assertions.assertNotEquals(map, this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4));
        Assertions.assertNotEquals(map, this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("4"), 4));

        Verify.assertEqualsAndHashCode(
                Maps.immutable.with(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3),
                this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3));
    }

    @Test
    public void serialization()
    {
        MapIterable<UUID, Integer> original = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);
        MapIterable<UUID, Integer> copy = SerializeTestHelper.serializeDeserialize(original);
        Verify.assertIterableSize(3, copy);
        Assertions.assertEquals(original, copy);
    }

    @Test
    public void isEmpty()
    {
        Assertions.assertFalse(this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2).isEmpty());
        Assertions.assertTrue(this.newUuidIntMap().isEmpty());
    }

    @Test
    public void notEmpty()
    {
        Assertions.assertFalse(this.newUuidIntMap().notEmpty());
        Assertions.assertTrue(this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2).notEmpty());
    }

    @Test
    public void ifPresentApply()
    {
        ConcurrentUuidIntHashMap map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2);
        Assertions.assertEquals("1", map.ifPresentApply(uuid("1"), String::valueOf));
        Assertions.assertNull(map.ifPresentApply(uuid("3"), String::valueOf));
    }

    @Test
    public void getIfAbsent_function()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);
        Assertions.assertNull(map.get(4));
        Assertions.assertEquals(Integer.valueOf(1), map.getIfAbsent(uuid("1"), new PassThruFunction0<>(4)));
        Assertions.assertEquals(Integer.valueOf(4), map.getIfAbsent(uuid("4"), new PassThruFunction0<>(4)));
        Assertions.assertEquals(Integer.valueOf(3), map.getIfAbsent(uuid("3"), new PassThruFunction0<>(3)));
        Assertions.assertNull(map.get(4));
    }

    @Test
    public void getOrDefault()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);
        Assertions.assertNull(map.get(4));
        Assertions.assertEquals(Integer.valueOf(1), map.getOrDefault(uuid("1"), 4));
        Assertions.assertEquals(Integer.valueOf(4), map.getOrDefault(uuid("4"), 4));
        Assertions.assertEquals(Integer.valueOf(3), map.getOrDefault(uuid("3"), 3));
        Assertions.assertNull(map.get(4));
    }

    @Test
    public void getIfAbsent()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);
        Assertions.assertNull(map.get(4));
        Assertions.assertEquals(Integer.valueOf(1), map.getIfAbsentValue(uuid("1"), 4));
        Assertions.assertEquals(Integer.valueOf(4), map.getIfAbsentValue(uuid("4"), 4));
        Assertions.assertEquals(Integer.valueOf(3), map.getIfAbsentValue(uuid("3"), 3));
        Assertions.assertNull(map.get(4));
    }

    @Test
    public void getIfAbsentWith()
    {
        ConcurrentUuidIntHashMap map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);
        Assertions.assertNull(map.get(4));
        Assertions.assertEquals(Integer.valueOf(1), map.getIfAbsentWith(uuid("1"),
                Integer::valueOf,
                4));
        Assertions.assertEquals(Integer.valueOf(4), map.getIfAbsentWith(uuid("4"), Integer::valueOf, Integer.valueOf(4)));
        Assertions.assertEquals(Integer.valueOf(3), map.getIfAbsentWith(uuid("3"), Integer::valueOf, Integer.valueOf(3)));
        Assertions.assertNull(map.get(4));
    }

    @Test
    public void tap()
    {
        MutableList<Integer> tapResult = Lists.mutable.of();
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;
        Assertions.assertSame(map, map.tap(tapResult::add));
        Assertions.assertEquals(tapResult.toList(), tapResult);
    }

    @Test
    public void forEach()
    {
        MutableBag<Integer> result = Bags.mutable.of();
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;
        map.forEach(CollectionAddProcedure.on(result));
        Assertions.assertEquals(Bags.mutable.of(1, 2, 3, 4), result);
    }

    @Test
    public void forEachWith()
    {
        MutableList<Integer> result = Lists.mutable.of();
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;
        map.forEachWith((argument1, argument2) -> result.add(argument1 + argument2), 10);
        Verify.assertSize(4, result);
        Verify.assertContainsAll(result, 11, 12, 13, 14);
    }

    @Test
    public void forEachWithIndex()
    {
        MutableList<Integer> result = Lists.mutable.of();
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;
        map.forEachWithIndex((value, index) -> {
            result.add(value);
            result.add(index + 10);
        });
        Verify.assertSize(8, result);
        Verify.assertContainsAll(
                result,
                1, 2, 3, 4, // Map values
                10, 11, 12, 13);  // Stringified index values
    }

    @Test
    public void forEachKey()
    {
        UnifiedSet<UUID> result = UnifiedSet.newSet();
        ConcurrentUuidIntHashMap map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);
        map.forEachKey(CollectionAddProcedure.on(result));
        Verify.assertSetsEqual(UnifiedSet.newSetWith(uuid("1"), uuid("2"), uuid("3")), result);
    }

    @Test
    public void forEachValue()
    {
        UnifiedSet<Integer> result = UnifiedSet.newSet();
        ConcurrentUuidIntHashMap map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);
        map.forEachValue(CollectionAddProcedure.on(result));
        Verify.assertSetsEqual(UnifiedSet.newSetWith(1, 2, 3), result);
    }

    @Test
    public void forEachKeyValue()
    {
        UnifiedMap<UUID, Integer> result = UnifiedMap.newMap();
        ConcurrentUuidIntHashMap map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);
        map.forEachKeyValue(result::put);
        Assertions.assertEquals(UnifiedMap.newWithKeysValues(uuid("1"), 1, uuid("2"),2, uuid("3"), 3), result);

        MutableBag<String> result2 = Bags.mutable.of();
        ConcurrentUuidIntHashMap map2 = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);
        map2.forEachKeyValue((key, value) -> result2.add(key.toString() + value));
        Assertions.assertEquals(Bags.mutable.of(uuid("1").toString() + 1, uuid("2").toString() + 2, uuid("3").toString() + 3), result2);
    }

    @Test
    public void flipUniqueValues()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3 );
        MapIterable<Integer, UUID> result = map.flipUniqueValues();
        Assertions.assertEquals(UnifiedMap.newWithKeysValues(1, uuid("1"), 2, uuid("2"), 3, uuid("3")), result);

        Verify.assertThrows(
                IllegalStateException.class,
                () -> this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 1).flipUniqueValues());
    }

    @Test
    public void collectMap()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), textToNum("One"),
                uuid(2), textToNum("Two"), uuid(3), textToNum("Three"));
        MapIterable<Integer, String> actual =
                map.collect((Function2<UUID, Integer, Pair<Integer, String>>) (argument1, argument2) -> Tuples.pair(
                        toInt(argument1),
                        Integer.toString(toInt(argument1)) + ':' + new StringBuilder(numToText(argument2)).reverse()));
        Assertions.assertEquals(UnifiedMap.newWithKeysValues(1, "1:enO", 2, "2:owT", 3, "3:eerhT"), actual);
    }

    @Test
    public void collectBoolean()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), booleanStringToInt("true"), uuid("Two"), booleanStringToInt("nah"), uuid("Three"), booleanStringToInt("TrUe"));
        BooleanIterable actual = map.collectBoolean(MapIterableTestCase::intToBoolean);
        Assertions.assertEquals(BooleanHashBag.newBagWith(true, false, true), actual.toBag());
    }

    @Test
    public void collectBooleanWithTarget()
    {
        BooleanHashBag target = new BooleanHashBag();
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), booleanStringToInt("true"), uuid("Two"), booleanStringToInt("nah"), uuid("Three"), booleanStringToInt("TrUe"));
        BooleanHashBag result = map.collectBoolean(MapIterableTestCase::intToBoolean, target);
        Assertions.assertSame(target, result, "Target sent as parameter not returned");
        Assertions.assertEquals(BooleanHashBag.newBagWith(true, false, true), result.toBag());
    }

    @Test
    public void collectByte()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        ByteIterable actual = map.collectByte(Integer::byteValue);
        Assertions.assertEquals(ByteHashBag.newBagWith((byte) 1, (byte) 2, (byte) 3), actual.toBag());
    }

    @Test
    public void collectByteWithTarget()
    {
        ByteHashBag target = new ByteHashBag();
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        ByteHashBag result = map.collectByte(Integer::byteValue, target);
        Assertions.assertSame(target, result, "Target sent as parameter not returned");
        Assertions.assertEquals(ByteHashBag.newBagWith((byte) 1, (byte) 2, (byte) 3), result.toBag());
    }

    @Test
    public void collectChar()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), Integer.parseInt("A", 16), uuid("Two"), Integer.parseInt("B", 16), uuid("Three"), Integer.parseInt("C", 16));
        CharIterable actual = map.collectChar((CharFunction<Integer>) intVal -> Integer.toString(intVal, 16).toUpperCase().charAt(0));
        Assertions.assertEquals(CharHashBag.newBagWith('A', 'B', 'C'), actual.toBag());
    }

    @Test
    public void collectCharWithTarget()
    {
        CharHashBag target = new CharHashBag();
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), Integer.parseInt("A", 16), uuid("Two"), Integer.parseInt("B", 16), uuid("Three"), Integer.parseInt("C", 16));
        CharHashBag result = map.collectChar((CharFunction<Integer>) intVal -> Integer.toString(intVal, 16).toUpperCase().charAt(0), target);
        Assertions.assertSame(target, result, "Target sent as parameter not returned");
        Assertions.assertEquals(CharHashBag.newBagWith('A', 'B', 'C'), result.toBag());
    }

    @Test
    public void collectDouble()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        DoubleIterable actual = map.collectDouble(Integer::doubleValue);
        Assertions.assertEquals(DoubleHashBag.newBagWith(1.0d, 2.0d, 3.0d), actual.toBag());
    }

    @Test
    public void collectDoubleWithTarget()
    {
        DoubleHashBag target = new DoubleHashBag();
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        DoubleHashBag result = map.collectDouble(Integer::doubleValue, target);
        Assertions.assertSame(target, result, "Target sent as parameter not returned");
        Assertions.assertEquals(DoubleHashBag.newBagWith(1.0d, 2.0d, 3.0d), result.toBag());
    }

    @Test
    public void collectFloat()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        FloatIterable actual = map.collectFloat(Integer::floatValue);
        Assertions.assertEquals(FloatHashBag.newBagWith(1.0f, 2.0f, 3.0f), actual.toBag());
    }

    @Test
    public void collectFloatWithTarget()
    {
        FloatHashBag target = new FloatHashBag();
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        FloatHashBag result = map.collectFloat(Integer::floatValue, target);
        Assertions.assertSame(target, result, "Target sent as parameter not returned");
        Assertions.assertEquals(FloatHashBag.newBagWith(1.0f, 2.0f, 3.0f), result.toBag());
    }

    @Test
    public void collectInt()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        IntIterable actual = map.collectInt(Integer::intValue);
        Assertions.assertEquals(IntHashBag.newBagWith(1, 2, 3), actual.toBag());
    }

    @Test
    public void collectIntWithTarget()
    {
        IntHashBag target = new IntHashBag();
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        IntHashBag result = map.collectInt(Integer::intValue, target);
        Assertions.assertSame(target, result, "Target sent as parameter not returned");
        Assertions.assertEquals(IntHashBag.newBagWith(1, 2, 3), result.toBag());
    }

    @Test
    public void collectLong()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        LongIterable actual = map.collectLong(Integer::longValue);
        Assertions.assertEquals(LongHashBag.newBagWith(1L, 2L, 3L), actual.toBag());
    }

    @Test
    public void collectLongWithTarget()
    {
        LongHashBag target = new LongHashBag();
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        LongHashBag result = map.collectLong(Integer::longValue, target);
        Assertions.assertSame(target, result, "Target sent as parameter not returned");
        Assertions.assertEquals(LongHashBag.newBagWith(1L, 2L, 3L), result.toBag());
    }

    @Test
    public void collectShort()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        ShortIterable actual = map.collectShort(Integer::shortValue);
        Assertions.assertEquals(ShortHashBag.newBagWith((short) 1, (short) 2, (short) 3), actual.toBag());
    }

    @Test
    public void collectShortWithTarget()
    {
        ShortHashBag target = new ShortHashBag();
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        ShortHashBag result = map.collectShort(Integer::shortValue, target);
        Assertions.assertSame(target, result, "Target sent as parameter not returned");
        Assertions.assertEquals(ShortHashBag.newBagWith((short) 1, (short) 2, (short) 3), result.toBag());
    }

    @Test
    public void collectValues()
    {

        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), textToNum("One"),
                uuid(2), textToNum("Two"), uuid(3), textToNum("Three"));

        MapIterable<UUID, Integer> actual =
                map.collectValues((argument1, argument2) -> argument2);

        Assertions.assertEquals(UnifiedMap.newWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3), actual);
    }

    @Test
    public void select()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), textToNum("One"),
                uuid(2), textToNum("Two"), uuid(3), textToNum("Three"));
        RichIterable<Integer> actual = map.select((value) -> value == 2);
        Assertions.assertEquals(HashBag.newBagWith(2), actual.toBag());
    }

    @Test
    public void selectWith()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), textToNum("One"),
                uuid(2), textToNum("Two"), uuid(3), textToNum("Three"));
        RichIterable<Integer> actual = map.selectWith(Object::equals, 2);
        Assertions.assertEquals(HashBag.newBagWith(2), actual.toBag());
    }

    @Test
    public void reject()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), textToNum("One"),
                uuid(2), textToNum("Two"), uuid(3), textToNum("Three"));
        RichIterable<Integer> actual = map.reject((value) -> value == 2);
        Assertions.assertEquals(HashBag.newBagWith(textToNum("One"), textToNum("Three")), actual.toBag());
    }

    @Test
    public void rejectWith()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), textToNum("One"),
                uuid(2), textToNum("Two"), uuid(3), textToNum("Three"));
        RichIterable<Integer> actual = map.rejectWith(Object::equals, 2);
        Assertions.assertEquals(HashBag.newBagWith(textToNum("One"), textToNum("Three")), actual.toBag());
    }

    @Test
    public void collect()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), textToNum("One"),
                uuid(2), textToNum("Two"), uuid(3), textToNum("Three"));
        RichIterable<String> actual = map.collect(MapIterableTestCase::numToText);
        Assertions.assertEquals(HashBag.newBagWith("One", "Two", "Three"), actual.toBag());
    }

    @Test
    public void flatCollect()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;
        Function<Integer, MutableList<Integer>> function = Lists.mutable::with;

        Verify.assertListsEqual(
                Lists.mutable.with(1, 2, 3, 4),
                map.flatCollect(function).toSortedList());

        Verify.assertSetsEqual(
                UnifiedSet.newSetWith(1, 2, 3, 4),
                map.flatCollect(function, UnifiedSet.newSet()));
    }

    @Test
    public void flatCollectWith()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("4"),4, uuid("5"), 5, uuid("6"), 6, uuid("7"), 7) ;

        Verify.assertSetsEqual(
                Sets.mutable.with(1, 2, 3, 4, 5, 6, 7),
                map.flatCollectWith(Interval::fromTo, 1).toSet());

        Verify.assertBagsEqual(
                Bags.mutable.with(4, 3, 2, 1, 5, 4, 3, 2, 1, 6, 5, 4, 3, 2, 1, 7, 6, 5, 4, 3, 2, 1),
                map.flatCollectWith(Interval::fromTo, 1, Bags.mutable.empty()));
    }

    @Test
    public void selectMap()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), textToNum("One"),
                uuid(2), textToNum("Two"), uuid(3), textToNum("Three"));

        MapIterable<UUID, Integer> actual =
                map.select((argument1, argument2) -> toInt(argument1) == 1 || argument2 == 2);
        Assertions.assertEquals(2, actual.size());
        Assertions.assertTrue(actual.keysView().containsAllArguments(uuid(1), uuid(2)));
        Assertions.assertTrue(actual.valuesView().containsAllArguments(textToNum("One"), textToNum("Two")));
    }

    @Test
    public void rejectMap()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), textToNum("One"),
                uuid(2), textToNum("Two"), uuid(3), textToNum("Three"));
        MapIterable<UUID, Integer> actual =
                map.reject((argument1, argument2) -> toInt(argument1) == 1 || argument2 == 2);
        Assertions.assertEquals(UnifiedMap.newWithKeysValues(uuid(3), textToNum("Three")), actual);
    }

    @Test
    public void detect()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), textToNum("One"),
                uuid(2), textToNum("Two"), uuid(3), textToNum("Three"));
        Pair<UUID, Integer> one = map.detect((argument1, argument2) -> toInt(argument1) == 1);
        Assertions.assertNotNull(one);
        Assertions.assertEquals(Integer.valueOf(1), toInt(one.getOne()));
        Assertions.assertEquals("One", numToText(one.getTwo()));

        Pair<UUID, Integer> two = map.detect((argument1, argument2) -> "Two".equals(numToText(argument2)));
        Assertions.assertNotNull(two);
        Assertions.assertEquals(Integer.valueOf(2),  toInt(two.getOne()));
        Assertions.assertEquals("Two", numToText(two.getTwo()));

        Assertions.assertNull(map.detect((ignored1, ignored2) -> false));
    }

    @Test
    public void detectOptional()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), textToNum("One"),
                uuid(2), textToNum("Two"), uuid(3), textToNum("Three"));
        Pair<UUID, Integer> one =
                map.detectOptional((argument1, argument2) -> toInt(argument1) == 1).get();
        Assertions.assertNotNull(one);
        Assertions.assertEquals(Integer.valueOf(1), toInt(one.getOne()));
        Assertions.assertEquals("One", numToText(one.getTwo()));

        Pair<UUID, Integer> two = map.detectOptional((argument1, argument2) -> "Two".equals(numToText(argument2))).get();
        Assertions.assertNotNull(two);
        Assertions.assertEquals(Integer.valueOf(2),  toInt(two.getOne()));
        Assertions.assertEquals("Two", numToText(two.getTwo()));

        Assertions.assertFalse(map.detectOptional((ignored1, ignored2) -> false).isPresent());
    }

    @Test
    public void anySatisfy()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), textToNum("One"),
                uuid(2), textToNum("Two"), uuid(3), textToNum("Three"));

        Verify.assertAnySatisfy((Map<UUID, Integer>) map, Integer.class::isInstance);
        Assertions.assertFalse(map.anySatisfy("Monkey"::equals));
    }

    @Test
    public void anySatisfyWith()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), textToNum("One"),
                uuid(2), textToNum("Two"), uuid(3), textToNum("Three"));

        Assertions.assertTrue(map.anySatisfyWith(Predicates2.instanceOf(), Integer.class));
        Assertions.assertFalse(map.anySatisfyWith(Object::equals, "Monkey"));
    }

    @Test
    public void allSatisfy()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), textToNum("One"),
                uuid(2), textToNum("Two"), uuid(3), textToNum("Three"));

        Verify.assertAllSatisfy((Map<UUID, Integer>) map, Integer.class::isInstance);
        Assertions.assertFalse(map.allSatisfy("Monkey"::equals));
    }

    @Test
    public void allSatisfyWith()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), textToNum("One"),
                uuid(2), textToNum("Two"), uuid(3), textToNum("Three"));

        Assertions.assertTrue(map.allSatisfyWith(Predicates2.instanceOf(), Integer.class));
        Assertions.assertFalse(map.allSatisfyWith(Object::equals, "Monkey"));
    }

    @Test
    public void noneSatisfy()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), textToNum("One"),
                uuid(2), textToNum("Two"), uuid(3), textToNum("Three"));

        Verify.assertNoneSatisfy((Map<UUID, Integer>) map, String.class::isInstance);
        Assertions.assertTrue(map.noneSatisfy("Monkey"::equals));
        Assertions.assertFalse(map.noneSatisfy(each -> each == 2));
    }

    @Test
    public void noneSatisfyWith()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), textToNum("One"),
                uuid(2), textToNum("Two"), uuid(3), textToNum("Three"));

        Assertions.assertTrue(map.noneSatisfyWith(Predicates2.instanceOf(), String.class));
        Assertions.assertTrue(map.noneSatisfyWith(Object::equals, "Monkey"));
        Assertions.assertFalse(map.noneSatisfyWith(Object::equals, Integer.valueOf(2)));
    }

    @Test
    public void appendString()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), textToNum("One"),
                uuid(2), textToNum("Two"), uuid(3), textToNum("Three"));

        StringBuilder builder1 = new StringBuilder();
        map.appendString(builder1);
        String defaultString = builder1.toString();
        Assertions.assertEquals(7, defaultString.length());

        StringBuilder builder2 = new StringBuilder();
        map.appendString(builder2, "|");
        String delimitedString = builder2.toString();
        Assertions.assertEquals(5, delimitedString.length());
        Verify.assertContains("|", delimitedString);

        StringBuilder builder3 = new StringBuilder();
        map.appendString(builder3, "{", "|", "}");
        String wrappedString = builder3.toString();
        Assertions.assertEquals(7, wrappedString.length());
        Verify.assertContains("|", wrappedString);
        Assertions.assertTrue(wrappedString.startsWith("{"));
        Assertions.assertTrue(wrappedString.endsWith("}"));
    }

    @Test
    public void toBag()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), textToNum("One"),
                uuid(2), textToNum("Two"), uuid(3), textToNum("Three"));

        MutableBag<Integer> bag = map.toBag();
        Assertions.assertEquals(Bags.mutable.of(1, 2, 3), bag);
    }

    @Test
    public void toSortedBag()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        MutableSortedBag<Integer> sorted = map.toSortedBag();
        Verify.assertSortedBagsEqual(TreeBag.newBagWith(1, 2, 3, 4), sorted);

        MutableSortedBag<Integer> reverse = map.toSortedBag(Collections.reverseOrder());
        Verify.assertSortedBagsEqual(TreeBag.newBagWith(Comparators.reverseNaturalOrder(), 1, 2, 3, 4), reverse);
    }

    @Test
    public void toSortedBagBy()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        MutableSortedBag<Integer> sorted = map.toSortedBagBy(String::valueOf);
        Verify.assertSortedBagsEqual(TreeBag.newBagWith(1, 2, 3, 4), sorted);
    }

    @Test
    public void asLazy()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), textToNum("One"),
                uuid(2), textToNum("Two"), uuid(3), textToNum("Three"));

        LazyIterable<Integer> lazy = map.asLazy();
        Verify.assertContainsAll(lazy.toList(), 1, 2, 3);
    }

    @Test
    public void toList()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), textToNum("One"),
                uuid(2), textToNum("Two"), uuid(3), textToNum("Three"));
        MutableList<Integer> list = map.toList();
        Verify.assertContainsAll(list, 1, 2, 3);
    }

    @Test
    public void toSet()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), textToNum("One"),
                uuid(2), textToNum("Two"), uuid(3), textToNum("Three"));

        MutableSet<Integer> set = map.toSet();
        Verify.assertContainsAll(set, 1, 2, 3);
    }

    @Test
    public void toSortedList()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        MutableList<Integer> sorted = map.toSortedList();
        Assertions.assertEquals(FastList.newListWith(1, 2, 3, 4), sorted);

        MutableList<Integer> reverse = map.toSortedList(Collections.reverseOrder());
        Assertions.assertEquals(FastList.newListWith(4, 3, 2, 1), reverse);
    }

    @Test
    public void toSortedListBy()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        MutableList<Integer> list = map.toSortedListBy(Integer::valueOf);
        Assertions.assertEquals(FastList.newListWith(1, 2, 3, 4), list);
    }

    @Test
    public void toSortedSet()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        MutableSortedSet<Integer> sorted = map.toSortedSet();
        Verify.assertSortedSetsEqual(TreeSortedSet.newSetWith(1, 2, 3, 4), sorted);

        MutableSortedSet<Integer> reverse = map.toSortedSet(Collections.reverseOrder());
        Verify.assertSortedSetsEqual(TreeSortedSet.newSetWith(Comparators.reverseNaturalOrder(), 1, 2, 3, 4), reverse);
    }

    @Test
    public void toSortedSetBy()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        MutableSortedSet<Integer> sorted = map.toSortedSetBy(String::valueOf);
        Verify.assertSortedSetsEqual(TreeSortedSet.newSetWith(1, 2, 3, 4), sorted);
    }

    @Test
    public void collect_value()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        Verify.assertContainsAll(
                map.collect(Functions.getToString()).toSet(),
                "1", "2", "3", "4");
        Verify.assertContainsAll(
                map.collect(
                        String::valueOf,
                        UnifiedSet.newSet()), "1", "2", "3", "4");
    }

    @Test
    public void collectIf()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        Bag<String> odd = map.collectIf(IntegerPredicates.isOdd(), Functions.getToString()).toBag();
        Assertions.assertEquals(Bags.mutable.of("1", "3"), odd);

        Bag<String> even = map.collectIf(IntegerPredicates.isEven(), String::valueOf, HashBag.newBag());
        Assertions.assertEquals(Bags.mutable.of("2", "4"), even);
    }

    @Test
    public void collectWith()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        RichIterable<Integer> actual = map.collectWith(AddFunction.INTEGER, 1);
        Verify.assertContainsAll(actual, 2, 3, 4, 5);
    }

    @Test
    public void collectWithToTarget()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        FastList<Integer> actual = map.collectWith(AddFunction.INTEGER, 1, FastList.newList());
        Verify.assertContainsAll(actual, 2, 3, 4, 5);
    }

    @Test
    public void contains()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);
        Assertions.assertTrue(map.contains(2));
    }

    @Test
    public void containsAll()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);

        Assertions.assertTrue(map.containsAll(FastList.newListWith(1, 2)));
        Assertions.assertTrue(map.containsAll(FastList.newListWith(1, 2, 3)));
        Assertions.assertFalse(map.containsAll(FastList.newListWith(1, 2, 3, 4)));
    }

    @Test
    public void containsKey()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);
        Assertions.assertTrue(map.containsKey(uuid("1")));
        Assertions.assertFalse(map.containsKey(uuid("4")));
    }

    @Test
    public void containsValue()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);
        Assertions.assertTrue(map.containsValue(1));
        Assertions.assertFalse(map.containsValue(4));

        MapIterable<UUID, Integer> map2 = this.newMapWithKeysValues(uuid("3"), 1, uuid("2"), 2, uuid("1"), 3);
        Assertions.assertTrue(map2.containsValue(1));
        Assertions.assertFalse(map2.containsValue(4));
    }

    @Test
    public void getFirst()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);

        Integer value = map.getFirst();
        Assertions.assertNotNull(value);
        Assertions.assertTrue(map.valuesView().contains(value));

        Assertions.assertNull(this.newUuidIntMap().getFirst());
    }

    @Test
    public void getLast()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);

        Integer value = map.getLast();
        Assertions.assertNotNull(value);
        Assertions.assertTrue(map.valuesView().contains(value));

        Assertions.assertNull(this.newUuidIntMap().getLast());
    }

    @Test
    public void getOnly()
    {
        ConcurrentUuidIntHashMap map = this.newMapWithKeyValue(uuid("One"), 1);
        Assertions.assertEquals((Integer) 1, map.getOnly());
    }

    @Test
    public void getOnly_throws_when_empty()
    {
        Assertions.assertThrows(IllegalStateException.class, () -> this.newUuidIntMap().getOnly());
    }

    @Test
    public void getOnly_throws_when_multiple_values()
    {
        Assertions.assertThrows(IllegalStateException.class, () -> this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2).getOnly());
    }

    @Test
    public void containsAllIterable()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);

        Assertions.assertTrue(map.containsAllIterable(FastList.newListWith(1, 2)));
        Assertions.assertFalse(map.containsAllIterable(FastList.newListWith(1, 4)));
    }

    @Test
    public void containsAllArguments()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);

        Assertions.assertTrue(map.containsAllArguments(1, 2));
        Assertions.assertFalse(map.containsAllArguments(1, 4));
    }

    @Test
    public void count()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);

        int actual = map.count(Predicates.or((value) -> value == 1, (value) -> value == 3));

        Assertions.assertEquals(2, actual);
    }

    @Test
    public void countWith()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);

        int actual = map.countWith(Object::equals, Integer.valueOf(1));

        Assertions.assertEquals(1, actual);
    }

    @Test
    public void detect_value()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);

        Integer resultFound = map.detect((value) -> value == 1);
        Assertions.assertEquals(Integer.valueOf(1), resultFound);

        Integer resultNotFound = map.detect((value) -> value == 4);
        Assertions.assertNull(resultNotFound);
    }

    @Test
    public void detectOptional_value()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);

        Integer resultFound = map.detectOptional((value) -> value == 1).get();
        Assertions.assertEquals(Integer.valueOf(1), resultFound);

        Assertions.assertFalse(map.detectOptional((value) -> value == 4).isPresent());
    }

    @Test
    public void detectWith()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);

        Integer resultFound = map.detectWith(Object::equals, Integer.valueOf(1));
        Assertions.assertEquals(Integer.valueOf(1), resultFound);

        Integer resultNotFound = map.detectWith(Object::equals, Integer.valueOf(4));
        Assertions.assertNull(resultNotFound);
    }

    @Test
    public void detectWithOptional()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);

        Integer resultFound = map.detectWithOptional(Object::equals, Integer.valueOf(1)).get();
        Assertions.assertEquals(Integer.valueOf(1), resultFound);

        Assertions.assertFalse(map.detectWithOptional(Object::equals, Integer.valueOf(4)).isPresent());
    }

    @Test
    public void detectIfNone_value()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);

        Integer resultNotFound = map.detectIfNone((value) -> value == 4, () -> 0);
        Assertions.assertEquals(Integer.valueOf(0), resultNotFound);

        Integer resultFound = map.detectIfNone((value) -> value == 1, () -> 0);
        Assertions.assertEquals(Integer.valueOf(1), resultFound);
    }

    @Test
    public void detectWithIfNone()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("3"), 3);

        Integer resultNotFound = map.detectWithIfNone(Object::equals, 4, () -> 0);
        Assertions.assertEquals(Integer.valueOf(0), resultNotFound);

        Integer resultFound = map.detectWithIfNone(Object::equals, 1, () -> 0);
        Assertions.assertEquals(Integer.valueOf(1), resultFound);
    }

    @Test
    public void flatten_value()
    {
        ConcurrentUuidIntHashMap map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2);

        Function<Integer, Iterable<Character>> function = object -> {
            MutableList<Character> result = Lists.mutable.of();
            char[] chars;
            switch (object) {
                case 1:
                    chars = "one".toCharArray();
                    break;
                case 2:
                    chars ="two".toCharArray();
                    break;
                default:
                    chars ="can't handle".toCharArray();
            };

            for (char aChar : chars)
            {
                result.add(Character.valueOf(aChar));
            }
            return result;
        };

        RichIterable<Character> blob = map.flatCollect(function);
        Assertions.assertTrue(blob.containsAllArguments(
                Character.valueOf('o'),
                Character.valueOf('n'),
                Character.valueOf('e'),
                Character.valueOf('t'),
                Character.valueOf('w'),
                Character.valueOf('o')));

        RichIterable<Character> blobFromTarget = map.flatCollect(function, FastList.newList());
        Assertions.assertTrue(blobFromTarget.containsAllArguments(
                Character.valueOf('o'),
                Character.valueOf('n'),
                Character.valueOf('e'),
                Character.valueOf('t'),
                Character.valueOf('w'),
                Character.valueOf('o')));
    }

    /**
     * @since 9.0
     */
    @Test
    public void countBy()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;
        Bag<Integer> evensAndOdds = map.countBy(each -> Integer.valueOf(each % 2));
        Assertions.assertEquals(2, evensAndOdds.occurrencesOf(1));
        Assertions.assertEquals(2, evensAndOdds.occurrencesOf(0));
        Bag<Integer> evensAndOdds2 = map.countBy(each -> Integer.valueOf(each % 2), Bags.mutable.empty());
        Assertions.assertEquals(2, evensAndOdds2.occurrencesOf(1));
        Assertions.assertEquals(2, evensAndOdds2.occurrencesOf(0));
    }

    /**
     * @since 9.0
     */
    @Test
    public void countByWith()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;
        Bag<Integer> evensAndOdds = map.countByWith((each, parm) -> Integer.valueOf(each % parm), 2);
        Assertions.assertEquals(2, evensAndOdds.occurrencesOf(1));
        Assertions.assertEquals(2, evensAndOdds.occurrencesOf(0));
        Bag<Integer> evensAndOdds2 =
                map.countByWith((each, parm) -> Integer.valueOf(each % parm), 2, Bags.mutable.empty());
        Assertions.assertEquals(2, evensAndOdds2.occurrencesOf(1));
        Assertions.assertEquals(2, evensAndOdds2.occurrencesOf(0));
    }

    /**
     * @since 10.0.0
     */
    @Test
    public void countByEach()
    {
        RichIterable<Integer> integerList = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2, uuid("4"), 4);
        Bag<Integer> integerBag1 = integerList.countByEach(each -> IntInterval.oneTo(5).collect(i -> each * i));
        Assertions.assertEquals(1, integerBag1.occurrencesOf(1));
        Assertions.assertEquals(2, integerBag1.occurrencesOf(2));
        Assertions.assertEquals(3, integerBag1.occurrencesOf(4));
        Assertions.assertEquals(2, integerBag1.occurrencesOf(8));
        Assertions.assertEquals(1, integerBag1.occurrencesOf(12));
        Bag<Integer> integerBag2 =
                integerList.countByEach(each -> IntInterval.oneTo(5).collect(i -> each * i), Bags.mutable.empty());
        Assertions.assertEquals(1, integerBag2.occurrencesOf(1));
        Assertions.assertEquals(2, integerBag2.occurrencesOf(2));
        Assertions.assertEquals(3, integerBag2.occurrencesOf(4));
        Assertions.assertEquals(2, integerBag2.occurrencesOf(8));
        Assertions.assertEquals(1, integerBag2.occurrencesOf(12));
    }

    @Test
    public void groupBy()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        Function<Integer, Boolean> isOddFunction = object -> IntegerPredicates.isOdd().accept(object);

        Multimap<Boolean, Integer> expected = FastListMultimap.newMultimap(
                Tuples.pair(Boolean.TRUE, 1), Tuples.pair(Boolean.TRUE, 3),
                Tuples.pair(Boolean.FALSE, 2), Tuples.pair(Boolean.FALSE, 4));

        Multimap<Boolean, Integer> actual = map.groupBy(isOddFunction);
        expected.forEachKey(each -> {
            Assertions.assertTrue(actual.containsKey(each));
            MutableList<Integer> values = actual.get(each).toList();
            Verify.assertNotEmpty(values);
            Assertions.assertTrue(expected.get(each).containsAllIterable(values));
        });

        Multimap<Boolean, Integer> actualFromTarget = map.groupBy(isOddFunction, FastListMultimap.newMultimap());
        expected.forEachKey(each -> {
            Assertions.assertTrue(actualFromTarget.containsKey(each));
            MutableList<Integer> values = actualFromTarget.get(each).toList();
            Verify.assertNotEmpty(values);
            Assertions.assertTrue(expected.get(each).containsAllIterable(values));
        });
    }

    @Test
    public void groupByEach()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        MutableMultimap<Integer, Integer> expected = FastListMultimap.newMultimap();
        for (int i = 1; i < 4; i++)
        {
            expected.putAll(-i, Interval.fromTo(i, 4));
        }

        NegativeIntervalFunction function = new NegativeIntervalFunction();
        Multimap<Integer, Integer> actual = map.groupByEach(function);
        expected.forEachKey(each -> {
            Assertions.assertTrue(actual.containsKey(each));
            MutableList<Integer> values = actual.get(each).toList();
            Verify.assertNotEmpty(values);
            Assertions.assertTrue(expected.get(each).containsAllIterable(values));
        });

        Multimap<Integer, Integer> actualFromTarget = map.groupByEach(function, FastListMultimap.newMultimap());
        expected.forEachKey(each -> {
            Assertions.assertTrue(actualFromTarget.containsKey(each));
            MutableList<Integer> values = actualFromTarget.get(each).toList();
            Verify.assertNotEmpty(values);
            Assertions.assertTrue(expected.get(each).containsAllIterable(values));
        });
    }

    @Test
    public void groupByUniqueKey()
    {
        MapIterable<UUID,Integer> map = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3);
        Assertions.assertEquals(UnifiedMap.newWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3), map.groupByUniqueKey(id -> uuid(id)));
    }

    @Test
    public void groupByUniqueKey_throws()
    {
        Assertions.assertThrows(IllegalStateException.class, () -> this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3).groupByUniqueKey(Functions.getFixedValue(uuid(1))));
    }

    @Test
    public void groupByUniqueKey_target()
    {
        MapIterable<UUID,Integer> map = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3);
        MutableMap<UUID,Integer> integers = map.groupByUniqueKey(id -> uuid(id), UnifiedMap.newWithKeysValues(uuid(0), 0));
        Assertions.assertEquals(UnifiedMap.newWithKeysValues(uuid(0), 0, uuid(1), 1, uuid(2), 2, uuid(3), 3), integers);
    }

    @Test
    public void groupByUniqueKey_target_throws()
    {
        Assertions.assertThrows(IllegalStateException.class, () -> this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3).groupByUniqueKey(id -> uuid(id), UnifiedMap.newWithKeysValues(uuid(2), 2)));
    }

    @Test
    public void injectInto()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        Integer actual = map.injectInto(0, AddFunction.INTEGER);
        Assertions.assertEquals(Integer.valueOf(10), actual);

        Sum sum = map.injectInto(new IntegerSum(0), SumProcedure.number());
        Assertions.assertEquals(new IntegerSum(10), sum);
    }

    @Test
    public void injectIntoInt()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        int actual = map.injectInto(0, AddFunction.INTEGER_TO_INT);
        Assertions.assertEquals(10, actual);
    }

    @Test
    public void injectIntoLong()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        long actual = map.injectInto(0, AddFunction.INTEGER_TO_LONG);
        Assertions.assertEquals(10, actual);
    }

    @Test
    public void injectIntoFloat()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        float actual = map.injectInto(0, AddFunction.INTEGER_TO_FLOAT);
        Assertions.assertEquals(10.0F, actual, 0.01);
    }

    @Test
    public void injectIntoDouble()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        double actual = map.injectInto(0, AddFunction.INTEGER_TO_DOUBLE);
        Assertions.assertEquals(10.0d, actual, 0.01);
    }

    @Test
    public void sumOfInt()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        long actual = map.sumOfInt(integer -> integer);
        Assertions.assertEquals(10L, actual);
    }

    @Test
    public void sumOfLong()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        long actual = map.sumOfLong(Integer::longValue);
        Assertions.assertEquals(10, actual);
    }

    @Test
    public void testAggregateBy()
    {
        String oneToFive = "oneToFive";
        String sixToNine = "sixToNine";
        String tenToFifteen = "tenToFifteen";
        String sixteenToTwenty = "sixteenToTwenty";

        MapIterable<String, Interval> map = Maps.mutable.with(oneToFive, Interval.fromTo(1, 5),
                sixToNine, Interval.fromTo(6, 9), tenToFifteen, Interval.fromTo(10, 15),
                sixteenToTwenty, Interval.fromTo(16, 20));

        String lessThanTen = "lessThanTen";
        String greaterOrEqualsToTen = "greaterOrEqualsToTen";

        MapIterable<String, Long> result = map.aggregateBy(
                eachKey -> {
                    return eachKey.equals(oneToFive) || eachKey.equals(sixToNine) ? lessThanTen : greaterOrEqualsToTen;
                },
                each -> each.sumOfInt(Integer::intValue),
                () -> 0L,
                (argument1, argument2) -> argument1 + argument2);

        MapIterable<String, Long> expected =
                Maps.mutable.with(lessThanTen, Interval.fromTo(1, 9).sumOfInt(Integer::intValue),
                        greaterOrEqualsToTen, Interval.fromTo(10, 20).sumOfInt(Integer::intValue));
        Assertions.assertEquals(expected, result);
    }

    @Test
    public void sumOfFloat()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        double actual = map.sumOfFloat(Integer::floatValue);
        Assertions.assertEquals(10.0d, actual, 0.01);
    }

    @Test
    public void sumOfDouble()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        double actual = map.sumOfDouble(Integer::doubleValue);
        Assertions.assertEquals(10.0d, actual, 0.01);
    }

    @Test
    public void sumByInt()
    {
        RichIterable<Integer> values = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3);
        ObjectLongMap<Integer> result = values.sumByInt(s -> s % 2, Integer::valueOf);
        Assertions.assertEquals(4, result.get(1));
        Assertions.assertEquals(2, result.get(0));
    }

    @Test
    public void sumByFloat()
    {
        RichIterable<Integer> values = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3);
        ObjectDoubleMap<Integer> result = values.sumByFloat(s -> s % 2, Integer::valueOf);
        Assertions.assertEquals(4.0f, result.get(1));
        Assertions.assertEquals(2.0f, result.get(0));
    }

    @Test
    public void sumByLong()
    {
        RichIterable<Integer> values = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3);
        ObjectLongMap<Integer> result = values.sumByLong(s -> s % 2, Long::valueOf);
        Assertions.assertEquals(4, result.get(1));
        Assertions.assertEquals(2, result.get(0));
    }

    @Test
    public void sumByDouble()
    {
        RichIterable<Integer> values = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3);
        ObjectDoubleMap<Integer> result = values.sumByDouble(s -> s % 2, Double::valueOf);
        Assertions.assertEquals(4.0d, result.get(1));
        Assertions.assertEquals(2.0d, result.get(0));
    }

    @Test
    public void makeString()
    {
        MapIterable<UUID,Integer> map = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3);

        String defaultString = map.makeString();
        Assertions.assertEquals(7, defaultString.length());

        String delimitedString = map.makeString("|");
        Assertions.assertEquals(5, delimitedString.length());
        Verify.assertContains("|", delimitedString);

        String wrappedString = map.makeString("{", "|", "}");
        Assertions.assertEquals(7, wrappedString.length());
        Verify.assertContains("|", wrappedString);
        Assertions.assertTrue(wrappedString.startsWith("{"));
        Assertions.assertTrue(wrappedString.endsWith("}"));
    }

    @Test
    public void min()
    {
        MapIterable<UUID,Integer> map = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3);

        Assertions.assertEquals(Integer.valueOf(1), map.min());
        Assertions.assertEquals(Integer.valueOf(1), map.min(Integer::compareTo));
    }

    @Test
    public void max()
    {
        MapIterable<UUID,Integer> map = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(4), 4);

        Assertions.assertEquals(Integer.valueOf(4), map.max());
        Assertions.assertEquals(Integer.valueOf(4), map.max(Integer::compareTo));
    }

    @Test
    public void minBy()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        Assertions.assertEquals(Integer.valueOf(1), map.minBy(String::valueOf));
    }

    @Test
    public void maxBy()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        Assertions.assertEquals(Integer.valueOf(4), map.maxBy(String::valueOf));
    }

    @Test
    public void reject_value()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        Verify.assertContainsAll(map.reject(Predicates.lessThan(3)).toSet(), 3, 4);
        Verify.assertContainsAll(map.reject(Predicates.lessThan(3), UnifiedSet.newSet()), 3, 4);
    }

    @Test
    public void rejectWith_value()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        Verify.assertContainsAll(map.rejectWith(Predicates2.lessThan(), 3, UnifiedSet.newSet()), 3, 4);
    }

    @Test
    public void select_value()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        Verify.assertContainsAll(map.select(Predicates.lessThan(3)).toSet(), 1, 2);
        Verify.assertContainsAll(map.select(Predicates.lessThan(3), UnifiedSet.newSet()), 1, 2);
    }

    @Test
    public void selectWith_value()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        Verify.assertContainsAll(map.selectWith(Predicates2.lessThan(), 3, UnifiedSet.newSet()), 1, 2);
    }

    @Test
    public void partition_value()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;
        PartitionIterable<Integer> partition = map.partition(IntegerPredicates.isEven());
        Assertions.assertEquals(iSet(4, 2), partition.getSelected().toSet());
        Assertions.assertEquals(iSet(3, 1), partition.getRejected().toSet());
    }

    @Test
    public void partitionWith_value()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;
        PartitionIterable<Integer> partition =
                map.partitionWith(Predicates2.in(), map.select(IntegerPredicates.isEven()));
        Assertions.assertEquals(iSet(4, 2), partition.getSelected().toSet());
        Assertions.assertEquals(iSet(3, 1), partition.getRejected().toSet());
    }

    @Test
    public void toArray()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;

        Object[] array = map.toArray();
        Verify.assertSize(4, array);
        Integer[] array2 = map.toArray(new Integer[0]);
        Verify.assertSize(4, array2);
        Integer[] array3 = map.toArray(new Integer[4]);
        Verify.assertSize(4, array3);
        Integer[] array4 = map.toArray(new Integer[5]);
        Verify.assertSize(5, array4);
    }

    @Test
    public void zip()
    {
        MapIterable<UUID,Integer> map = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3);

        List<Object> nulls = Collections.nCopies(map.size(), null);
        List<Object> nullsPlusOne = Collections.nCopies(map.size() + 1, null);
        List<Object> nullsMinusOne = Collections.nCopies(map.size() - 1, null);

        RichIterable<Pair<Integer, Object>> pairs = map.zip(nulls);
        Assertions.assertEquals(
                map.toSet(),
                pairs.collect((Function<Pair<Integer, ?>, Integer>) Pair::getOne).toSet());
        Assertions.assertEquals(
                nulls,
                pairs.collect((Function<Pair<?, Object>, Object>) Pair::getTwo, Lists.mutable.of()));

        RichIterable<Pair<Integer, Object>> pairsPlusOne = map.zip(nullsPlusOne);
        Assertions.assertEquals(
                map.toSet(),
                pairsPlusOne.collect((Function<Pair<Integer, ?>, Integer>) Pair::getOne).toSet());
        Assertions.assertEquals(
                nulls,
                pairsPlusOne.collect((Function<Pair<?, Object>, Object>) Pair::getTwo, Lists.mutable.of()));

        RichIterable<Pair<Integer, Object>> pairsMinusOne = map.zip(nullsMinusOne);
        Assertions.assertEquals(map.size() - 1, pairsMinusOne.size());
        Assertions.assertTrue(map
                .valuesView()
                .containsAllIterable(pairsMinusOne.collect((Function<Pair<Integer, ?>, Integer>) Pair::getOne).toSet()));

        Assertions.assertEquals(
                map.zip(nulls).toSet(),
                map.zip(nulls, UnifiedSet.newSet()));
    }

    @Test
    public void zipWithIndex()
    {
        MapIterable<UUID,Integer> map = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3);

        RichIterable<Pair<Integer, Integer>> pairs = map.zipWithIndex();

        Assertions.assertEquals(
                map.toSet(),
                pairs.collect(Pair::getOne).toSet());
        Assertions.assertEquals(
                Interval.zeroTo(map.size() - 1).toSet(),
                pairs.collect(Pair::getTwo, UnifiedSet.newSet()));

        Assertions.assertEquals(
                map.zipWithIndex().toSet(),
                map.zipWithIndex(UnifiedSet.newSet()));
    }

    @Test
    public void aggregateByMutating()
    {
        Function0<AtomicInteger> valueCreator = AtomicInteger::new;
        RichIterable<Integer> collection = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3);
        MapIterable<UUID, AtomicInteger> aggregation =
                collection.aggregateInPlaceBy(MapIterableTestCase::uuid, valueCreator, AtomicInteger::addAndGet);
        Assertions.assertEquals(1, aggregation.get(uuid(1)).intValue());
        Assertions.assertEquals(2, aggregation.get(uuid(2)).intValue());
        Assertions.assertEquals(3, aggregation.get(uuid(3)).intValue());
    }

    @Test
    public void aggregateByNonMutating()
    {
        Function0<Integer> valueCreator = () -> 0;
        Function2<Integer, Integer, Integer> sumAggregator = (integer1, integer2) -> integer1 + integer2;
        RichIterable<Integer> collection = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3);
        MapIterable<UUID, Integer> aggregation = collection.aggregateBy(MapIterableTestCase::uuid, valueCreator, sumAggregator);
        Assertions.assertEquals(1, aggregation.get(uuid(1)).intValue());
        Assertions.assertEquals(2, aggregation.get(uuid(2)).intValue());
        Assertions.assertEquals(3, aggregation.get(uuid(3)).intValue());
    }

    @Test
    public void keyValuesView()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid("1"),1, uuid("2"), 2, uuid("3"), 3, uuid("4"), 4) ;
        MutableSet<Pair<UUID, Integer>> keyValues = map.keyValuesView().toSet();
        Assertions.assertEquals(UnifiedSet.newSetWith(
                Tuples.pair(uuid("1"), 1),
                Tuples.pair(uuid("2"), 2),
                Tuples.pair(uuid("3"), 3),
                Tuples.pair(uuid("4"), 4)), keyValues);
    }

    @Test
    public void testNewMap()
    {
        ConcurrentUuidIntHashMap map = this.newUuidIntMap();
        Verify.assertEmpty(map);
        Verify.assertSize(0, map);
    }

    @Test
    public void testNewMapWithKeyValue()
    {
        ConcurrentUuidIntHashMap map = this.newMapWithKeyValue(uuid("One"), 1);
        Verify.assertNotEmpty(map);
        Verify.assertSize(1, map);
        Verify.assertContainsKeyValue(uuid("One"), 1, map);
    }

    @Test
    public void newMapWithWith()
    {
        ConcurrentUuidIntHashMap map = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2);
        Verify.assertNotEmpty(map);
        Verify.assertSize(2, map);
        Verify.assertContainsAllKeyValues(map, uuid("1"), 1, uuid("2"), 2);
    }

    @Test
    public void newMapWithWithWith()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3);
        Verify.assertNotEmpty(map);
        Verify.assertSize(3, map);
        Verify.assertContainsAllKeyValues(map, uuid(1), 1, uuid(2), 2, uuid(3), 3);
    }

    @Test
    public void newMapWithWithWithWith()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3, uuid(4), 4);
        Verify.assertNotEmpty(map);
        Verify.assertSize(4, map);
        Verify.assertContainsAllKeyValues(map, uuid(1), 1, uuid(2), 2, uuid(3), 3, uuid(4), 4);
    }

    @Test
    public void iterator()
    {
        MutableMap<UUID, Integer> map = this.newMapWithKeysValues(uuid("One"), 1, uuid("Two"), 2, uuid("Three"), 3);
        Iterator<Integer> iterator = map.iterator();
        Assertions.assertTrue(iterator.hasNext());
        int sum = 0;
        while (iterator.hasNext())
        {
            sum += iterator.next();
        }
        Assertions.assertFalse(iterator.hasNext());
        Assertions.assertEquals(6, sum);
    }

    @Test
    public void keysView()
    {
        MutableList<UUID> keys = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2).keysView().toSortedList();
        Assertions.assertEquals(FastList.newListWith(uuid("1"), uuid("2")), keys);
    }

    @Test
    public void valuesView()
    {
        MutableList<Integer> values = this.newMapWithKeysValues(uuid("1"), 1, uuid("2"), 2).valuesView().toSortedList();
        Assertions.assertEquals(FastList.newListWith(1, 2), values);
    }

    @Test
    public void test_toString()
    {
        MapIterable<UUID, Integer> map = this.newMapWithKeysValues(uuid(1), 1, uuid(2), 2, uuid(3), 3);

        String stringToSearch = map.toString();
        Verify.assertContains(uuid(1).toString() + "=" + 1, stringToSearch);
        Verify.assertContains(uuid(2).toString() + "=" + 2, stringToSearch);
        Verify.assertContains(uuid(3).toString() + "=" + 3, stringToSearch);
    }
}
