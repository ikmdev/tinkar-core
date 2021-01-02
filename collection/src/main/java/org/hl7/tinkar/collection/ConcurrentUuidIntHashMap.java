package org.hl7.tinkar.collection;

import org.eclipse.collections.api.block.function.Function;
import org.eclipse.collections.api.block.function.Function0;
import org.eclipse.collections.api.block.function.Function2;
import org.eclipse.collections.api.block.function.Function3;
import org.eclipse.collections.api.block.procedure.Procedure;
import org.eclipse.collections.api.block.procedure.Procedure2;
import org.eclipse.collections.api.block.procedure.primitive.ObjectIntProcedure;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.ConcurrentMutableMap;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.block.procedure.MapEntryToProcedure2;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.map.mutable.AbstractMutableMap;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.MapIterate;
import org.eclipse.collections.impl.utility.internal.IterableIterate;

import java.io.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;

/**
 * Note that this class uses underlying primitive representations of UUID and Integer, and therefore does not
 * support Object identity preservation of keys.
 */
@SuppressWarnings({"rawtypes", "ObjectEquality"})
public final class ConcurrentUuidIntHashMap
        extends AbstractMutableMap<UUID, Integer>
        implements ConcurrentMutableMap<UUID, Integer>, Externalizable
{
    @Serial
    private static final long serialVersionUID = 1L;

    private static final Object RESIZE_SENTINEL = new Object();
    private static final int DEFAULT_INITIAL_CAPACITY = 16;

    /**
     * The maximum capacity, used if a higher value is implicitly specified
     * by either of the constructors with arguments.
     * MUST be a power of two <= 1<<30.
     */
    private static final int MAXIMUM_CAPACITY = 1 << 30;

    private static final AtomicReferenceFieldUpdater<ConcurrentUuidIntHashMap, AtomicReferenceArray> TABLE_UPDATER = AtomicReferenceFieldUpdater.newUpdater(ConcurrentUuidIntHashMap.class, AtomicReferenceArray.class, "table");
    private static final AtomicIntegerFieldUpdater<ConcurrentUuidIntHashMap> SIZE_UPDATER = AtomicIntegerFieldUpdater.newUpdater(ConcurrentUuidIntHashMap.class, "size");
    private static final Object RESIZED = new Object();
    private static final Object RESIZING = new Object();
    private static final int PARTITIONED_SIZE_THRESHOLD = 4096; // chosen to keep size below 1% of the total size of the map
    private static final int SIZE_BUCKETS = 7;

    /**
     * The table, resized as necessary. Length MUST Always be a power of two.
     */
    private volatile AtomicReferenceArray table;

    private AtomicIntegerArray partitionedSize;

    @SuppressWarnings("UnusedDeclaration")
    private volatile int size; // updated via atomic field updater

    public ConcurrentUuidIntHashMap()
    {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    public ConcurrentUuidIntHashMap(int initialCapacity)
    {
        if (initialCapacity < 0)
        {
            throw new IllegalArgumentException("Illegal Initial Capacity: " + initialCapacity);
        }
        if (initialCapacity > MAXIMUM_CAPACITY)
        {
            initialCapacity = MAXIMUM_CAPACITY;
        }

        int threshold = initialCapacity;
        threshold += threshold >> 1; // threshold = length * 0.75

        int capacity = 1;
        while (capacity < threshold)
        {
            capacity <<= 1;
        }
        if (capacity >= PARTITIONED_SIZE_THRESHOLD)
        {
            this.partitionedSize = new AtomicIntegerArray(SIZE_BUCKETS * 16); // we want 7 extra slots and 64 bytes for each slot. int is 4 bytes, so 64 bytes is 16 ints.
        }
        this.table = new AtomicReferenceArray(capacity + 1);
    }

    public static ConcurrentUuidIntHashMap newMap()
    {
        return new ConcurrentUuidIntHashMap();
    }

    public static ConcurrentUuidIntHashMap newMap(int newSize)
    {
        return new ConcurrentUuidIntHashMap(newSize);
    }

    private static int indexFor(int h, int length)
    {
        return h & length - 2;
    }

    @Override
    public Integer putIfAbsent(UUID key, Integer value)
    {
        int hash = this.hash(key);
        AtomicReferenceArray currentArray = this.table;
        while (true)
        {
            int length = currentArray.length();
            int index = ConcurrentUuidIntHashMap.indexFor(hash, length);
            Object o = currentArray.get(index);
            if (o == RESIZED || o == RESIZING)
            {
                currentArray = this.helpWithResizeWhileCurrentIndex(currentArray, index);
            }
            else
            {
                ConcurrentUuidIntHashMap.Entry e = (ConcurrentUuidIntHashMap.Entry) o;
                while (e != null)
                {
                    UUID candidate = e.getKey();
                    if (candidate.equals(key))
                    {
                        return e.getValue();
                    }
                    e = e.getNext();
                }
                ConcurrentUuidIntHashMap.Entry newEntry = new ConcurrentUuidIntHashMap.Entry(key, value, (ConcurrentUuidIntHashMap.Entry) o);
                if (currentArray.compareAndSet(index, o, newEntry))
                {
                    this.incrementSizeAndPossiblyResize(currentArray, length, o);
                    return null; // per the contract of putIfAbsent, we return null when the map didn't have this key before
                }
            }
        }
    }

    private void incrementSizeAndPossiblyResize(AtomicReferenceArray currentArray, int length, Object prev)
    {
        this.addToSize(1);
        if (prev != null)
        {
            int localSize = this.size();
            int threshold = (length >> 1) + (length >> 2); // threshold = length * 0.75
            if (localSize + 1 > threshold)
            {
                this.resize(currentArray);
            }
        }
    }

    private int hash(Object key)
    {
        int h = key.hashCode();
        h ^= h >>> 20 ^ h >>> 12;
        h ^= h >>> 7 ^ h >>> 4;
        return h;
    }

    private AtomicReferenceArray helpWithResizeWhileCurrentIndex(AtomicReferenceArray currentArray, int index)
    {
        AtomicReferenceArray newArray = this.helpWithResize(currentArray);
        int helpCount = 0;
        while (currentArray.get(index) != RESIZED)
        {
            helpCount++;
            newArray = this.helpWithResize(currentArray);
            if ((helpCount & 7) == 0)
            {
                Thread.yield();
            }
        }
        return newArray;
    }

    private AtomicReferenceArray helpWithResize(AtomicReferenceArray currentArray)
    {
        ConcurrentUuidIntHashMap.ResizeContainer resizeContainer = (ConcurrentUuidIntHashMap.ResizeContainer) currentArray.get(currentArray.length() - 1);
        AtomicReferenceArray newTable = resizeContainer.nextArray;
        if (resizeContainer.getQueuePosition() > ConcurrentUuidIntHashMap.ResizeContainer.QUEUE_INCREMENT)
        {
            resizeContainer.incrementResizer();
            this.reverseTransfer(currentArray, resizeContainer);
            resizeContainer.decrementResizerAndNotify();
        }
        return newTable;
    }

    private void resize(AtomicReferenceArray oldTable)
    {
        this.resize(oldTable, (oldTable.length() - 1 << 1) + 1);
    }

    // newSize must be a power of 2 + 1
    @SuppressWarnings("JLM_JSR166_UTILCONCURRENT_MONITORENTER")
    private void resize(AtomicReferenceArray oldTable, int newSize)
    {
        int oldCapacity = oldTable.length();
        int end = oldCapacity - 1;
        Object last = oldTable.get(end);
        if (this.size() < end && last == RESIZE_SENTINEL)
        {
            return;
        }
        if (oldCapacity >= MAXIMUM_CAPACITY)
        {
            throw new IllegalStateException("index is too large!");
        }
        ConcurrentUuidIntHashMap.ResizeContainer resizeContainer = null;
        boolean ownResize = false;
        if (last == null || last == RESIZE_SENTINEL)
        {
            synchronized (oldTable) // allocating a new array is too expensive to make this an atomic operation
            {
                if (oldTable.get(end) == null)
                {
                    oldTable.set(end, RESIZE_SENTINEL);
                    if (this.partitionedSize == null && newSize >= PARTITIONED_SIZE_THRESHOLD)
                    {
                        this.partitionedSize = new AtomicIntegerArray(SIZE_BUCKETS * 16);
                    }
                    resizeContainer = new ConcurrentUuidIntHashMap.ResizeContainer(new AtomicReferenceArray(newSize), oldTable.length() - 1);
                    oldTable.set(end, resizeContainer);
                    ownResize = true;
                }
            }
        }
        if (ownResize)
        {
            this.transfer(oldTable, resizeContainer);
            AtomicReferenceArray src = this.table;
            while (!TABLE_UPDATER.compareAndSet(this, oldTable, resizeContainer.nextArray))
            {
                // we're in a double resize situation; we'll have to go help until it's our turn to set the table
                if (src != oldTable)
                {
                    this.helpWithResize(src);
                }
            }
        }
        else
        {
            this.helpWithResize(oldTable);
        }
    }

    /*
     * Transfer all entries from src to dest tables
     */
    private void transfer(AtomicReferenceArray src, ConcurrentUuidIntHashMap.ResizeContainer resizeContainer)
    {
        AtomicReferenceArray dest = resizeContainer.nextArray;

        for (int j = 0; j < src.length() - 1; )
        {
            Object o = src.get(j);
            if (o == null)
            {
                if (src.compareAndSet(j, null, RESIZED))
                {
                    j++;
                }
            }
            else if (o == RESIZED || o == RESIZING)
            {
                j = (j & ~(ConcurrentUuidIntHashMap.ResizeContainer.QUEUE_INCREMENT - 1)) + ConcurrentUuidIntHashMap.ResizeContainer.QUEUE_INCREMENT;
                if (resizeContainer.resizers.get() == 1)
                {
                    break;
                }
            }
            else
            {
                ConcurrentUuidIntHashMap.Entry e = (ConcurrentUuidIntHashMap.Entry) o;
                if (src.compareAndSet(j, o, RESIZING))
                {
                    while (e != null)
                    {
                        this.unconditionalCopy(dest, e);
                        e = e.getNext();
                    }
                    src.set(j, RESIZED);
                    j++;
                }
            }
        }
        resizeContainer.decrementResizerAndNotify();
        resizeContainer.waitForAllResizers();
    }

    private void reverseTransfer(AtomicReferenceArray src, ConcurrentUuidIntHashMap.ResizeContainer resizeContainer)
    {
        AtomicReferenceArray dest = resizeContainer.nextArray;
        while (resizeContainer.getQueuePosition() > 0)
        {
            int start = resizeContainer.subtractAndGetQueuePosition();
            int end = start + ConcurrentUuidIntHashMap.ResizeContainer.QUEUE_INCREMENT;
            if (end > 0)
            {
                if (start < 0)
                {
                    start = 0;
                }
                for (int j = end - 1; j >= start; )
                {
                    Object o = src.get(j);
                    if (o == null)
                    {
                        if (src.compareAndSet(j, null, RESIZED))
                        {
                            j--;
                        }
                    }
                    else if (o == RESIZED || o == RESIZING)
                    {
                        resizeContainer.zeroOutQueuePosition();
                        return;
                    }
                    else
                    {
                        ConcurrentUuidIntHashMap.Entry e = (ConcurrentUuidIntHashMap.Entry) o;
                        if (src.compareAndSet(j, o, RESIZING))
                        {
                            while (e != null)
                            {
                                this.unconditionalCopy(dest, e);
                                e = e.getNext();
                            }
                            src.set(j, RESIZED);
                            j--;
                        }
                    }
                }
            }
        }
    }

    private void unconditionalCopy(AtomicReferenceArray dest, ConcurrentUuidIntHashMap.Entry toCopyEntry)
    {
        int hash = this.hash(toCopyEntry.getKey());
        AtomicReferenceArray currentArray = dest;
        while (true)
        {
            int length = currentArray.length();
            int index = ConcurrentUuidIntHashMap.indexFor(hash, length);
            Object o = currentArray.get(index);
            if (o == RESIZED || o == RESIZING)
            {
                currentArray = ((ConcurrentUuidIntHashMap.ResizeContainer) currentArray.get(length - 1)).nextArray;
            }
            else
            {
                ConcurrentUuidIntHashMap.Entry newEntry;
                if (o == null)
                {
                    if (toCopyEntry.getNext() == null)
                    {
                        newEntry = toCopyEntry; // no need to duplicate
                    }
                    else
                    {
                        newEntry = new ConcurrentUuidIntHashMap.Entry(toCopyEntry.getKey(), toCopyEntry.getValue());
                    }
                }
                else
                {
                    newEntry = new ConcurrentUuidIntHashMap.Entry(toCopyEntry.getKey(), toCopyEntry.getValue(), (ConcurrentUuidIntHashMap.Entry) o);
                }
                if (currentArray.compareAndSet(index, o, newEntry))
                {
                    return;
                }
            }
        }
    }

    public Integer getIfAbsentPut(UUID key, Function<? super UUID, ? extends Integer> factory)
    {
        return this.getIfAbsentPutWith(key, factory, key);
    }

    @Override
    public Integer getIfAbsentPut(UUID key, Function0<? extends Integer> factory)
    {
        int hash = this.hash(key);
        AtomicReferenceArray currentArray = this.table;
        Integer newValue = null;
        boolean createdValue = false;
        while (true)
        {
            int length = currentArray.length();
            int index = ConcurrentUuidIntHashMap.indexFor(hash, length);
            Object o = currentArray.get(index);
            if (o == RESIZED || o == RESIZING)
            {
                currentArray = this.helpWithResizeWhileCurrentIndex(currentArray, index);
            }
            else
            {
                ConcurrentUuidIntHashMap.Entry e = (ConcurrentUuidIntHashMap.Entry) o;
                while (e != null)
                {
                    Object candidate = e.getKey();
                    if (candidate.equals(key))
                    {
                        return e.getValue();
                    }
                    e = e.getNext();
                }
                if (!createdValue)
                {
                    createdValue = true;
                    newValue = factory.value();
                }
                ConcurrentUuidIntHashMap.Entry newEntry = new ConcurrentUuidIntHashMap.Entry(key, newValue, (ConcurrentUuidIntHashMap.Entry) o);
                if (currentArray.compareAndSet(index, o, newEntry))
                {
                    this.incrementSizeAndPossiblyResize(currentArray, length, o);
                    return newValue;
                }
            }
        }
    }

    @Override
    public Integer getIfAbsentPut(UUID key, Integer value)
    {
        int hash = this.hash(key);
        AtomicReferenceArray currentArray = this.table;
        while (true)
        {
            int length = currentArray.length();
            int index = ConcurrentUuidIntHashMap.indexFor(hash, length);
            Object o = currentArray.get(index);
            if (o == RESIZED || o == RESIZING)
            {
                currentArray = this.helpWithResizeWhileCurrentIndex(currentArray, index);
            }
            else
            {
                ConcurrentUuidIntHashMap.Entry e = (ConcurrentUuidIntHashMap.Entry) o;
                while (e != null)
                {
                    Object candidate = e.getKey();
                    if (candidate.equals(key))
                    {
                        return e.getValue();
                    }
                    e = e.getNext();
                }
                ConcurrentUuidIntHashMap.Entry newEntry = new ConcurrentUuidIntHashMap.Entry(key, value, (ConcurrentUuidIntHashMap.Entry) o);
                if (currentArray.compareAndSet(index, o, newEntry))
                {
                    this.incrementSizeAndPossiblyResize(currentArray, length, o);
                    return value;
                }
            }
        }
    }

    /**
     * It puts an object into the map based on the key. It uses a copy of the key converted by transformer.
     *
     * @param key            The "mutable" key, which has the same identity/hashcode as the inserted key, only during this call
     * @param keyTransformer If the record is absent, the transformer will transform the "mutable" key into an immutable copy of the key.
     *                       Note that the transformed key must have the same identity/hashcode as the original "mutable" key.
     * @param factory        It creates an object, if it is not present in the map already.
     */
    public <P1, P2> Integer putIfAbsentGetIfPresent(UUID key, Function2<? super UUID, ? super Integer, ? extends UUID> keyTransformer, Function3<P1, P2, ? super UUID, ? extends Integer> factory, P1 param1, P2 param2)
    {
        int hash = this.hash(key);
        AtomicReferenceArray currentArray = this.table;
        Integer newValue = null;
        boolean createdValue = false;
        while (true)
        {
            int length = currentArray.length();
            int index = ConcurrentUuidIntHashMap.indexFor(hash, length);
            Object o = currentArray.get(index);
            if (o == RESIZED || o == RESIZING)
            {
                currentArray = this.helpWithResizeWhileCurrentIndex(currentArray, index);
            }
            else
            {
                ConcurrentUuidIntHashMap.Entry e = (ConcurrentUuidIntHashMap.Entry) o;
                while (e != null)
                {
                    Object candidate = e.getKey();
                    if (candidate.equals(key))
                    {
                        return e.getValue();
                    }
                    e = e.getNext();
                }
                if (!createdValue)
                {
                    createdValue = true;
                    newValue = factory.value(param1, param2, key);
                    if (newValue == null)
                    {
                        return null; // null value means no mapping is required
                    }
                    key = keyTransformer.value(key, newValue);
                }
                ConcurrentUuidIntHashMap.Entry newEntry = new ConcurrentUuidIntHashMap.Entry(key, newValue, (ConcurrentUuidIntHashMap.Entry) o);
                if (currentArray.compareAndSet(index, o, newEntry))
                {
                    this.incrementSizeAndPossiblyResize(currentArray, length, o);
                    return null;
                }
            }
        }
    }

    @Override
    public boolean remove(Object key, Object value)
    {
        int hash = this.hash(key);
        AtomicReferenceArray currentArray = this.table;
        //noinspection LabeledStatement
        outer:
        while (true)
        {
            int length = currentArray.length();
            int index = ConcurrentUuidIntHashMap.indexFor(hash, length);
            Object o = currentArray.get(index);
            if (o == RESIZED || o == RESIZING)
            {
                currentArray = this.helpWithResizeWhileCurrentIndex(currentArray, index);
            }
            else
            {
                ConcurrentUuidIntHashMap.Entry e = (ConcurrentUuidIntHashMap.Entry) o;
                while (e != null)
                {
                    Object candidate = e.getKey();
                    if (candidate.equals(key) && this.nullSafeEquals(e.getValue(), value))
                    {
                        ConcurrentUuidIntHashMap.Entry replacement = this.createReplacementChainForRemoval((ConcurrentUuidIntHashMap.Entry) o, e);
                        if (currentArray.compareAndSet(index, o, replacement))
                        {
                            this.addToSize(-1);
                            return true;
                        }
                        //noinspection ContinueStatementWithLabel
                        continue outer;
                    }
                    e = e.getNext();
                }
                return false;
            }
        }
    }

    private void addToSize(int value)
    {
        if (this.partitionedSize != null)
        {
            if (this.incrementPartitionedSize(value))
            {
                return;
            }
        }
        this.incrementLocalSize(value);
    }

    private boolean incrementPartitionedSize(int value)
    {
        int h = (int) Thread.currentThread().getId();
        h ^= (h >>> 18) ^ (h >>> 12);
        h = (h ^ (h >>> 10)) & SIZE_BUCKETS;
        if (h != 0)
        {
            h = (h - 1) << 4;
            while (true)
            {
                int localSize = this.partitionedSize.get(h);
                if (this.partitionedSize.compareAndSet(h, localSize, localSize + value))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private void incrementLocalSize(int value)
    {
        while (true)
        {
            int localSize = this.size;
            if (SIZE_UPDATER.compareAndSet(this, localSize, localSize + value))
            {
                break;
            }
        }
    }

    @Override
    public int size()
    {
        int localSize = this.size;
        if (this.partitionedSize != null)
        {
            for (int i = 0; i < SIZE_BUCKETS; i++)
            {
                localSize += this.partitionedSize.get(i << 4);
            }
        }
        return localSize;
    }

    @Override
    public boolean isEmpty()
    {
        return this.size() == 0;
    }

    @Override
    public boolean containsKey(Object key)
    {
        return this.getEntry(key) != null;
    }

    @Override
    public boolean containsValue(Object value)
    {
        AtomicReferenceArray currentArray = this.table;
        ConcurrentUuidIntHashMap.ResizeContainer resizeContainer;
        do
        {
            resizeContainer = null;
            for (int i = 0; i < currentArray.length() - 1; i++)
            {
                Object o = currentArray.get(i);
                if (o == RESIZED || o == RESIZING)
                {
                    resizeContainer = (ConcurrentUuidIntHashMap.ResizeContainer) currentArray.get(currentArray.length() - 1);
                }
                else if (o != null)
                {
                    ConcurrentUuidIntHashMap.Entry e = (ConcurrentUuidIntHashMap.Entry) o;
                    while (e != null)
                    {
                        Object v = e.getValue();
                        if (this.nullSafeEquals(v, value))
                        {
                            return true;
                        }
                        e = e.getNext();
                    }
                }
            }
            if (resizeContainer != null)
            {
                if (resizeContainer.isNotDone())
                {
                    this.helpWithResize(currentArray);
                    resizeContainer.waitForAllResizers();
                }
                currentArray = resizeContainer.nextArray;
            }
        }
        while (resizeContainer != null);
        return false;
    }

    private boolean nullSafeEquals(Object v, Object value)
    {
        return v == value || v != null && v.equals(value);
    }

    @Override
    public Integer get(Object key)
    {
        ConcurrentUuidIntHashMap.Entry e = getEntry(key);
        if (e == null) {
            return null;
        }
        return e.value;
    }

    private ConcurrentUuidIntHashMap.Entry slowGetEntry(Object key, int hash, int index, AtomicReferenceArray currentArray)
    {
        while (true)
        {
            int length = currentArray.length();
            index = ConcurrentUuidIntHashMap.indexFor(hash, length);
            Object o = currentArray.get(index);
            if (o == RESIZED || o == RESIZING)
            {
                currentArray = this.helpWithResizeWhileCurrentIndex(currentArray, index);
            }
            else
            {
                ConcurrentUuidIntHashMap.Entry e = (ConcurrentUuidIntHashMap.Entry) o;
                while (e != null)
                {
                    Object candidate = e.getKey();
                    if (candidate.equals(key))
                    {
                        return e;
                    }
                    e = e.getNext();
                }
                return null;
            }
        }
    }

    public ConcurrentUuidIntHashMap.Entry getEntry(Object key)
    {
        int hash = this.hash(key);
        AtomicReferenceArray currentArray = this.table;
        int index = ConcurrentUuidIntHashMap.indexFor(hash, currentArray.length());
        Object o = currentArray.get(index);
        if (o == RESIZED || o == RESIZING)
        {
            ConcurrentUuidIntHashMap.Entry e = this.slowGetEntry(key, hash, index, currentArray);
            if (e == null) {
                return null;
            }
            return e;
        }
        for (ConcurrentUuidIntHashMap.Entry e = (ConcurrentUuidIntHashMap.Entry) o; e != null; e = e.getNext())
        {
            Object k;
            if ((k = e.getKey()) == key || key.equals(k))
            {
                return e;
            }
        }
        return null;
    }

    @Override
    public Integer put(UUID key, Integer value)
    {
        int hash = this.hash(key);
        AtomicReferenceArray currentArray = this.table;
        int length = currentArray.length();
        int index = ConcurrentUuidIntHashMap.indexFor(hash, length);
        Object o = currentArray.get(index);
        if (o == null)
        {
            ConcurrentUuidIntHashMap.Entry newEntry = new ConcurrentUuidIntHashMap.Entry(key, value, null);
            this.addToSize(1);
            if (currentArray.compareAndSet(index, null, newEntry))
            {
                return null;
            }
            this.addToSize(-1);
        }
        return this.slowPut(key, value, hash, currentArray);
    }

    private Integer slowPut(UUID key, Integer value, int hash, AtomicReferenceArray currentArray)
    {
        //noinspection LabeledStatement
        outer:
        while (true)
        {
            int length = currentArray.length();
            int index = ConcurrentUuidIntHashMap.indexFor(hash, length);
            Object o = currentArray.get(index);
            if (o == RESIZED || o == RESIZING)
            {
                currentArray = this.helpWithResizeWhileCurrentIndex(currentArray, index);
            }
            else
            {
                ConcurrentUuidIntHashMap.Entry e = (ConcurrentUuidIntHashMap.Entry) o;
                while (e != null)
                {
                    Object candidate = e.getKey();
                    if (candidate.equals(key))
                    {
                        Integer oldValue = e.getValue();
                        ConcurrentUuidIntHashMap.Entry newEntry = new ConcurrentUuidIntHashMap.Entry(e.getKey(), value, this.createReplacementChainForRemoval((ConcurrentUuidIntHashMap.Entry) o, e));
                        if (!currentArray.compareAndSet(index, o, newEntry))
                        {
                            //noinspection ContinueStatementWithLabel
                            continue outer;
                        }
                        return oldValue;
                    }
                    e = e.getNext();
                }
                ConcurrentUuidIntHashMap.Entry newEntry = new ConcurrentUuidIntHashMap.Entry(key, value, (ConcurrentUuidIntHashMap.Entry) o);
                if (currentArray.compareAndSet(index, o, newEntry))
                {
                    this.incrementSizeAndPossiblyResize(currentArray, length, o);
                    return null;
                }
            }
        }
    }

    public void putAllInParallel(Map<? extends UUID, ? extends Integer> map, int chunks, Executor executor)
    {
        if (this.size() == 0)
        {
            int threshold = map.size();
            threshold += threshold >> 1; // threshold = length * 0.75

            int capacity = 1;
            while (capacity < threshold)
            {
                capacity <<= 1;
            }
            this.resize(this.table, capacity + 1);
        }
        if (map instanceof ConcurrentUuidIntHashMap && chunks > 1 && map.size() > 50000)
        {
            ConcurrentUuidIntHashMap incoming = (ConcurrentUuidIntHashMap) map;
            AtomicReferenceArray currentArray = incoming.table;
            FutureTask<?>[] futures = new FutureTask<?>[chunks];
            int chunkSize = currentArray.length() / chunks;
            if (currentArray.length() % chunks != 0)
            {
                chunkSize++;
            }
            for (int i = 0; i < chunks; i++)
            {
                int start = i * chunkSize;
                int end = Math.min((i + 1) * chunkSize, currentArray.length());
                futures[i] = new FutureTask(() -> this.sequentialPutAll(currentArray, start, end), null);
                executor.execute(futures[i]);
            }
            for (int i = 0; i < chunks; i++)
            {
                try
                {
                    futures[i].get();
                }
                catch (Exception e)
                {
                    throw new RuntimeException("parallelForEachKeyValue failed", e);
                }
            }
        }
        else
        {
            this.putAll(map);
        }
    }

    private void sequentialPutAll(AtomicReferenceArray currentArray, int start, int end)
    {
        for (int i = start; i < end; i++)
        {
            Object o = currentArray.get(i);
            if (o == RESIZED || o == RESIZING)
            {
                throw new ConcurrentModificationException("can't iterate while resizing!");
            }
            ConcurrentUuidIntHashMap.Entry e = (ConcurrentUuidIntHashMap.Entry) o;
            while (e != null)
            {
                UUID key = e.getKey();
                Integer value = e.getValue();
                this.put(key, value);
                e = e.getNext();
            }
        }
    }

    @Override
    public void putAll(Map<? extends UUID, ? extends Integer> map)
    {
        MapIterate.forEachKeyValue(map, this::put);
    }

    @Override
    public void clear()
    {
        AtomicReferenceArray currentArray = this.table;
        ConcurrentUuidIntHashMap.ResizeContainer resizeContainer;
        do
        {
            resizeContainer = null;
            for (int i = 0; i < currentArray.length() - 1; i++)
            {
                Object o = currentArray.get(i);
                if (o == RESIZED || o == RESIZING)
                {
                    resizeContainer = (ConcurrentUuidIntHashMap.ResizeContainer) currentArray.get(currentArray.length() - 1);
                }
                else if (o != null)
                {
                    ConcurrentUuidIntHashMap.Entry e = (ConcurrentUuidIntHashMap.Entry) o;
                    if (currentArray.compareAndSet(i, o, null))
                    {
                        int removedEntries = 0;
                        while (e != null)
                        {
                            removedEntries++;
                            e = e.getNext();
                        }
                        this.addToSize(-removedEntries);
                    }
                }
            }
            if (resizeContainer != null)
            {
                if (resizeContainer.isNotDone())
                {
                    this.helpWithResize(currentArray);
                    resizeContainer.waitForAllResizers();
                }
                currentArray = resizeContainer.nextArray;
            }
        }
        while (resizeContainer != null);
    }

    @Override
    public Set<UUID> keySet()
    {
        return new ConcurrentUuidIntHashMap.KeySet();
    }

    @Override
    public Collection<Integer> values()
    {
        return new ConcurrentUuidIntHashMap.Values();
    }

    @Override
    public Set<Map.Entry<UUID, Integer>> entrySet()
    {
        return new ConcurrentUuidIntHashMap.EntrySet();
    }

    @Override
    public boolean replace(UUID key, Integer oldValue, Integer newValue)
    {
        int hash = this.hash(key);
        AtomicReferenceArray currentArray = this.table;
        int length = currentArray.length();
        int index = ConcurrentUuidIntHashMap.indexFor(hash, length);
        Object o = currentArray.get(index);
        if (o == RESIZED || o == RESIZING)
        {
            return this.slowReplace(key, oldValue, newValue, hash, currentArray);
        }
        ConcurrentUuidIntHashMap.Entry e = (ConcurrentUuidIntHashMap.Entry) o;
        while (e != null)
        {
            Object candidate = e.getKey();
            if (candidate == key || candidate.equals(key))
            {
                if (oldValue == e.getValue() || (oldValue != null && oldValue.equals(e.getValue())))
                {
                    ConcurrentUuidIntHashMap.Entry replacement = this.createReplacementChainForRemoval((ConcurrentUuidIntHashMap.Entry) o, e);
                    ConcurrentUuidIntHashMap.Entry newEntry = new ConcurrentUuidIntHashMap.Entry(key, newValue, replacement);
                    return currentArray.compareAndSet(index, o, newEntry) || this.slowReplace(key, oldValue, newValue, hash, currentArray);
                }
                return false;
            }
            e = e.getNext();
        }
        return false;
    }

    private boolean slowReplace(UUID key, Integer oldValue, Integer newValue, int hash, AtomicReferenceArray currentArray)
    {
        //noinspection LabeledStatement
        outer:
        while (true)
        {
            int length = currentArray.length();
            int index = ConcurrentUuidIntHashMap.indexFor(hash, length);
            Object o = currentArray.get(index);
            if (o == RESIZED || o == RESIZING)
            {
                currentArray = this.helpWithResizeWhileCurrentIndex(currentArray, index);
            }
            else
            {
                ConcurrentUuidIntHashMap.Entry e = (ConcurrentUuidIntHashMap.Entry) o;
                while (e != null)
                {
                    Object candidate = e.getKey();
                    if (candidate == key || candidate.equals(key))
                    {
                        if (oldValue == e.getValue() || (oldValue != null && oldValue.equals(e.getValue())))
                        {
                            ConcurrentUuidIntHashMap.Entry replacement = this.createReplacementChainForRemoval((ConcurrentUuidIntHashMap.Entry) o, e);
                            ConcurrentUuidIntHashMap.Entry newEntry = new ConcurrentUuidIntHashMap.Entry(key, newValue, replacement);
                            if (currentArray.compareAndSet(index, o, newEntry))
                            {
                                return true;
                            }
                            //noinspection ContinueStatementWithLabel
                            continue outer;
                        }
                        return false;
                    }
                    e = e.getNext();
                }
                return false;
            }
        }
    }

    @Override
    public Integer replace(UUID key, Integer value)
    {
        int hash = this.hash(key);
        AtomicReferenceArray currentArray = this.table;
        int length = currentArray.length();
        int index = ConcurrentUuidIntHashMap.indexFor(hash, length);
        Object o = currentArray.get(index);
        if (o == null)
        {
            return null;
        }
        return this.slowReplace(key, value, hash, currentArray);
    }

    private Integer slowReplace(UUID key, Integer value, int hash, AtomicReferenceArray currentArray)
    {
        //noinspection LabeledStatement
        outer:
        while (true)
        {
            int length = currentArray.length();
            int index = ConcurrentUuidIntHashMap.indexFor(hash, length);
            Object o = currentArray.get(index);
            if (o == RESIZED || o == RESIZING)
            {
                currentArray = this.helpWithResizeWhileCurrentIndex(currentArray, index);
            }
            else
            {
                ConcurrentUuidIntHashMap.Entry e = (ConcurrentUuidIntHashMap.Entry) o;
                while (e != null)
                {
                    Object candidate = e.getKey();
                    if (candidate.equals(key))
                    {
                        Integer oldValue = e.getValue();
                        ConcurrentUuidIntHashMap.Entry newEntry = new ConcurrentUuidIntHashMap.Entry(e.getKey(), value, this.createReplacementChainForRemoval((ConcurrentUuidIntHashMap.Entry) o, e));
                        if (!currentArray.compareAndSet(index, o, newEntry))
                        {
                            //noinspection ContinueStatementWithLabel
                            continue outer;
                        }
                        return oldValue;
                    }
                    e = e.getNext();
                }
                return null;
            }
        }
    }

    @Override
    public Integer remove(Object key)
    {
        int hash = this.hash(key);
        AtomicReferenceArray currentArray = this.table;
        int length = currentArray.length();
        int index = ConcurrentUuidIntHashMap.indexFor(hash, length);
        Object o = currentArray.get(index);
        if (o == RESIZED || o == RESIZING)
        {
            return this.slowRemove(key, hash, currentArray);
        }
        ConcurrentUuidIntHashMap.Entry e = (ConcurrentUuidIntHashMap.Entry) o;
        while (e != null)
        {
            Object candidate = e.getKey();
            if (candidate.equals(key))
            {
                ConcurrentUuidIntHashMap.Entry replacement = this.createReplacementChainForRemoval((ConcurrentUuidIntHashMap.Entry) o, e);
                if (currentArray.compareAndSet(index, o, replacement))
                {
                    this.addToSize(-1);
                    return e.getValue();
                }
                return this.slowRemove(key, hash, currentArray);
            }
            e = e.getNext();
        }
        return null;
    }

    private Integer slowRemove(Object key, int hash, AtomicReferenceArray currentArray)
    {
        //noinspection LabeledStatement
        outer:
        while (true)
        {
            int length = currentArray.length();
            int index = ConcurrentUuidIntHashMap.indexFor(hash, length);
            Object o = currentArray.get(index);
            if (o == RESIZED || o == RESIZING)
            {
                currentArray = this.helpWithResizeWhileCurrentIndex(currentArray, index);
            }
            else
            {
                ConcurrentUuidIntHashMap.Entry e = (ConcurrentUuidIntHashMap.Entry) o;
                while (e != null)
                {
                    Object candidate = e.getKey();
                    if (candidate.equals(key))
                    {
                        ConcurrentUuidIntHashMap.Entry replacement = this.createReplacementChainForRemoval((ConcurrentUuidIntHashMap.Entry) o, e);
                        if (currentArray.compareAndSet(index, o, replacement))
                        {
                            this.addToSize(-1);
                            return e.getValue();
                        }
                        //noinspection ContinueStatementWithLabel
                        continue outer;
                    }
                    e = e.getNext();
                }
                return null;
            }
        }
    }

    private ConcurrentUuidIntHashMap.Entry createReplacementChainForRemoval(ConcurrentUuidIntHashMap.Entry original, ConcurrentUuidIntHashMap.Entry toRemove)
    {
        if (original == toRemove)
        {
            return original.getNext();
        }
        ConcurrentUuidIntHashMap.Entry replacement = null;
        ConcurrentUuidIntHashMap.Entry e = original;
        while (e != null)
        {
            if (e != toRemove)
            {
                replacement = new ConcurrentUuidIntHashMap.Entry(e.getKey(), e.getValue(), replacement);
            }
            e = e.getNext();
        }
        return replacement;
    }

    public void parallelForEachKeyValue(List<Procedure2<UUID, Integer>> blocks, Executor executor)
    {
        AtomicReferenceArray currentArray = this.table;
        int chunks = blocks.size();
        if (chunks > 1)
        {
            FutureTask<?>[] futures = new FutureTask<?>[chunks];
            int chunkSize = currentArray.length() / chunks;
            if (currentArray.length() % chunks != 0)
            {
                chunkSize++;
            }
            for (int i = 0; i < chunks; i++)
            {
                int start = i * chunkSize;
                int end = Math.min((i + 1) * chunkSize, currentArray.length());
                Procedure2<UUID, Integer> block = blocks.get(i);
                futures[i] = new FutureTask(() -> this.sequentialForEachKeyValue(block, currentArray, start, end), null);
                executor.execute(futures[i]);
            }
            for (int i = 0; i < chunks; i++)
            {
                try
                {
                    futures[i].get();
                }
                catch (Exception e)
                {
                    throw new RuntimeException("parallelForEachKeyValue failed", e);
                }
            }
        }
        else
        {
            this.sequentialForEachKeyValue(blocks.get(0), currentArray, 0, currentArray.length());
        }
    }

    private void sequentialForEachKeyValue(Procedure2<UUID, Integer> block, AtomicReferenceArray currentArray, int start, int end)
    {
        for (int i = start; i < end; i++)
        {
            Object o = currentArray.get(i);
            if (o == RESIZED || o == RESIZING)
            {
                throw new ConcurrentModificationException("can't iterate while resizing!");
            }
            ConcurrentUuidIntHashMap.Entry e = (ConcurrentUuidIntHashMap.Entry) o;
            while (e != null)
            {
                UUID key = e.getKey();
                Integer value = e.getValue();
                block.value(key, value);
                e = e.getNext();
            }
        }
    }

    public void parallelForEachValue(List<Procedure<Integer>> blocks, Executor executor)
    {
        AtomicReferenceArray currentArray = this.table;
        int chunks = blocks.size();
        if (chunks > 1)
        {
            FutureTask<?>[] futures = new FutureTask<?>[chunks];
            int chunkSize = currentArray.length() / chunks;
            if (currentArray.length() % chunks != 0)
            {
                chunkSize++;
            }
            for (int i = 0; i < chunks; i++)
            {
                int start = i * chunkSize;
                int end = Math.min((i + 1) * chunkSize, currentArray.length() - 1);
                Procedure<Integer> block = blocks.get(i);
                futures[i] = new FutureTask(() -> this.sequentialForEachValue(block, currentArray, start, end), null);
                executor.execute(futures[i]);
            }
            for (int i = 0; i < chunks; i++)
            {
                try
                {
                    futures[i].get();
                }
                catch (Exception e)
                {
                    throw new RuntimeException("parallelForEachKeyValue failed", e);
                }
            }
        }
        else
        {
            this.sequentialForEachValue(blocks.get(0), currentArray, 0, currentArray.length());
        }
    }

    private void sequentialForEachValue(Procedure<Integer> block, AtomicReferenceArray currentArray, int start, int end)
    {
        for (int i = start; i < end; i++)
        {
            Object o = currentArray.get(i);
            if (o == RESIZED || o == RESIZING)
            {
                throw new ConcurrentModificationException("can't iterate while resizing!");
            }
            ConcurrentUuidIntHashMap.Entry e = (ConcurrentUuidIntHashMap.Entry) o;
            while (e != null)
            {
                Object value = e.getValue();
                block.value((Integer) value);
                e = e.getNext();
            }
        }
    }

    @Override
    public int hashCode()
    {
        int h = 0;
        AtomicReferenceArray currentArray = this.table;
        for (int i = 0; i < currentArray.length() - 1; i++)
        {
            Object o = currentArray.get(i);
            if (o == RESIZED || o == RESIZING)
            {
                throw new ConcurrentModificationException("can't compute hashcode while resizing!");
            }
            ConcurrentUuidIntHashMap.Entry e = (ConcurrentUuidIntHashMap.Entry) o;
            while (e != null)
            {
                Object key = e.getKey();
                Object value = e.getValue();
                h += (key == null ? 0 : key.hashCode()) ^ (value == null ? 0 : value.hashCode());
                e = e.getNext();
            }
        }
        return h;
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        }

        if (!(o instanceof Map))
        {
            return false;
        }
        Map<UUID, Integer> m = (Map<UUID, Integer>) o;
        if (m.size() != this.size())
        {
            return false;
        }

        Iterator<Map.Entry<UUID, Integer>> i = this.entrySet().iterator();
        while (i.hasNext())
        {
            Map.Entry<UUID, Integer> e = i.next();
            UUID key = e.getKey();
            Integer value = e.getValue();
            if (value == null)
            {
                if (!(m.get(key) == null && m.containsKey(key)))
                {
                    return false;
                }
            }
            else
            {
                if (!value.equals(m.get(key)))
                {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public String toString()
    {
        if (this.isEmpty())
        {
            return "{}";
        }
        Iterator<Map.Entry<UUID, Integer>> iterator = this.entrySet().iterator();

        StringBuilder sb = new StringBuilder();
        sb.append('{');
        while (true)
        {
            Map.Entry<UUID, Integer> e = iterator.next();
            UUID key = e.getKey();
            Integer value = e.getValue();
            sb.append(key);
            sb.append('=');
            sb.append(value);
            if (!iterator.hasNext())
            {
                return sb.append('}').toString();
            }
            sb.append(", ");
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
    {
        int size = in.readInt();
        int capacity = 1;
        while (capacity < size)
        {
            capacity <<= 1;
        }
        this.table = new AtomicReferenceArray(capacity + 1);
        for (int i = 0; i < size; i++)
        {
            this.put((UUID) in.readObject(), (Integer) in.readObject());
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException
    {
        int size = this.size();
        out.writeInt(size);
        int count = 0;
        for (int i = 0; i < this.table.length() - 1; i++)
        {
            Object o = this.table.get(i);
            if (o == RESIZED || o == RESIZING)
            {
                throw new ConcurrentModificationException("Can't serialize while resizing!");
            }
            ConcurrentUuidIntHashMap.Entry e = (ConcurrentUuidIntHashMap.Entry) o;
            while (e != null)
            {
                count++;
                out.writeObject(e.getKey());
                out.writeObject(e.getValue());
                e = e.getNext();
            }
        }
        if (count != size)
        {
            throw new ConcurrentModificationException("Map changed while serializing");
        }
    }

    private static final class IteratorState
    {
        private AtomicReferenceArray currentTable;
        private int start;
        private int end;

        private IteratorState(AtomicReferenceArray currentTable)
        {
            this.currentTable = currentTable;
            this.end = this.currentTable.length() - 1;
        }

        private IteratorState(AtomicReferenceArray currentTable, int start, int end)
        {
            this.currentTable = currentTable;
            this.start = start;
            this.end = end;
        }
    }

    private abstract class HashIterator<E> implements Iterator<E>
    {
        private List<ConcurrentUuidIntHashMap.IteratorState> todo;
        private ConcurrentUuidIntHashMap.IteratorState currentState;
        private ConcurrentUuidIntHashMap.Entry next;
        private int index;
        private ConcurrentUuidIntHashMap.Entry current;

        protected HashIterator()
        {
            this.currentState = new ConcurrentUuidIntHashMap.IteratorState(ConcurrentUuidIntHashMap.this.table);
            this.findNext();
        }

        private void findNext()
        {
            while (this.index < this.currentState.end)
            {
                Object o = this.currentState.currentTable.get(this.index);
                if (o == RESIZED || o == RESIZING)
                {
                    AtomicReferenceArray nextArray = ConcurrentUuidIntHashMap.this.helpWithResizeWhileCurrentIndex(this.currentState.currentTable, this.index);
                    int endResized = this.index + 1;
                    while (endResized < this.currentState.end)
                    {
                        if (this.currentState.currentTable.get(endResized) != RESIZED)
                        {
                            break;
                        }
                        endResized++;
                    }
                    if (this.todo == null)
                    {
                        this.todo = new FastList<>(4);
                    }
                    if (endResized < this.currentState.end)
                    {
                        this.todo.add(new ConcurrentUuidIntHashMap.IteratorState(this.currentState.currentTable, endResized, this.currentState.end));
                    }
                    int powerTwoLength = this.currentState.currentTable.length() - 1;
                    this.todo.add(new ConcurrentUuidIntHashMap.IteratorState(nextArray, this.index + powerTwoLength, endResized + powerTwoLength));
                    this.currentState.currentTable = nextArray;
                    this.currentState.end = endResized;
                    this.currentState.start = this.index;
                }
                else if (o != null)
                {
                    this.next = (ConcurrentUuidIntHashMap.Entry) o;
                    this.index++;
                    break;
                }
                else
                {
                    this.index++;
                }
            }
            if (this.next == null && this.index == this.currentState.end && this.todo != null && !this.todo.isEmpty())
            {
                this.currentState = this.todo.remove(this.todo.size() - 1);
                this.index = this.currentState.start;
                this.findNext();
            }
        }

        @Override
        public final boolean hasNext()
        {
            return this.next != null;
        }

        final ConcurrentUuidIntHashMap.Entry nextEntry()
        {
            ConcurrentUuidIntHashMap.Entry e = this.next;
            if (e == null)
            {
                throw new NoSuchElementException();
            }

            if ((this.next = e.getNext()) == null)
            {
                this.findNext();
            }
            this.current = e;
            return e;
        }

        protected void removeByKey()
        {
            if (this.current == null)
            {
                throw new IllegalStateException();
            }
            UUID key = this.current.getKey();
            this.current = null;
            ConcurrentUuidIntHashMap.this.remove(key);
        }

        protected boolean removeByKeyValue()
        {
            if (this.current == null)
            {
                throw new IllegalStateException();
            }
            UUID key = this.current.getKey();
            Integer val = this.current.value;
            this.current = null;
            return ConcurrentUuidIntHashMap.this.remove(key, val);
        }
    }

    private final class ValueIterator extends ConcurrentUuidIntHashMap.HashIterator<Integer>
    {
        @Override
        public void remove()
        {
            this.removeByKeyValue();
        }

        @Override
        public Integer next()
        {
            return this.nextEntry().value;
        }
    }

    private final class KeyIterator extends ConcurrentUuidIntHashMap.HashIterator<UUID>
    {
        @Override
        public UUID next()
        {
            return this.nextEntry().getKey();
        }

        @Override
        public void remove()
        {
            this.removeByKey();
        }
    }

    private final class EntryIterator extends ConcurrentUuidIntHashMap.HashIterator<Map.Entry<UUID, Integer>>
    {
        @Override
        public Map.Entry<UUID, Integer> next()
        {
            return this.nextEntry();
        }

        @Override
        public void remove()
        {
            this.removeByKeyValue();
        }
    }

    private final class KeySet extends AbstractSet<UUID>
    {
        @Override
        public Iterator<UUID> iterator()
        {
            return new ConcurrentUuidIntHashMap.KeyIterator();
        }

        @Override
        public int size()
        {
            return ConcurrentUuidIntHashMap.this.size();
        }

        @Override
        public boolean contains(Object o)
        {
            return ConcurrentUuidIntHashMap.this.containsKey(o);
        }

        @Override
        public boolean remove(Object o)
        {
            return ConcurrentUuidIntHashMap.this.remove(o) != null;
        }

        @Override
        public void clear()
        {
            ConcurrentUuidIntHashMap.this.clear();
        }
    }

    private final class Values extends AbstractCollection<Integer>
    {
        @Override
        public Iterator<Integer> iterator()
        {
            return new ConcurrentUuidIntHashMap.ValueIterator();
        }

        @Override
        public boolean removeAll(Collection<?> col)
        {
            Objects.requireNonNull(col);
            boolean removed = false;
            ConcurrentUuidIntHashMap.ValueIterator itr = new ConcurrentUuidIntHashMap.ValueIterator();
            while (itr.hasNext())
            {
                if (col.contains(itr.next()))
                {
                    removed |= itr.removeByKeyValue();
                }
            }
            return removed;
        }

        @Override
        public boolean removeIf(Predicate<? super Integer> filter)
        {
            Objects.requireNonNull(filter);
            boolean removed = false;
            ConcurrentUuidIntHashMap.ValueIterator itr = new ConcurrentUuidIntHashMap.ValueIterator();
            while (itr.hasNext())
            {
                if (filter.test(itr.next()))
                {
                    removed |= itr.removeByKeyValue();
                }
            }
            return removed;
        }

        @Override
        public int size()
        {
            return ConcurrentUuidIntHashMap.this.size();
        }

        @Override
        public boolean contains(Object o)
        {
            return ConcurrentUuidIntHashMap.this.containsValue(o);
        }

        @Override
        public void clear()
        {
            ConcurrentUuidIntHashMap.this.clear();
        }
    }

    private final class EntrySet extends AbstractSet<Map.Entry<UUID, Integer>>
    {
        @Override
        public Iterator<Map.Entry<UUID, Integer>> iterator()
        {
            return new ConcurrentUuidIntHashMap.EntryIterator();
        }

        @Override
        public boolean removeAll(Collection<?> col)
        {
            Objects.requireNonNull(col);
            boolean removed = false;

            if (this.size() > col.size())
            {
                for (Iterator<?> itr = col.iterator(); itr.hasNext(); )
                {
                    removed |= this.remove(itr.next());
                }
            }
            else
            {
                for (ConcurrentUuidIntHashMap.EntryIterator itr = new ConcurrentUuidIntHashMap.EntryIterator(); itr.hasNext(); )
                {
                    if (col.contains(itr.next()))
                    {
                        removed |= itr.removeByKeyValue();
                    }
                }
            }
            return removed;
        }

        @Override
        public boolean removeIf(Predicate<? super Map.Entry<UUID, Integer>> filter)
        {
            Objects.requireNonNull(filter);
            boolean removed = false;
            ConcurrentUuidIntHashMap.EntryIterator itr = new ConcurrentUuidIntHashMap.EntryIterator();
            while (itr.hasNext())
            {
                if (filter.test(itr.next()))
                {
                    removed |= itr.removeByKeyValue();
                }
            }
            return removed;
        }

        @Override
        public boolean contains(Object o)
        {
            if (!(o instanceof Map.Entry<?, ?>))
            {
                return false;
            }
            Map.Entry e = (Map.Entry) o;
            ConcurrentUuidIntHashMap.Entry candidate = ConcurrentUuidIntHashMap.this.getEntry(e.getKey());
            return e.equals(candidate);
        }

        @Override
        public boolean remove(Object o)
        {
            if (!(o instanceof Map.Entry<?, ?>))
            {
                return false;
            }
            Map.Entry e = (Map.Entry) o;
            return ConcurrentUuidIntHashMap.this.remove(e.getKey(), e.getValue());
        }

        @Override
        public int size()
        {
            return ConcurrentUuidIntHashMap.this.size();
        }

        @Override
        public void clear()
        {
            ConcurrentUuidIntHashMap.this.clear();
        }
    }

    public static final class Entry implements Map.Entry<UUID, Integer>
    {
        private final long key_msb;
        private final long key_lsb;
        private final int value;
        private final ConcurrentUuidIntHashMap.Entry next;

        private Entry(UUID key, Integer value)
        {
            this.key_msb = key.getMostSignificantBits();
            this.key_lsb = key.getLeastSignificantBits();
            this.value = value;
            this.next = null;
        }

        private Entry(UUID key, Integer value, ConcurrentUuidIntHashMap.Entry next)
        {
            this.key_msb = key.getMostSignificantBits();
            this.key_lsb = key.getLeastSignificantBits();
            this.value = value;
            this.next = next;
        }

        @Override
        public UUID getKey()
        {
            return new UUID(this.key_msb, this.key_lsb);
        }

        @Override
        public Integer getValue()
        {
            return this.value;
        }

        @Override
        public Integer setValue(Integer value)
        {
            throw new RuntimeException("not implemented");
        }

        public ConcurrentUuidIntHashMap.Entry getNext()
        {
            return this.next;
        }

        @Override
        public boolean equals(Object o)
        {
            if (!(o instanceof Map.Entry<?, ?>))
            {
                return false;
            }
            if (o instanceof ConcurrentUuidIntHashMap.Entry) {
                ConcurrentUuidIntHashMap.Entry entry = (Entry) o;
                // avoid object creation to test equality...
                return this.value == entry.value &&
                        this.key_lsb == entry.key_lsb &&
                        this.key_msb == entry.key_msb;
            }
            Map.Entry e = (Map.Entry) o;
            UUID k1 = this.getKey();
            Object k2 = e.getKey();
            if (k1 == k2 || k1 != null && k1.equals(k2))
            {
                Integer v1 = this.value;
                Object v2 = e.getValue();
                if (v1 == v2 || v1 != null && v1.equals(v2))
                {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int hashCode()
        {
            long hilo = this.key_msb ^ this.key_lsb;
            return (((int)(hilo >> 32)) ^ (int) hilo) ^ Integer.hashCode(this.value);
        }

        @Override
        public String toString()
        {
            return this.getKey() + "=" + this.value;
        }
    }

    private static final class ResizeContainer
    {
        private static final int QUEUE_INCREMENT = Math.min(1 << 10, Integer.highestOneBit(Runtime.getRuntime().availableProcessors()) << 4);
        private final AtomicInteger resizers = new AtomicInteger(1);
        private final AtomicReferenceArray nextArray;
        private final AtomicInteger queuePosition;

        private ResizeContainer(AtomicReferenceArray nextArray, int oldSize)
        {
            this.nextArray = nextArray;
            this.queuePosition = new AtomicInteger(oldSize);
        }

        public void incrementResizer()
        {
            this.resizers.incrementAndGet();
        }

        public void decrementResizerAndNotify()
        {
            int remaining = this.resizers.decrementAndGet();
            if (remaining == 0)
            {
                synchronized (this)
                {
                    this.notifyAll();
                }
            }
        }

        public int getQueuePosition()
        {
            return this.queuePosition.get();
        }

        public int subtractAndGetQueuePosition()
        {
            return this.queuePosition.addAndGet(-QUEUE_INCREMENT);
        }

        public void waitForAllResizers()
        {
            if (this.resizers.get() > 0)
            {
                for (int i = 0; i < 16; i++)
                {
                    if (this.resizers.get() == 0)
                    {
                        break;
                    }
                }
                for (int i = 0; i < 16; i++)
                {
                    if (this.resizers.get() == 0)
                    {
                        break;
                    }
                    Thread.yield();
                }
            }
            if (this.resizers.get() > 0)
            {
                synchronized (this)
                {
                    while (this.resizers.get() > 0)
                    {
                        try
                        {
                            this.wait();
                        }
                        catch (InterruptedException e)
                        {
                            // ignore
                        }
                    }
                }
            }
        }

        public boolean isNotDone()
        {
            return this.resizers.get() > 0;
        }

        public void zeroOutQueuePosition()
        {
            this.queuePosition.set(0);
        }
    }

    public static ConcurrentUuidIntHashMap newMap(Map<UUID, Integer> map)
    {
        ConcurrentUuidIntHashMap result = new ConcurrentUuidIntHashMap(map.size());
        result.putAll(map);
        return result;
    }

    @Override
    public ConcurrentUuidIntHashMap withKeyValue(UUID key, Integer value)
    {
        return (ConcurrentUuidIntHashMap) super.withKeyValue(key, value);
    }

    @Override
    public ConcurrentUuidIntHashMap withMap(Map<? extends  UUID, ? extends Integer> map)
    {
        return (ConcurrentUuidIntHashMap) super.withMap(map);
    }

    @Override
    public ConcurrentUuidIntHashMap withAllKeyValues(Iterable<? extends Pair<? extends UUID, ? extends Integer>> keyValues)
    {
        return (ConcurrentUuidIntHashMap) super.withAllKeyValues(keyValues);
    }

    @Override
    public ConcurrentUuidIntHashMap withAllKeyValueArguments(Pair<? extends UUID, ? extends Integer>... keyValues)
    {
        return (ConcurrentUuidIntHashMap) super.withAllKeyValueArguments(keyValues);
    }

    @Override
    public ConcurrentUuidIntHashMap withoutKey(UUID key)
    {
        return (ConcurrentUuidIntHashMap) super.withoutKey(key);
    }

    @Override
    public ConcurrentUuidIntHashMap withoutAllKeys(Iterable<? extends UUID> keys)
    {
        return (ConcurrentUuidIntHashMap) super.withoutAllKeys(keys);
    }

    @Override
    public MutableMap<UUID, Integer> clone()
    {
        return ConcurrentUuidIntHashMap.newMap(this);
    }

    @Override
    public ConcurrentUuidIntHashMap newEmpty(int capacity)
    {
        return ConcurrentUuidIntHashMap.newMap();
    }

    @Override
    public boolean notEmpty()
    {
        return !this.isEmpty();
    }

    @Override
    public void forEachWithIndex(ObjectIntProcedure<? super Integer> objectIntProcedure)
    {
        Iterate.forEachWithIndex(this.values(), objectIntProcedure);
    }

    @Override
    public Iterator<Integer> iterator()
    {
        return this.values().iterator();
    }

    @Override
    public MutableMap<UUID, Integer> newEmpty()
    {
        return ConcurrentUuidIntHashMap.newMap();
    }

    @Override
    public ConcurrentMutableMap<UUID, Integer> tap(Procedure<? super Integer> procedure)
    {
        this.each(procedure);
        return this;
    }

    @Override
    public void forEachValue(Procedure<? super Integer> procedure)
    {
        IterableIterate.forEach(this.values(), procedure);
    }

    @Override
    public void forEachKey(Procedure<? super UUID> procedure)
    {
        IterableIterate.forEach(this.keySet(), procedure);
    }

    @Override
    public void forEachKeyValue(Procedure2<? super UUID, ? super Integer> procedure)
    {
        IterableIterate.forEach(this.entrySet(), new MapEntryToProcedure2<>(procedure));
    }

    @Override
    public <E> MutableMap<UUID, Integer> collectKeysAndValues(
            Iterable<E> iterable,
            Function<? super E, ? extends UUID> keyFunction,
            Function<? super E, ? extends Integer> valueFunction)
    {
        Iterate.addToMap(iterable, keyFunction, valueFunction, this);
        return this;
    }

    @Override
    public Integer removeKey(UUID key)
    {
        return this.remove(key);
    }

    @Override
    public <P> Integer getIfAbsentPutWith(UUID key, Function<? super P, ? extends Integer> function, P parameter)
    {
        int hash = this.hash(key);
        AtomicReferenceArray currentArray = this.table;
        Integer newValue = null;
        boolean createdValue = false;
        while (true)
        {
            int length = currentArray.length();
            int index = ConcurrentUuidIntHashMap.indexFor(hash, length);
            Object o = currentArray.get(index);
            if (o == RESIZED || o == RESIZING)
            {
                currentArray = this.helpWithResizeWhileCurrentIndex(currentArray, index);
            }
            else
            {
                ConcurrentUuidIntHashMap.Entry e = (ConcurrentUuidIntHashMap.Entry) o;
                while (e != null)
                {
                    Object candidate = e.getKey();
                    if (candidate.equals(key))
                    {
                        return e.getValue();
                    }
                    e = e.getNext();
                }
                if (!createdValue)
                {
                    createdValue = true;
                    newValue = function.valueOf(parameter);
                }
                ConcurrentUuidIntHashMap.Entry newEntry = new ConcurrentUuidIntHashMap.Entry(key, newValue, (ConcurrentUuidIntHashMap.Entry) o);
                if (currentArray.compareAndSet(index, o, newEntry))
                {
                    this.incrementSizeAndPossiblyResize(currentArray, length, o);
                    return newValue;
                }
            }
        }
    }

    @Override
    public Integer getIfAbsent(UUID key, Function0<? extends Integer> function)
    {
        Integer result = this.get(key);
        if (result == null)
        {
            return function.value();
        }
        return result;
    }

    @Override
    public <P> Integer getIfAbsentWith(
            UUID key,
            Function<? super P, ? extends Integer> function,
            P parameter)
    {
        Integer result = this.get(key);
        if (result == null)
        {
            return function.valueOf(parameter);
        }
        return result;
    }

    @Override
    public <A> A ifPresentApply(UUID key, Function<? super Integer, ? extends A> function)
    {
        Integer result = this.get(key);
        return result == null ? null : function.valueOf(result);
    }

    @Override
    public <P> void forEachWith(Procedure2<? super Integer, ? super P> procedure, P parameter)
    {
        Iterate.forEachWith(this.values(), procedure, parameter);
    }

    @Override
    public Integer updateValue(UUID key, Function0<? extends Integer> factory, Function<? super Integer, ? extends Integer> function)
    {
        int hash = this.hash(key);
        AtomicReferenceArray currentArray = this.table;
        int length = currentArray.length();
        int index = ConcurrentUuidIntHashMap.indexFor(hash, length);
        Object o = currentArray.get(index);
        if (o == null)
        {
            Integer result = function.valueOf(factory.value());
            ConcurrentUuidIntHashMap.Entry newEntry = new ConcurrentUuidIntHashMap.Entry(key, result, null);
            if (currentArray.compareAndSet(index, null, newEntry))
            {
                this.addToSize(1);
                return result;
            }
        }
        return this.slowUpdateValue(key, factory, function, hash, currentArray);
    }

    private Integer slowUpdateValue(UUID key, Function0<? extends Integer> factory, Function<? super Integer, ? extends Integer> function, int hash, AtomicReferenceArray currentArray)
    {
        //noinspection LabeledStatement
        outer:
        while (true)
        {
            int length = currentArray.length();
            int index = ConcurrentUuidIntHashMap.indexFor(hash, length);
            Object o = currentArray.get(index);
            if (o == RESIZED || o == RESIZING)
            {
                currentArray = this.helpWithResizeWhileCurrentIndex(currentArray, index);
            }
            else
            {
                ConcurrentUuidIntHashMap.Entry e = (ConcurrentUuidIntHashMap.Entry) o;
                while (e != null)
                {
                    Object candidate = e.getKey();
                    if (candidate.equals(key))
                    {
                        Integer oldValue = e.getValue();
                        Integer newValue = function.valueOf(oldValue);
                        ConcurrentUuidIntHashMap.Entry newEntry = new ConcurrentUuidIntHashMap.Entry(e.getKey(), newValue, this.createReplacementChainForRemoval((ConcurrentUuidIntHashMap.Entry) o, e));
                        if (!currentArray.compareAndSet(index, o, newEntry))
                        {
                            //noinspection ContinueStatementWithLabel
                            continue outer;
                        }
                        return newValue;
                    }
                    e = e.getNext();
                }
                Integer result = function.valueOf(factory.value());
                ConcurrentUuidIntHashMap.Entry newEntry = new ConcurrentUuidIntHashMap.Entry(key, result, (ConcurrentUuidIntHashMap.Entry) o);
                if (currentArray.compareAndSet(index, o, newEntry))
                {
                    this.incrementSizeAndPossiblyResize(currentArray, length, o);
                    return result;
                }
            }
        }
    }

    @Override
    public <P> Integer updateValueWith(UUID key, Function0<? extends Integer> factory, Function2<? super Integer, ? super P, ? extends Integer> function, P parameter)
    {
        int hash = this.hash(key);
        AtomicReferenceArray currentArray = this.table;
        int length = currentArray.length();
        int index = ConcurrentUuidIntHashMap.indexFor(hash, length);
        Object o = currentArray.get(index);
        if (o == null)
        {
            Integer result = function.value(factory.value(), parameter);
            ConcurrentUuidIntHashMap.Entry newEntry = new ConcurrentUuidIntHashMap.Entry(key, result, null);
            if (currentArray.compareAndSet(index, null, newEntry))
            {
                this.addToSize(1);
                return result;
            }
        }
        return this.slowUpdateValueWith(key, factory, function, parameter, hash, currentArray);
    }

    private <P> Integer slowUpdateValueWith(
            UUID key,
            Function0<? extends Integer> factory,
            Function2<? super Integer, ? super P, ? extends Integer> function,
            P parameter,
            int hash,
            AtomicReferenceArray currentArray)
    {
        //noinspection LabeledStatement
        outer:
        while (true)
        {
            int length = currentArray.length();
            int index = ConcurrentUuidIntHashMap.indexFor(hash, length);
            Object o = currentArray.get(index);
            if (o == RESIZED || o == RESIZING)
            {
                currentArray = this.helpWithResizeWhileCurrentIndex(currentArray, index);
            }
            else
            {
                ConcurrentUuidIntHashMap.Entry e = (ConcurrentUuidIntHashMap.Entry) o;
                while (e != null)
                {
                    Object candidate = e.getKey();
                    if (candidate.equals(key))
                    {
                        Integer oldValue = e.getValue();
                        Integer newValue = function.value(oldValue, parameter);
                        ConcurrentUuidIntHashMap.Entry newEntry = new ConcurrentUuidIntHashMap.Entry(e.getKey(), newValue, this.createReplacementChainForRemoval((ConcurrentUuidIntHashMap.Entry) o, e));
                        if (!currentArray.compareAndSet(index, o, newEntry))
                        {
                            //noinspection ContinueStatementWithLabel
                            continue outer;
                        }
                        return newValue;
                    }
                    e = e.getNext();
                }
                Integer result = function.value(factory.value(), parameter);
                ConcurrentUuidIntHashMap.Entry newEntry = new ConcurrentUuidIntHashMap.Entry(key, result, (ConcurrentUuidIntHashMap.Entry) o);
                if (currentArray.compareAndSet(index, o, newEntry))
                {
                    this.incrementSizeAndPossiblyResize(currentArray, length, o);
                    return result;
                }
            }
        }
    }

    @Override
    public ImmutableMap<UUID, Integer> toImmutable()
    {
        return Maps.immutable.ofMap(this);
    }
}
