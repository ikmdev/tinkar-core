package dev.ikm.tinkar.common.id;

import java.util.function.Consumer;
import java.util.stream.Stream;

public interface PublicIdCollection<E extends PublicId>  extends IdCollection, Iterable<E> {

    void forEach(Consumer<? super E> consumer);

    Stream<? extends PublicId> stream();

    PublicId[] toIdArray();

    boolean contains(PublicId value);
}
