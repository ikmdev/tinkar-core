package dev.ikm.tinkar.entity.util;

import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityVersion;

public class EntityRealizer extends EntityProcessor<Entity<EntityVersion>, EntityVersion> {

    @Override
    protected void process(Entity<EntityVersion> entity) {
        // Entity is already realized by unmarshalEntity()
        // This processor just needs to load entities into memory
    }
}