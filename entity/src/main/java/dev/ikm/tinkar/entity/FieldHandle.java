package dev.ikm.tinkar.entity;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.component.graph.DiGraph;
import dev.ikm.tinkar.component.graph.DiTree;
import dev.ikm.tinkar.component.graph.Vertex;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.PatternFacade;
import dev.ikm.tinkar.terms.SemanticFacade;
import dev.ikm.tinkar.terms.StampFacade;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.map.primitive.ImmutableIntIntMap;
import org.eclipse.collections.api.map.primitive.ImmutableIntObjectMap;
import org.eclipse.collections.api.set.ImmutableSet;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A fluent handle for semantic field values that provides type-safe access without casting.
 * <p>
 * Semantic fields can contain various types: primitives (boolean, int, float, long, String),
 * entity references (PublicId, ConceptFacade), complex structures (DiGraph, DiTree), and more.
 * This handle provides three access patterns similar to {@link EntityHandle}:
 *
 * <h2>Handling EntityProxy and EntityFacade</h2>
 * <p>
 * When a field value is an {@code EntityFacade} (including {@code EntityProxy}), the handle
 * automatically resolves the actual entity to determine its true type. This is necessary because
 * {@code EntityProxy} is abstract and doesn't directly implement type-specific interfaces like
 * {@code ConceptFacade}. The handle uses {@link EntityHandle} internally to perform this resolution.
 *
 * <h2>The Three Patterns</h2>
 * <table border="1" cellpadding="5">
 * <caption>Field Access Patterns</caption>
 * <tr>
 *   <th>Pattern</th>
 *   <th>Method</th>
 *   <th>Use When</th>
 *   <th>Example</th>
 * </tr>
 * <tr>
 *   <td><b>Side Effects</b></td>
 *   <td>{@code ifString()}</td>
 *   <td>Execute code based on type</td>
 *   <td>{@code .ifString(s -> log.info(s))}</td>
 * </tr>
 * <tr>
 *   <td><b>Safe Extraction</b></td>
 *   <td>{@code asString()}</td>
 *   <td>Wrong type is valid</td>
 *   <td>{@code .asString().orElse("")}</td>
 * </tr>
 * <tr>
 *   <td><b>Assertion</b></td>
 *   <td>{@code expectString()}</td>
 *   <td>Type guaranteed by pattern</td>
 *   <td>{@code .expectString()}</td>
 * </tr>
 * </table>
 *
 * <h2>Convenient Field Access</h2>
 * <p>
 * The static factory methods provide multiple ways to access fields from a {@link SemanticEntityVersion},
 * reducing boilerplate code:
 * <pre>{@code
 * SemanticEntityVersion version = ...;
 * 
 * // By index (when you know the position):
 * ConceptFacade concept = FieldHandle.of(version, 0).expectConcept();
 * 
 * // By meaning (when you know the semantic meaning):
 * ConceptFacade caseSig = FieldHandle.of(version, TinkarTerm.DESCRIPTION_CASE_SIGNIFICANCE)
 *     .expectConcept();
 * 
 * // By purpose (when you want to find by purpose):
 * String text = FieldHandle.ofPurpose(version, TinkarTerm.TEXT_FOR_DESCRIPTION)
 *     .expectString();
 * 
 * // Compared to the verbose alternative:
 * PatternEntityVersion pattern = version.pattern().lastVersion();
 * int index = pattern.indexForMeaning(TinkarTerm.DESCRIPTION_CASE_SIGNIFICANCE);
 * ConceptFacade caseSig = FieldHandle.of(version.fieldValues().get(index)).expectConcept();
 * }</pre>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Pattern 1: Side effects
 * FieldHandle.of(version, meaningConcept)
 *     .ifConcept(concept -> process(concept))
 *     .ifString(string -> log.info(string));
 *
 * // Pattern 2: Safe extraction (when type might vary)
 * Optional<ConceptEntity> maybeConcept = FieldHandle.of(version, 2).asConcept();
 *
 * // Pattern 3: Assertion (when pattern guarantees type)
 * ConceptFacade caseSig = FieldHandle.of(version, caseMeaningConcept).expectConcept();
 * }</pre>
 *
 * @see EntityHandle for the entity-level equivalent
 */
