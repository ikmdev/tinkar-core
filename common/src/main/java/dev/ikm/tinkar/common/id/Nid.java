package dev.ikm.tinkar.common.id;

/**
 * Native Identifier (NID) validation utilities for the Tinkar system.
 * <p>A NID is an integer identifier used throughout Tinkar to reference entities like concepts,
 * semantics, and patterns. Certain integer values are reserved for special purposes and should
 * not be used as valid NIDs.
 * <p><b>Reserved Values:</b>
 * <ul>
 *   <li><b>0</b> - {@link #UNSET}: a nid that is not set</li>
 *   <li><b>Integer.MAX_VALUE</b> - {@link #NOT_APPLICABLE}: no nid applies here (for example the
 *   pattern of an entity that is not a semantic), or a wildcard</li>
 *   <li><b>Integer.MIN_VALUE</b> - {@link #NONE}: no nid; on the entity change broadcast,
 *   everything changed</li>
 * </ul>
 * <p>These are nid sentinels only. Uncommitted, canceled and latest are stamp <em>times</em>
 * ({@code Long.MAX_VALUE} for uncommitted and latest, {@code Long.MIN_VALUE} for canceled), never
 * nids.
 * <p>This interface provides static methods to validate NIDs in both throwing and non-throwing
 * variants, ensuring data integrity and preventing corruption from reserved value usage.
 * <p>It also holds what the move to {@code long} nids needs (design
 * {@code design-2026-09-30-64-bit-nids}): the four sentinels as named {@code long} constants,
 * {@link #narrowChecked(long)}, and the 64-bit layout, 32/32, in which a nid is a pattern sequence
 * in the upper half and an element sequence in the lower, both from 1 through 2,147,483,646.
 * In a 6-bit, 8-bit or sequential store a nid is the {@code int} the store assigns, widened.
 *
 * @see #validate(int) for throwing validation
 * @see #isValid(int) for non-throwing validation
 */
public interface Nid {

    /**
     * The sentinel for a nid that is not set: 0.
     */
    long UNSET = 0L;

    /**
     * The sentinel for no nid: {@code Integer.MIN_VALUE}, widened. On the entity change
     * broadcast it means that everything changed. This is the form written: it is what the
     * providers that keep {@code int} nids store, on disk as well as in memory.
     * {@link #NONE_64} means the same; test with {@link #isNone(long)}.
     */
    long NONE = Integer.MIN_VALUE;

    /**
     * {@code Long.MIN_VALUE}: the same signal as {@link #NONE}, recognized wherever a nid is
     * tested and mapped to {@code NONE} by {@link #narrowChecked(long)}, so that a 64-bit caller
     * using it keeps working against a provider that holds {@code int} nids. Never written.
     */
    long NONE_64 = Long.MIN_VALUE;

    /**
     * The sentinel for a nid that does not apply, and for a wildcard: {@code Integer.MAX_VALUE},
     * widened; for example the pattern of an entity that is not a semantic, which the spined-array
     * provider stores as such. This is the form written. {@link #NOT_APPLICABLE_64} means the
     * same; test with {@link #isNotApplicable(long)}.
     */
    long NOT_APPLICABLE = Integer.MAX_VALUE;

    /**
     * {@code Long.MAX_VALUE}: the same signal as {@link #NOT_APPLICABLE}, recognized wherever a
     * nid is tested and mapped to {@code NOT_APPLICABLE} by {@link #narrowChecked(long)}. Never
     * written, and never a 64-bit nid: neither half of one reaches {@code Integer.MAX_VALUE}.
     */
    long NOT_APPLICABLE_64 = Long.MAX_VALUE;

    /**
     * The sentinel for a nid that was not found: -1. Unlike the others, -1 is a value the
     * 6-bit and 8-bit layouts can produce, so it is only a sentinel where the code using it says
     * so.
     */
    long NOT_FOUND = -1L;

    /**
     * The largest value of either half of a 64-bit nid. Both halves run from 1 through
     * 2,147,483,646: like 0, {@code Integer.MAX_VALUE} is never a sequence, so no 64-bit nid is
     * {@link #NOT_APPLICABLE_64}.
     */
    int MAX_SEQUENCE_64 = Integer.MAX_VALUE - 1;

    /**
     * Whether a value is the none sentinel, in either form.
     *
     * @param nid the value
     * @return {@code true} for {@link #NONE} and {@link #NONE_64}
     */
    static boolean isNone(long nid) {
        return nid == NONE || nid == NONE_64;
    }

    /**
     * Whether a value is the not-applicable (wildcard) sentinel, in either form.
     *
     * @param nid the value
     * @return {@code true} for {@link #NOT_APPLICABLE} and {@link #NOT_APPLICABLE_64}
     */
    static boolean isNotApplicable(long nid) {
        return nid == NOT_APPLICABLE || nid == NOT_APPLICABLE_64;
    }

