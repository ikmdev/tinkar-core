package dev.ikm.tinkar.entity;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A fluent handle for an entity that may be absent or one of the four entity subtypes
 * (Concept, Semantic, Pattern, Stamp).
 * <p>
 * Provides three complementary patterns for type-safe entity processing without manual
 * instanceof checks or casts. Choose the right pattern based on your use case.
 *
 * <h2>The Three Patterns: When to Use Each</h2>
 *
 * <table border="1" cellpadding="5">
 * <caption>Entity Access Patterns</caption>
 * <tr>
 *   <th>Pattern</th>
 *   <th>Method</th>
 *   <th>Return Type</th>
 *   <th>Use When</th>
 *   <th>Example</th>
 * </tr>
 * <tr>
 *   <td><b>Side Effects</b></td>
 *   <td>{@code ifConcept()}</td>
 *   <td>{@code EntityHandle}</td>
 *   <td>Executing code without returning a value (logging, state updates)</td>
 *   <td>{@code .ifConcept(c -> log.info(c))}</td>
 * </tr>
 * <tr>
 *   <td><b>Safe Extraction</b></td>
 *   <td>{@code asConcept()}</td>
 *   <td>{@code Optional<ConceptEntity>}</td>
 *   <td>Wrong type is a valid possibility (user input, filtering)</td>
 *   <td>{@code .asConcept().orElse(null)}</td>
 * </tr>
 * <tr>
 *   <td><b>Assertion</b></td>
 *   <td>{@code expectConcept()}</td>
 *   <td>{@code ConceptEntity}</td>
 *   <td>Wrong type indicates data corruption (data model guarantees)</td>
 *   <td>{@code .expectConcept()}</td>
 * </tr>
 * </table>
 *
 * <h2>Pattern 1: Side Effects with {@code ifXxx} Methods</h2>
 * <p>
 * <b>When to use:</b> You want to execute code based on entity type but don't need to return anything.
 * <br><b>Returns:</b> {@code this} for chaining.
 * <br><b>Key benefit:</b> Fluent branching without if/else statements.
 *
 * <pre>{@code
 * // Logging and state updates
 * EntityHandle.get(nid)
 *     .ifConcept(concept -> log.info("Processing concept: {}", concept))
 *     .ifSemantic(semantic -> updateIndex(semantic))
 *     .ifAbsent(() -> log.warn("Entity not found"));
 *
 * // Handle all four types
 * EntityHandle.get(nid)
 *     .ifConcept(this::handleConcept)
 *     .ifSemantic(this::handleSemantic)
 *     .ifPattern(this::handlePattern)
 *     .ifStamp(this::handleStamp);
 * }</pre>
 *
 * <h2>Pattern 2: Safe Extraction with {@code asXxx} Methods</h2>
 * <p>
 * <b>When to use:</b> Wrong type is a valid possibility (user input, filtering, exploratory queries).
 * <br><b>Returns:</b> {@code Optional<T>} - allows standard Optional operations.
 * <br><b>Key benefit:</b> Explicit handling of the "not this type" case.
 *
 * <pre>{@code
 * // User input - might be any type
 * public String getDisplayName(int userEnteredNid) {
 *     return EntityHandle.get(userEnteredNid)
 *         .asConcept()
 *         .map(concept -> concept.getDescription())
 *         .orElse("Not a concept");
 * }
 *
 * // Filtering a collection - keep only concepts
 * List<ConceptEntity> concepts = nids.stream()
 *     .map(EntityHandle::get)
 *     .map(handle -> handle.asConcept())
 *     .flatMap(Optional::stream)
 *     .toList();
 *
 * // Transformation chains
 * Optional<String> result = EntityHandle.get(nid)
 *     .asSemantic()
 *     .filter(semantic -> semantic.active())
 *     .map(semantic -> semantic.fieldValues().get(0).toString());
 * }</pre>
 *
 * <h2>Pattern 3: Assertion with {@code expectXxx} Methods</h2>
 * <p>
 * <b>When to use:</b> Entity type is guaranteed by your data model. Wrong type means data corruption.
 * <br><b>Returns:</b> {@code ConceptEntity} (never null) - throws if wrong type.
 * <br><b>Key benefit:</b> Clean, direct access when type is certain. Avoids unsafe casts.
 *
 * <pre>{@code
 * // Field definitions - meaning is ALWAYS a concept per data model
 * public ConceptEntity meaning() {
 *     return EntityHandle.get(meaningNid()).expectConcept();
 * }
 *
 * // Pattern definitions - data type is ALWAYS a concept
 * public ConceptEntity dataType() {
 *     return EntityHandle.get(dataTypeNid())
 *         .expectConcept("FieldDefinition dataType must be a concept");
 * }
 *
 * // Business logic with model guarantees
 * public void processHierarchy(int parentNid) {
 *     ConceptEntity parent = EntityHandle.get(parentNid).expectConcept();
 *     // Continue with guaranteed concept...
 * }
 *
 * // Inline usage
 * String meaningText = EntityHandle.get(meaningNid())
 *     .expectConcept()
 *     .getDescription();
 * }</pre>
 *
 * <h2>Static Convenience Methods</h2>
 * <p>
 * For the common "get by nid and assert type" pattern, static methods combine fetch + assertion:
 *
 * <pre>{@code
 * // Equivalent to EntityHandle.get(nid).expectConcept()
 * ConceptEntity meaning = EntityHandle.getConceptOrThrow(meaningNid);
 * SemanticEntity semantic = EntityHandle.getSemanticOrThrow(semanticNid);
 * PatternEntity pattern = EntityHandle.getPatternOrThrow(patternNid);
 * StampEntity stamp = EntityHandle.getStampOrThrow(stampNid);
 * }</pre>
 *
 * <h2>Choosing the Right Pattern</h2>
 *
 * <h3>Ask yourself:</h3>
 * <ol>
 *   <li><b>Am I returning a value?</b>
 *     <ul>
 *       <li>No → Use {@code ifXxx()} for side effects</li>
 *       <li>Yes → Continue to question 2</li>
 *     </ul>
 *   </li>
 *   <li><b>Is wrong type a valid possibility?</b>
 *     <ul>
 *       <li>Yes (user input, filtering) → Use {@code asXxx()} with Optional handling</li>
 *       <li>No (data model guarantee) → Use {@code expectXxx()} for direct access</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <h3>Anti-patterns to avoid:</h3>
 * <pre>{@code
 * // ❌ DON'T: Unsafe cast (bypasses type checking)
 * ConceptEntity concept = (ConceptEntity) Entity.getFast(nid);
 *
 * // ❌ DON'T: Verbose Optional handling when type is guaranteed
 * ConceptEntity meaning = EntityHandle.get(meaningNid())
 *     .asConcept()
 *     .orElseThrow(() -> new IllegalStateException("Should never happen"));
 *
 * // ✅ DO: Use expectConcept() when type is guaranteed
 * ConceptEntity meaning = EntityHandle.get(meaningNid()).expectConcept();
 *
 * // ✅ DO: Use asConcept() when wrong type is valid
 * Optional<ConceptEntity> maybeConcept = EntityHandle.get(userNid).asConcept();
 * }</pre>
 *
 * <h2>Pattern Matching Alternative</h2>
 * <p>
 * For comprehensive type handling, use {@code ifPresent} with switch expressions:
 *
 * <pre>{@code
 * EntityHandle.get(nid).ifPresent(entity -> {
 *     switch (entity) {
 *         case ConceptEntity c -> processConcept(c);
 *         case SemanticEntity s -> processSemantic(s);
 *         case PatternEntity p -> processPattern(p);
 *         case StampEntity st -> processStamp(st);
 *     }
 * });
 * }</pre>
 *
 * <h2>Implementation</h2>
 * <p>
 * Implementors need only provide {@link #entity()} which returns an {@link Optional}
 * containing the entity (or empty if absent). All fluent methods are provided as
 * default implementations. This class is an example of Interface-based traits with data records.
 *
 * @see ConceptEntity
 * @see SemanticEntity
 * @see PatternEntity
 * @see StampEntity
 */
public interface EntityHandle {

    // ========== Core Method - Must Be Implemented ==========

    /**
     * Returns an Optional containing the entity, or empty if absent.
     * <p>
     * This is the only method implementors must provide. All other methods
     * are implemented as defaults based on this method.
     *
     * @return Optional containing the entity, or empty if absent
     */
    Optional<Entity<?>> entity();

    // ========== Static Factory Method ==========

    /**
     * Retrieves an entity by nid and returns a fluent handle for type-safe processing.
     * <p>
     * The returned handle allows chaining type-specific operations without manual
     * instanceof checks or casts.
     *
     * @param nid the native identifier
     * @return an EntityHandle representing the entity, or an empty EntityHandle if absent.
      */
    static EntityHandle get(int nid) {
        Entity entity = Entity.getFast(nid);
        if (entity != null) {
            return of(entity);
        }
        return AbsentHandle.INSTANCE;
    }

    /**
     * Returns a singleton handle representing an absent entity.
     * <p>
     * Use this when you need to explicitly represent absence.
     *
     * @return the absent entity handle singleton
     */
    static EntityHandle absent() {
        return AbsentHandle.INSTANCE;
    }

    /**
     * Creates a handle for an entity.
     *
     * @param entity the entity to wrap
     * @return a handle wrapping the entity
     */
    static EntityHandle of(Entity<?> entity) {
        if (entity == null) {
            return absent();
        }
        return new PresentHandle(entity);
    }

    // ========== Default Implementation: Fluent Type Matching ==========

    /**
     * If entity is present and is a {@link ConceptEntity}, executes the consumer.
     * <p>
     * No casting required - consumer receives ConceptEntity directly.
     * Returns {@code this} for chaining regardless of whether consumer executed.
     *
     * @param consumer the action to perform on the ConceptEntity
     * @return this handle for chaining
     */
    default EntityHandle ifConcept(Consumer<ConceptEntity> consumer) {
        entity().ifPresent(e -> {
            if (e instanceof ConceptEntity concept) {
                consumer.accept(concept);
            }
        });
        return this;
    }

    /**
     * If entity is present and is a {@link SemanticEntity}, executes the consumer.
     *
     * @param consumer the action to perform on the SemanticEntity
     * @return this handle for chaining
     */
    default EntityHandle ifSemantic(Consumer<SemanticEntity> consumer) {
        entity().ifPresent(e -> {
            if (e instanceof SemanticEntity semantic) {
                consumer.accept(semantic);
            }
        });
        return this;
    }

    /**
     * If entity is present and is a {@link PatternEntity}, executes the consumer.
     *
     * @param consumer the action to perform on the PatternEntity
     * @return this handle for chaining
     */
    default EntityHandle ifPattern(Consumer<PatternEntity> consumer) {
        entity().ifPresent(e -> {
            if (e instanceof PatternEntity pattern) {
                consumer.accept(pattern);
            }
        });
        return this;
    }

    /**
     * If entity is present and is a {@link StampEntity}, executes the consumer.
     *
     * @param consumer the action to perform on the StampEntity
     * @return this handle for chaining
     */
    default EntityHandle ifStamp(Consumer<StampEntity> consumer) {
        entity().ifPresent(e -> {
            if (e instanceof StampEntity stamp) {
                consumer.accept(stamp);
            }
        });
        return this;
    }

    /**
     * If entity is absent (not found in database), executes the action.
     *
     * @param action the action to perform if entity is absent
     * @return this handle for chaining
     */
    default EntityHandle ifAbsent(Runnable action) {
        if (entity().isEmpty()) {
            action.run();
        }
        return this;
    }

    // ========== Default Implementation: OrElse Methods ==========

    /**
     * If entity is a {@link ConceptEntity}, executes the consumer, otherwise executes elseAction.
     *
     * @param consumer the action to perform if entity is a Concept
     * @param elseAction the action to perform otherwise (absent or wrong type)
     * @return this handle for chaining
     */
    default EntityHandle ifConceptOrElse(Consumer<ConceptEntity> consumer, Runnable elseAction) {
        Optional<Entity<?>> opt = entity();
        if (opt.isPresent() && opt.get() instanceof ConceptEntity concept) {
            consumer.accept(concept);
        } else {
            elseAction.run();
        }
        return this;
    }

    /**
     * If entity is a {@link SemanticEntity}, executes the consumer, otherwise executes elseAction.
     *
     * @param consumer the action to perform if entity is a Semantic
     * @param elseAction the action to perform otherwise (absent or wrong type)
     * @return this handle for chaining
     */
    default EntityHandle ifSemanticOrElse(Consumer<SemanticEntity> consumer, Runnable elseAction) {
        Optional<Entity<?>> opt = entity();
        if (opt.isPresent() && opt.get() instanceof SemanticEntity semantic) {
            consumer.accept(semantic);
        } else {
            elseAction.run();
        }
        return this;
    }

    /**
     * If entity is a {@link PatternEntity}, executes the consumer, otherwise executes elseAction.
     *
     * @param consumer the action to perform if an entity is a Pattern
     * @param elseAction the action to perform otherwise (absent or wrong type)
     * @return this handle for chaining
     */
    default EntityHandle ifPatternOrElse(Consumer<PatternEntity> consumer, Runnable elseAction) {
        Optional<Entity<?>> opt = entity();
        if (opt.isPresent() && opt.get() instanceof PatternEntity pattern) {
            consumer.accept(pattern);
        } else {
            elseAction.run();
        }
        return this;
    }

    /**
     * If entity is a {@link StampEntity}, executes the consumer, otherwise executes elseAction.
     *
     * @param consumer the action to perform if entity is a Stamp
     * @param elseAction the action to perform otherwise (absent or wrong type)
     * @return this handle for chaining
     */
    default EntityHandle ifStampOrElse(Consumer<StampEntity> consumer, Runnable elseAction) {
        Optional<Entity<?>> opt = entity();
        if (opt.isPresent() && opt.get() instanceof StampEntity stamp) {
            consumer.accept(stamp);
        } else {
            elseAction.run();
        }
        return this;
    }

    // ========== Default Implementation: General Presence Methods ==========

    /**
     * If entity is present (any type), executes the consumer with the entity.
     *
     * @param consumer the action to perform on the entity
     * @return this handle for chaining
     */
    default EntityHandle ifPresent(Consumer<Entity<?>> consumer) {
        entity().ifPresent(consumer);
        return this;
    }

    /**
     * If the entity is present, executes presentAction, otherwise executes absentAction.
     *
     * @param presentAction the action to perform if entity is present
     * @param absentAction the action to perform if entity is absent
     * @return this handle for chaining
     */
    default EntityHandle ifPresentOrElse(Runnable presentAction, Runnable absentAction) {
        if (entity().isPresent()) {
            presentAction.run();
        } else {
            absentAction.run();
        }
        return this;
    }

    // ========== Static Convenience Methods ==========

    /**
     * Gets entity by nid and returns it as a {@link ConceptEntity}, throwing if absent or wrong type.
     * <p>
     * Convenience method equivalent to {@code get(nid).expectConcept()}.
     * Use when you need to fetch and assert type in one call.
     *
     * @param nid the entity nid
     * @return the ConceptEntity (never null)
     * @throws IllegalStateException if entity is absent or not a concept
     * @see #expectConcept()
     */
    static ConceptEntity getConceptOrThrow(int nid) {
        return get(nid).expectConcept();
    }

    /**
     * Gets entity by nid and returns it as a {@link SemanticEntity}, throwing if absent or wrong type.
     * <p>
     * Convenience method equivalent to {@code get(nid).expectSemantic()}.
     *
     * @param nid the entity nid
     * @return the SemanticEntity (never null)
     * @throws IllegalStateException if entity is absent or not a semantic
     */
    static SemanticEntity getSemanticOrThrow(int nid) {
        return get(nid).expectSemantic();
    }

    /**
     * Gets entity by nid and returns it as a {@link PatternEntity}, throwing if absent or wrong type.
     * <p>
     * Convenience method equivalent to {@code get(nid).expectPattern()}.
     *
     * @param nid the entity nid
     * @return the PatternEntity (never null)
     * @throws IllegalStateException if entity is absent or not a pattern
     */
    static PatternEntity getPatternOrThrow(int nid) {
        return get(nid).expectPattern();
    }

    /**
     * Gets entity by nid and returns it as a {@link StampEntity}, throwing if absent or wrong type.
     * <p>
     * Convenience method equivalent to {@code get(nid).expectStamp()}.
     *
     * @param nid the entity nid
     * @return the StampEntity (never null)
     * @throws IllegalStateException if entity is absent or not a stamp
     */
    static StampEntity getStampOrThrow(int nid) {
        return get(nid).expectStamp();
    }

    // ========== Default Implementation: Type Extraction Methods (Optional) ==========

    /**
     * Returns this entity as a {@link ConceptEntity} if it is one.
     * <p>
     * Use this method when you need to extract and return the concept, or when you
     * want to apply transformations using {@link Optional#map(java.util.function.Function)}.
     *
     * <h3>Usage Examples:</h3>
     * <pre>{@code
     * // Extract concept or return null
     * ConceptEntity concept = EntityHandle.get(nid)
     *     .asConcept()
     *     .orElse(null);
     *
     * // Extract and return from method
     * public Optional<ConceptEntity> findConcept(int nid) {
     *     return EntityHandle.get(nid).asConcept();
     * }
     *
     * // Transform and extract
     * String description = EntityHandle.get(nid)
     *     .asConcept()
     *     .map(concept -> concept.getDescription())
     *     .orElse("Not a concept");
     *
     * // Chain with flatMap
     * Optional<String> result = EntityHandle.get(nid)
     *     .asConcept()
     *     .flatMap(concept -> concept.findProperty())
     *     .map(prop -> prop.toString());
     * }</pre>
     *
     * @return Optional containing the ConceptEntity, or empty if absent or not a concept
     * @see #ifConcept(Consumer) for side-effect operations
     */
    default Optional<ConceptEntity> asConcept() {
        return entity()
            .filter(e -> e instanceof ConceptEntity)
            .map(e -> (ConceptEntity) e);
    }

    /**
     * Returns this entity as a {@link SemanticEntity} if it is one.
     * <p>
     * Use this method when you need to extract and return the semantic, or when you
     * want to apply transformations using {@link Optional#map(java.util.function.Function)}.
     *
     * <h3>Usage Examples:</h3>
     * <pre>{@code
     * // Extract semantic with default
     * SemanticEntity semantic = EntityHandle.get(nid)
     *     .asSemantic()
     *     .orElseThrow(() -> new IllegalArgumentException("Not a semantic"));
     *
     * // Map to field value
     * Optional<String> fieldValue = EntityHandle.get(nid)
     *     .asSemantic()
     *     .map(semantic -> semantic.fieldValues().get(0).toString());
     * }</pre>
     *
     * @return Optional containing the SemanticEntity, or empty if absent or not a semantic
     * @see #ifSemantic(Consumer) for side-effect operations
     */
    default Optional<SemanticEntity> asSemantic() {
        return entity()
            .filter(e -> e instanceof SemanticEntity)
            .map(e -> (SemanticEntity) e);
    }

    /**
     * Returns this entity as a {@link PatternEntity} if it is one.
     * <p>
     * Use this method when you need to extract and return the pattern, or when you
     * want to apply transformations using {@link Optional#map(java.util.function.Function)}.
     *
     * <h3>Usage Examples:</h3>
     * <pre>{@code
     * // Extract pattern
     * Optional<PatternEntity> pattern = EntityHandle.get(nid).asPattern();
     *
     * // Get field definitions
     * List<FieldDefinition> defs = EntityHandle.get(nid)
     *     .asPattern()
     *     .map(pattern -> pattern.fieldDefinitions())
     *     .orElse(Collections.emptyList());
     * }</pre>
     *
     * @return Optional containing the PatternEntity, or empty if absent or not a pattern
     * @see #ifPattern(Consumer) for side-effect operations
     */
    default Optional<PatternEntity> asPattern() {
        return entity()
            .filter(e -> e instanceof PatternEntity)
            .map(e -> (PatternEntity) e);
    }

    /**
     * Returns this entity as a {@link StampEntity} if it is one.
     * <p>
     * Use this method when you need to extract and return the stamp, or when you
     * want to apply transformations using {@link Optional#map(java.util.function.Function)}.
     *
     * <h3>Usage Examples:</h3>
     * <pre>{@code
     * // Extract stamp
     * StampEntity stamp = EntityHandle.get(nid)
     *     .asStamp()
     *     .orElse(null);
     *
     * // Get timestamp
     * long time = EntityHandle.get(nid)
     *     .asStamp()
     *     .map(stamp -> stamp.time())
     *     .orElse(0L);
     * }</pre>
     *
     * @return Optional containing the StampEntity, or empty if absent or not a stamp
     * @see #ifStamp(Consumer) for side-effect operations
     */
    default Optional<StampEntity> asStamp() {
        return entity()
            .filter(e -> e instanceof StampEntity)
            .map(e -> (StampEntity) e);
    }

    // ========== Default Implementation: Type Assertion Methods (Direct) ==========

    /**
     * Returns this entity as a {@link ConceptEntity}, throwing an exception if absent or wrong type.
     * <p>
     * <b>Use this method when:</b> Entity type is guaranteed by your data model. Wrong type indicates
     * data corruption or programming error, not a legitimate alternative case.
     *
     * <h3>Common Use Cases:</h3>
     * <ul>
     *   <li>Field definitions where meaning/datatype/purpose are always concepts</li>
     *   <li>Pattern definitions where field types are known</li>
     *   <li>Navigating relationships where type is guaranteed by schema</li>
     *   <li>Any context where wrong type means data integrity violation</li>
     * </ul>
     *
     * <h3>Usage Examples:</h3>
     * <pre>{@code
     * // Field definition - meaning is ALWAYS a concept per data model
     * public ConceptEntity meaning() {
     *     return EntityHandle.get(meaningNid()).expectConcept();
     * }
     *
     * // Inline extraction with guaranteed type
     * String meaningText = EntityHandle.get(meaningNid())
     *     .expectConcept()
     *     .getDescription();
     *
     * // Business logic with schema guarantee
     * public void processParent(int parentNid) {
     *     ConceptEntity parent = EntityHandle.get(parentNid).expectConcept();
     *     // Continue with guaranteed concept...
     * }
     * }</pre>
     *
     * @return the ConceptEntity (never null)
     * @throws IllegalStateException if entity is absent or not a concept, with descriptive error message
     * @see #asConcept() for safe Optional-based extraction when wrong type is valid
     * @see #ifConcept(Consumer) for side-effect operations
     */
    default ConceptEntity expectConcept() {
        return asConcept().orElseThrow(() ->
            new IllegalStateException(
                entity().map(e -> "Expected ConceptEntity but was " + e.getClass().getSimpleName() +
                                 " with nid: " + e.nid() + " (" + e.publicId() + ")")
                        .orElse("Expected ConceptEntity but entity was absent")
            )
        );
    }

    /**
     * Returns this entity as a {@link ConceptEntity}, throwing an exception with custom message if absent or wrong type.
     * <p>
     * Use this variant when you want to provide domain-specific error context.
     *
     * <h3>Usage Example:</h3>
     * <pre>{@code
     * public ConceptEntity dataType() {
     *     return EntityHandle.get(dataTypeNid())
     *         .expectConcept("FieldDefinition dataType must be a concept");
     * }
     * }</pre>
     *
     * @param errorMessage the error message for the exception
     * @return the ConceptEntity (never null)
     * @throws IllegalStateException if entity is absent or not a concept
     */
    default ConceptEntity expectConcept(String errorMessage) {
        return asConcept().orElseThrow(() -> new IllegalStateException(errorMessage));
    }

    /**
     * Returns this entity as a {@link SemanticEntity}, throwing an exception if absent or wrong type.
     * <p>
     * <b>Use this method when:</b> Entity type is guaranteed by your data model.
     *
     * <h3>Usage Example:</h3>
     * <pre>{@code
     * public SemanticEntity getDefinition(int semanticNid) {
     *     return EntityHandle.get(semanticNid).expectSemantic();
     * }
     * }</pre>
     *
     * @return the SemanticEntity (never null)
     * @throws IllegalStateException if entity is absent or not a semantic, with descriptive error message
     * @see #asSemantic() for safe Optional-based extraction when wrong type is valid
     */
    default SemanticEntity expectSemantic() {
        return asSemantic().orElseThrow(() ->
            new IllegalStateException(
                entity().map(e -> "Expected SemanticEntity but was " + e.getClass().getSimpleName() +
                                 " with nid: " + e.nid() + " (" + e.publicId() + ")")
                        .orElse("Expected SemanticEntity but entity was absent")
            )
        );
    }

    /**
     * Returns this entity as a {@link SemanticEntity}, throwing an exception with custom message if absent or wrong type.
     *
     * @param errorMessage the error message for the exception
     * @return the SemanticEntity (never null)
     * @throws IllegalStateException if entity is absent or not a semantic
     */
    default SemanticEntity expectSemantic(String errorMessage) {
        return asSemantic().orElseThrow(() -> new IllegalStateException(errorMessage));
    }

    /**
     * Returns this entity as a {@link PatternEntity}, throwing an exception if absent or wrong type.
     * <p>
     * <b>Use this method when:</b> Entity type is guaranteed by your data model.
     *
     * <h3>Usage Example:</h3>
     * <pre>{@code
     * public PatternEntity getPatternForSemantic(int patternNid) {
     *     return EntityHandle.get(patternNid).expectPattern();
     * }
     * }</pre>
     *
     * @return the PatternEntity (never null)
     * @throws IllegalStateException if entity is absent or not a pattern, with descriptive error message
     * @see #asPattern() for safe Optional-based extraction when wrong type is valid
     */
    default PatternEntity expectPattern() {
        return asPattern().orElseThrow(() ->
            new IllegalStateException(
                entity().map(e -> "Expected PatternEntity but was " + e.getClass().getSimpleName() +
                                 " with nid: " + e.nid() + " (" + e.publicId() + ")")
                        .orElse("Expected PatternEntity but entity was absent")
            )
        );
    }

    /**
     * Returns this entity as a {@link PatternEntity}, throwing an exception with custom message if absent or wrong type.
     *
     * @param errorMessage the error message for the exception
     * @return the PatternEntity (never null)
     * @throws IllegalStateException if entity is absent or not a pattern
     */
    default PatternEntity expectPattern(String errorMessage) {
        return asPattern().orElseThrow(() -> new IllegalStateException(errorMessage));
    }

    /**
     * Returns this entity as a {@link StampEntity}, throwing an exception if absent or wrong type.
     * <p>
     * <b>Use this method when:</b> Entity type is guaranteed by your data model.
     *
     * <h3>Usage Example:</h3>
     * <pre>{@code
     * public StampEntity getVersionStamp(int stampNid) {
     *     return EntityHandle.get(stampNid).expectStamp();
     * }
     * }</pre>
     *
     * @return the StampEntity (never null)
     * @throws IllegalStateException if entity is absent or not a stamp, with descriptive error message
     * @see #asStamp() for safe Optional-based extraction when wrong type is valid
     */
    default StampEntity expectStamp() {
        return asStamp().orElseThrow(() ->
            new IllegalStateException(
                entity().map(e -> "Expected StampEntity but was " + e.getClass().getSimpleName() +
                                 " with nid: " + e.nid() + " (" + e.publicId() + ")")
                        .orElse("Expected StampEntity but entity was absent")
            )
        );
    }

    /**
     * Returns this entity as a {@link StampEntity}, throwing an exception with custom message if absent or wrong type.
     *
     * @param errorMessage the error message for the exception
     * @return the StampEntity (never null)
     * @throws IllegalStateException if entity is absent or not a stamp
     */
    default StampEntity expectStamp(String errorMessage) {
        return asStamp().orElseThrow(() -> new IllegalStateException(errorMessage));
    }

    // ========== Default Implementation: Query Methods ==========

    /**
     * Returns {@code true} if entity is present, {@code false} if absent.
     *
     * @return true if entity is present
     */
    default boolean isPresent() {
        return entity().isPresent();
    }

    /**
     * Returns {@code true} if entity is absent, {@code false} if present.
     *
     * @return true if entity is absent
     */
    default boolean isAbsent() {
        return entity().isEmpty();
    }

    /**
     * Returns {@code true} if entity is present and is a {@link ConceptEntity}.
     *
     * @return true if entity is a Concept
     */
    default boolean isConcept() {
        return entity().filter(e -> e instanceof ConceptEntity).isPresent();
    }

    /**
     * Returns {@code true} if entity is present and is a {@link SemanticEntity}.
     *
     * @return true if entity is a Semantic
     */
    default boolean isSemantic() {
        return entity().filter(e -> e instanceof SemanticEntity).isPresent();
    }

    /**
     * Returns {@code true} if entity is present and is a {@link PatternEntity}.
     *
     * @return true if entity is a Pattern
     */
    default boolean isPattern() {
        return entity().filter(e -> e instanceof PatternEntity).isPresent();
    }

    /**
     * Returns {@code true} if entity is present and is a {@link StampEntity}.
     *
     * @return true if entity is a Stamp
     */
    default boolean isStamp() {
        return entity().filter(e -> e instanceof StampEntity).isPresent();
    }

    // ========== Default Implementation: Escape Hatches ==========

    /**
     * Returns the entity, or null if absent.
     *
     * @return the entity, or null if absent
     */
    default Entity<?> orNull() {
        return entity().orElse(null);
    }

    /**
     * Returns the entity, or throws the provided exception if absent.
     *
     * @param exceptionSupplier supplier of exception to throw
     * @param <X> the type of exception to throw
     * @return the entity (never null)
     * @throws X if entity is absent
     */
    default <X extends Throwable> Entity<?> orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        return entity().orElseThrow(exceptionSupplier);
    }

    // ========== Nested Implementation Classes ==========

    /**
     * Record implementation for a present entity.
     * <p>
     * Immutable and provides all fluent methods via default implementations.
     *
     * @param entityValue the entity being handled (never null)
     */
    record PresentHandle(Entity<?> entityValue) implements EntityHandle {
        /**
         * Compact constructor validates entity is non-null.
         */
        public PresentHandle {
            if (entityValue == null) {
                throw new NullPointerException("entity must not be null");
            }
        }

        @Override
        public Optional<Entity<?>> entity() {
            return Optional.of(entityValue);
        }
    }

    /**
     * Singleton implementation for an absent entity.
     * <p>
     * Provides all fluent methods via default implementations, all of which
     * handle the empty case appropriately.
     */
    final class AbsentHandle implements EntityHandle {
        /**
         * Singleton instance for absent entities.
         */
        static final AbsentHandle INSTANCE = new AbsentHandle();

        private AbsentHandle() {}

        @Override
        public Optional<Entity<?>> entity() {
            return Optional.empty();
        }

        @Override
        public String toString() {
            return "AbsentHandle";
        }
    }
}
