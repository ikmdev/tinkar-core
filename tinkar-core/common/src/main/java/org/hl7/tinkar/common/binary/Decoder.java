package org.hl7.tinkar.common.binary;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 *  Used to indicate which static method on a class shall be used as the
 *   decode from byte stream to object.
 *
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Decoder {
}