    /**
     * Narrows a nid to an {@code int}, refusing one that does not fit. The only sanctioned way to
     * turn a nid into an {@code int}: used by the providers that keep {@code int} nids, at their
     * edge, and by the format 1 entity codec, which writes each reference as four bytes.
     * <p>The test is that the value fits a signed {@code int}, not that its upper half is zero:
     * sequential nids and many packed nids are negative, and widened they have every upper bit
     * set. The 64-bit forms of the sentinels, {@link #NOT_APPLICABLE_64} and {@link #NONE_64},
     * narrow to the {@code int} forms the providers store.
     *
     * @param nid the nid
     * @return the same nid as an {@code int}
     * @throws IllegalStateException if the nid does not fit an {@code int}, which only a 64-bit
     *                               nid does not
     */
    static int narrowChecked(long nid) {
        if (nid == NOT_APPLICABLE_64) {
            return Integer.MAX_VALUE;
        }
        if (nid == NONE_64) {
            return Integer.MIN_VALUE;
        }
        int narrowed = (int) nid;
        if (narrowed != nid) {
            throw new IllegalStateException("nid " + nid + " (pattern sequence " + patternSequence64(nid)
                    + ", element sequence " + elementSequence64(nid)
                    + ") is a 64-bit nid, and it reached code that holds an int nid");
        }
        return narrowed;
    }

    /**
     * Composes a 64-bit nid: the pattern sequence in the upper 32 bits, the element sequence in
     * the lower 32. {@code long} order, pattern-then-element order and big-endian key order are
     * the same order.
     *
     * @param patternSequence the element sequence of the entity's pattern within the
     *                        pattern-of-patterns, from 1 through {@value #MAX_SEQUENCE_64}
     * @param elementSequence the entity's element sequence within its pattern, from 1 through
     *                        {@value #MAX_SEQUENCE_64}
     * @return the 64-bit nid, always at least 2<sup>32</sup> + 1
     * @throws IllegalArgumentException if either sequence is outside 1 through {@value #MAX_SEQUENCE_64}
     */
    static long compose64(int patternSequence, int elementSequence) {
        if (patternSequence < 1 || patternSequence > MAX_SEQUENCE_64) {
            throw new IllegalArgumentException("pattern sequence " + patternSequence
                    + " is out of range: a 64-bit nid's halves run from 1 through " + MAX_SEQUENCE_64);
        }
        if (elementSequence < 1 || elementSequence > MAX_SEQUENCE_64) {
            throw new IllegalArgumentException("element sequence " + elementSequence
                    + " is out of range: a 64-bit nid's halves run from 1 through " + MAX_SEQUENCE_64);
        }
        return ((long) patternSequence << 32) | elementSequence;
    }

    /**
     * The upper half of a 64-bit nid: the element sequence of the entity's pattern within the
     * pattern-of-patterns. Not checked; meaningful only for a {@linkplain #isValid64 valid}
     * 64-bit nid.
     *
     * @param nid a 64-bit nid
     * @return its pattern sequence
     */
    static int patternSequence64(long nid) {
        return (int) (nid >>> 32);
    }

    /**
     * The lower half of a 64-bit nid: the entity's element sequence within its pattern, usable as
     * an index within the pattern. Not checked; meaningful only for a {@linkplain #isValid64
     * valid} 64-bit nid.
     *
     * @param nid a 64-bit nid
     * @return its element sequence
     */
    static int elementSequence64(long nid) {
        return (int) nid;
    }

    /**
     * Whether a value is a valid 64-bit nid: both halves from 1 through
     * {@value #MAX_SEQUENCE_64}. No widened {@code int} is one, its upper half being 0 or -1, and
     * no sentinel in either form is one.
     *
     * @param nid the value
     * @return {@code true} if it is a valid 64-bit nid
     */
    static boolean isValid64(long nid) {
        int patternSequence = patternSequence64(nid);
        int elementSequence = elementSequence64(nid);
        return patternSequence > 0 && patternSequence <= MAX_SEQUENCE_64
                && elementSequence > 0 && elementSequence <= MAX_SEQUENCE_64;
    }

    /**
     * Checks that a value is a valid 64-bit nid and returns it.
     *
     * @param nid the value
     * @return the same value
     * @throws IllegalArgumentException if it is not a valid 64-bit nid
     * @see #isValid64(long)
     */
    static long validate64(long nid) {
        if (!isValid64(nid)) {
            throw new IllegalArgumentException("nid " + nid + " is not a 64-bit nid: its pattern sequence is "
                    + patternSequence64(nid) + " and its element sequence " + elementSequence64(nid)
                    + "; both run from 1 through " + MAX_SEQUENCE_64);
        }
        return nid;
    }

