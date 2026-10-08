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

import dev.ikm.tinkar.common.id.LongIdCollection;

import dev.ikm.tinkar.common.id.LongIdSet;
import dev.ikm.tinkar.common.service.PrimitiveData;

public class LongId1Set extends LongId1 implements LongIdSet {
    public LongId1Set(long element) {
        super(element);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof LongIdSet longIdSet) {
            return longIdSet.size() == 1 && longIdSet.toArray()[0] == element;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return LongIdCollection.hashOf(element);
    }

    @Override
    public String toString() {
        return "LongIdSet[" + PrimitiveData.textWithNid(element) + "]";
    }

}
