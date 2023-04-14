package dev.ikm.tinkar.entity;

import org.eclipse.collections.api.*;
import org.eclipse.collections.api.annotation.Beta;
import org.eclipse.collections.api.bag.ImmutableBag;
import org.eclipse.collections.api.bag.MutableBag;
import org.eclipse.collections.api.bag.MutableBagIterable;
import org.eclipse.collections.api.bag.sorted.MutableSortedBag;
import org.eclipse.collections.api.bimap.MutableBiMap;
import org.eclipse.collections.api.block.HashingStrategy;
import org.eclipse.collections.api.block.function.Function;
import org.eclipse.collections.api.block.function.Function0;
import org.eclipse.collections.api.block.function.Function2;
import org.eclipse.collections.api.block.function.primitive.*;
import org.eclipse.collections.api.block.predicate.Predicate;
import org.eclipse.collections.api.block.predicate.Predicate2;
import org.eclipse.collections.api.block.procedure.Procedure;
import org.eclipse.collections.api.block.procedure.Procedure2;
import org.eclipse.collections.api.block.procedure.primitive.ObjectIntProcedure;
import org.eclipse.collections.api.collection.primitive.*;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.ParallelListIterable;
import org.eclipse.collections.api.list.primitive.*;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.map.MutableMapIterable;
import org.eclipse.collections.api.map.primitive.ImmutableObjectDoubleMap;
import org.eclipse.collections.api.map.primitive.ImmutableObjectLongMap;
import org.eclipse.collections.api.map.sorted.MutableSortedMap;
import org.eclipse.collections.api.multimap.MutableMultimap;
import org.eclipse.collections.api.multimap.list.ImmutableListMultimap;
import org.eclipse.collections.api.ordered.OrderedIterable;
import org.eclipse.collections.api.partition.list.PartitionImmutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.sorted.MutableSortedSet;
import org.eclipse.collections.api.stack.MutableStack;
import org.eclipse.collections.api.tuple.Pair;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

public class RecordListBuilder<T> implements ImmutableList<T> {
    ImmutableList<T> immutableList;
    MutableList<T> mutableList = Lists.mutable.withInitialCapacity(2);

    public static RecordListBuilder make() {
        return new RecordListBuilder();
    }

    public RecordListBuilder<T> removeIf(Predicate<T> condition) {
        Iterator<T> i = mutableList.iterator();
        while (i.hasNext()) {
            if (condition.test(i.next())) {
                i.remove();
            }
        }
        return this;
    }

    public RecordListBuilder<T> add(T element) {
        if (mutableList != null) {
            mutableList.add(element);
        } else {
            throw new IllegalStateException("Cannot add to list. Immutable list has already been built. ");
        }
        return this;
    }

    public ImmutableList<T> addAndBuild(T element) {
        if (mutableList != null) {
            mutableList.add(element);
            build();
            return this;
        } else {
            throw new IllegalStateException("Cannot add to list. Immutable list has already been built. ");
        }
    }

    public ImmutableList<T> build() {
        if (mutableList != null) {
            immutableList = mutableList.toImmutable();
            mutableList = null;
        }
        return this;
    }

    public RecordListBuilder<T> with(T element) {
        if (mutableList != null) {
            mutableList.add(element);
        } else {
            throw new IllegalStateException("Cannot add to list. Immutable list has already been built. ");
        }
        return this;
    }

    @Override
    public ImmutableList<T> newWith(T t) {
        if (immutableList == null) {
            build();
        }
        return immutableList.newWith(t);
    }

    @Override
    public ImmutableList<T> newWithout(T t) {
        buildIfNecessary();
        return immutableList.newWithout(t);
    }

    private void buildIfNecessary() {
        if (immutableList == null) {
            build();
        }
    }

    @Override
    public ImmutableList<T> newWithAll(Iterable<? extends T> iterable) {
        buildIfNecessary();
        return immutableList.newWithAll(iterable);
    }

    @Override
    public ImmutableList<T> newWithoutAll(Iterable<? extends T> iterable) {
        buildIfNecessary();
        return immutableList.newWithoutAll(iterable);
    }

    @Override
    public ImmutableList<T> tap(Procedure<? super T> procedure) {
        buildIfNecessary();
        return immutableList.tap(procedure);
    }

    @Override
    public ImmutableList<T> select(Predicate<? super T> predicate) {
        buildIfNecessary();
        return immutableList.select(predicate);
    }

    @Override
    public <P> ImmutableList<T> selectWith(Predicate2<? super T, ? super P> predicate2, P p) {
        buildIfNecessary();
        return immutableList.selectWith(predicate2, p);
    }

    @Override
    public ImmutableList<T> reject(Predicate<? super T> predicate) {
        buildIfNecessary();
        return immutableList.reject(predicate);
    }

    @Override
    public <P> ImmutableList<T> rejectWith(Predicate2<? super T, ? super P> predicate2, P p) {
        buildIfNecessary();
        return immutableList.rejectWith(predicate2, p);
    }

    @Override
    public PartitionImmutableList<T> partition(Predicate<? super T> predicate) {
        buildIfNecessary();
        return immutableList.partition(predicate);
    }

