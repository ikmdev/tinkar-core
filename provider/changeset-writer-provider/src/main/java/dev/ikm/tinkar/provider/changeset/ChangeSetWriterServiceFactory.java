package dev.ikm.tinkar.provider.changeset;

import dev.ikm.tinkar.entity.ChangeSetWriterService;

/**
 * Factory class for providing the ChangeSetWriterService implementation.
 * This factory enables JPMS service discovery for the singleton ChangeSetWriterProvider.
 * <p>
 * The factory follows the JPMS service provider pattern by providing a public no-arg
 * constructor and a {@code provider()} method that returns the singleton instance
 * of the ChangeSetWriterProvider.
 */
public class ChangeSetWriterServiceFactory {

    /**
     * Public no-arg constructor required for JPMS service loading.
     */
    public ChangeSetWriterServiceFactory() {
    }

    /**
     * Provider method that returns the singleton instance of ChangeSetWriterProvider.
     * This method is called by the Java module system's service loader mechanism
     * to obtain the ChangeSetWriterService implementation.
     *
     * @return the singleton instance of ChangeSetWriterProvider
     */
    public static ChangeSetWriterService provider() {
        return ChangeSetWriterProvider.provider();
    }
}

