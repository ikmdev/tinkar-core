/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.common.id.impl;

import java.util.function.LongConsumer;
import java.util.stream.LongStream;

/**
 * LongId0 is an optimization for IntList or IntSet of size 0.
 */
public abstract class LongId0 {
    public static final long[] elements = new long[0];

    public long get(int index) {
        throw new IndexOutOfBoundsException("Index: " + index + ", Size: 0");
    }

    public int size() {
        return 0;
    }

    public void forEach(LongConsumer consumer) {
        // nothing to do...
    }

    public LongStream longStream() {
        return LongStream.of(elements);
    }

    public long[] toArray() {
        return elements;
    }

    public boolean contains(long value) {
        return false;
    }

    public boolean isEmpty() {
        return true;
    }

}