public interface FieldHandle {

    /**
     * Returns the raw field value as an Object.
     * This is the only method implementors must provide.
     *
     * @return the field value
     */
    Object value();

    // ========== Static Factory Methods ==========

    /**
     * Creates a handle for a field value.
     *
     * @param value the field value (may be null)
     * @return a FieldHandle wrapping the value
     */
    static FieldHandle of(Object value) {
        return () -> value;
    }

    /**
     * Creates a handle for a field value from a semantic by field index.
     * <p>
     * This is a convenience method that combines field value retrieval with handle creation:
     * <pre>{@code
     * // Instead of:
     * FieldHandle.of(version.fieldValues().get(index))
     * 
     * // You can write:
     * FieldHandle.of(version, index)
     * }</pre>
     *
     * @param version the semantic version containing the field
     * @param fieldIndex zero-based index into the field values
     * @return a FieldHandle wrapping the field value at the specified index
     * @throws IndexOutOfBoundsException if fieldIndex is out of range
     */
    static FieldHandle of(SemanticEntityVersion version, int fieldIndex) {
        return of(version.fieldValues().get(fieldIndex));
    }

    /**
     * Creates a handle for a field value from a semantic by field meaning.
     * <p>
     * This method looks up the field index using the pattern's {@code indexForMeaning} method,
     * then retrieves the field value. This is useful when you know the semantic meaning of a
     * field but not its position:
     * <pre>{@code
     * // Instead of:
     * int index = patternVersion.indexForMeaning(TinkarTerm.DESCRIPTION_CASE_SIGNIFICANCE);
     * FieldHandle.of(version.fieldValues().get(index))
     * 
     * // You can write:
     * FieldHandle.of(version, TinkarTerm.DESCRIPTION_CASE_SIGNIFICANCE)
     * }</pre>
     *
     * @param version the semantic version containing the field
     * @param meaning the concept representing the semantic meaning of the field
     * @return a FieldHandle wrapping the field value with the specified meaning
     * @throws IllegalArgumentException if no field with the specified meaning exists
     */
    static FieldHandle of(SemanticEntityVersion version, ConceptFacade meaning) {
        PatternEntityVersion patternVersion = version.pattern().lastVersion();
        int index = patternVersion.indexForMeaning(meaning);
        if (index < 0) {
            throw new IllegalArgumentException(
                    "No field with meaning '" + meaning.toXmlFragment() + "' found in pattern");
        }
        return of(version.fieldValues().get(index));
    }

    /**
     * Creates a handle for a field value from a semantic by field meaning NID.
     * <p>
     * This method looks up the field index using the pattern's {@code indexForMeaning} method
     * with the meaning NID, then retrieves the field value:
     * <pre>{@code
     * // Instead of:
     * int index = patternVersion.indexForMeaning(meaningNid);
     * FieldHandle.of(version.fieldValues().get(index))
     * 
     * // You can write:
     * FieldHandle.of(version, meaningNid)
     * }</pre>
     *
     * @param version the semantic version containing the field
     * @param meaningNid the NID of the concept representing the semantic meaning of the field
     * @return a FieldHandle wrapping the field value with the specified meaning
     * @throws IllegalArgumentException if no field with the specified meaning NID exists
     */
    static FieldHandle ofMeaning(SemanticEntityVersion version, int meaningNid) {
        PatternEntityVersion patternVersion = version.pattern().lastVersion();
        int index = patternVersion.indexForMeaning(meaningNid);
        if (index < 0) {
            throw new IllegalArgumentException(
                    "No field with meaning NID '" + meaningNid + "' found in pattern");
        }
        return of(version.fieldValues().get(index));
    }

