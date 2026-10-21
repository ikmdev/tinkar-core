package dev.ikm.tinkar.entity;

import dev.ikm.tinkar.common.service.DataActivity;

/**
 * ChangeSetWriterService ensures durability and exchangeability every time a new entity version is written
 * and a later STAMP entity for that version is written.
 */
public interface ChangeSetWriterService {
    /**
     * Writes the given entity to the change set.
     *
     * @param entity       The entity instance for which a new version is written.
     * @param dataActivity The type of data activity being performed, which helps determine
     *                     if the entity should be written to the change set (e.g., initialization,
     *                     loading change set, synchronizable edit, or local edit), based
     *                     on the service's policies.
     */
    void writeToChangeSet(Entity entity, DataActivity dataActivity);

    /**
     * Shuts down the ChangeSetWriterService, ensuring any necessary cleanup
     * or finalization steps are performed.
     */
    void shutdown();

    /**
     * Removes components' records from the change sets this writer keeps that hold instance or
     * ephemeral data (IKE-Network/ike-issues#1313), once a scope of them has been deleted from the
     * store. Knowledge change sets are immutable and exchanged, and are never rewritten. A writer
     * that keeps no such change sets removes nothing.
     *
     * @param components the UUIDs of the deleted components
     * @return what was removed
     */
    default dev.ikm.tinkar.common.service.ScopeErasure.ChangeSetRemoval removeComponents(java.util.Set<java.util.UUID> components) {
        return dev.ikm.tinkar.common.service.ScopeErasure.ChangeSetRemoval.NONE;
    }
}
