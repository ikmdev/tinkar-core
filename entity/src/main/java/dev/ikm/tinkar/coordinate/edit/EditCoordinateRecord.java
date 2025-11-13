/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.coordinate.edit;

import dev.ikm.tinkar.collection.ConcurrentReferenceHashMap;
import dev.ikm.tinkar.common.binary.Decoder;
import dev.ikm.tinkar.common.binary.DecoderInput;
import dev.ikm.tinkar.common.binary.Encodable;
import dev.ikm.tinkar.common.binary.Encoder;
import dev.ikm.tinkar.common.binary.EncoderOutput;
import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.coordinate.ImmutableCoordinate;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.terms.ConceptFacade;

import java.util.Objects;

/**
 * Immutable record implementation of {@link EditCoordinate} that defines the metadata used when
 * creating or modifying Component data. This coordinate specifies the author, module, and path
 * information that will be applied to STAMPs (Status, Time, Author, Module, Path) when
 * editing data.
 * <p>
 * The EditCoordinate supports three different types of editing operations:
 * <ul>
 *     <li><b>Developing:</b> Creating new content or modifying existing content on the default path.
 *         New content uses the default module, while existing content retains its original module.</li>
 *     <li><b>Modularizing:</b> Moving existing content to a different module (the destination module)
 *         while keeping it on the same path.</li>
 *     <li><b>Promoting:</b> Copying content from the default path to the promotion path, preserving
 *         the module assignment.</li>
 * </ul>
 * <p>
 * This implementation uses weak reference caching via the SINGLETONS map to ensure that equivalent
 * coordinate instances are reused, reducing memory overhead.
 *
 * @param authorNid The NID of the author concept who is making the changes. This is recorded in
 *                  the STAMP for all versions created with this coordinate.
 * @param defaultModuleNid The NID of the default module concept used for new content when developing.
 *                         Modifications to existing content retain their original module.
 * @param destinationModuleNid The NID of the destination module concept used when modularizing existing
 *                             content. This is the module that content is moved to during modularization operations.
 * @param defaultPathNid The NID of the default path concept where new content is created and where edits
 *                       are made when developing.
 * @param promotionPathNid The NID of the promotion path concept used when promoting content. This is the
 *                         path where content is copied to during promotion operations.
 */
