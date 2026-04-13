/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.common.binary;

/**
 * Unchecked exception thrown when binary encoding or decoding operations encounter
 * errors such as version mismatches or missing decoder methods.
 */
public class EncodingExceptionUnchecked extends RuntimeException {

    /**
     * Constructs an encoding exception with the specified detail message.
     *
     * @param message the detail message
     */
    public EncodingExceptionUnchecked(String message) {
        super(message);
    }

    /**
     * Constructs an encoding exception with the specified cause.
     *
     * @param cause the underlying cause of this exception
     */
    public EncodingExceptionUnchecked(Throwable cause) {
        super(cause);
    }

    /**
     * Creates an exception indicating that the encoding version in the decoder input
     * does not match the single expected version.
     *
     * @param expected the expected encoding version
     * @param in the decoder input containing the actual version
     * @return the constructed exception
     */
    public static EncodingExceptionUnchecked makeWrongVersionException(int expected, DecoderInput in) {
        return new EncodingExceptionUnchecked("Wrong encoding version. Expected: " + expected + " found: " + in.encodingFormatVersion());
    }

    /**
     * Creates an exception indicating that the encoding version in the decoder input
     * falls outside the supported range.
     *
     * @param lowerBound the lowest supported encoding version (inclusive)
     * @param upperBound the highest supported encoding version (inclusive)
     * @param in the decoder input containing the actual version
     * @return the constructed exception
     */
    public static EncodingExceptionUnchecked makeWrongVersionException(int lowerBound, int upperBound, DecoderInput in) {
        return new EncodingExceptionUnchecked("Wrong encoding version. Expected version between [" +
                lowerBound + ", " + upperBound + "] found: " + in.encodingFormatVersion());
    }
}
