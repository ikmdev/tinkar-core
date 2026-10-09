package dev.ikm.tinkar.common.util.thread;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JDK 25 {@code StructuredTaskScope.open()} failure contract that
 * {@link StructuredScopes#open()} preserves on later JDKs (IKE-Network/ike-issues#1143).
 */
class StructuredScopesTest {

    @Test
    void allSubtasksSucceed_joinReturns_andResultsAreAvailable() throws InterruptedException {
        List<StructuredTaskScope.Subtask<Integer>> subtasks = new ArrayList<>();
        try (StructuredTaskScope<Integer, Void, SubtaskFailedException> scope = StructuredScopes.open()) {
            for (int i = 0; i < 16; i++) {
                int value = i;
                subtasks.add(scope.fork(() -> value * 2));
            }
            scope.join();
        }
        int sum = 0;
        for (StructuredTaskScope.Subtask<Integer> subtask : subtasks) {
            sum += subtask.get();
        }
        assertEquals(240, sum);
    }

    @Test
    void aFailingSubtask_makesJoinThrowAnUncheckedExceptionWithTheCause() {
        IllegalStateException failure = new IllegalStateException("subtask failed");
        SubtaskFailedException thrown = assertThrows(SubtaskFailedException.class, () -> {
            try (StructuredTaskScope<Object, Void, SubtaskFailedException> scope = StructuredScopes.open()) {
                scope.fork(() -> "fine");
                scope.fork(() -> { throw failure; });
                scope.join();
            }
        });
        assertInstanceOf(RuntimeException.class, thrown, "callers need no checked-exception handling");
        assertEquals(failure, thrown.getCause());
    }

    @Test
    void aPermitIsTakenWhileTheScopeRuns() throws Exception {
        Semaphore permits = new Semaphore(1);
        try (StructuredTaskScope<Object, Void, SubtaskFailedException> scope = StructuredScopes.open()) {
            assertTrue(StructuredScopes.acquireUnlessCancelled(permits, scope));
            scope.join();
        }
        assertEquals(0, permits.availablePermits(), "the permit was taken");
    }

    /**
     * Readers fork a subtask per record and take a permit for each. When one fails, the scope
     * cancels itself and never runs the subtasks forked after it, so their permits never come
     * back; a plain {@code acquire()} then blocked for good, and an import or export hung
     * instead of reporting the failure.
     */
    @Test
    void aWaitForAPermit_endsOnceAFailedSubtaskCancelsTheScope() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            Semaphore permits = new Semaphore(1);
            try (StructuredTaskScope<Object, Void, SubtaskFailedException> scope = StructuredScopes.open()) {
                // Holds the only permit and fails without releasing it, as a subtask the
                // cancelled scope never ran would.
                assertTrue(StructuredScopes.acquireUnlessCancelled(permits, scope));
                scope.fork(() -> {
                    throw new IllegalStateException("a record failed");
                });
                assertFalse(StructuredScopes.acquireUnlessCancelled(permits, scope));
                assertThrows(SubtaskFailedException.class, scope::join);
            }
        });
    }
}
