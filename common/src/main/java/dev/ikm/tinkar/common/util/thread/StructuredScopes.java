package dev.ikm.tinkar.common.util.thread;

import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.TimeUnit;

/**
 * Opens {@link StructuredTaskScope}s with the failure behaviour IKE code was
 * written against (IKE-Network/ike-issues#1143).
 *
 * <p>On JDK 25 (preview), {@code StructuredTaskScope.open()} waited for all
 * subtasks and, if any failed, made {@code join()} throw the unchecked
 * {@code FailedException} with the first failure as its cause. Later JDKs give
 * {@code StructuredTaskScope} a third type parameter for the exception
 * {@code join()} throws, and the default {@code open()} now throws the checked
 * {@code ExecutionException}. {@link #open()} keeps the earlier contract so callers
 * need no new checked-exception handling:
 * <ul>
 *   <li>{@code join()} waits for every subtask to succeed, or for the first to fail;</li>
 *   <li>on failure the scope is cancelled and {@code join()} throws
 *       {@link SubtaskFailedException} (unchecked) with the subtask's exception as
 *       its cause;</li>
 *   <li>{@code join()} still throws {@link InterruptedException} if interrupted.</li>
 * </ul>
 */
public final class StructuredScopes {

    private StructuredScopes() {
    }

    /**
     * Opens a scope whose {@code join()} throws {@link SubtaskFailedException} if
     * any subtask fails.
     *
     * @param <T> the result type of the subtasks
     * @return a new scope, to be closed with try-with-resources
     */
    public static <T> StructuredTaskScope<T, Void, SubtaskFailedException> open() {
        return StructuredTaskScope.open(Joiner.<T, SubtaskFailedException>awaitAllSuccessfulOrThrow(
                SubtaskFailedException::new));
    }

    /** How often a wait for a permit checks whether its scope has been cancelled. */
    private static final long PERMIT_POLL_MS = 100L;

    /**
     * Takes a permit for the next subtask, or returns false once {@code scope} has been cancelled.
     *
     * <p>A scope from {@link #open()} cancels itself on the first failed subtask, and a cancelled
     * scope does not run the subtasks forked after that. Each of those would have released its
     * permit when it finished, so their permits never come back: a plain {@code acquire()} then
     * blocks for good, and the work hangs instead of reporting the failure. Polling lets the
     * forking thread notice the cancellation and stop, so that {@code join()} can throw the
     * subtask's error.
     *
     * @return true with a permit taken; false, with none, once the scope is cancelled
     */
    public static boolean acquireUnlessCancelled(Semaphore permits, StructuredTaskScope<?, ?, ?> scope)
            throws InterruptedException {
        while (!permits.tryAcquire(PERMIT_POLL_MS, TimeUnit.MILLISECONDS)) {
            if (scope.isCancelled()) {
                return false;
            }
        }
        return true;
    }
}
