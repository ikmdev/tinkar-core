package dev.ikm.tinkar.service.service;

import dev.ikm.tinkar.service.dto.LanguageCoordinateDto;
import dev.ikm.tinkar.service.dto.NavigationCoordinateDto;
import dev.ikm.tinkar.service.dto.SavedLanguageCoordinateResponse;
import dev.ikm.tinkar.service.dto.SavedNavigationCoordinateResponse;
import dev.ikm.tinkar.service.dto.SavedStampCoordinateResponse;
import dev.ikm.tinkar.service.dto.StampCoordinateDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.language.LanguageCoordinateRecord;
import dev.ikm.tinkar.coordinate.navigation.NavigationCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StampCoordinateRecord;
import dev.ikm.tinkar.entity.ConceptRecord;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.terms.State;
import dev.ikm.tinkar.terms.TinkarTerm;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.collections.api.factory.Lists;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages saved coordinate configurations as first-class Tinkar entities in the loaded dataset.
 *
 * <p>Each saved coordinate consists of two entities written in one transaction:
 * <ol>
 *   <li>A {@code ConceptRecord} whose UUID is derived deterministically from the coordinate's
 *       content — identical settings always produce the same UUID (idempotent saves).</li>
 *   <li>A {@code SemanticRecord} attached to that concept using a <em>type-specific</em> pattern
 *       ({@link #STAMP_COORDINATE_PATTERN_UUID}, {@link #NAVIGATION_COORDINATE_PATTERN_UUID}, or
 *       {@link #LANGUAGE_COORDINATE_PATTERN_UUID}), with a single field: JSON-serialized coordinate
 *       settings.</li>
 * </ol>
 *
 * <p>Coordinates are stored in RocksDB and survive server restarts.
 * Listing uses {@code PrimitiveData.get().semanticNidsForComponent(registryNid)},
 * keyed by the registry concept's full 64-bit {@code longKeyForNid} — immune to
 * the NidCodec6 element-sequence collision that breaks {@code semanticNidsOfPattern}.
 */
@Component
@Slf4j
public class CoordinateStoreService {

    /**
     * Registry concept UUIDs — one per coordinate type.
     *
     * <p>All saved coordinate semantics use the registry concept as their
     * {@code referencedComponentNid}.  Listing uses
     * {@code PrimitiveData.semanticNidsForComponent(registryNid)}, which is indexed
     * by the full 64-bit {@code longKeyForNid(componentNid)} and therefore immune to
     * the NidCodec6 element-sequence collision that breaks {@code semanticNidsOfPattern}.
     */
    static final UUID STAMP_COORDINATE_REGISTRY_UUID =
            UUID.fromString("1409ec9e-3240-41ec-86e4-55a2d3f69968");

    static final UUID NAVIGATION_COORDINATE_REGISTRY_UUID =
            UUID.fromString("f770d677-d5c3-46db-bb5e-df489cb0887b");

    static final UUID LANGUAGE_COORDINATE_REGISTRY_UUID =
            UUID.fromString("f2821ad0-e3eb-45b8-9ad5-7d81c87714bc");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile int stampRegistryNid = -1;
    private volatile int navigationRegistryNid = -1;
    private volatile int languageRegistryNid = -1;

    // ────────────────────────────────────────────────────────────────────────
    // Stamp coordinate

    /**
     * Save a StampCoordinate to the dataset. If an identical coordinate was previously saved
     * the existing record is returned unchanged (idempotent).
     */
    public SavedStampCoordinateResponse saveStamp(StampCoordinateDto dto) {
        StampCoordinateRecord record = CoordinateFactory.buildStampCoordinate(dto);
        UUID coordinateUuid = record.getStampFilterUuid();
        int registryNid = stampRegistryNid();

        Optional<SavedStampCoordinateResponse> existing = findStampById(coordinateUuid.toString());
        if (existing.isPresent()) {
            log.debug("Stamp coordinate {} already saved, returning existing", coordinateUuid);
            return existing.get();
        }

        long now = System.currentTimeMillis();
        String settingsJson;
        try {
            settingsJson = objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize stamp coordinate settings", e);
        }

        Transaction tx = Transaction.make("Save stamp coordinate: " + coordinateUuid);
        try {
            StampEntity<?> stamp = tx.getStamp(
                    State.ACTIVE, now,
                    TinkarTerm.USER.nid(),
                    TinkarTerm.SOLOR_OVERLAY_MODULE.nid(),
                    TinkarTerm.DEVELOPMENT_PATH.nid());

            SemanticRecord semantic = SemanticRecord.build(
                    UUID.randomUUID(),
                    registryNid,
                    registryNid,
                    stamp.versions().get(0),
                    Lists.immutable.of(coordinateUuid.toString(), settingsJson));
            EntityService.get().putEntity(semantic);
            tx.addComponent(semantic);

            tx.commit();

            log.info("Saved stamp coordinate with id {}", coordinateUuid);
            return new SavedStampCoordinateResponse(
                    coordinateUuid.toString(), dto, Instant.ofEpochMilli(now).toString());

        } catch (Exception e) {
            tx.cancel();
            throw new RuntimeException("Failed to save stamp coordinate", e);
        }
    }

    /** List all saved StampCoordinates in the dataset. */
    public List<SavedStampCoordinateResponse> findAllStamp() {
        int[] semanticNids = PrimitiveData.get().semanticNidsForComponent(stampRegistryNid());
        List<SavedStampCoordinateResponse> results = new ArrayList<>();
        for (int sNid : semanticNids) {
            deserializeStampSemantic(sNid).ifPresent(results::add);
        }
        return results;
    }

    /** Look up a saved StampCoordinate by its content-derived UUID string. */
    public Optional<SavedStampCoordinateResponse> findStampById(String coordinateId) {
        int[] semanticNids = PrimitiveData.get().semanticNidsForComponent(stampRegistryNid());
        for (int sNid : semanticNids) {
            Optional<SavedStampCoordinateResponse> result = deserializeStampSemantic(sNid);
            if (result.isPresent() && result.get().id().equals(coordinateId)) {
                return result;
            }
        }
        return Optional.empty();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Navigation coordinate

    /**
     * Save a NavigationCoordinate to the dataset. Idempotent — identical settings return
     * the same UUID.
     */
    public SavedNavigationCoordinateResponse saveNavigation(NavigationCoordinateDto dto) {
        NavigationCoordinateRecord record = CoordinateFactory.buildNavigationCoordinate(dto);
        UUID coordinateUuid = record.getNavigationCoordinateUuid();
        int registryNid = navigationRegistryNid();

        Optional<SavedNavigationCoordinateResponse> existing = findNavigationById(coordinateUuid.toString());
        if (existing.isPresent()) {
            log.debug("Navigation coordinate {} already saved, returning existing", coordinateUuid);
            return existing.get();
        }

        long now = System.currentTimeMillis();
        String settingsJson;
        try {
            settingsJson = objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize navigation coordinate settings", e);
        }

        Transaction tx = Transaction.make("Save navigation coordinate: " + coordinateUuid);
        try {
            StampEntity<?> stamp = tx.getStamp(
                    State.ACTIVE, now,
                    TinkarTerm.USER.nid(),
                    TinkarTerm.SOLOR_OVERLAY_MODULE.nid(),
                    TinkarTerm.DEVELOPMENT_PATH.nid());

            SemanticRecord semantic = SemanticRecord.build(
                    UUID.randomUUID(),
                    registryNid,
                    registryNid,
                    stamp.versions().get(0),
                    Lists.immutable.of(coordinateUuid.toString(), settingsJson));
            EntityService.get().putEntity(semantic);
            tx.addComponent(semantic);

            tx.commit();

            log.info("Saved navigation coordinate with id {}", coordinateUuid);
            return new SavedNavigationCoordinateResponse(
                    coordinateUuid.toString(), dto, Instant.ofEpochMilli(now).toString());

        } catch (Exception e) {
            tx.cancel();
            throw new RuntimeException("Failed to save navigation coordinate", e);
        }
    }

    /** List all saved NavigationCoordinates in the dataset. */
    public List<SavedNavigationCoordinateResponse> findAllNavigation() {
        int[] semanticNids = PrimitiveData.get().semanticNidsForComponent(navigationRegistryNid());
        List<SavedNavigationCoordinateResponse> results = new ArrayList<>();
        for (int sNid : semanticNids) {
            deserializeNavigationSemantic(sNid).ifPresent(results::add);
        }
        return results;
    }

    /** Look up a saved NavigationCoordinate by its content-derived UUID string. */
    public Optional<SavedNavigationCoordinateResponse> findNavigationById(String coordinateId) {
        int[] semanticNids = PrimitiveData.get().semanticNidsForComponent(navigationRegistryNid());
        for (int sNid : semanticNids) {
            Optional<SavedNavigationCoordinateResponse> result = deserializeNavigationSemantic(sNid);
            if (result.isPresent() && result.get().id().equals(coordinateId)) {
                return result;
            }
        }
        return Optional.empty();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Language coordinate

    /**
     * Save a LanguageCoordinate to the dataset. Idempotent — identical settings return
     * the same UUID.
     */
    public SavedLanguageCoordinateResponse saveLanguage(LanguageCoordinateDto dto) {
        LanguageCoordinateRecord record = CoordinateFactory.buildLanguageCoordinate(dto);
        UUID coordinateUuid = record.getLanguageCoordinateUuid();
        int registryNid = languageRegistryNid();

        Optional<SavedLanguageCoordinateResponse> existing = findLanguageById(coordinateUuid.toString());
        if (existing.isPresent()) {
            log.debug("Language coordinate {} already saved, returning existing", coordinateUuid);
            return existing.get();
        }

        long now = System.currentTimeMillis();
        String settingsJson;
        try {
            settingsJson = objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize language coordinate settings", e);
        }

        Transaction tx = Transaction.make("Save language coordinate: " + coordinateUuid);
        try {
            StampEntity<?> stamp = tx.getStamp(
                    State.ACTIVE, now,
                    TinkarTerm.USER.nid(),
                    TinkarTerm.SOLOR_OVERLAY_MODULE.nid(),
                    TinkarTerm.DEVELOPMENT_PATH.nid());

            SemanticRecord semantic = SemanticRecord.build(
                    UUID.randomUUID(),
                    registryNid,
                    registryNid,
                    stamp.versions().get(0),
                    Lists.immutable.of(coordinateUuid.toString(), settingsJson));
            EntityService.get().putEntity(semantic);
            tx.addComponent(semantic);

            tx.commit();

            log.info("Saved language coordinate with id {}", coordinateUuid);
            return new SavedLanguageCoordinateResponse(
                    coordinateUuid.toString(), dto, Instant.ofEpochMilli(now).toString());

        } catch (Exception e) {
            tx.cancel();
            throw new RuntimeException("Failed to save language coordinate", e);
        }
    }

    /** List all saved LanguageCoordinates in the dataset. */
    public List<SavedLanguageCoordinateResponse> findAllLanguage() {
        int[] semanticNids = PrimitiveData.get().semanticNidsForComponent(languageRegistryNid());
        List<SavedLanguageCoordinateResponse> results = new ArrayList<>();
        for (int sNid : semanticNids) {
            deserializeLanguageSemantic(sNid).ifPresent(results::add);
        }
        return results;
    }

    /** Look up a saved LanguageCoordinate by its content-derived UUID string. */
    public Optional<SavedLanguageCoordinateResponse> findLanguageById(String coordinateId) {
        int[] semanticNids = PrimitiveData.get().semanticNidsForComponent(languageRegistryNid());
        for (int sNid : semanticNids) {
            Optional<SavedLanguageCoordinateResponse> result = deserializeLanguageSemantic(sNid);
            if (result.isPresent() && result.get().id().equals(coordinateId)) {
                return result;
            }
        }
        return Optional.empty();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Registry NID helpers (lazy, double-checked locking)

    private int stampRegistryNid() {
        if (stampRegistryNid != -1) return stampRegistryNid;
        synchronized (this) {
            if (stampRegistryNid != -1) return stampRegistryNid;
            stampRegistryNid = resolveOrCreateRegistryConcept(STAMP_COORDINATE_REGISTRY_UUID, "STAMP_COORDINATE_REGISTRY");
        }
        return stampRegistryNid;
    }

    private int navigationRegistryNid() {
        if (navigationRegistryNid != -1) return navigationRegistryNid;
        synchronized (this) {
            if (navigationRegistryNid != -1) return navigationRegistryNid;
            navigationRegistryNid = resolveOrCreateRegistryConcept(NAVIGATION_COORDINATE_REGISTRY_UUID, "NAVIGATION_COORDINATE_REGISTRY");
        }
        return navigationRegistryNid;
    }

    private int languageRegistryNid() {
        if (languageRegistryNid != -1) return languageRegistryNid;
        synchronized (this) {
            if (languageRegistryNid != -1) return languageRegistryNid;
            languageRegistryNid = resolveOrCreateRegistryConcept(LANGUAGE_COORDINATE_REGISTRY_UUID, "LANGUAGE_COORDINATE_REGISTRY");
        }
        return languageRegistryNid;
    }

    /**
     * Returns the NID for {@code registryUuid}, creating a {@code ConceptRecord} stub if absent.
     * The registry concept is used solely as the {@code referencedComponentNid} anchor for
     * coordinate semantics; lookup is via {@code semanticNidsForComponent} (exact 64-bit key),
     * not {@code semanticNidsOfPattern} (which suffers from NidCodec6 element-sequence collisions).
     */
    private int resolveOrCreateRegistryConcept(UUID registryUuid, String label) {
        PublicId pid = PublicIds.of(registryUuid);
        try {
            return EntityService.get().nidForPublicId(pid);
        } catch (IllegalStateException e) {
            // UUID not yet in DB — first use; create the registry concept stub.
        }
        Transaction tx = Transaction.make("Init " + label);
        try {
            StampEntity<?> stamp = tx.getStamp(
                    State.ACTIVE, System.currentTimeMillis(),
                    TinkarTerm.USER.nid(),
                    TinkarTerm.SOLOR_OVERLAY_MODULE.nid(),
                    TinkarTerm.DEVELOPMENT_PATH.nid());
            ConceptRecord stub = ConceptRecord.build(registryUuid, stamp.versions().get(0));
            EntityService.get().putEntity(stub);
            tx.addComponent(stub);
            tx.commit();
        } catch (Exception e) {
            tx.cancel();
            throw new RuntimeException("Failed to initialize " + label, e);
        }
        int nid = EntityService.get().nidForPublicId(PublicIds.of(registryUuid));
        log.debug("Created {} registry concept (nid={})", label, nid);
        return nid;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Deserialization helpers
    // field[0] = coordinate UUID string, field[1] = settings JSON

    private Optional<SavedStampCoordinateResponse> deserializeStampSemantic(int sNid) {
        try {
            Optional<Entity<?>> entityOpt = EntityService.get().packagePrivateGetEntity(sNid);
            if (entityOpt.isEmpty() || !(entityOpt.get() instanceof SemanticEntity<?> sem) || sem.versions().isEmpty()) {
                return Optional.empty();
            }
            SemanticEntityVersion ver = (SemanticEntityVersion) sem.versions().get(0);
            String id = (String) ver.fieldValues().get(0);
            String json = (String) ver.fieldValues().get(1);
            StampCoordinateDto settings = objectMapper.readValue(json, StampCoordinateDto.class);
            long time = EntityService.get().getStampFast(ver.stampNid()).time();
            return Optional.of(new SavedStampCoordinateResponse(id, settings, Instant.ofEpochMilli(time).toString()));
        } catch (Exception e) {
            log.warn("Failed to deserialize stamp coordinate semantic nid={}: {}", sNid, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<SavedNavigationCoordinateResponse> deserializeNavigationSemantic(int sNid) {
        try {
            Optional<Entity<?>> entityOpt = EntityService.get().packagePrivateGetEntity(sNid);
            if (entityOpt.isEmpty() || !(entityOpt.get() instanceof SemanticEntity<?> sem) || sem.versions().isEmpty()) {
                return Optional.empty();
            }
            SemanticEntityVersion ver = (SemanticEntityVersion) sem.versions().get(0);
            String id = (String) ver.fieldValues().get(0);
            String json = (String) ver.fieldValues().get(1);
            NavigationCoordinateDto settings = objectMapper.readValue(json, NavigationCoordinateDto.class);
            long time = EntityService.get().getStampFast(ver.stampNid()).time();
            return Optional.of(new SavedNavigationCoordinateResponse(id, settings, Instant.ofEpochMilli(time).toString()));
        } catch (Exception e) {
            log.warn("Failed to deserialize navigation coordinate semantic nid={}: {}", sNid, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<SavedLanguageCoordinateResponse> deserializeLanguageSemantic(int sNid) {
        try {
            Optional<Entity<?>> entityOpt = EntityService.get().packagePrivateGetEntity(sNid);
            if (entityOpt.isEmpty() || !(entityOpt.get() instanceof SemanticEntity<?> sem) || sem.versions().isEmpty()) {
                return Optional.empty();
            }
            SemanticEntityVersion ver = (SemanticEntityVersion) sem.versions().get(0);
            String id = (String) ver.fieldValues().get(0);
            String json = (String) ver.fieldValues().get(1);
            LanguageCoordinateDto settings = objectMapper.readValue(json, LanguageCoordinateDto.class);
            long time = EntityService.get().getStampFast(ver.stampNid()).time();
            return Optional.of(new SavedLanguageCoordinateResponse(id, settings, Instant.ofEpochMilli(time).toString()));
        } catch (Exception e) {
            log.warn("Failed to deserialize language coordinate semantic nid={}: {}", sNid, e.getMessage());
            return Optional.empty();
        }
    }
}
