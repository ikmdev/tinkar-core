package org.hl7.tinkar.binary;

public class MarshalExceptionUnchecked extends RuntimeException {

    public MarshalExceptionUnchecked(String message) {
        super(message);
    }

    public MarshalExceptionUnchecked(Throwable cause) {
        super(cause);
    }
}
