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
package dev.ikm.tinkar.entity.builder;

import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.SemanticRecordBuilder;
import dev.ikm.tinkar.entity.SemanticVersionRecord;
import dev.ikm.tinkar.entity.SemanticVersionRecordBuilder;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.factory.Lists;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The shared ledger state and replay machinery behind {@link ConceptBuilder} and
 * {@link PatternBuilder}: birth bookkeeping, chronological-scope enforcement, the
 * description ledgers (with their US-dialect acceptability semantics), and the
 * stamp/description writes. Package-private — the builders are the API.
 */
final class ComponentLedger {

    final UUID componentUuid;
    final String birthFqn;

    final List<Stamp> componentStamps = new ArrayList<>();
    final List<DescriptionLedger> descriptions = new ArrayList<>();
    private final Set<UUID> writtenStamps = new HashSet<>();

    private long lastStampTime = Long.MIN_VALUE;
    private boolean born = false;
    private boolean built = false;

    ComponentLedger(UUID componentUuid, String birthFqn) {
        this.componentUuid = componentUuid;
        this.birthFqn = birthFqn;
    }

    boolean born() {
        return born;
    }

    /**
     * Records the birth scope: the component's first version and the fully qualified
     * name description seeded by the birth FQN. Idempotent guard is the caller's
     * {@code born()} check.
     */
    void birth(ActiveStamp stamp) {
        born = true;
        componentStamps.add(stamp);
        DescriptionLedger fqn = new DescriptionLedger(
                UuidT5Generator.get(componentUuid,
                        "fully-qualified-name|" + TinkarTerm.ENGLISH_LANGUAGE.publicId().asUuidArray()[0]),
                TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE, TinkarTerm.ENGLISH_LANGUAGE);
        fqn.versions.add(new VersionEntry<>(stamp, birthFqn));
        fqn.dialects.add(new VersionEntry<>(stamp, TinkarTerm.PREFERRED));
        descriptions.add(fqn);
    }

    void checkChronology(Stamp stamp) {
        if (built) {
            throw new IllegalStateException("This declaration has already been built: " + birthFqn);
        }
        if (stamp.time() < lastStampTime) {
            throw new IllegalArgumentException(
                    "Ledger scopes must be chronological: stamp time " + stamp.time()
                            + " precedes prior scope time " + lastStampTime + " for " + birthFqn);
        }
        lastStampTime = stamp.time();
    }

    void requireBornForRetirement() {
        if (!born) {
            throw new IllegalStateException(
                    "Cannot open a retirement scope before the birth scope: " + birthFqn);
        }
    }

    void markBuilt() {
        if (!born) {
            throw new IllegalStateException("A declaration requires a birth scope: " + birthFqn);
        }
        if (built) {
            throw new IllegalStateException("This declaration has already been built: " + birthFqn);
        }
        built = true;
    }

    /**
     * Adds a new description of the given type; identity is derived from the authoring
     * context: {@code T5(component, kindKey|language|ordinal)}, where the ordinal is the
     * count of same-type descriptions already declared — stable because the ledger is
     * append-only.
     */
    void addDescription(EntityProxy.Concept type, String kindKey, String text, Stamp stamp) {
        long ordinal = descriptions.stream().filter(d -> d.type.equals(type)).count();
        UUID descriptionUuid = UuidT5Generator.get(componentUuid,
                kindKey + "|" + TinkarTerm.ENGLISH_LANGUAGE.publicId().asUuidArray()[0] + "|" + ordinal);
        DescriptionLedger description = new DescriptionLedger(descriptionUuid, type, TinkarTerm.ENGLISH_LANGUAGE);
        description.versions.add(new VersionEntry<>(stamp, text));
        description.dialects.add(new VersionEntry<>(stamp, TinkarTerm.PREFERRED));
        descriptions.add(description);
    }

