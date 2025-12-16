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
package dev.ikm.tinkar.entity.util;

import dev.ikm.tinkar.common.util.time.Stopwatch;
import dev.ikm.tinkar.component.FieldDataType;
import dev.ikm.tinkar.entity.*;

import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

/**
 * Generic entity processor supporting both byte-based and typed entity processing.
 * Can process any entity type (Stamp, Concept, Pattern, Semantic).
 * <p>
 * Subclasses implement a single method: {@link #process(Entity)}
 *
 * @param <E> The entity type this processor handles (must extend Entity)
 * @param <V> The entity version type (must extend EntityVersion)
 */
public abstract class EntityProcessor<E extends Entity<V>, V extends EntityVersion>
        implements ObjIntConsumer<byte[]>, Consumer<E> {

    LongAdder totalCount = new LongAdder();
    LongAdder conceptCount = new LongAdder();
    LongAdder semanticCount = new LongAdder();
    LongAdder patternCount = new LongAdder();
    LongAdder stampCount = new LongAdder();
    LongAdder other = new LongAdder();
    Stopwatch stopwatch = new Stopwatch();

    /**
     * Entry point from ObjIntConsumer for byte-based processing (legacy mode).
     * Converts bytes to entity and delegates to {@link #process(Entity)}.
     */
    @Override
    public final void accept(byte[] bytes, int nid) {
        // Parse the entity type from bytes
        FieldDataType componentType = FieldDataType.fromToken(bytes[9]);
        totalCount.increment();

        // Update type-specific counters
        switch (componentType) {
            case PATTERN_CHRONOLOGY -> patternCount.increment();
            case CONCEPT_CHRONOLOGY -> conceptCount.increment();
            case SEMANTIC_CHRONOLOGY -> semanticCount.increment();
            case STAMP -> stampCount.increment();
            default -> other.increment();
        }

        // Convert bytes to entity and process
        E entity = unmarshalEntity(bytes);
        if (entity != null) {
            process(entity);
        }
    }

    /**
     * Entry point from Consumer for entity-based processing (modern mode).
     * Directly delegates to {@link #process(Entity)}.
     */
    @Override
    public final void accept(E entity) {
        totalCount.increment();

        // Update type-specific counters
        switch (entity) {
            case StampEntity<?> _ -> stampCount.increment();
            case ConceptEntity<?> _ -> conceptCount.increment();
            case PatternEntity<?> _ -> patternCount.increment();
            case SemanticEntity<?> _ -> semanticCount.increment();
            default -> other.increment();
        }

        process(entity);
    }

    /**
     * The single method subclasses must implement to process an entity.
     * This method is called whether the entity came from bytes or was passed directly.
     *
     * @param entity the entity to process
     */
    protected abstract void process(E entity);

    /**
     * Converts byte array to entity. Subclasses can override for custom unmarshalling.
     * Default implementation uses EntityRecordFactory.
     *
     * @param bytes the entity bytes
     * @return the unmarshalled entity, or null if unmarshalling fails
     */
    @SuppressWarnings("unchecked")
    protected E unmarshalEntity(byte[] bytes) {
        try {
            return (E) EntityRecordFactory.make(bytes);
        } catch (Exception e) {
            // Log or handle unmarshalling errors
            return null;
        }
    }

    public void finish() {
        this.stopwatch.end();
    }

    public String report() {
        finish();
        StringBuilder sb = new StringBuilder();
        sb.append("Finished: ").append(this.getClass().getSimpleName());
        sb.append("\nDuration: ").append(stopwatch.durationString());
        sb.append("\nAverage realization time: ").append(stopwatch.averageDurationForElementString((int) totalCount.sum()));
        if (conceptCount.sum() > 0) {
            sb.append("\nConcepts: ").append(conceptCount);
        }
        if (semanticCount.sum() > 0) {
            sb.append(" Semantics: ").append(semanticCount);
        }
        if (patternCount.sum() > 0) {
            sb.append(" Patterns: ").append(patternCount);
        }
        if (stampCount.sum() > 0) {
            sb.append(" Stamps: ").append(stampCount);
        }
        if (other.sum() > 0) {
            sb.append(" Others: ").append(other);
        }
        sb.append("\nTotal: ").append(totalCount).append("\n\n");

        return sb.toString();
    }
}