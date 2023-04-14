package dev.ikm.tinkar.common.id;


import dev.ikm.tinkar.common.id.impl.*;
import dev.ikm.tinkar.common.id.impl.*;

import java.util.Arrays;


enum IntIdSetFactoryEnum implements IntIdSetFactory {
    INSTANCE;

    @Override
    public IntIdSet of(IntIdSet idSet, int... elements) {
        int[] combined = new int[idSet.size() + elements.length];
        int[] listArray = idSet.toArray();
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
    public IntIdSet empty() {
        return IntId0Set.INSTANCE;
    }

    @Override
    public IntIdSet of() {
        return this.empty();
    }

    @Override
    public IntIdSet of(int one) {
        return new IntId1Set(one);
    }


    @Override
    public IntIdSet of(int one, int two) {
        if (one != two) {
            return new IntId2Set(one, two);
        }
        return of(one);
    }

    @Override
    public IntIdSet of(int... elements) {
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
            return IntIdSetArray.newIntIdSet(elements);
        }
        return IntIdSetRoaring.newIntIdSet(elements);
    }

    @Override
    public IntIdSet ofAlreadySorted(int... elements) {
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
            return IntIdSetArray.newIntIdSetAlreadySorted(elements);
        }
        return IntIdSetRoaring.newIntIdSetAlreadySorted(elements);
    }
}
