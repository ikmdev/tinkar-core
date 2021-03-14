package org.hl7.tinkar.common.binary;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 *  Used to indicate which instance method shall be used to encode the object within a byte stream.
 *
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Encoder {

}