    @Override
    public <P> PartitionImmutableList<T> partitionWith(Predicate2<? super T, ? super P> predicate2, P p) {
        buildIfNecessary();
        return immutableList.partitionWith(predicate2, p);
    }

    @Override
    public <S> ImmutableList<S> selectInstancesOf(Class<S> aClass) {
        buildIfNecessary();
        return immutableList.selectInstancesOf(aClass);
    }

    @Override
    public <V> ImmutableList<V> collect(Function<? super T, ? extends V> function) {
        buildIfNecessary();
        return immutableList.collect(function);
    }

    @Override
    public <V> ImmutableList<V> collectWithIndex(ObjectIntToObjectFunction<? super T, ? extends V> function) {
        buildIfNecessary();
        return immutableList.collectWithIndex(function);
    }

    @Override
    public ImmutableBooleanList collectBoolean(BooleanFunction<? super T> booleanFunction) {
        buildIfNecessary();
        return immutableList.collectBoolean(booleanFunction);
    }

    @Override
    public ImmutableByteList collectByte(ByteFunction<? super T> byteFunction) {
        buildIfNecessary();
        return immutableList.collectByte(byteFunction);
    }

    @Override
    public ImmutableCharList collectChar(CharFunction<? super T> charFunction) {
        buildIfNecessary();
        return immutableList.collectChar(charFunction);
    }

    @Override
    public ImmutableDoubleList collectDouble(DoubleFunction<? super T> doubleFunction) {
        buildIfNecessary();
        return immutableList.collectDouble(doubleFunction);
    }

    @Override
    public ImmutableFloatList collectFloat(FloatFunction<? super T> floatFunction) {
        buildIfNecessary();
        return immutableList.collectFloat(floatFunction);
    }

    @Override
    public ImmutableIntList collectInt(IntFunction<? super T> intFunction) {
        buildIfNecessary();
        return immutableList.collectInt(intFunction);
    }

    @Override
    public ImmutableLongList collectLong(LongFunction<? super T> longFunction) {
        buildIfNecessary();
        return immutableList.collectLong(longFunction);
    }

    @Override
    public ImmutableShortList collectShort(ShortFunction<? super T> shortFunction) {
        buildIfNecessary();
        return immutableList.collectShort(shortFunction);
    }

    @Override
    public <P, V> ImmutableList<V> collectWith(Function2<? super T, ? super P, ? extends V> function2, P p) {
        buildIfNecessary();
        return immutableList.collectWith(function2, p);
    }

    @Override
    public <V> ImmutableList<V> collectIf(Predicate<? super T> predicate, Function<? super T, ? extends V> function) {
        buildIfNecessary();
        return immutableList.collectIf(predicate, function);
    }

    @Override
    public <V> ImmutableList<V> flatCollect(Function<? super T, ? extends Iterable<V>> function) {
        buildIfNecessary();
        return immutableList.flatCollect(function);
    }

    @Override
    public <P, V> ImmutableList<V> flatCollectWith(Function2<? super T, ? super P, ? extends Iterable<V>> function, P parameter) {
        buildIfNecessary();
        return immutableList.flatCollectWith(function, parameter);
    }

    @Override
    public <V> ImmutableListMultimap<V, T> groupBy(Function<? super T, ? extends V> function) {
        buildIfNecessary();
        return immutableList.groupBy(function);
    }

    @Override
    public <V> ImmutableListMultimap<V, T> groupByEach(Function<? super T, ? extends Iterable<V>> function) {
        buildIfNecessary();
        return immutableList.groupByEach(function);
    }

    @Override
    public ImmutableList<T> distinct() {
        buildIfNecessary();
        return immutableList.distinct();
    }

    @Override
    public ImmutableList<T> distinct(HashingStrategy<? super T> hashingStrategy) {
        buildIfNecessary();
        return immutableList.distinct(hashingStrategy);
    }

    @Override
    public <V> ImmutableList<T> distinctBy(Function<? super T, ? extends V> function) {
        buildIfNecessary();
        return immutableList.distinctBy(function);
    }

    @Override
    public <S> ImmutableList<Pair<T, S>> zip(Iterable<S> iterable) {
        buildIfNecessary();
        return immutableList.zip(iterable);
    }

    @Override
    public ImmutableList<Pair<T, Integer>> zipWithIndex() {
        buildIfNecessary();
        return immutableList.zipWithIndex();
    }

    @Override
    public ImmutableList<T> take(int i) {
        buildIfNecessary();
        return immutableList.take(i);
    }

    @Override
    public ImmutableList<T> takeWhile(Predicate<? super T> predicate) {
        buildIfNecessary();
        return immutableList.takeWhile(predicate);
    }

    @Override
    public ImmutableList<T> drop(int i) {
        buildIfNecessary();
        return immutableList.drop(i);
    }

    @Override
    public ImmutableList<T> dropWhile(Predicate<? super T> predicate) {
        buildIfNecessary();
        return immutableList.dropWhile(predicate);
    }