    /**
     * Creates a handle for a field value from a semantic by field purpose.
     * <p>
     * This method looks up the field index using the pattern's {@code indexForPurpose} method,
     * then retrieves the field value. This is useful when you want to find a field by its purpose
     * rather than its semantic meaning:
     * <pre>{@code
     * FieldHandle.ofPurpose(version, TinkarTerm.REFERENCED_COMPONENT_PURPOSE)
     * }</pre>
     *
     * @param version the semantic version containing the field
     * @param purpose the concept representing the purpose of the field
     * @return a FieldHandle wrapping the field value with the specified purpose
     * @throws IllegalArgumentException if no field with the specified purpose exists
     */
    static FieldHandle ofPurpose(SemanticEntityVersion version, ConceptFacade purpose) {
        PatternEntityVersion patternVersion = version.pattern().lastVersion();
        int index = patternVersion.indexForPurpose(purpose);
        if (index < 0) {
            throw new IllegalArgumentException(
                    "No field with purpose '" + purpose.toXmlFragment() + "' found in pattern");
        }
        return of(version.fieldValues().get(index));
    }

    /**
     * Creates a handle for a field value from a semantic by field purpose NID.
     * <p>
     * This method looks up the field index using the pattern's {@code indexForPurpose} method
     * with the purpose NID, then retrieves the field value:
     * <pre>{@code
     * FieldHandle.ofPurpose(version, purposeNid)
     * }</pre>
     *
     * @param version the semantic version containing the field
     * @param purposeNid the NID of the concept representing the purpose of the field
     * @return a FieldHandle wrapping the field value with the specified purpose
     * @throws IllegalArgumentException if no field with the specified purpose NID exists
     */
    static FieldHandle ofPurpose(SemanticEntityVersion version, int purposeNid) {
        PatternEntityVersion patternVersion = version.pattern().lastVersion();
        int index = patternVersion.indexForPurpose(purposeNid);
        if (index < 0) {
            throw new IllegalArgumentException(
                    "No field with purpose NID '" + purposeNid + "' found in pattern");
        }
        return of(version.fieldValues().get(index));
    }

    // ========== Side Effect Methods (ifXxx) ==========

    default FieldHandle ifBoolean(Consumer<Boolean> consumer) {
        if (value() instanceof Boolean b) {
            consumer.accept(b);
        }
        return this;
    }

    default FieldHandle ifInt(Consumer<Integer> consumer) {
        if (value() instanceof Integer i) {
            consumer.accept(i);
        }
        return this;
    }

    default FieldHandle ifFloat(Consumer<Float> consumer) {
        if (value() instanceof Float f) {
            consumer.accept(f);
        }
        return this;
    }

    default FieldHandle ifLong(Consumer<Long> consumer) {
        if (value() instanceof Long l) {
            consumer.accept(l);
        }
        return this;
    }

    default FieldHandle ifBigDecimal(Consumer<BigDecimal> consumer) {
        if (value() instanceof BigDecimal bd) {
            consumer.accept(bd);
        }
        return this;
    }

    default FieldHandle ifString(Consumer<String> consumer) {
        if (value() instanceof String s) {
            consumer.accept(s);
        }
        return this;
    }

    default FieldHandle ifInstant(Consumer<Instant> consumer) {
        if (value() instanceof Instant instant) {
            consumer.accept(instant);
        }
        return this;
    }

    default FieldHandle ifByteArray(Consumer<byte[]> consumer) {
        if (value() instanceof byte[] bytes) {
            consumer.accept(bytes);
        }
        return this;
    }

    default FieldHandle ifPublicId(Consumer<PublicId> consumer) {
        if (value() instanceof PublicId publicId) {
            consumer.accept(publicId);
        }
        return this;
    }

    default FieldHandle ifPublicIdList(Consumer<ImmutableList<PublicId>> consumer) {
        if (value() instanceof ImmutableList<?> list && !list.isEmpty() && list.get(0) instanceof PublicId) {
            consumer.accept((ImmutableList<PublicId>) list);
        }
        return this;
    }

    default FieldHandle ifPublicIdSet(Consumer<ImmutableSet<PublicId>> consumer) {
        if (value() instanceof ImmutableSet<?> set && (set.isEmpty() || set.iterator().next() instanceof PublicId)) {
            consumer.accept((ImmutableSet<PublicId>) set);
        }
        return this;
    }