    /**
     * Validates that a NID is not one of the reserved/invalid values and returns it.
     * <p>     * These values are reserved in Tinkar for special purposes:
     * <ul>
     *   <li><b>0</b> - {@link #UNSET}: a nid that is not set</li>
     *   <li><b>Integer.MAX_VALUE</b> - {@link #NOT_APPLICABLE}: no nid applies, or a wildcard</li>
     *   <li><b>Integer.MIN_VALUE</b> - {@link #NONE}: no nid, or everything changed</li>
     * </ul>
     * <p>     * This validation is useful for:
     * <ul>
     *   <li>Unit testing field values to ensure they don't contain reserved integers</li>
     *   <li>Validating user input before storing in semantic fields</li>
     *   <li>Asserting that entity NIDs are valid before processing</li>
     *   <li>Preventing data corruption from reserved value usage</li>
     * </ul>
     *
     * <p><b>Usage Examples:</b></p>
     * <pre>{@code
     * // Validate and assign in one line
     * public void setMeaningNid(int meaningNid) {
     *     this.meaningNid = Nid.validate(meaningNid);
     * }
     *
     * // In tests - validate field values
     * @Test
     * void testSemanticFieldsNotReserved() {
     *     SemanticEntity semantic = EntityHandle.getSemanticOrThrow(semanticNid);
     *     semantic.fieldValues().forEach(field -> {
     *         if (field instanceof Integer intValue) {
     *             Nid.validate(intValue);
     *         }
     *     });
     * }
     *
     * // Validate entity references before use
     * Entity<?> entity = EntityHandle.getEntityOrThrow(Nid.validate(entityNid));
     * }</pre>
     *
     * @param nid the NID value to validate
     * @return the validated NID (same as input)
     * @throws IllegalArgumentException if the NID is one of the reserved values (0, Integer.MAX_VALUE, Integer.MIN_VALUE)
     * @see #isValid(int) for non-throwing validation
     */
    static int validate(int nid) {
        if (nid == 0) {
            throw new IllegalArgumentException(
                    "NID cannot be 0 - this is a reserved value representing 'not set' or invalid NID"
            );
        }
        if (nid == Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "NID cannot be Integer.MAX_VALUE (" + Integer.MAX_VALUE + ") - " +
                            "this is a reserved sentinel value (not applicable, or a wildcard)"
            );
        }
        if (nid == Integer.MIN_VALUE) {
            throw new IllegalArgumentException(
                    "NID cannot be Integer.MIN_VALUE (" + Integer.MIN_VALUE + ") - " +
                            "this is a reserved sentinel value (none)"
            );
        }
        return nid;
    }

    /**
     * Checks if a NID is valid (not one of the reserved values).
     * <p>     * This is a non-throwing variant of {@link #validate(int)} that returns a boolean
     * instead of throwing an exception. Use this when you want to check validity without
     * exception handling.
     *
     * <p><b>Usage Examples:</b></p>
     * <pre>{@code
     * // Filter valid NIDs from a collection
     * List<Integer> validNids = allNids.stream()
     *     .filter(Nid::isValid)
     *     .toList();
     *
     * // Conditional validation
     * if (Nid.isValid(nid)) {
     *     processEntity(nid);
     * } else {
     *     LOG.warn("Skipping invalid NID: {}", nid);
     * }
     *
     * // Count valid vs invalid
     * long validCount = nids.stream()
     *     .filter(Nid::isValid)
     *     .count();
     * }</pre>
     *
     * @param nid the NID value to check
     * @return {@code true} if the NID is valid (not 0, Integer.MAX_VALUE, or Integer.MIN_VALUE)
     * @see #validate(int) for validation that throws exceptions
     */
    static boolean isValid(int nid) {
        return nid != 0 && nid != Integer.MAX_VALUE && nid != Integer.MIN_VALUE;
    }

    /**
     * Validates that a NID is valid with a custom error message and returns it.
     * <p>     * This variant allows you to provide domain-specific context in the exception message.
     *
     * <p><b>Usage Example:</b></p>
     * <pre>{@code
     * // Validate with context and assign in one line
     * public void setMeaningNid(int meaningNid) {
     *     this.meaningNid = Nid.validate(meaningNid, "Field meaning NID");
     * }
     * }</pre>
     *
     * @param nid the NID value to validate
     * @param contextDescription a description of what this NID represents (e.g., "field value", "concept reference")
     * @return the validated NID (same as input)
     * @throws IllegalArgumentException if the NID is one of the reserved values, with contextual error message
     */
    static int validate(int nid, String contextDescription) {
        if (nid == 0) {
            throw new IllegalArgumentException(
                    contextDescription + " cannot be 0 - this is a reserved value representing 'not set' or invalid NID"
            );
        }
        if (nid == Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    contextDescription + " cannot be Integer.MAX_VALUE (" + Integer.MAX_VALUE + ") - " +
                            "this is a reserved sentinel value"
            );
        }
        if (nid == Integer.MIN_VALUE) {
            throw new IllegalArgumentException(
                    contextDescription + " cannot be Integer.MIN_VALUE (" + Integer.MIN_VALUE + ") - " +
                            "this is a reserved sentinel value"
            );
        }
        return nid;
    }
}