    @Override
    public PartitionImmutableList<T> partitionWhile(Predicate<? super T> predicate) {
        buildIfNecessary();
        return immutableList.partitionWhile(predicate);
    }

    @Override
    public List<T> castToList() {
        buildIfNecessary();
        return immutableList.castToList();
    }

    @Override
    public ImmutableList<T> subList(int i, int i1) {
        buildIfNecessary();
        return immutableList.subList(i, i1);
    }

    @Override
    public ImmutableList<T> toReversed() {
        buildIfNecessary();
        return immutableList.toReversed();
    }

    @Override
    public <V> ImmutableObjectLongMap<V> sumByInt(Function<? super T, ? extends V> function, IntFunction<? super T> intFunction) {
        buildIfNecessary();
        return immutableList.sumByInt(function, intFunction);
    }

    @Override
    public <V> ImmutableObjectDoubleMap<V> sumByFloat(Function<? super T, ? extends V> function, FloatFunction<? super T> floatFunction) {
        buildIfNecessary();
        return immutableList.sumByFloat(function, floatFunction);
    }

    @Override
    public <V> ImmutableObjectLongMap<V> sumByLong(Function<? super T, ? extends V> function, LongFunction<? super T> longFunction) {
        buildIfNecessary();
        return immutableList.sumByLong(function, longFunction);
    }

    @Override
    public <V> ImmutableObjectDoubleMap<V> sumByDouble(Function<? super T, ? extends V> function, DoubleFunction<? super T> doubleFunction) {
        buildIfNecessary();
        return immutableList.sumByDouble(function, doubleFunction);
    }

    @Override
    public <V> ImmutableBag<V> countBy(Function<? super T, ? extends V> function) {
        buildIfNecessary();
        return immutableList.countBy(function);
    }

    @Override
    public <V, P> ImmutableBag<V> countByWith(Function2<? super T, ? super P, ? extends V> function, P parameter) {
        buildIfNecessary();
        return immutableList.countByWith(function, parameter);
    }

    @Override
    public <V> ImmutableBag<V> countByEach(Function<? super T, ? extends Iterable<V>> function) {
        buildIfNecessary();
        return immutableList.countByEach(function);
    }

    @Override
    public <V> ImmutableMap<V, T> groupByUniqueKey(Function<? super T, ? extends V> function) {
        buildIfNecessary();
        return immutableList.groupByUniqueKey(function);
    }

    @Override
    public <K, V> ImmutableMap<K, V> aggregateInPlaceBy(Function<? super T, ? extends K> groupBy, Function0<? extends V> zeroValueFactory, Procedure2<? super V, ? super T> mutatingAggregator) {
        buildIfNecessary();
        return immutableList.aggregateInPlaceBy(groupBy, zeroValueFactory, mutatingAggregator);
    }

    @Override
    public <K, V> ImmutableMap<K, V> aggregateBy(Function<? super T, ? extends K> groupBy, Function0<? extends V> zeroValueFactory, Function2<? super V, ? super T, ? extends V> nonMutatingAggregator) {
        buildIfNecessary();
        return immutableList.aggregateBy(groupBy, zeroValueFactory, nonMutatingAggregator);
    }

    @Override
    public Stream<T> stream() {
        buildIfNecessary();
        return immutableList.stream();
    }

    @Override
    public Stream<T> parallelStream() {
        buildIfNecessary();
        return immutableList.parallelStream();
    }

    @Override
    public Spliterator<T> spliterator() {
        buildIfNecessary();
        return immutableList.spliterator();
    }

    @Override
    public Collection<T> castToCollection() {
        buildIfNecessary();
        return immutableList.castToCollection();
    }

    @Override
    public void forEach(Procedure<? super T> procedure) {
        buildIfNecessary();
        immutableList.forEach(procedure);
    }

    @Override
    public int size() {
        buildIfNecessary();
        return immutableList.size();
    }

    @Override
    public boolean isEmpty() {
        buildIfNecessary();
        return immutableList.isEmpty();
    }

    @Override
    public boolean notEmpty() {
        buildIfNecessary();
        return immutableList.notEmpty();
    }

    @Override
    public T getAny() {
        buildIfNecessary();
        return immutableList.getAny();
    }

    @Override
    @Deprecated
    public T getFirst() {
        buildIfNecessary();
        return immutableList.getFirst();
    }

    @Override
    @Deprecated
    public T getLast() {
        buildIfNecessary();
        return immutableList.getLast();
    }

    @Override
    public T getOnly() {
        buildIfNecessary();
        return immutableList.getOnly();
    }

    @Override
    public boolean contains(Object o) {
        buildIfNecessary();
        return immutableList.contains(o);
    }

    @Override
    public <V> boolean containsBy(Function<? super T, ? extends V> function, V value) {
        buildIfNecessary();
        return immutableList.containsBy(function, value);
    }

    @Override
    public boolean containsAllIterable(Iterable<?> iterable) {
        buildIfNecessary();
        return immutableList.containsAllIterable(iterable);
    }

    @Override
    public boolean containsAll(Collection<?> collection) {
        buildIfNecessary();
        return immutableList.containsAll(collection);
    }

