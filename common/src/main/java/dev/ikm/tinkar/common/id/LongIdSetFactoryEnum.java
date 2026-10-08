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
package dev.ikm.tinkar.common.id;


import dev.ikm.tinkar.common.id.impl.LongId0Set;
import dev.ikm.tinkar.common.id.impl.LongId1Set;
import dev.ikm.tinkar.common.id.impl.LongId2Set;
import dev.ikm.tinkar.common.id.impl.LongIdSetArray;
import dev.ikm.tinkar.common.id.impl.LongIdSetRoaring;

import java.util.Arrays;


enum LongIdSetFactoryEnum implements LongIdSetFactory {
    INSTANCE;

    @Override
    public LongIdSet of(LongIdSet idSet, long... elements) {
        long[] combined = new long[idSet.size() + elements.length];
        long[] listArray = idSet.toArray();
        int elementIndex = 0;
        for (int i = 0; i < combined.length; i++) {
            if (i < listArray.length) {
                combined[i] = listArray[i];
            } else {
                combined[i] = elements[elementIndex++];
            }
        }
        combined = Arrays.stream(combined).distinct().toArray();
        return of(combined);
    }

    @Override
    public LongIdSet empty() {
        return LongId0Set.INSTANCE;
    }

    @Override
    public LongIdSet of() {
        return this.empty();
    }

    @Override
    public LongIdSet of(long one) {
        return new LongId1Set(one);
    }


    @Override
    public LongIdSet of(long one, long two) {
        if (one != two) {
            return new LongId2Set(one, two);
        }
        return of(one);
    }

    @Override
    public LongIdSet of(long... elements) {
        if (elements == null || elements.length == 0) {
            return empty();
        }
        if (elements.length == 1) {
            return this.of(elements[0]);
        }
        elements = Arrays.stream(elements).distinct().toArray();
        if (elements.length == 2) {
            if (elements[0] == elements[1]) {
                return this.of(elements[0]);
            } else {
                return this.of(elements[0], elements[1]);
            }
        }
        if (elements.length < 1024) {
            return LongIdSetArray.newLongIdSet(elements);
        }
        return LongIdSetRoaring.newLongIdSet(elements);
    }

    @Override
    public LongIdSet ofAlreadySorted(long... elements) {
        if (elements == null || elements.length == 0) {
            return empty();
        }
        if (elements.length == 1) {
            return this.of(elements[0]);
        }
        if (elements.length == 2) {
            if (elements[0] == elements[1]) {
                return this.of(elements[0]);
            } else {
                return this.of(elements[0], elements[1]);
            }
        }
        if (elements.length < 1024) {
            return LongIdSetArray.newLongIdSetAlreadySorted(elements);
        }
        return LongIdSetRoaring.newLongIdSetAlreadySorted(elements);
    }
}