    /**
     * Resolves the single live description of the given type carrying the given current
     * text — the reference mechanism for revise/retire verbs.
     */
    DescriptionLedger resolveLive(EntityProxy.Concept type, String currentText, String kindLabel) {
        List<DescriptionLedger> matches = descriptions.stream()
                .filter(d -> d.type.equals(type))
                .filter(d -> !d.retired())
                .filter(d -> d.currentText().equals(currentText))
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalArgumentException(
                    "No live " + kindLabel + " with text \"" + currentText + "\" on " + birthFqn);
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException(
                    "Ambiguous reference: " + matches.size() + " live " + kindLabel + "s with text \""
                            + currentText + "\" on " + birthFqn);
        }
        return matches.getFirst();
    }

    /** The fully qualified name's ledger — always the first description (created at birth). */
    DescriptionLedger fqnLedger() {
        return descriptions.getFirst();
    }

    // ------------------------------------------------------------------ replay

    int componentNid() {
        return nidFor(componentUuid);
    }

    /**
     * Writes the stamp entity for a declared stamp if this ledger has not written it
     * yet, and returns its nid. Stamp identity is tuple-derived, so re-writing the same
     * tuple is a no-op merge.
     */
    int writeStamp(Stamp stamp) {
        UUID stampUuid = stamp.publicId().asUuidArray()[0];
        int stampNid = nidFor(stampUuid);
        if (writtenStamps.add(stampUuid)) {
            StampRecord record = StampRecord.make(stampUuid, stamp.state(), stamp.time(),
                    stamp.author().publicId(), stamp.module().publicId(), stamp.path().publicId());
            EntityService.get().putEntity(record);
        }
        return stampNid;
    }

    /** Writes every description ledger and its dialect-acceptability semantic. */
    void writeDescriptions(int componentNid) {
        for (DescriptionLedger description : descriptions) {
            writeDescription(description, componentNid);
        }
    }

    private void writeDescription(DescriptionLedger description, int componentNid) {
        int descriptionNid = nidFor(description.uuid);
        RecordListBuilder<SemanticVersionRecord> versions = RecordListBuilder.make();
        SemanticRecord bootstrap = SemanticRecordBuilder.builder()
                .nid(descriptionNid)
                .mostSignificantBits(description.uuid.getMostSignificantBits())
                .leastSignificantBits(description.uuid.getLeastSignificantBits())
                .patternNid(TinkarTerm.DESCRIPTION_PATTERN.nid())
                .referencedComponentNid(componentNid)
                .versions(versions)
                .build();
        for (VersionEntry<String> version : description.versions) {
            versions.add(SemanticVersionRecordBuilder.builder()
                    .chronology(bootstrap)
                    .stampNid(writeStamp(version.stamp()))
                    .fieldValues(Lists.immutable.of(
                            description.language, version.value(),
                            TinkarTerm.DESCRIPTION_NOT_CASE_SENSITIVE, description.type))
                    .build());
        }
        EntityService.get().putEntity(
                SemanticRecordBuilder.builder(bootstrap).versions(versions.toImmutable()).build());

        writeDialect(description, descriptionNid);
    }

    private void writeDialect(DescriptionLedger description, int descriptionNid) {
        UUID dialectUuid = UuidT5Generator.get(description.uuid, "us-dialect");
        RecordListBuilder<SemanticVersionRecord> versions = RecordListBuilder.make();
        SemanticRecord bootstrap = SemanticRecordBuilder.builder()
                .nid(nidFor(dialectUuid))
                .mostSignificantBits(dialectUuid.getMostSignificantBits())
                .leastSignificantBits(dialectUuid.getLeastSignificantBits())
                .patternNid(TinkarTerm.US_DIALECT_PATTERN.nid())
                .referencedComponentNid(descriptionNid)
                .versions(versions)
                .build();
        for (VersionEntry<EntityProxy.Concept> dialect : description.dialects) {
            versions.add(SemanticVersionRecordBuilder.builder()
                    .chronology(bootstrap)
                    .stampNid(writeStamp(dialect.stamp()))
                    .fieldValues(Lists.immutable.of(dialect.value()))
                    .build());
        }
        EntityService.get().putEntity(
                SemanticRecordBuilder.builder(bootstrap).versions(versions.toImmutable()).build());
    }

    static int nidFor(UUID uuid) {
        return PrimitiveData.nid(PublicIds.of(uuid));
    }

    // ------------------------------------------------------------------ ledger records

    record VersionEntry<T>(Stamp stamp, T value) {
    }

    static final class DescriptionLedger {
        final UUID uuid;
        final EntityProxy.Concept type;
        final ConceptFacade language;
        final List<VersionEntry<String>> versions = new ArrayList<>();
        final List<VersionEntry<EntityProxy.Concept>> dialects = new ArrayList<>();

        DescriptionLedger(UUID uuid, EntityProxy.Concept type, ConceptFacade language) {
            this.uuid = uuid;
            this.type = type;
            this.language = language;
        }

        String currentText() {
            return versions.getLast().value();
        }

        boolean retired() {
            return versions.getLast().stamp() instanceof InactiveStamp;
        }
    }
}