    @Override
    public boolean containsAllArguments(Object... objects) {
        buildIfNecessary();
        return immutableList.containsAllArguments(objects);
    }

    @Override
    public void each(Procedure<? super T> procedure) {
        buildIfNecessary();
        immutableList.each(procedure);
    }

    @Override
    public <R extends Collection<T>> R select(Predicate<? super T> predicate, R ts) {
        buildIfNecessary();
        return immutableList.select(predicate, ts);
    }

    @Override
    public <P, R extends Collection<T>> R selectWith(Predicate2<? super T, ? super P> predicate2, P p, R ts) {
        buildIfNecessary();
        return immutableList.selectWith(predicate2, p, ts);
    }

    @Override
    public <R extends Collection<T>> R reject(Predicate<? super T> predicate, R ts) {
        buildIfNecessary();
        return immutableList.reject(predicate, ts);
    }

    @Override
    public <P, R extends Collection<T>> R rejectWith(Predicate2<? super T, ? super P> predicate2, P p, R ts) {
        buildIfNecessary();
        return immutableList.rejectWith(predicate2, p, ts);
    }

    @Override
    public <V, R extends Collection<V>> R collect(Function<? super T, ? extends V> function, R vs) {
        buildIfNecessary();
        return immutableList.collect(function, vs);
    }

    @Override
    public <R extends MutableBooleanCollection> R collectBoolean(BooleanFunction<? super T> booleanFunction, R r) {
        buildIfNecessary();
        return immutableList.collectBoolean(booleanFunction, r);
    }

    @Override
    public <R extends MutableByteCollection> R collectByte(ByteFunction<? super T> byteFunction, R r) {
        buildIfNecessary();
        return immutableList.collectByte(byteFunction, r);
    }

    @Override
    public <R extends MutableCharCollection> R collectChar(CharFunction<? super T> charFunction, R r) {
        buildIfNecessary();
        return immutableList.collectChar(charFunction, r);
    }

    @Override
    public <R extends MutableDoubleCollection> R collectDouble(DoubleFunction<? super T> doubleFunction, R r) {
        buildIfNecessary();
        return immutableList.collectDouble(doubleFunction, r);
    }

    @Override
    public <R extends MutableFloatCollection> R collectFloat(FloatFunction<? super T> floatFunction, R r) {
        buildIfNecessary();
        return immutableList.collectFloat(floatFunction, r);
    }

    @Override
    public <R extends MutableIntCollection> R collectInt(IntFunction<? super T> intFunction, R r) {
        buildIfNecessary();
        return immutableList.collectInt(intFunction, r);
    }

    @Override
    public <R extends MutableLongCollection> R collectLong(LongFunction<? super T> longFunction, R r) {
        buildIfNecessary();
        return immutableList.collectLong(longFunction, r);
    }

    @Override
    public <R extends MutableShortCollection> R collectShort(ShortFunction<? super T> shortFunction, R r) {
        buildIfNecessary();
        return immutableList.collectShort(shortFunction, r);
    }

    @Override
    public <P, V, R extends Collection<V>> R collectWith(Function2<? super T, ? super P, ? extends V> function2, P p, R vs) {
        buildIfNecessary();
        return immutableList.collectWith(function2, p, vs);
    }

    @Override
    public <V, R extends Collection<V>> R collectIf(Predicate<? super T> predicate, Function<? super T, ? extends V> function, R vs) {
        buildIfNecessary();
        return immutableList.collectIf(predicate, function, vs);
    }

    @Override
    public <R extends MutableByteCollection> R flatCollectByte(Function<? super T, ? extends ByteIterable> function, R target) {
        buildIfNecessary();
        return immutableList.flatCollectByte(function, target);
    }

    @Override
    public <R extends MutableCharCollection> R flatCollectChar(Function<? super T, ? extends CharIterable> function, R target) {
        buildIfNecessary();
        return immutableList.flatCollectChar(function, target);
    }

    @Override
    public <R extends MutableIntCollection> R flatCollectInt(Function<? super T, ? extends IntIterable> function, R target) {
        buildIfNecessary();
        return immutableList.flatCollectInt(function, target);
    }

    @Override
    public <R extends MutableShortCollection> R flatCollectShort(Function<? super T, ? extends ShortIterable> function, R target) {
        buildIfNecessary();
        return immutableList.flatCollectShort(function, target);
    }

    @Override
    public <R extends MutableDoubleCollection> R flatCollectDouble(Function<? super T, ? extends DoubleIterable> function, R target) {
        buildIfNecessary();
        return immutableList.flatCollectDouble(function, target);
    }

    @Override
    public <R extends MutableFloatCollection> R flatCollectFloat(Function<? super T, ? extends FloatIterable> function, R target) {
        buildIfNecessary();
        return immutableList.flatCollectFloat(function, target);
    }

    @Override
    public <R extends MutableLongCollection> R flatCollectLong(Function<? super T, ? extends LongIterable> function, R target) {
        buildIfNecessary();
        return immutableList.flatCollectLong(function, target);
    }

