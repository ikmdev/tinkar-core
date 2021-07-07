package org.hl7.tinkar.common.binary;

public class EncodingExceptionUnchecked extends RuntimeException {

    public EncodingExceptionUnchecked(String message) {
        super(message);
    }

    public EncodingExceptionUnchecked(Throwable cause) {
        super(cause);
    }

    public static EncodingExceptionUnchecked makeWrongVersionException(int expected, DecoderInput in) {
        return new EncodingExceptionUnchecked("Wrong encoding version. Expected: " + expected + " found: " + in.encodingFormatVersion());
    }
}
