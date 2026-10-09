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
package dev.ikm.tinkar.integration.changeSet;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.ConceptRecord;
import dev.ikm.tinkar.entity.ConceptRecordBuilder;
import dev.ikm.tinkar.entity.ConceptVersionRecord;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.entity.changeset.ComponentTable;
import dev.ikm.tinkar.entity.export.ExportEntitiesToProtobufFile;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.fixtures.ForkedJvm;
import dev.ikm.tinkar.fixtures.OpenSpinedArrayKeyValueProvider;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.fixtures.TestTags;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelTerm;
import dev.ikm.tinkar.terms.State;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A change set carries canceled work as a store reads it (IKE-Network/ike-issues#1276). A
 * version whose stamp is canceled stays in the record but is left out when the record is
 * read ({@code EntityCodec2.read}), so a component with a canceled version among live ones
 * travels with the live ones only, and the stamp that canceled it travels as a stamp; a
 * component whose every version is canceled reads as one with no versions, and travels as
 * such, its identity kept; and a nid minted but never written, which a record still refers
 * to, travels as a referenced-only entry of the component table, minted again on import
 * without a record, so the reference resolves. The three are built on a spined-array store
 * in a JVM of their own, exported in format 3, and restored into a fresh store here.
 */
@ExtendWith(OpenSpinedArrayKeyValueProvider.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag(TestTags.STAGED)
class CanceledComponentsChangeSetIT {
    private static final Logger LOG = LoggerFactory.getLogger(CanceledComponentsChangeSetIT.class);
    private static final File WORK = TestConstants.createFilePathInTargetFromClassName.apply(CanceledComponentsChangeSetIT.class);
    /** Deterministic, so the generating stage and the test agree without sharing anything else. */
    static final UUID LIVE_WITH_CANCELED_VERSION = UUID.nameUUIDFromBytes("canceled-version-concept".getBytes());
    static final UUID CANCELED_ONLY = UUID.nameUUIDFromBytes("canceled-only-concept".getBytes());
    static final UUID CANCELED_STAMP = UUID.nameUUIDFromBytes("the-canceled-stamp".getBytes());
    static final UUID NEVER_WRITTEN = UUID.nameUUIDFromBytes("never-written-component".getBytes());
    static final UUID REFERRING_SEMANTIC = UUID.nameUUIDFromBytes("semantic-referring-to-never-written".getBytes());
    static final UUID REFERRING_TO_CANCELED = UUID.nameUUIDFromBytes("semantic-referring-to-canceled-only".getBytes());
    private static final String STORE = "store";
    private static final String CHANGESET_FILE = "changeset.file";
    private File changeSet;

    @BeforeAll
    void generateChangeSet() {
        FileUtil.recursiveDelete(WORK);
        assertTrue(WORK.mkdirs(), "Could not create " + WORK);
        Properties in = new Properties();
        in.setProperty(STORE, new File(WORK, "generate-store").getPath());
        in.setProperty(CHANGESET_FILE, new File(WORK, "canceled-components-pb.zip").getPath());
        Properties out = ForkedJvm.run(Generate.class, in);
        changeSet = new File(out.getProperty(CHANGESET_FILE));
        assertTrue(changeSet.exists(), "No change set was written");
        assertEquals("1", out.getProperty("live.versions.stored"),
                "the generating store reads the live concept without its canceled version");
    }

    @BeforeEach
    void loadTheStarterSet() {
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
    }

    @Test
    void canceledWorkIsRestoredAsItWas() throws IOException {
        new LoadEntitiesFromProtobufFile(changeSet).compute();

        ConceptEntity<?> live = EntityHandle.get(PublicIds.of(LIVE_WITH_CANCELED_VERSION)).expectConcept();
        assertEquals(1, live.versions().size(), "the live concept reads with its live version only");
        assertEquals(State.ACTIVE, EntityHandle.getStampOrThrow(live.versions().get(0).stampNid()).state());
        StampEntity canceledStamp = EntityHandle.get(PublicIds.of(CANCELED_STAMP)).expectStamp();
        assertEquals(State.CANCELED, canceledStamp.state(), "the stamp that canceled a version travels, canceled");

        // The canceled-only concept reads with no versions in the source, so no record travels;
        // the semantic referring to it carries its identity, minted again without a record.
        SemanticEntity<?> referringToCanceled = EntityHandle.get(PublicIds.of(REFERRING_TO_CANCELED)).expectSemantic();
        long canceledOnly = ((EntityFacade) referringToCanceled.versions().get(0).fieldValues().get(0)).nid();
        assertTrue(EntityHandle.get(canceledOnly).entity().isEmpty(), "the canceled-only concept has no record");
        assertTrue(PublicId.equals(PublicIds.of(CANCELED_ONLY), PrimitiveData.publicId(canceledOnly)),
                "the canceled-only concept keeps its public id");
        assertTrue(EntityService.get().getChronology(canceledOnly).isEmpty(), "and reads as absent, as in the source");

        SemanticEntity<?> referring = EntityHandle.get(PublicIds.of(REFERRING_SEMANTIC)).expectSemantic();
        Object language = referring.versions().get(0).fieldValues().get(0);
        assertTrue(language instanceof EntityFacade, "the language field refers to a component: " + language);
        long neverWritten = ((EntityFacade) language).nid();
        assertTrue(EntityHandle.get(neverWritten).entity().isEmpty(), "the never-written component has no record");
        assertTrue(PublicId.equals(PublicIds.of(NEVER_WRITTEN), PrimitiveData.publicId(neverWritten)),
                "the never-written component keeps its public id");

        List<ComponentTable.Component> referencedOnly = new ArrayList<>();
        try (ZipFile zip = new ZipFile(changeSet)) {
            ComponentTable.forEach(zip, component -> {
                if (component.referencedOnly()) {
                    referencedOnly.add(component);
                }
            });
        }
        List<UUID> referencedUuids = referencedOnly.stream().map(component -> component.uuids()[0]).toList();
        assertEquals(2, referencedOnly.size(), "components the change set references but does not carry: " + referencedUuids);
        assertTrue(referencedUuids.contains(NEVER_WRITTEN) && referencedUuids.contains(CANCELED_ONLY), referencedUuids.toString());
        ComponentTable.Component neverWrittenEntry = referencedOnly.get(referencedUuids.indexOf(NEVER_WRITTEN));
        assertEquals(0, neverWrittenEntry.patternSequence(), "a spined-array nid names no pattern");
        ComponentTable.Component canceledEntry = referencedOnly.get(referencedUuids.indexOf(CANCELED_ONLY));
        assertTrue(canceledEntry.patternSequence() > 0, "a canceled-only concept names the concept pattern");
    }

    /** Builds the three shapes on a store of its own and exports them. */
    static class Generate implements ForkedJvm.Stage {
        @Override
        public void run(Properties in, Properties out) throws Exception {
            out.putAll(in);
            TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, new File(in.getProperty(STORE)));
            try {
                TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
                StampEntity live = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(),
                        KernelTerm.USER.publicId(), KernelTerm.PRIMORDIAL_MODULE.publicId(), KernelTerm.DEVELOPMENT_PATH.publicId());
                EntityService.get().putEntity(live);
                // A canceled stamp as a store holds one: canceled state, no time.
                StampEntity canceled = StampRecord.make(CANCELED_STAMP, State.CANCELED, Long.MIN_VALUE,
                        KernelTerm.USER.publicId(), KernelTerm.PRIMORDIAL_MODULE.publicId(), KernelTerm.DEVELOPMENT_PATH.publicId());
                EntityService.get().putEntity(canceled);

                // A concept with a live version and a canceled one.
                ConceptRecord liveConcept = concept(LIVE_WITH_CANCELED_VERSION, live.nid(), canceled.nid());
                EntityService.get().putEntity(liveConcept);
                out.setProperty("live.versions.stored", Integer.toString(
                        EntityHandle.get(liveConcept.nid()).expectConcept().versions().size()));
                // A concept whose only version is canceled.
                ConceptRecord canceledConcept = concept(CANCELED_ONLY, canceled.nid());
                EntityService.get().putEntity(canceledConcept);

                // A nid minted and never written, and a description whose language refers to it.
                long neverWritten = Entity.nid(PublicIds.of(NEVER_WRITTEN));
                ImmutableList<Object> fields = Lists.immutable.of(
                        EntityProxy.Concept.make(neverWritten),
                        "A description whose language was never written",
                        EntityProxy.Concept.make(KernelTerm.DESCRIPTION_NOT_CASE_SENSITIVE.nid()),
                        EntityProxy.Concept.make(KernelTerm.REGULAR_NAME_DESCRIPTION_TYPE.nid()));
                SemanticRecord referring = SemanticRecord.build(REFERRING_SEMANTIC, KernelTerm.DESCRIPTION_PATTERN.nid(),
                        liveConcept.nid(), live.lastVersion(), fields);
                EntityService.get().putEntity(referring);
                // And a description whose language is the canceled-only concept.
                ImmutableList<Object> fieldsToCanceled = Lists.immutable.of(
                        EntityProxy.Concept.make(canceledConcept.nid()),
                        "A description whose language is canceled",
                        EntityProxy.Concept.make(KernelTerm.DESCRIPTION_NOT_CASE_SENSITIVE.nid()),
                        EntityProxy.Concept.make(KernelTerm.REGULAR_NAME_DESCRIPTION_TYPE.nid()));
                EntityService.get().putEntity(SemanticRecord.build(REFERRING_TO_CANCELED, KernelTerm.DESCRIPTION_PATTERN.nid(),
                        liveConcept.nid(), live.lastVersion(), fieldsToCanceled));

                File changeSet = new File(in.getProperty(CHANGESET_FILE));
                long exported = new ExportEntitiesToProtobufFile(changeSet).compute().getTotalCount();
                LOG.info("Exported {} entities with the canceled work to {}", exported, changeSet);
                out.setProperty("exported", Long.toString(exported));
            } finally {
                TestHelper.stopDatabase();
            }
        }

        private static ConceptRecord concept(UUID uuid, long... stampNids) {
            RecordListBuilder<ConceptVersionRecord> versions = RecordListBuilder.make();
            long nid = ScopedValue.where(PrimitiveData.SCOPED_PATTERN_PUBLICID_FOR_NID, dev.ikm.tinkar.terms.EntityBinding.Concept.pattern().publicId())
                    .call(() -> PrimitiveData.nid(PublicIds.of(uuid)));
            ConceptRecord concept = ConceptRecordBuilder.builder()
                    .leastSignificantBits(uuid.getLeastSignificantBits())
                    .mostSignificantBits(uuid.getMostSignificantBits())
                    .nid(nid)
                    .versions(versions)
                    .build();
            for (int i = 0; i < stampNids.length; i++) {
                ConceptVersionRecord version = new ConceptVersionRecord(concept, stampNids[i]);
                if (i < stampNids.length - 1) {
                    versions.add(version);
                } else {
                    versions.addAndBuild(version); // the last build seals the list
                }
            }
            return concept;
        }
    }
}