    @Override
    public <R extends MutableBooleanCollection> R flatCollectBoolean(Function<? super T, ? extends BooleanIterable> function, R target) {
        buildIfNecessary();
        return immutableList.flatCollectBoolean(function, target);
    }

    @Override
    public <V, R extends Collection<V>> R flatCollect(Function<? super T, ? extends Iterable<V>> function, R vs) {
        buildIfNecessary();
        return immutableList.flatCollect(function, vs);
    }

    @Override
    public <P, V, R extends Collection<V>> R flatCollectWith(Function2<? super T, ? super P, ? extends Iterable<V>> function, P parameter, R target) {
        buildIfNecessary();
        return immutableList.flatCollectWith(function, parameter, target);
    }

    @Override
    public T detect(Predicate<? super T> predicate) {
        buildIfNecessary();
        return immutableList.detect(predicate);
    }

    @Override
    public <P> T detectWith(Predicate2<? super T, ? super P> predicate2, P p) {
        buildIfNecessary();
        return immutableList.detectWith(predicate2, p);
    }

    @Override
    public Optional<T> detectOptional(Predicate<? super T> predicate) {
        buildIfNecessary();
        return immutableList.detectOptional(predicate);
    }

    @Override
    public <P> Optional<T> detectWithOptional(Predicate2<? super T, ? super P> predicate2, P p) {
        buildIfNecessary();
        return immutableList.detectWithOptional(predicate2, p);
    }

    @Override
    public T detectIfNone(Predicate<? super T> predicate, Function0<? extends T> function) {
        buildIfNecessary();
        return immutableList.detectIfNone(predicate, function);
    }

    @Override
    public <P> T detectWithIfNone(Predicate2<? super T, ? super P> predicate2, P p, Function0<? extends T> function0) {
        buildIfNecessary();
        return immutableList.detectWithIfNone(predicate2, p, function0);
    }

    @Override
    public int count(Predicate<? super T> predicate) {
        buildIfNecessary();
        return immutableList.count(predicate);
    }

    @Override
    public <P> int countWith(Predicate2<? super T, ? super P> predicate2, P p) {
        buildIfNecessary();
        return immutableList.countWith(predicate2, p);
    }

    @Override
    public boolean anySatisfy(Predicate<? super T> predicate) {
        buildIfNecessary();
        return immutableList.anySatisfy(predicate);
    }

    @Override
    public <P> boolean anySatisfyWith(Predicate2<? super T, ? super P> predicate2, P p) {
        buildIfNecessary();
        return immutableList.anySatisfyWith(predicate2, p);
    }

    @Override
    public boolean allSatisfy(Predicate<? super T> predicate) {
        buildIfNecessary();
        return immutableList.allSatisfy(predicate);
    }

    @Override
    public <P> boolean allSatisfyWith(Predicate2<? super T, ? super P> predicate2, P p) {
        buildIfNecessary();
        return immutableList.allSatisfyWith(predicate2, p);
    }

    @Override
    public boolean noneSatisfy(Predicate<? super T> predicate) {
        buildIfNecessary();
        return immutableList.noneSatisfy(predicate);
    }

    @Override
    public <P> boolean noneSatisfyWith(Predicate2<? super T, ? super P> predicate2, P p) {
        buildIfNecessary();
        return immutableList.noneSatisfyWith(predicate2, p);
    }

    @Override
    public <IV> IV injectInto(IV iv, Function2<? super IV, ? super T, ? extends IV> function2) {
        buildIfNecessary();
        return immutableList.injectInto(iv, function2);
    }

    @Override
    public int injectInto(int i, IntObjectToIntFunction<? super T> intObjectToIntFunction) {
        buildIfNecessary();
        return immutableList.injectInto(i, intObjectToIntFunction);
    }

    @Override
    public long injectInto(long l, LongObjectToLongFunction<? super T> longObjectToLongFunction) {
        buildIfNecessary();
        return immutableList.injectInto(l, longObjectToLongFunction);
    }

    @Override
    public float injectInto(float v, FloatObjectToFloatFunction<? super T> floatObjectToFloatFunction) {
        buildIfNecessary();
        return immutableList.injectInto(v, floatObjectToFloatFunction);
    }

    @Override
    public double injectInto(double v, DoubleObjectToDoubleFunction<? super T> doubleObjectToDoubleFunction) {
        buildIfNecessary();
        return immutableList.injectInto(v, doubleObjectToDoubleFunction);
    }

    @Override
    public <R extends Collection<T>> R into(R ts) {
        buildIfNecessary();
        return immutableList.into(ts);
    }

    @Override
    public MutableList<T> toList() {
        buildIfNecessary();
        return immutableList.toList();
    }

    @Override
    public MutableList<T> toSortedList() {
        buildIfNecessary();
        return immutableList.toSortedList();
    }

    @Override
    public MutableList<T> toSortedList(Comparator<? super T> comparator) {
        buildIfNecessary();
        return immutableList.toSortedList(comparator);
    }

    @Override
    public <V extends Comparable<? super V>> MutableList<T> toSortedListBy(Function<? super T, ? extends V> function) {
        buildIfNecessary();
        return immutableList.toSortedListBy(function);
    }

