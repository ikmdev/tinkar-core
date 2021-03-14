/*
 * Copyright 2017 Organizations participating in ISAAC, ISAAC's KOMET, and SOLOR development include the
         US Veterans Health Administration, OSHERA, and the Health Services Platform Consortium..
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hl7.tinkar.collection;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Spliterator;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.*;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * @param <E> the generic type for the spined list.
 * @author kec
 */
public class SpinedIntObjectMap<E> implements IntObjectMap<E> {

    private static final Logger LOG = LogManager.getLogManager().getLogger(SpinedIntObjectMap.class.getName());

    public static final int DEFAULT_SPINE_SIZE = 8192;

    private final Semaphore newSpineSemaphore = new Semaphore(1);
    protected final Semaphore fileSemaphore = new Semaphore(1);

    protected final int spineSize;
    // TODO: consider growth strategies instead of just a large array expected to be big enough to hold all the spines...
    private final AtomicReferenceArray<AtomicReferenceArray<E>> spines = new AtomicReferenceArray(DEFAULT_SPINE_SIZE);
    private final AtomicInteger spineCount = new AtomicInteger();
    private final boolean[] changedSpineIndexes = new boolean[DEFAULT_SPINE_SIZE];
    private Function<E, String> elementStringConverter;

    public SpinedIntObjectMap(int spineCount) {
        this.spineSize = DEFAULT_SPINE_SIZE;
        this.spineCount.set(spineCount);
    }


    public void setElementStringConverter(Function<E, String> elementStringConverter) {
        this.elementStringConverter = elementStringConverter;
    }

    protected final int indexToSpineIndex(int index) {
        if (index < 0) {
            index = Integer.MAX_VALUE + index;
        }
        return index;
    }

    public void forEachSpine(ObjIntConsumer<AtomicReferenceArray<E>> consumer) {
        int spineCountNow = spineCount.get();
        for (int spineIndex = 0; spineIndex < spineCountNow; spineIndex++) {
            consumer.accept(getSpine(spineIndex), spineIndex);
        }
    }

    public boolean forEachChangedSpine(ObjIntConsumer<AtomicReferenceArray<E>> consumer) {
        boolean foundChange = false;
        int spineCountNow = spineCount.get();
        for (int spineIndex = 0; spineIndex < spineCountNow; spineIndex++) {
            if (changedSpineIndexes[spineIndex]) {
                foundChange = true;
                consumer.accept(getSpine(spineIndex), spineIndex);
                changedSpineIndexes[spineIndex] = false;
            }
        }
        return foundChange;
    }


    public int getSpineCount() {
        return spineCount.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        for (int i = 0; i < spines.length(); i++) {
            spines.set(i, null);
        }
        ;
    }

    public void printToConsole() {
        if (elementStringConverter != null) {
            forEach((E value, int key) -> {
                LOG.info(key + ": " + elementStringConverter.apply(value));
            });
        } else {
            forEach((E value, int key) -> {
                LOG.info(key + ": " + value);
            });
        }
    }

    private AtomicReferenceArray<E> newSpine(Integer spineKey) {
        return makeNewSpine(spineKey);
    }

    public AtomicReferenceArray<E> makeNewSpine(Integer spineKey) {
        AtomicReferenceArray<E> spine = new AtomicReferenceArray<>(spineSize);
        this.spineCount.set(Math.max(this.spineCount.get(), spineKey + 1));
        return spine;
    }

    private AtomicReferenceArray<E> getSpine(int spineIndex) {
        int startSpineCount = spineCount.get();
        if (spineIndex < startSpineCount) {
            AtomicReferenceArray<E> spine = this.spines.get(spineIndex);
            if (spine == null) {
                try  {
                    newSpineSemaphore.acquireUninterruptibly();
                    spine = this.spines.get(spineIndex);
                    if (spine == null) {
                        spine = readSpine(spineIndex);
                        this.spines.compareAndSet(spineIndex, null, spine);
                    }
                } finally {
                    newSpineSemaphore.release();
                }
            }
            return spine;
        }
        try {
            newSpineSemaphore.acquireUninterruptibly();
            if (spineIndex < spineCount.get()) {
                return this.spines.get(spineIndex);
            }
            return this.spines.updateAndGet(spineIndex, eAtomicReferenceArray -> {
                if (eAtomicReferenceArray == null) {
                    eAtomicReferenceArray = newSpine(spineIndex);
                    spineCount.compareAndSet(startSpineCount, startSpineCount + 1);
                }
                return eAtomicReferenceArray;
            });
        } finally {
            newSpineSemaphore.release();
        }
    }

