package dev.ikm.tinkar.integration.snomed.core;

import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityVersion;

import java.util.*;

public class EntityService {
    static Map<UUID, Integer> uuidToNidMap = new HashMap<>();
    static Map<Integer, UUID> nidToUuidMap = new HashMap<>();
    static Map<Integer, Entity> nidToEntity = new HashMap<>();
    static Map<Entity, UUID> entityToUuid = new HashMap<>();
    static Map<Long, Entity> lsbToEntity = new HashMap<>();

    EntityService() {
        final List<String> conceptValues = Arrays.asList(
                "Active", // nid: 1
                "Inactive", // nid: 2
                "Deloitte User", // nid: 3
                "SNOMED CT Starter Data Module", // nid: 4
                "Development Path", // nid: 5
                "SNOMED CT Author", // nid: 6
                "SNOMED CT identifier", // nid: 7
                "Relationship Modifier Semantic", // nid: 8
                "Relationship Modifier", // nid: 9
                "Concept Relationship Modifier", // nid: 10
                "Relationship Modifier Semantic", // nid: 11
                "Relationship Modifier", // nid: 12
                "Concept Relationship Modifier" // nid: 13
        );
        conceptValues.forEach((x) -> {
            uuidToNidMap.put(UuidT5Generator.get(x), conceptValues.indexOf(x) + 1);
            nidToUuidMap.put(conceptValues.indexOf(x) + 1, UuidT5Generator.get(x));
        });

        // Adding stampUUID to Nid
        uuidToNidMap.put(UUID.fromString("46edbeeb-7ea7-5c08-ae86-0c497c8ac310"), 5);
        nidToUuidMap.put(5, UUID.fromString("46edbeeb-7ea7-5c08-ae86-0c497c8ac310"));
    }

    static EntityService get() {
        return EntityServiceFinder.INSTANCE.service;
    }

    static <T extends Entity<V>, V extends EntityVersion> T getEntityFast(UUID... uuids) {
        return (T) lsbToEntity.get(uuids[0].getLeastSignificantBits());
    }

    static int nidForUuids(UUID... uuids) {
        return uuidToNidMap.getOrDefault(uuids[0], (int) Math.random());
    }

    static void putEntity(Entity entity) {
        int _nid = entity.nid();
        entityToUuid.putIfAbsent(entity, nidToUuidMap.get(_nid));
        nidToEntity.putIfAbsent(_nid, entity);
        lsbToEntity.put(entity.leastSignificantBits(), entity);
    }

    public enum EntityServiceFinder {
        INSTANCE;

        EntityService service;
    }
}
