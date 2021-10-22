package org.hl7.tinkar.entity.transfom;

public interface EntityTransform<T,V> {
    V transform(T data);
}
