package dev.ikm.tinkar.common.bind;


/**
 * A {@link ClassConceptBinding} backed by a Java enum constant.
 * Implementations expose the enum's {@link #ordinal()} to allow
 * positional lookup alongside the UUID-based identity from
 * {@link ClassConceptBinding}.
 */
public interface EnumConceptBinding extends ClassConceptBinding {

    /**
     * Returns the ordinal position of the enum constant that implements this binding.
     *
     * @return the zero-based ordinal of the backing enum constant
     */
    int ordinal();

}