public record EditCoordinateRecord(int authorNid, int defaultModuleNid, int destinationModuleNid, int defaultPathNid, int promotionPathNid)
        implements EditCoordinate, ImmutableCoordinate {

    private static final ConcurrentReferenceHashMap<EditCoordinateRecord, EditCoordinateRecord> SINGLETONS =
            new ConcurrentReferenceHashMap<>(ConcurrentReferenceHashMap.ReferenceType.WEAK,
                    ConcurrentReferenceHashMap.ReferenceType.WEAK);

    /**
     * Creates a simplified EditCoordinate where the same module and path are used for both
     * developing and modularizing/promoting operations. This is a convenience method for common
     * scenarios where content doesn't need to be moved between modules or paths.
     * <p>
     * This method returns a cached instance if one already exists with the same parameters,
     * ensuring efficient memory usage.
     *
     * @param authorNid The NID of the author concept making the changes
     * @param moduleNid The NID of the module concept used for both developing and modularizing activities.
     *                  This becomes both the defaultModuleNid and destinationModuleNid.
     * @param pathNid The NID of the path concept used for both default edits and promotion.
     *                This becomes both the defaultPathNid and promotionPathNid.
     * @return An EditCoordinateRecord configured with the specified parameters
     */
    public static EditCoordinateRecord make(int authorNid, int moduleNid, int pathNid) {
        return SINGLETONS.computeIfAbsent(new EditCoordinateRecord(authorNid, moduleNid, moduleNid, pathNid, pathNid),
                editCoordinateImmutable -> editCoordinateImmutable);
    }

    /**
     * Creates an EditCoordinate with full control over all module and path configurations. This allows for
     * separate configuration of default and destination modules, as well as default and promotion paths.
     * <p>
     * This method returns a cached instance if one already exists with the same parameters,
     * ensuring efficient memory usage.
     *
     * @param author The author concept who is making the changes. The author NID from this concept
     *               is recorded in the STAMP for all versions created with this coordinate.
     * @param defaultModule The default module concept used for new content when developing.
     *                      Modifications to existing content retain their original module.
     * @param destinationModule The destination module concept that existing content is moved to
     *                          when performing modularization operations.
     * @param defaultPath The path concept where new content is created and where edits are made
     *                    when developing.
     * @param promotionPath The promotion path concept where existing content is copied to when
     *                      performing promotion operations.
     * @return An EditCoordinateRecord configured with the specified parameters
     */
    public static EditCoordinateRecord make(ConceptFacade author, ConceptFacade defaultModule, ConceptFacade destinationModule,
                                            ConceptFacade defaultPath, ConceptFacade promotionPath) {
        return make(Entity.nid(author), Entity.nid(defaultModule), Entity.nid(destinationModule), Entity.nid(defaultPath), Entity.nid(promotionPath));
    }

    /**
     * Creates an EditCoordinate with full control over all module and path configurations using
     * NID parameters. This allows for separate configuration of default and destination modules,
     * as well as default and promotion paths.
     * <p>
     * This method returns a cached instance if one already exists with the same parameters,
     * ensuring efficient memory usage.
     *
     * @param authorNid The NID of the author concept making the changes. This is recorded in
     *                  the STAMP for all versions created with this coordinate.
     * @param defaultModuleNid The NID of the default module concept used for new content when developing.
     *                         Modifications to existing content retain their original module.
     * @param destinationModuleNid The NID of the destination module concept that existing content is moved to
     *                             when performing modularization operations.
     * @param defaultPathNid The NID of the path concept where new content is created and where edits are made
     *                       when developing.
     * @param promotionPathNid The NID of the promotion path concept where existing content is copied to when
     *                         performing promotion operations.
     * @return An EditCoordinateRecord configured with the specified parameters
     */
    public static EditCoordinateRecord make(int authorNid, int defaultModuleNid, int destinationModuleNid, int defaultPathNid, int promotionPathNid) {
        return SINGLETONS.computeIfAbsent(new EditCoordinateRecord(authorNid, defaultModuleNid, destinationModuleNid, defaultPathNid, promotionPathNid),
                editCoordinateImmutable -> editCoordinateImmutable);
    }

    /**
     * Deserializes an EditCoordinateRecord from binary input using the Tinkar binary format.
     * This method reads the five NID values (author, default module, destination module, default path,
     * and promotion path) from the decoder input and reconstructs an EditCoordinateRecord instance.
     * <p>
     * The returned instance is cached in the SINGLETONS map to ensure efficient memory usage.
     *
     * @param in The decoder input to read from
     * @return A decoded EditCoordinateRecord instance
     */
    @Decoder
    public static EditCoordinateRecord decode(DecoderInput in) {
        switch (Encodable.checkVersion(in)) {
            default:
                return SINGLETONS.computeIfAbsent(new EditCoordinateRecord(in.readNid(), in.readNid(), in.readNid(), in.readNid(), in.readNid()),
                        editCoordinateImmutable -> editCoordinateImmutable);
        }
    }

    /**
     * Serializes this EditCoordinateRecord to binary output using the Tinkar binary format.
     * This method writes the five NID values (author, default module, destination module, default path,
     * and promotion path) to the encoder output in sequence.
     *
     * @param out The encoder output to write to
     */
    @Override
    @Encoder
    public void encode(EncoderOutput out) {
        out.writeNid(this.authorNid);
        out.writeNid(this.defaultModuleNid);
        out.writeNid(this.destinationModuleNid);
        out.writeNid(this.defaultPathNid);
        out.writeNid(this.promotionPathNid);
    }

    @Override
    public String toString() {
        return "EditCoordinateRecord{" +
                toUserString() +
                '}';
    }

    /**
     * Gets the NID of the author concept making changes. This value is used as the 'Author'
     * component of the STAMP (Status, Time, Author, Module, Path) when creating or modifying
     * component versions.
     * <p>
     * For example, when creating a transaction to update a semantic:
     * <pre>
     * StampEntity stamp = transaction.getStamp(
     *     State.ACTIVE,
     *     editCoordinate.getAuthorNidForChanges(), // Author from this coordinate
     *     editCoordinate.getDefaultModuleNid(),    // Module for the change
     *     editCoordinate.getDefaultPathNid());     // Path for the change
     * </pre>
     *
     * @return The NID of the author concept
     */
    @Override
    public int getAuthorNidForChanges() {
        return this.authorNid;
    }

    /**
     * Gets the NID of the default module concept used for new content when developing.
     * This value is used as the 'Module' component of the STAMP (Status, Time, Author, Module, Path)
     * when creating new component versions.
     * <p>
     * For example, when creating a transaction for new content:
     * <pre>
     * StampEntity stamp = transaction.getStamp(
     *     State.ACTIVE,
     *     editCoordinate.getAuthorNidForChanges(),
     *     editCoordinate.getDefaultModuleNid(),    // Used for new content
     *     editCoordinate.getDefaultPathNid());
     * </pre>
     *
     * @return The NID of the default module concept
     */
    @Override
    public int getDefaultModuleNid() {
        return this.defaultModuleNid;
    }

    /**
     * Gets the NID of the destination module concept used when modularizing existing content.
     * This value is used as the 'Module' component of the STAMP when moving content from one
     * module to another.
     * <p>
     * For example, when modularizing content:
     * <pre>
     * StampEntity stamp = transaction.getStamp(
     *     State.ACTIVE,
     *     editCoordinate.getAuthorNidForChanges(),
     *     editCoordinate.getDestinationModuleNid(), // Used when modularizing
     *     editCoordinate.getDefaultPathNid());
     * </pre>
     *
     * @return The NID of the destination module concept
     */
    @Override
    public int getDestinationModuleNid() {
        return this.destinationModuleNid;
    }

    /**
     * Gets the NID of the default path concept where new content is created and edits are made
     * when developing. This value is used as the 'Path' component of the STAMP (Status, Time,
     * Author, Module, Path) when creating or modifying component versions. Paths in Tinkar enable
     * parallel development and versioning similar to branches in version control systems.
     * <p>
     * For example, when creating or editing content:
     * <pre>
     * StampEntity stamp = transaction.getStamp(
     *     State.ACTIVE,
     *     editCoordinate.getAuthorNidForChanges(),
     *     editCoordinate.getDefaultModuleNid(),
     *     editCoordinate.getDefaultPathNid());    // Used for development
     * </pre>
     *
     * @return The NID of the default path concept
     */
    @Override
    public int getDefaultPathNid() {
        return this.defaultPathNid;
    }

    /**
     * Gets the NID of the promotion path concept used when promoting content. This value is used
     * as the 'Path' component of the STAMP when copying content from the default path to a
     * promotion path (such as moving from development to production). Promotion allows content to
     * move through different stages of approval or deployment while maintaining version history.
     * <p>
     * For example, when promoting content:
     * <pre>
     * StampEntity stamp = transaction.getStamp(
     *     State.ACTIVE,
     *     editCoordinate.getAuthorNidForChanges(),
     *     originalModuleNid,                        // Module is preserved
     *     editCoordinate.getPromotionPathNid());    // Used for promotion
     * </pre>
     *
     * @return The NID of the promotion path concept
     */
    @Override
    public int getPromotionPathNid() {
        return this.promotionPathNid;
    }

    /**
     * Returns this EditCoordinateRecord instance. This method is part of the {@link EditCoordinate}
     * interface contract and allows implementations to provide a record representation.
     * Since this class is already a record, it simply returns itself.
     *
     * @return This EditCoordinateRecord instance
     */
    @Override
    public EditCoordinateRecord toEditCoordinateRecord() {
        return this;
    }

    /**
     * Cache provider implementation for managing the singleton instance cache of EditCoordinateRecords.
     * This service allows the cache to be cleared when needed, such as during system resets or testing.
     * The cache uses weak references to allow garbage collection when instances are no longer in use.
     */
    public static class CacheProvider implements CachingService {
        // TODO: this has implicit assumption that no one will hold on to a calculator... Should we be defensive?

        /**
         * Clears the EditCoordinateRecord singleton cache. This method is called during system
         * resets or when cached instances need to be invalidated. After calling this method,
         * new instances will be created for subsequent make() calls until the cache is repopulated.
         * <p>
         * Note: This assumes that no external references to coordinate instances are held by
         * calculators or other components. Clearing the cache while instances are still in use
         * may result in duplicate coordinate instances with identical values.
         */
        @Override
        public void reset() {
            SINGLETONS.clear();
        }
    }
}
