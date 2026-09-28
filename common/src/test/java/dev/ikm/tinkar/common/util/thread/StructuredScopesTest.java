package dev.ikm.tinkar.common.util.thread;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
