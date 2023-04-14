package dev.ikm.tinkar.collection;
import java.io.Serializable;

/**
 * A sum is a mutable or immutable object that may have either other objects or values added to it.
 *
 * @deprecated Don't use in new tests
 */
@Deprecated
public interface Sum
        extends Serializable
{
    Sum add(Object number);

    Sum add(Number number);

    Sum add(Sum otherSum);

    Sum add(int value);

    Number getValue();

    Sum speciesNew();
}
