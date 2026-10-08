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

import dev.ikm.tinkar.common.id.impl.LongId0List;
import dev.ikm.tinkar.common.id.impl.LongId1List;
import dev.ikm.tinkar.common.id.impl.LongId2List;
import dev.ikm.tinkar.common.id.impl.LongIdListArray;

/**
 *
 */
enum LongIdListFactoryEnum implements LongIdListFactory {
    INSTANCE;

    @Override
    public LongIdList empty() {
        return LongId0List.INSTANCE;
    }

    @Override
    public LongIdList of() {
        return this.empty();
    }

    @Override
    public LongIdList of(long one) {
        return new LongId1List(one);
    }

    @Override
    public LongIdList of(long one, long two) {
        return new LongId2List(one, two);
    }

    @Override
    public LongIdList of(LongIdList list, long... elements) {
        long[] combined = new long[list.size() + elements.length];
        long[] listArray = list.toArray();
        int elementIndex = 0;
        for (int i = 0; i < combined.length; i++) {
            if (i < listArray.length) {
                combined[i] = listArray[i];
            } else {
                combined[i] = elements[elementIndex++];
            }
        }
        return of(combined);
    }

    @Override
    public LongIdList of(long... elements) {
        if (elements == null || elements.length == 0) {
            return this.empty();
        }
        if (elements.length == 1) {
            return new LongId1List(elements[0]);
        }
        if (elements.length == 2) {
            return new LongId2List(elements[0], elements[1]);
        }
        return new LongIdListArray(elements);
    }


}
