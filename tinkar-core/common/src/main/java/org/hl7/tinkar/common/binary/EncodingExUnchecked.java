package org.hl7.tinkar.common.binary;

public class EncodingExUnchecked extends RuntimeException {

    public EncodingExUnchecked(String message) {
        super(message);
    }

    public EncodingExUnchecked(Throwable cause) {
        super(cause);
    }
}
