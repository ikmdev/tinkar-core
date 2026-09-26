package dev.ikm.tinkar.common.util.thread;

/**
 * Unchecked exception thrown by {@code join()} on a scope from
 * {@link StructuredScopes#open()} when a subtask fails; the subtask's exception
 * is the {@linkplain #getCause() cause}.
 *
 * <p>Stands in for the JDK 25 preview's {@code StructuredTaskScope.FailedException},
 * which later JDKs removed: their default {@code StructuredTaskScope.open()} makes
 * {@code join()} throw the checked {@code ExecutionException} instead
 * (IKE-Network/ike-issues#1143).
 */
public class SubtaskFailedException extends RuntimeException {

    /**
     * @param cause the exception of the first subtask that failed
     */
    public SubtaskFailedException(Throwable cause) {
        super(cause);
    }
}
