package dev.ikm.tinkar.entity.util;

import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityVersion;

public class EntityCounter extends EntityProcessor<Entity<EntityVersion>, EntityVersion> {

    @Override
    protected void process(Entity<EntityVersion> entity) {
        // No need to do anything - counting is handled by the base class
    }
}