    protected AtomicReferenceArray<E> readSpine(int spineIndex) {
        throw new IllegalStateException("Subclass must implement readSpine");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean put(int index, E element) {
        if (index < 0) {
            index = Integer.MAX_VALUE + index;
        }
        int spineIndex = index / spineSize;
        int indexInSpine = index % spineSize;
        this.changedSpineIndexes[spineIndex] = true;
        return getSpine(spineIndex).getAndSet(indexInSpine, element) == null;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final E get(int index) {
        if (index < 0) {
            index = Integer.MAX_VALUE + index;
        }
        int spineIndex = index / spineSize;
        int indexInSpine = index % spineSize;
        return getSpine(spineIndex).get(indexInSpine);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public final E getAndSet(int index, E element) {
        if (index < 0) {
            index = Integer.MAX_VALUE + index;
        }
        int spineIndex = index / spineSize;
        int indexInSpine = index % spineSize;
        this.changedSpineIndexes[spineIndex] = true;
        return getSpine(spineIndex).getAndSet(indexInSpine, element);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Optional<E> getOptional(int index) {
        if (index < 0) {
            index = Integer.MAX_VALUE + index;
        }
        int spineIndex = index / spineSize;
        int indexInSpine = index % spineSize;
        return Optional.ofNullable(getSpine(spineIndex).get(indexInSpine));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean containsKey(int index) {
        if (index < 0) {
            index = Integer.MAX_VALUE + index;
        }
        int spineIndex = index / spineSize;
        int indexInSpine = index % spineSize;
        return getSpine(spineIndex).get(indexInSpine) != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int size() {
        int size = 0;
        int currentSpineCount = this.spineCount.get();
        for (int spineIndex = 0; spineIndex < currentSpineCount; spineIndex++) {
            AtomicReferenceArray<E> spine = getSpine(spineIndex);
            for (int indexInSpine = 0; indexInSpine < spineSize; indexInSpine++) {
                E element = spine.get(indexInSpine);
                if (element != null) {
                    size++;
                }
            }
        }
        return size;
    }

    public final void forEach(ObjIntConsumer<E> consumer) {
        int currentSpineCount = this.spineCount.get();
        int key = 0;
        for (int spineIndex = 0; spineIndex < currentSpineCount; spineIndex++) {
            forEachOnSpine(consumer, spineIndex);
        }
    }

    ExecutorService exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

    public final void forEachParallel(ObjIntConsumer<E> consumer) throws ExecutionException, InterruptedException {
        int currentSpineCount = this.spineCount.get();
        ArrayList<Future<?>> futures = new ArrayList<>(currentSpineCount);
        for (int spineIndex = 0; spineIndex < currentSpineCount; spineIndex++) {
            final int indexToProcess = spineIndex;
            Future<?> future = exec.submit(() -> forEachOnSpine(consumer, indexToProcess));
            futures.add(future);
        }
        for (Future<?> future: futures) {
            Object obj = future.get();
        }
    }

    private int forEachOnSpine(ObjIntConsumer<E> consumer, int spineIndex) {
        AtomicReferenceArray<E> spine = getSpine(spineIndex);
        int index = spineIndex * spineSize;
        int processed = 0;
        for (int indexInSpine = 0; indexInSpine < spineSize; indexInSpine++) {
            E element = spine.get(indexInSpine);
            if (element != null) {
                int nid = Integer.MIN_VALUE + index;
                consumer.accept((E) element, nid);
                processed++;
            }
            index++;
        }
        //if (processed < spineSize) {
            // TODO where do the null values come from?
            //System.out.println(spineSize - processed + " null values on spine: " + spineIndex);
        //}
        return processed;
     }


    /**
     * {@inheritDoc}
     */
    @Override
    public final E accumulateAndGet(int index, E x, BinaryOperator<E> accumulatorFunction) {
        if (index < 0) {
            index = Integer.MAX_VALUE + index;
        }
        int spineIndex = index / spineSize;
        int indexInSpine = index % spineSize;
        this.changedSpineIndexes[spineIndex] = true;
        return getSpine(spineIndex)
                .accumulateAndGet(indexInSpine, x, accumulatorFunction);

    }

    public final Stream<E> stream() {
        final Supplier<? extends Spliterator<E>> streamSupplier = this.get();

        return StreamSupport.stream(streamSupplier, streamSupplier.get()
                .characteristics(), false);
    }

    /**
     * Gets the.
     *
     * @return the supplier<? extends spliterator. of int>
     */
    protected Supplier<? extends Spliterator<E>> get() {
        return new SpliteratorSupplier();
    }

    /**
     * The Class SpliteratorSupplier.
     */
    private class SpliteratorSupplier
            implements Supplier<Spliterator<E>> {

        /**
         * Gets the.
         *
         * @return the spliterator
         */
        @Override
        public Spliterator<E> get() {
            return new SpinedValueSpliterator();
        }
    }

    private class SpinedValueSpliterator implements Spliterator<E> {

        int end;
        int currentPosition;

        public SpinedValueSpliterator() {
            this.end = DEFAULT_SPINE_SIZE * spineCount.get();
            this.currentPosition = 0;
        }

        public SpinedValueSpliterator(int start, int end) {
            this.currentPosition = start;
            this.end = end;
        }

        @Override
        public Spliterator<E> trySplit() {
            int splitEnd = end;
            int split = end - currentPosition;
            int half = split / 2;
            this.end = currentPosition + half;
            return new SpinedValueSpliterator(currentPosition + half + 1, splitEnd);
        }

        @Override
        public boolean tryAdvance(Consumer<? super E> action) {
            while (currentPosition < end) {
                E value = get(currentPosition++);
                if (value != null) {
                    action.accept(value);
                    return true;
                }
            }
            return false;
        }

        @Override
        public long estimateSize() {
            return end - currentPosition;
        }

        @Override
        public int characteristics() {
            return Spliterator.DISTINCT | Spliterator.IMMUTABLE | Spliterator.NONNULL | Spliterator.ORDERED
                    | Spliterator.SIZED;
        }

    }

    public boolean containsSpine(int spineIndex) {
        return this.spines.get(spineIndex) != null;
    }
}
