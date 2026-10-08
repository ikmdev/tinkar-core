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
import dev.ikm.tinkar.common.service.DiagnosticText;
import dev.ikm.tinkar.common.service.PrimitiveData;

import java.util.Arrays;

public class LongId2Set extends LongId2 implements LongIdSet {
    public LongId2Set(long element, long element2) {
        super(element, element2);
        if (element == element2) {
            throw new IllegalStateException("Duplicate values in set: " + DiagnosticText.component(element));
        }
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof LongIdSet longIdSet && longIdSet.size() == 2) {
            long[] clone1 = this.toArray().clone();
            long[] clone2 = longIdSet.toArray().clone();
            Arrays.sort(clone1);
            Arrays.sort(clone2);
            return Arrays.equals(clone1, clone2);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return LongIdCollection.hashOf(element) + LongIdCollection.hashOf(element2);
    }

    @Override
    public String toString() {
        return "LongIdSet[" + PrimitiveData.textWithNid(element) + ", " + PrimitiveData.textWithNid(element2) + "]";
    }
}
