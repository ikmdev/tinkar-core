package dev.ikm.tinkar.common.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code StableValue.orElseSet} contract {@link SetOnce} preserves
 * (IKE-Network/ike-issues#1143) — in particular retry after a failed
 * computation, which {@code LazyConstant} does not offer.
 */
class SetOnceTest {

    @Test
    void computesOnce_andReturnsTheSameValueThereafter() {
        SetOnce<Object> holder = new SetOnce<>();
        AtomicInteger calls = new AtomicInteger();
        Object first = holder.orElseSet(() -> { calls.incrementAndGet(); return new Object(); });
        Object second = holder.orElseSet(() -> { calls.incrementAndGet(); return new Object(); });
        assertSame(first, second);
        assertEquals(1, calls.get());
        assertTrue(holder.isSet());
    }

    @Test
    void aThrowingSupplierLeavesItUnset_andTheNextCallRetries() {
        SetOnce<String> holder = new SetOnce<>();
        assertThrows(IllegalStateException.class,
                () -> holder.orElseSet(() -> { throw new IllegalStateException("service not started yet"); }));
        assertFalse(holder.isSet(), "a failed computation sets nothing");
        assertEquals("ready", holder.orElseSet(() -> "ready"), "a later call computes again");
    }

    @Test
    void nullIsRejected_andLeavesItUnset() {
        SetOnce<String> holder = new SetOnce<>();
        assertThrows(NullPointerException.class, () -> holder.orElseSet(() -> null));
        assertFalse(holder.isSet());
    }

    @Test
    void concurrentCallers_shareOneComputation() throws Exception {
        SetOnce<Object> holder = new SetOnce<>();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 64; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return holder.orElseSet(() -> { calls.incrementAndGet(); return new Object(); });
                }));
            }
            start.countDown();
            Object first = results.get(0).get();
            for (Future<Object> result : results) {
                assertSame(first, result.get());
            }
        }
        assertEquals(1, calls.get());
    }

    @Test
    void supplier_memoizes_andRetriesAfterFailure() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<Integer> memo = SetOnce.supplier(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("first attempt fails");
            }
            return 42;
        });
        assertThrows(IllegalStateException.class, memo::get);
        assertEquals(42, memo.get());
        assertEquals(42, memo.get());
        assertEquals(2, calls.get(), "computed until the first success, then never again");
    }
}
