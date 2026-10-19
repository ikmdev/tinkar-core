package dev.ikm.tinkar.integration.provider.spinedarray;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A component that is referenced but has no entity still has an identity a SpinedArray store can
 * report.
 *
 * <p>Importing a time-range changeset does this: a navigation semantic in it can list a child that
 * changed outside the range and so is not in the file. The child gets a nid from its UUID with no
 * entity behind it. Converting the semantic back to public ids — for the gRPC service, or another
 * export — asks the store which component that nid is, and used to fail with "does not maintain a
 * reverse (nid to public id) identity map".
 */
class SpinedArrayAbsentComponentIdentityIT {

    private static final File DATASTORE_ROOT =
            TestConstants.createFilePathInTargetFromClassName.apply(SpinedArrayAbsentComponentIdentityIT.class);

    @BeforeAll
    static void startEmptySpinedArrayStore() {
        FileUtil.recursiveDelete(DATASTORE_ROOT);
        TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, DATASTORE_ROOT);
    }

    @AfterAll
    static void stopStore() {
        TestHelper.stopDatabase();
    }

    @Test
    void aReferencedButAbsentComponentResolvesToTheIdItWasMintedFrom() {
        PublicId absent = PublicIds.of(UUID.randomUUID());
        long nid = PrimitiveData.nid(absent);

        assertNull(dev.ikm.tinkar.common.service.internal.EntityStore.current().getBytes(nid), "the component should have no entity");
        assertEquals(absent, PrimitiveData.publicId(nid));
        // Asked again, as every conversion of the referring semantic does.
        assertEquals(absent, PrimitiveData.publicId(nid));
    }

    @Test
    void aSaveRecordsTheIdentity_soItSurvivesARestart() throws Exception {
        // The store rebuilds its UUID map from entity bytes when it opens, so a nid with no entity
        // used to come back with no identity at all. The save now writes it down; loading it is
        // SpinedArrayAbsentIdentityReloadIT, since this test harness cannot reopen a store mid-class.
        PublicId absent = PublicIds.of(UUID.randomUUID());
        long nid = PrimitiveData.nid(absent);
        // Through the data controller: PrimitiveData.save() does not reach the store.
        dev.ikm.tinkar.common.service.ServiceLifecycleManager.get()
                .getServicesForGroup(dev.ikm.tinkar.common.service.ServiceExclusionGroup.DATA_PROVIDER)
                .stream()
                .filter(dev.ikm.tinkar.common.service.DataServiceController.class::isInstance)
                .map(service -> (dev.ikm.tinkar.common.service.DataServiceController<?>) service)
                .forEach(dev.ikm.tinkar.common.service.DataServiceController::save);

        String saved = java.nio.file.Files.readString(
                findFile(DATASTORE_ROOT, "absentIdentities.txt").toPath());
        org.junit.jupiter.api.Assertions.assertTrue(
                saved.contains(nid + " " + absent.asUuidArray()[0]), "saved: " + saved);
    }

    private static File findFile(File root, String name) throws java.io.IOException {
        try (var paths = java.nio.file.Files.walk(root.toPath())) {
            return paths.filter(p -> p.getFileName().toString().equals(name)).findFirst()
                    .orElseThrow(() -> new AssertionError(name + " not written under " + root)).toFile();
        }
    }

    @Test
    void aNidMintedButNotYetWrittenIsKnownAbsent_withoutASweep() throws Exception {
        // Vertex identity derivation asks this of every component a definition references, and in
        // a parallel load most are minted before they are written: the answer must come from what
        // the mint recorded, never from a pass over the identity map.
        PublicId absent = PublicIds.of(UUID.randomUUID(), UUID.randomUUID());
        long nid = PrimitiveData.nid(absent);

        org.junit.jupiter.api.Assertions.assertTrue(recordedAbsent(absent.asUuidArray()[0]), "recorded at mint");
        assertEquals(java.util.Set.copyOf(absent.asUuidList().castToList()),
                java.util.Set.copyOf(PrimitiveData.publicId(nid).asUuidList().castToList()));
    }

    @Test
    void aWrittenEntityLeavesTheAbsentSet() throws Exception {
        PublicId id = PublicIds.of(UUID.randomUUID());
        long nid = PrimitiveData.nid(id);
        org.junit.jupiter.api.Assertions.assertTrue(recordedAbsent(id.asUuidArray()[0]), "absent until written");

        dev.ikm.tinkar.entity.StampEntity<?> stamp = dev.ikm.tinkar.entity.StampRecord.make(UUID.randomUUID(),
                dev.ikm.tinkar.terms.State.ACTIVE, System.currentTimeMillis(),
                dev.ikm.tinkar.terms.KernelConcept.USER.publicId(),
                dev.ikm.tinkar.terms.KernelConcept.PRIMORDIAL_MODULE.publicId(),
                dev.ikm.tinkar.terms.KernelConcept.DEVELOPMENT_PATH.publicId());
        dev.ikm.tinkar.entity.EntityService.get().putEntity(stamp);
        dev.ikm.tinkar.entity.RecordListBuilder<dev.ikm.tinkar.entity.ConceptVersionRecord> versions =
                dev.ikm.tinkar.entity.RecordListBuilder.make();
        dev.ikm.tinkar.entity.ConceptRecord concept = dev.ikm.tinkar.entity.ConceptRecordBuilder.builder()
                .leastSignificantBits(id.asUuidArray()[0].getLeastSignificantBits())
                .mostSignificantBits(id.asUuidArray()[0].getMostSignificantBits())
                .nid(nid)
                .versions(versions)
                .build();
        versions.addAndBuild(new dev.ikm.tinkar.entity.ConceptVersionRecord(concept, stamp.nid()));
        dev.ikm.tinkar.entity.EntityService.get().putEntity(concept);

        org.junit.jupiter.api.Assertions.assertFalse(recordedAbsent(id.asUuidArray()[0]), "written, so no longer absent");
        assertEquals(id, PrimitiveData.publicId(nid));
    }

    /** Whether the store's absent-identity record names a UUID. Read through reflection: an implementation detail. */
    private static boolean recordedAbsent(UUID uuid) throws Exception {
        return absentUuids().values().stream().anyMatch(uuids -> uuids.contains(uuid));
    }

    /** The store's absent-identity record, by nid. */
    @SuppressWarnings("unchecked")
    private static java.util.Map<Integer, java.util.List<UUID>> absentUuids() throws Exception {
        Object provider = PrimitiveData.get();
        java.lang.reflect.Field field = provider.getClass().getDeclaredField("absentUuids");
        field.setAccessible(true);
        return (java.util.Map<Integer, java.util.List<UUID>>) field.get(provider);
    }

    @Test
    void everyUuidTheNidWasMintedFromIsReported() {
        PublicId absent = PublicIds.of(UUID.randomUUID(), UUID.randomUUID());
        long nid = PrimitiveData.nid(absent);

        assertEquals(java.util.Set.copyOf(absent.asUuidList().castToList()),
                java.util.Set.copyOf(PrimitiveData.publicId(nid).asUuidList().castToList()));
    }
}
