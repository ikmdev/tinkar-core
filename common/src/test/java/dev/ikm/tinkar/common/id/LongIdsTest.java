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

import dev.ikm.tinkar.common.id.impl.LongIdListArray;
import dev.ikm.tinkar.common.id.impl.LongIdSetArray;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class LongIdsTest {

    @Test
    public void intIdOrderedSetTests() {
        // Size 1
        LongIdSet set1 = LongIdSetFactoryEnum.INSTANCE.of(0);
        LongIdSet set2 = LongIdSetFactoryEnum.INSTANCE.of(1);
        assertNotEquals(set1, set2);
        assertNotEquals(set1.toArray()[0], set2.toArray()[0]);

        set1 = LongIdSetFactoryEnum.INSTANCE.of(0);
        set2 = LongIdSetFactoryEnum.INSTANCE.of(0);
        assertEquals(set1, set2);
        assertEquals(set1.toArray()[0], set2.toArray()[0]);

        set2 = LongIdSetArray.newLongIdSet(0);
        assertEquals(set1, set2);
        assertEquals(set1.toArray()[0], set2.toArray()[0]);

        // Size 2
        set1 = LongIdSetFactoryEnum.INSTANCE.of(1, 0);
        set2 = LongIdSetFactoryEnum.INSTANCE.of(0, 1);
        assertEquals(set1, set2);
        assertNotEquals(set1.toArray()[0], set2.toArray()[0]);

        // Size > 2
        set1 = LongIdSetFactoryEnum.INSTANCE.of(1, 0, 2);
        set2 = LongIdSetFactoryEnum.INSTANCE.of(0, 3);
        assertNotEquals(set1, set2);
        assertNotEquals(set1.toArray()[0], set2.toArray()[0]);

        // Ensure order is preserved
        set1 = LongIdSetFactoryEnum.INSTANCE.of(1, 0, 2, 1);
        assertEquals(3, set1.size());
        List<Long> tempList = new ArrayList<>();
        set1.forEach(tempList::add);
        assertEquals(3, tempList.size());
        assertEquals(1L, tempList.get(0));
        assertEquals(0L, tempList.get(1));
        assertEquals(2L, tempList.get(2));

        set1 = set1.with(8, 7, 6, 6).with(6).with(1);
        assertEquals(6, set1.size());
        tempList.clear();
        set1.forEach(tempList::add);
        assertEquals(6, tempList.size());
        assertEquals(1L, tempList.get(0));
        assertEquals(0L, tempList.get(1));
        assertEquals(2L, tempList.get(2));
        assertEquals(8L, tempList.get(3));
        assertEquals(7L, tempList.get(4));
        assertEquals(6L, tempList.get(5));
    }

    @Test
    public void intIdListTests() {
        // Size 1
        LongIdList list1 = LongIdListFactoryEnum.INSTANCE.of(0);
        LongIdList list2 = LongIdListFactoryEnum.INSTANCE.of(1);
        assertNotEquals(list1, list2);
        assertNotEquals(list1.toArray()[0], list2.toArray()[0]);

        list1 = LongIdListFactoryEnum.INSTANCE.of(0);
        list2 = LongIdListFactoryEnum.INSTANCE.of(0);
        assertEquals(list1, list2);
        assertEquals(list1.toArray()[0], list2.toArray()[0]);

        list2 = new LongIdListArray(0);
        assertEquals(list1, list2);
        assertEquals(list1.toArray()[0], list2.toArray()[0]);

        // Size 2
        list1 = LongIdListFactoryEnum.INSTANCE.of(1, 0);
        list2 = LongIdListFactoryEnum.INSTANCE.of(0, 1);
        assertNotEquals(list1, list2);
        assertNotEquals(list1.toArray()[0], list2.toArray()[0]);

        // Size > 2
        list1 = LongIdListFactoryEnum.INSTANCE.of(1, 0, 2);
        list2 = LongIdListFactoryEnum.INSTANCE.of(0, 3);
        assertNotEquals(list1, list2);
        assertNotEquals(list1.toArray()[0], list2.toArray()[0]);

        // Ensure order is preserved
        list1 = LongIdListFactoryEnum.INSTANCE.of(1, 0, 2, 1);
        assertEquals(4, list1.size());
        List<Long> tempList = new ArrayList<>();
        list1.forEach(tempList::add);
        assertEquals(4, tempList.size());
        assertEquals(1L, tempList.get(0));
        assertEquals(0L, tempList.get(1));
        assertEquals(2L, tempList.get(2));
        assertEquals(1L, tempList.get(3));

        list1 = list1.with(8, 7, 6, 6).with(6).with(1);
        assertEquals(10, list1.size());
        tempList.clear();
        list1.forEach(tempList::add);
        assertEquals(10, tempList.size());
        assertEquals(1L, tempList.get(0));
        assertEquals(0L, tempList.get(1));
        assertEquals(2L, tempList.get(2));
        assertEquals(1L, tempList.get(3));
        assertEquals(8L, tempList.get(4));
        assertEquals(7L, tempList.get(5));
        assertEquals(6L, tempList.get(6));
        assertEquals(6L, tempList.get(7));
        assertEquals(6L, tempList.get(8));
        assertEquals(1L, tempList.get(9));
    }
}