    @Override
    public MutableSet<T> toSet() {
        buildIfNecessary();
        return immutableList.toSet();
    }

    @Override
    public MutableSortedSet<T> toSortedSet() {
        buildIfNecessary();
        return immutableList.toSortedSet();
    }

    @Override
    public MutableSortedSet<T> toSortedSet(Comparator<? super T> comparator) {
        buildIfNecessary();
        return immutableList.toSortedSet(comparator);
    }

    @Override
    public <V extends Comparable<? super V>> MutableSortedSet<T> toSortedSetBy(Function<? super T, ? extends V> function) {
        buildIfNecessary();
        return immutableList.toSortedSetBy(function);
    }

    @Override
    public MutableBag<T> toBag() {
        buildIfNecessary();
        return immutableList.toBag();
    }

    @Override
    public MutableSortedBag<T> toSortedBag() {
        buildIfNecessary();
        return immutableList.toSortedBag();
    }

    @Override
    public MutableSortedBag<T> toSortedBag(Comparator<? super T> comparator) {
        buildIfNecessary();
        return immutableList.toSortedBag(comparator);
    }

    @Override
    public <V extends Comparable<? super V>> MutableSortedBag<T> toSortedBagBy(Function<? super T, ? extends V> function) {
        buildIfNecessary();
        return immutableList.toSortedBagBy(function);
    }

    @Override
    public <NK, NV> MutableMap<NK, NV> toMap(Function<? super T, ? extends NK> function, Function<? super T, ? extends NV> function1) {
        buildIfNecessary();
        return immutableList.toMap(function, function1);
    }

    @Override
    public <NK, NV, R extends Map<NK, NV>> R toMap(Function<? super T, ? extends NK> keyFunction, Function<? super T, ? extends NV> valueFunction, R target) {
        buildIfNecessary();
        return immutableList.toMap(keyFunction, valueFunction, target);
    }

    @Override
    public <NK, NV> MutableSortedMap<NK, NV> toSortedMap(Function<? super T, ? extends NK> function, Function<? super T, ? extends NV> function1) {
        buildIfNecessary();
        return immutableList.toSortedMap(function, function1);
    }

    @Override
    public <NK, NV> MutableSortedMap<NK, NV> toSortedMap(Comparator<? super NK> comparator, Function<? super T, ? extends NK> function, Function<? super T, ? extends NV> function1) {
        buildIfNecessary();
        return immutableList.toSortedMap(comparator, function, function1);
    }

    @Override
    public <KK extends Comparable<? super KK>, NK, NV> MutableSortedMap<NK, NV> toSortedMapBy(Function<? super NK, KK> sortBy, Function<? super T, ? extends NK> keyFunction, Function<? super T, ? extends NV> valueFunction) {
        buildIfNecessary();
        return immutableList.toSortedMapBy(sortBy, keyFunction, valueFunction);
    }

    @Override
    public <NK, NV> MutableBiMap<NK, NV> toBiMap(Function<? super T, ? extends NK> function, Function<? super T, ? extends NV> function1) {
        buildIfNecessary();
        return immutableList.toBiMap(function, function1);
    }

    @Override
    public LazyIterable<T> asLazy() {
        buildIfNecessary();
        return immutableList.asLazy();
    }

    @Override
    public Object[] toArray() {
        buildIfNecessary();
        return immutableList.toArray();
    }

    @Override
    public <E> E[] toArray(E[] es) {
        buildIfNecessary();
        return immutableList.toArray(es);
    }

    @Override
    public T min(Comparator<? super T> comparator) {
        buildIfNecessary();
        return immutableList.min(comparator);
    }

    @Override
    public T max(Comparator<? super T> comparator) {
        buildIfNecessary();
        return immutableList.max(comparator);
    }

    @Override
    public Optional<T> minOptional(Comparator<? super T> comparator) {
        buildIfNecessary();
        return immutableList.minOptional(comparator);
    }

    @Override
    public Optional<T> maxOptional(Comparator<? super T> comparator) {
        buildIfNecessary();
        return immutableList.maxOptional(comparator);
    }

    @Override
    public T min() {
        buildIfNecessary();
        return immutableList.min();
    }

    @Override
    public T max() {
        buildIfNecessary();
        return immutableList.max();
    }

    @Override
    public Optional<T> minOptional() {
        buildIfNecessary();
        return immutableList.minOptional();
    }

    @Override
    public Optional<T> maxOptional() {
        buildIfNecessary();
        return immutableList.maxOptional();
    }

    @Override
    public <V extends Comparable<? super V>> T minBy(Function<? super T, ? extends V> function) {
        buildIfNecessary();
        return immutableList.minBy(function);
    }

    @Override
    public <V extends Comparable<? super V>> T maxBy(Function<? super T, ? extends V> function) {
        buildIfNecessary();
        return immutableList.maxBy(function);
    }

    @Override
    public <V extends Comparable<? super V>> Optional<T> minByOptional(Function<? super T, ? extends V> function) {
        buildIfNecessary();
        return immutableList.minByOptional(function);
    }

