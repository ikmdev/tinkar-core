package dev.ikm.tinkar.common.service;

import java.util.Optional;

/**
 * Thrown when a data store was written with a nid layout this build cannot
 * read — today, a RocksDB knowledge base written with the 6-bit layout (at
 * most 63 patterns) opened by the 8-bit build (up to 255 patterns;
 * IKE-Network/ike-issues#1138).
 * <p>Nids are persisted in the stored entity bytes, so decoding a 6-bit
 * database with the 8-bit codec would silently resolve every reference to the
 * wrong entity. The store is therefore refused and left unmodified. The
 * condition is <b>terminal</b> — retrying cannot succeed — so the exception
 * implements {@link NonRetryableStartupFailure}, and the application can tell
 * the user how to migrate: export the database with a build that reads it, then
 * import the export with this build.
 *
 * @see NonRetryableStartupFailure
 * @see DataStoreAlreadyOpenException
 */
public class IncompatibleNidLayoutException extends IllegalStateException
        implements NonRetryableStartupFailure {

    private final String dataStorePath;

    /**
     * Constructs an {@code IncompatibleNidLayoutException} for the given data
     * store location.
     *
     * @param dataStorePath the file-system path of the refused data store
     * @param message       what was found and the remedy
     */
    public IncompatibleNidLayoutException(String dataStorePath, String message) {
        super(message);
        this.dataStorePath = dataStorePath;
    }

    /**
     * Returns the file-system path of the refused data store.
     *
     * @return the data store path; never {@code null}
     */
    public String dataStorePath() {
        return dataStorePath;
    }

    /**
     * Finds the first {@code IncompatibleNidLayoutException} in the cause chain
     * of the given throwable. Startup failures are wrapped repeatedly as they
     * propagate, so callers must inspect the whole chain.
     *
     * @param throwable the throwable to inspect; may be {@code null}
     * @return the first matching exception in the chain, or empty if none
     */
    public static Optional<IncompatibleNidLayoutException> findIn(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof IncompatibleNidLayoutException incompatible) {
                return Optional.of(incompatible);
            }
        }
        return Optional.empty();
    }
}