    /**
     * If the field value is a ConceptFacade (or resolves to a ConceptEntity), executes the consumer.
     * <p>
     * This handles the common case where field values from patterns store concept references.
     * If the value is an {@code EntityFacade}, it resolves the actual entity to check if it's a concept.
     */
    default FieldHandle ifConcept(Consumer<ConceptEntity> consumer) {
        if (value() instanceof EntityFacade facade) {
            EntityHandle.get(facade).ifConcept(consumer);
        }
        return this;
    }

    /**
     * If the field value is a SemanticFacade (or resolves to a SemanticEntity), executes the consumer.
     */
    default FieldHandle ifSemantic(Consumer<SemanticEntity> consumer) {
        if (value() instanceof EntityFacade facade) {
            EntityHandle.get(facade).ifSemantic(consumer);
        }
        return this;
    }

    /**
     * If the field value is a PatternFacade (or resolves to a PatternEntity), executes the consumer.
     */
    default FieldHandle ifPattern(Consumer<PatternEntity> consumer) {
        if (value() instanceof EntityFacade facade) {
            EntityHandle.get(facade).ifPattern(consumer);
        }
        return this;
    }

    /**
     * If the field value is a StampFacade (or resolves to a StampEntity), executes the consumer.
     */
    default FieldHandle ifStamp(Consumer<StampEntity> consumer) {
        if (value() instanceof EntityFacade facade) {
            EntityHandle.get(facade).ifStamp(consumer);
        }
        return this;
    }

    default FieldHandle ifEntity(Consumer<EntityFacade> consumer) {
        if (value() instanceof EntityFacade entity) {
            consumer.accept(entity);
        }
        return this;
    }

    default FieldHandle ifDiGraph(Consumer<DiGraph<? extends EntityVertex>> consumer) {
        if (value() instanceof DiGraph<?> graph) {
            consumer.accept((DiGraph<? extends EntityVertex>) graph);
        }
        return this;
    }

    default FieldHandle ifDiTree(Consumer<DiTree<? extends EntityVertex>> consumer) {
        if (value() instanceof DiTree<?> tree) {
            consumer.accept((DiTree<? extends EntityVertex>) tree);
        }
        return this;
    }

    default FieldHandle ifVertex(Consumer<Vertex> consumer) {
        if (value() instanceof Vertex vertex) {
            consumer.accept(vertex);
        }
        return this;
    }

    default FieldHandle ifIntToIntMap(Consumer<ImmutableIntIntMap> consumer) {
        if (value() instanceof ImmutableIntIntMap map) {
            consumer.accept(map);
        }
        return this;
    }

    default FieldHandle ifIntToMultipleIntMap(Consumer<ImmutableIntObjectMap<ImmutableList<Integer>>> consumer) {
        if (value() instanceof ImmutableIntObjectMap<?> map) {
            consumer.accept((ImmutableIntObjectMap<ImmutableList<Integer>>) map);
        }
        return this;
    }

    default FieldHandle ifUuid(Consumer<UUID> consumer) {
        if (value() instanceof UUID uuid) {
            consumer.accept(uuid);
        }
        return this;
    }

    // ========== Safe Extraction Methods (asXxx) ==========

    default Optional<Boolean> asBoolean() {
        return value() instanceof Boolean b ? Optional.of(b) : Optional.empty();
    }

    default Optional<Integer> asInt() {
        return value() instanceof Integer i ? Optional.of(i) : Optional.empty();
    }

    default Optional<Float> asFloat() {
        return value() instanceof Float f ? Optional.of(f) : Optional.empty();
    }

    default Optional<Long> asLong() {
        return value() instanceof Long l ? Optional.of(l) : Optional.empty();
    }

    default Optional<BigDecimal> asBigDecimal() {
        return value() instanceof BigDecimal bd ? Optional.of(bd) : Optional.empty();
    }

    default Optional<String> asString() {
        return value() instanceof String s ? Optional.of(s) : Optional.empty();
    }

    default Optional<Instant> asInstant() {
        return value() instanceof Instant instant ? Optional.of(instant) : Optional.empty();
    }

    default Optional<byte[]> asByteArray() {
        return value() instanceof byte[] bytes ? Optional.of(bytes) : Optional.empty();
    }

    default Optional<PublicId> asPublicId() {
        return value() instanceof PublicId publicId ? Optional.of(publicId) : Optional.empty();
    }