    @Override
    public <V extends Comparable<? super V>> Optional<T> maxByOptional(Function<? super T, ? extends V> function) {
        buildIfNecessary();
        return immutableList.maxByOptional(function);
    }

    @Override
    public long sumOfInt(IntFunction<? super T> intFunction) {
        buildIfNecessary();
        return immutableList.sumOfInt(intFunction);
    }

    @Override
    public double sumOfFloat(FloatFunction<? super T> floatFunction) {
        buildIfNecessary();
        return immutableList.sumOfFloat(floatFunction);
    }

    @Override
    public long sumOfLong(LongFunction<? super T> longFunction) {
        buildIfNecessary();
        return immutableList.sumOfLong(longFunction);
    }

    @Override
    public double sumOfDouble(DoubleFunction<? super T> doubleFunction) {
        buildIfNecessary();
        return immutableList.sumOfDouble(doubleFunction);
    }

    @Override
    public IntSummaryStatistics summarizeInt(IntFunction<? super T> function) {
        buildIfNecessary();
        return immutableList.summarizeInt(function);
    }

    @Override
    public DoubleSummaryStatistics summarizeFloat(FloatFunction<? super T> function) {
        buildIfNecessary();
        return immutableList.summarizeFloat(function);
    }

    @Override
    public LongSummaryStatistics summarizeLong(LongFunction<? super T> function) {
        buildIfNecessary();
        return immutableList.summarizeLong(function);
    }

    @Override
    public DoubleSummaryStatistics summarizeDouble(DoubleFunction<? super T> function) {
        buildIfNecessary();
        return immutableList.summarizeDouble(function);
    }

    @Override
    public <R, A> R reduceInPlace(Collector<? super T, A, R> collector) {
        buildIfNecessary();
        return immutableList.reduceInPlace(collector);
    }

    @Override
    public <R> R reduceInPlace(Supplier<R> supplier, BiConsumer<R, ? super T> accumulator) {
        buildIfNecessary();
        return immutableList.reduceInPlace(supplier, accumulator);
    }

    @Override
    public Optional<T> reduce(BinaryOperator<T> accumulator) {
        buildIfNecessary();
        return immutableList.reduce(accumulator);
    }

    @Override
    public String makeString() {
        buildIfNecessary();
        return immutableList.makeString();
    }

    @Override
    public String makeString(String separator) {
        buildIfNecessary();
        return immutableList.makeString(separator);
    }

    @Override
    public String makeString(String start, String separator, String end) {
        buildIfNecessary();
        return immutableList.makeString(start, separator, end);
    }

    @Override
    public void appendString(Appendable appendable) {
        buildIfNecessary();
        immutableList.appendString(appendable);
    }

    @Override
    public void appendString(Appendable appendable, String separator) {
        buildIfNecessary();
        immutableList.appendString(appendable, separator);
    }

    @Override
    public void appendString(Appendable appendable, String s, String s1, String s2) {
        buildIfNecessary();
        immutableList.appendString(appendable, s, s1, s2);
    }

    @Override
    public <V, R extends MutableBagIterable<V>> R countBy(Function<? super T, ? extends V> function, R target) {
        buildIfNecessary();
        return immutableList.countBy(function, target);
    }

    @Override
    public <V, P, R extends MutableBagIterable<V>> R countByWith(Function2<? super T, ? super P, ? extends V> function, P parameter, R target) {
        buildIfNecessary();
        return immutableList.countByWith(function, parameter, target);
    }

    @Override
    public <V, R extends MutableBagIterable<V>> R countByEach(Function<? super T, ? extends Iterable<V>> function, R target) {
        buildIfNecessary();
        return immutableList.countByEach(function, target);
    }

    @Override
    public <V, R extends MutableMultimap<V, T>> R groupBy(Function<? super T, ? extends V> function, R r) {
        buildIfNecessary();
        return immutableList.groupBy(function, r);
    }

    @Override
    public <V, R extends MutableMultimap<V, T>> R groupByEach(Function<? super T, ? extends Iterable<V>> function, R r) {
        buildIfNecessary();
        return immutableList.groupByEach(function, r);
    }

    @Override
    public <V, R extends MutableMapIterable<V, T>> R groupByUniqueKey(Function<? super T, ? extends V> function, R ts) {
        buildIfNecessary();
        return immutableList.groupByUniqueKey(function, ts);
    }

    @Override
    @Deprecated
    public <S, R extends Collection<Pair<T, S>>> R zip(Iterable<S> iterable, R pairs) {
        buildIfNecessary();
        return immutableList.zip(iterable, pairs);
    }

    @Override
    @Deprecated
    public <R extends Collection<Pair<T, Integer>>> R zipWithIndex(R pairs) {
        buildIfNecessary();
        return immutableList.zipWithIndex(pairs);
    }

    @Override
    public RichIterable<RichIterable<T>> chunk(int i) {
        buildIfNecessary();
        return immutableList.chunk(i);
    }

