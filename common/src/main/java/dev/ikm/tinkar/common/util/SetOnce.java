package dev.ikm.tinkar.common.util;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A holder whose value is set at most once, on first successful computation, and
 * retried if the computation throws.
 *
 * <p>Replaces the JDK 25 preview {@code java.lang.StableValue}, removed in later
 * JDKs (IKE-Network/ike-issues#1143). Its successor, {@link java.lang.LazyConstant},
 * is deliberately not a drop-in replacement: when the computing function throws, a
 * {@code LazyConstant} enters a permanent error state and never computes again,
 * whereas {@code StableValue.orElseSet} left the value unset so a later call could
 * succeed. Callers here depend on that retry — a service lookup that fails until the
 * service has started, a provider whose construction may fail and be attempted
 * again — so this class keeps {@code orElseSet}'s contract:
 * <ul>
 *   <li>the value is computed and set at most once, atomically;</li>
 *   <li>if the supplier throws, nothing is set and the exception propagates; the
 *       next call computes again;</li>
 *   <li>a {@code null} result is rejected, so "unset" stays unambiguous.</li>
 * </ul>
 *
 * @param <T> the value type
 */
public final class SetOnce<T> {

    private volatile T value;

    /** Creates an unset holder. */
    public SetOnce() {
    }

    /**
     * Returns the value, computing and setting it with {@code supplier} if unset.
     * Concurrent callers wait for one computation; if it throws, the holder stays
     * unset and the next caller computes again.
     *
     * @param supplier computes the value; must not return {@code null}
     * @return the value
     * @throws NullPointerException if {@code supplier} returns {@code null}
     */
    public T orElseSet(Supplier<? extends T> supplier) {
        T current = value;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            current = value;
            if (current == null) {
                current = Objects.requireNonNull(supplier.get(), "SetOnce supplier returned null");
                value = current;
            }
            return current;
        }
    }

    /**
     * Returns {@code true} if the value has been set.
     *
     * @return whether the value is set
     */
    public boolean isSet() {
        return value != null;
    }

    /**
     * Returns a supplier that computes its value with {@code supplier} on first
     * successful {@code get()} and returns it thereafter, retrying if it throws.
     *
     * @param supplier computes the value; must not return {@code null}
     * @param <T>      the value type
     * @return a memoizing supplier
     */
    public static <T> Supplier<T> supplier(Supplier<? extends T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        SetOnce<T> holder = new SetOnce<>();
        return () -> holder.orElseSet(supplier);
    }
}
