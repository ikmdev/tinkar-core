package org.hl7.tinkar.common.id;

import java.util.function.IntConsumer;
import java.util.stream.IntStream;

public interface IntIdCollection extends IdCollection {

    void forEach(IntConsumer consumer);

    IntStream intStream();

    int[] toArray();

    boolean contains(int value);
}