    default Optional<ImmutableList<PublicId>> asPublicIdList() {
        if (value() instanceof ImmutableList<?> list && !list.isEmpty() && list.get(0) instanceof PublicId) {
            return Optional.of((ImmutableList<PublicId>) list);
        }
        return Optional.empty();
    }

    default Optional<ImmutableSet<PublicId>> asPublicIdSet() {
        if (value() instanceof ImmutableSet<?> set && (set.isEmpty() || set.iterator().next() instanceof PublicId)) {
            return Optional.of((ImmutableSet<PublicId>) set);
        }
        return Optional.empty();
    }

    /**
     * Returns the field value as a ConceptEntity if it is one (or resolves to one).
     * <p>
     * This is the safe extraction method for concept field values. Use when the field
     * might or might not be a concept (e.g., handling multiple pattern types).
     * If the value is an {@code EntityFacade}, it resolves the actual entity to check if it's a concept.
     */
    default Optional<ConceptEntity> asConcept() {
        if (value() instanceof EntityFacade facade) {
            return EntityHandle.get(facade).asConcept();
        }
        return Optional.empty();
    }

    /**
     * Returns the field value as a SemanticEntity if it is one (or resolves to one).
     */
    default Optional<SemanticEntity> asSemantic() {
        if (value() instanceof EntityFacade facade) {
            return EntityHandle.get(facade).asSemantic();
        }
        return Optional.empty();
    }

    /**
     * Returns the field value as a PatternEntity if it is one (or resolves to one).
     */
    default Optional<PatternEntity> asPattern() {
        if (value() instanceof EntityFacade facade) {
            return EntityHandle.get(facade).asPattern();
        }
        return Optional.empty();
    }

    /**
     * Returns the field value as a StampEntity if it is one (or resolves to one).
     */
    default Optional<StampEntity> asStamp() {
        if (value() instanceof EntityFacade facade) {
            return EntityHandle.get(facade).asStamp();
        }
        return Optional.empty();
    }

    default Optional<EntityFacade> asEntity() {
        return value() instanceof EntityFacade entity ? Optional.of(entity) : Optional.empty();
    }

    default Optional<DiGraph<? extends EntityVertex>> asDiGraph() {
        return value() instanceof DiGraph<?> graph ?
                Optional.of((DiGraph<? extends EntityVertex>) graph) : Optional.empty();
    }

    default Optional<DiTree<? extends EntityVertex>> asDiTree() {
        return value() instanceof DiTree<?> tree ?
                Optional.of((DiTree<? extends EntityVertex>) tree) : Optional.empty();
    }

    default Optional<Vertex> asVertex() {
        return value() instanceof Vertex vertex ? Optional.of(vertex) : Optional.empty();
    }

    default Optional<ImmutableIntIntMap> asIntToIntMap() {
        return value() instanceof ImmutableIntIntMap map ? Optional.of(map) : Optional.empty();
    }

    default Optional<ImmutableIntObjectMap<ImmutableList<Integer>>> asIntToMultipleIntMap() {
        if (value() instanceof ImmutableIntObjectMap<?> map) {
            return Optional.of((ImmutableIntObjectMap<ImmutableList<Integer>>) map);
        }
        return Optional.empty();
    }

    default Optional<UUID> asUuid() {
        return value() instanceof UUID uuid ? Optional.of(uuid) : Optional.empty();
    }

    // ========== Assertion Methods (expectXxx) ==========

    /**
     * Returns the field value as a ConceptFacade, throwing if it's not.
     * <p>
     * Use this when the pattern definition guarantees the field is a concept.
     * This is the replacement for unsafe casts like {@code (ConceptFacade) fieldValue}.
     * If the value is an {@code EntityFacade}, it resolves the actual entity and asserts it's a concept.
     *
     * @return the ConceptFacade (never null)
     * @throws IllegalStateException if value is not a ConceptFacade or doesn't resolve to a ConceptEntity
     */
    default ConceptFacade expectConcept() {
        return asConcept().orElseThrow(() ->
                new IllegalStateException(
                        "Expected ConceptFacade but was " +
                                (value() != null ? value().getClass().getSimpleName() : "null")
                )
        );
    }

