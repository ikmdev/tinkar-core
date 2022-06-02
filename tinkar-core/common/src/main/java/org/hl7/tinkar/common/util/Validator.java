package org.hl7.tinkar.common.util;

public class Validator {
    public static final void notZero(long value) {
        if (value == 0L) {
            throw new IllegalStateException("long value must not be zero");
        }
    }
    public static final void notZero(int value) {
        if (value == 0) {
            throw new IllegalStateException("int value must not be zero");
        }
    }
}