    @Override
    public <K, V, R extends MutableMapIterable<K, V>> R aggregateBy(Function<? super T, ? extends K> groupBy, Function0<? extends V> zeroValueFactory, Function2<? super V, ? super T, ? extends V> nonMutatingAggregator, R target) {
        buildIfNecessary();
        return immutableList.aggregateBy(groupBy, zeroValueFactory, nonMutatingAggregator, target);
    }

    @Override
    public <K, V, R extends MutableMultimap<K, V>> R groupByAndCollect(Function<? super T, ? extends K> groupByFunction, Function<? super T, ? extends V> collectFunction, R target) {
        buildIfNecessary();
        return immutableList.groupByAndCollect(groupByFunction, collectFunction, target);
    }

    @Override
    public void forEach(Consumer<? super T> consumer) {
        buildIfNecessary();
        immutableList.forEach(consumer);
    }

    @Override
    @Deprecated
    public void forEachWithIndex(ObjectIntProcedure<? super T> objectIntProcedure) {
        buildIfNecessary();
        immutableList.forEachWithIndex(objectIntProcedure);
    }

    @Override
    public <P> void forEachWith(Procedure2<? super T, ? super P> procedure2, P p) {
        buildIfNecessary();
        immutableList.forEachWith(procedure2, p);
    }

    @Override
    public Iterator<T> iterator() {
        buildIfNecessary();
        return immutableList.iterator();
    }

    @Override
    public T get(int i) {
        buildIfNecessary();
        return immutableList.get(i);
    }

    @Override
    public int lastIndexOf(Object o) {
        buildIfNecessary();
        return immutableList.lastIndexOf(o);
    }

    @Override
    public ListIterator<T> listIterator() {
        buildIfNecessary();
        return immutableList.listIterator();
    }

    @Override
    public ListIterator<T> listIterator(int i) {
        buildIfNecessary();
        return immutableList.listIterator(i);
    }

    @Override
    public ImmutableList<T> toImmutable() {
        buildIfNecessary();
        return immutableList.toImmutable();
    }

    @Override
    @Beta
    public ParallelListIterable<T> asParallel(ExecutorService executorService, int i) {
        buildIfNecessary();
        return immutableList.asParallel(executorService, i);
    }

    @Override
    public int binarySearch(T key, Comparator<? super T> comparator) {
        buildIfNecessary();
        return immutableList.binarySearch(key, comparator);
    }

    @Override
    public int binarySearch(T key) {
        buildIfNecessary();
        return immutableList.binarySearch(key);
    }

    @Override
    public <T2> void forEachInBoth(ListIterable<T2> other, Procedure2<? super T, ? super T2> procedure) {
        buildIfNecessary();
        immutableList.forEachInBoth(other, procedure);
    }

    @Override
    public int hashCode() {
        buildIfNecessary();
        return immutableList.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        buildIfNecessary();
        return immutableList.equals(o);
    }

    @Override
    public String toString() {
        buildIfNecessary();
        return immutableList.toString();
    }

    @Override
    public void reverseForEach(Procedure<? super T> procedure) {
        buildIfNecessary();
        immutableList.reverseForEach(procedure);
    }

    @Override
    public void reverseForEachWithIndex(ObjectIntProcedure<? super T> procedure) {
        buildIfNecessary();
        immutableList.reverseForEachWithIndex(procedure);
    }

    @Override
    public LazyIterable<T> asReversed() {
        buildIfNecessary();
        return immutableList.asReversed();
    }

    @Override
    public int detectLastIndex(Predicate<? super T> predicate) {
        buildIfNecessary();
        return immutableList.detectLastIndex(predicate);
    }

    @Override
    public int indexOf(Object o) {
        buildIfNecessary();
        return immutableList.indexOf(o);
    }

    @Override
    public Optional<T> getFirstOptional() {
        buildIfNecessary();
        return immutableList.getFirstOptional();
    }

    @Override
    public Optional<T> getLastOptional() {
        buildIfNecessary();
        return immutableList.getLastOptional();
    }

    @Override
    public <S> boolean corresponds(OrderedIterable<S> orderedIterable, Predicate2<? super T, ? super S> predicate2) {
        buildIfNecessary();
        return immutableList.corresponds(orderedIterable, predicate2);
    }

    @Override
    public void forEach(int i, int i1, Procedure<? super T> procedure) {
        buildIfNecessary();
        immutableList.forEach(i, i1, procedure);
    }

    @Override
    public void forEachWithIndex(int i, int i1, ObjectIntProcedure<? super T> objectIntProcedure) {
        buildIfNecessary();
        immutableList.forEachWithIndex(i, i1, objectIntProcedure);
    }

    @Override
    public MutableStack<T> toStack() {
        buildIfNecessary();
        return immutableList.toStack();
    }

    @Override
    public <V, R extends Collection<V>> R collectWithIndex(ObjectIntToObjectFunction<? super T, ? extends V> function, R target) {
        buildIfNecessary();
        return immutableList.collectWithIndex(function, target);
    }

    @Override
    public int detectIndex(Predicate<? super T> predicate) {
        buildIfNecessary();
        return immutableList.detectIndex(predicate);
    }

}