    default ConceptFacade expectConcept(String errorMessage) {
        return asConcept().orElseThrow(() -> new IllegalStateException(errorMessage));
    }

    /**
     * Returns the field value as a SemanticFacade, throwing if it's not.
     */
    default SemanticFacade expectSemantic() {
        return asSemantic().orElseThrow(() ->
                new IllegalStateException(
                        "Expected SemanticFacade but was " +
                                (value() != null ? value().getClass().getSimpleName() : "null")
                )
        );
    }

    default SemanticFacade expectSemantic(String errorMessage) {
        return asSemantic().orElseThrow(() -> new IllegalStateException(errorMessage));
    }

    /**
     * Returns the field value as a PatternFacade, throwing if it's not.
     */
    default PatternFacade expectPattern() {
        return asPattern().orElseThrow(() ->
                new IllegalStateException(
                        "Expected PatternFacade but was " +
                                (value() != null ? value().getClass().getSimpleName() : "null")
                )
        );
    }

    default PatternFacade expectPattern(String errorMessage) {
        return asPattern().orElseThrow(() -> new IllegalStateException(errorMessage));
    }

    /**
     * Returns the field value as a StampFacade, throwing if it's not.
     */
    default StampEntity expectStamp() {
        return asStamp().orElseThrow(() ->
                new IllegalStateException(
                        "Expected StampFacade but was " +
                                (value() != null ? value().getClass().getSimpleName() : "null")
                )
        );
    }

    default StampEntity expectStamp(String errorMessage) {
        return asStamp().orElseThrow(() -> new IllegalStateException(errorMessage));
    }

    default String expectString() {
        return asString().orElseThrow(() ->
                new IllegalStateException(
                        "Expected String but was " +
                                (value() != null ? value().getClass().getSimpleName() : "null")
                )
        );
    }

    default Integer expectInt() {
        return asInt().orElseThrow(() ->
                new IllegalStateException(
                        "Expected Integer but was " +
                                (value() != null ? value().getClass().getSimpleName() : "null")
                )
        );
    }

    default Boolean expectBoolean() {
        return asBoolean().orElseThrow(() ->
                new IllegalStateException(
                        "Expected Boolean but was " +
                                (value() != null ? value().getClass().getSimpleName() : "null")
                )
        );
    }

    default Float expectFloat() {
        return asFloat().orElseThrow(() ->
                new IllegalStateException(
                        "Expected Float but was " +
                                (value() != null ? value().getClass().getSimpleName() : "null")
                )
        );
    }

    default Long expectLong() {
        return asLong().orElseThrow(() ->
                new IllegalStateException(
                        "Expected Long but was " +
                                (value() != null ? value().getClass().getSimpleName() : "null")
                )
        );
    }

    default PublicId expectPublicId() {
        return asPublicId().orElseThrow(() ->
                new IllegalStateException(
                        "Expected PublicId but was " +
                                (value() != null ? value().getClass().getSimpleName() : "null")
                )
        );
    }

    // ========== Query Methods ==========

    /**
     * Returns {@code true} if the field value is a ConceptFacade (or resolves to a ConceptEntity).
     */
    default boolean isConcept() {
        return asConcept().isPresent();
    }

    /**
     * Returns {@code true} if the field value is a SemanticFacade (or resolves to a SemanticEntity).
     */
    default boolean isSemantic() {
        return asSemantic().isPresent();
    }

    /**
     * Returns {@code true} if the field value is a PatternFacade (or resolves to a PatternEntity).
     */
    default boolean isPattern() {
        return asPattern().isPresent();
    }

    /**
     * Returns {@code true} if the field value is a StampFacade (or resolves to a StampEntity).
     */
    default boolean isStamp() {
        return asStamp().isPresent();
    }

    default boolean isString() {
        return value() instanceof String;
    }

    default boolean isInt() {
        return value() instanceof Integer;
    }

    default boolean isNull() {
        return value() == null;
    }

    // ========== Escape Hatches ==========

    default Object orNull() {
        return value();
    }

    default <X extends Throwable> Object orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        Object val = value();
        if (val == null) {
            throw exceptionSupplier.get();
        }
        return val;
    }
}