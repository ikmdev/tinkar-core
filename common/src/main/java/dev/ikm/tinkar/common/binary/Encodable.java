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

import dev.ikm.tinkar.common.service.PluggableService;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

/**
 * Template for marshalable class implementations classes
 *
 * <pre><code>

 &#64;Decoder
 public static ClassBeingDecoded decode(DecoderInput in) {
    switch (Encodable.checkVersion(in)) {
        // if special handling for particular versions, add case condition.
        default -> {
            // decode the input
            throw new UnsupportedOperationException("Implement decoding");
        }
    }
 }


 &#64;Override
 &#64;Encoder
 public void encode(EncoderOutput out) {
    try {
        // Creation of the EncoderOutput class will handle writing version
        // Writing the class name, if necessary, happens before this call.
        // Just write the class data here.
        throw new UnsupportedOperationException("Implement encoding");
    } catch (IOException ex) {
        throw new UncheckedIOException(ex);
    }
 }

</code></pre>
 *
  */
public interface Encodable {

    /**
     * Only use the encodingVersion at the stream level. Components within the stream
     * should not have independent versions.
     * If a component or version encoding format changes, bump the encoding version for the entire
     * set of marshalable objects.
     */
    /** The first supported encoding format version. */
    int FIRST_VERSION = 10;
    /** The latest supported encoding format version. */
    int LATEST_VERSION = 11;

    /**
     * Validates that the encoding format version in the given decoder input falls within
     * the supported range [{@link #FIRST_VERSION}, {@link #LATEST_VERSION}].
     *
     * @param in the decoder input whose version to check
     * @return the encoding format version from the input
     * @throws EncodingExceptionUnchecked if the version is out of the supported range
     */
    static int checkVersion(DecoderInput in) {
        if (in.encodingFormatVersion < FIRST_VERSION || in.encodingFormatVersion > LATEST_VERSION) {
            EncodingExceptionUnchecked.makeWrongVersionException(FIRST_VERSION, LATEST_VERSION, in);
        }
        return in.encodingFormatVersion;
    }

    /**
     * Decodes an object from a byte array. The class name is read from the byte stream,
     * resolved via {@link PluggableService}, and the appropriate decoder method is invoked.
     *
     * @param <T> the expected type of the decoded object
     * @param bytes the byte array to decode
     * @return the decoded object
     * @throws EncodingExceptionUnchecked if decoding fails
     */
    static <T> T decode(byte[] bytes) {
        try {
            DecoderInput input = new DecoderInput(bytes);
            String objectClassString = input.readString();
            return (T) decode(PluggableService.forName(objectClassString), Decoder.class, new Object[]{input});

        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | ClassNotFoundException ex) {
            throw new EncodingExceptionUnchecked(ex);
        }
    }

    /**
     * Decodes an object by invoking the static method annotated with the given annotation class
     * on the specified object class.
     *
     * @param <T> the expected type of the decoded object
     * @param objectClass the class to decode into
     * @param annotationClass the annotation that marks the decoder method
     * @param parameters the parameters to pass to the decoder method
     * @return the decoded object
     * @throws IllegalAccessException if the decoder method is inaccessible
     * @throws InvocationTargetException if the decoder method throws an exception
     * @throws EncodingExceptionUnchecked if no decoder method or multiple decoder methods are found
     */
    static <T> T decode(Class<T> objectClass, Class<? extends Annotation> annotationClass,
                        Object[] parameters) throws IllegalAccessException, InvocationTargetException {
        ArrayList<Method> unmarshalMethodList = getDecodingMethods(objectClass, annotationClass);
        if (unmarshalMethodList.isEmpty()) {
            throw new EncodingExceptionUnchecked("No " + annotationClass.getSimpleName() +
                    " method for class: " + objectClass);
        } else if (unmarshalMethodList.size() == 1) {
            Method unmarshalMethod = unmarshalMethodList.get(0);
            return (T) unmarshalMethod.invoke(null, parameters);
        }
        throw new EncodingExceptionUnchecked("More than one unmarshal method for class: " + objectClass
                + " methods: " + unmarshalMethodList);
    }

    /**
     * Finds all static methods on the given class annotated with the specified annotation.
     *
     * @param <T> the class type
     * @param objectClass the class to inspect for decoder methods
     * @param annotationClass the annotation to search for
     * @return a list of matching static methods
     * @throws EncodingExceptionUnchecked if a matching method is not static
     */
    static <T> ArrayList<Method> getDecodingMethods(Class<T> objectClass, Class<? extends Annotation> annotationClass) {
        ArrayList<Method> unmarshalMethodList = new ArrayList<>();
        for (Method method : objectClass.getDeclaredMethods()) {
            for (Annotation annotation : method.getAnnotations()) {
                if (annotation.annotationType().equals(annotationClass)) {
                    if (Modifier.isStatic(method.getModifiers())) {
                        unmarshalMethodList.add(method);
                    } else {
                        throw new EncodingExceptionUnchecked(annotationClass.getSimpleName() + " method for class: " + objectClass
                                + " is not static: " + method);
                    }
                }
            }
        }
        return unmarshalMethodList;
    }

    /**
     * Decodes an object of the specified class from a byte array.
     *
     * @param <T> the expected type of the decoded object
     * @param objectClass the class to decode into
     * @param bytes the byte array to decode
     * @return the decoded object
     * @throws EncodingExceptionUnchecked if decoding fails
     */
    static <T> T decode(Class<T> objectClass, byte[] bytes) {

        try {
            DecoderInput input = new DecoderInput(bytes);
            return decode(objectClass, Decoder.class, new Object[]{input});

        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new EncodingExceptionUnchecked(ex);
        }
    }

    /**
     * Decodes an object of the specified class from a {@link DecoderInput}.
     *
     * @param <T> the expected type of the decoded object
     * @param objectClass the class to decode into
     * @param input the decoder input to read from
     * @return the decoded object
     * @throws EncodingExceptionUnchecked if decoding fails
     */
    static <T> T decode(Class<T> objectClass, DecoderInput input) {
        try {
            return decode(objectClass, Decoder.class, new Object[]{input});

        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new EncodingExceptionUnchecked(ex);
        }
    }

    /**
     * Writes this object's class name followed by its encoded data to the given output.
     * Used when encoding a polymorphic object whose type must be recoverable at decode time.
     *
     * @param out the encoder output to write to
     */
    default void addToEncodable(EncoderOutput out) {
        out.writeString(this.getClass().getName());
        encode(out);
    }

    /**
     * Encodes this object's data to the given encoder output.
     *
     * @param out the encoder output to write to
     */
    @Encoder
    void encode(EncoderOutput out);

    /**
     * Encodes this object to a byte array, including the version header and class name.
     *
     * @return the encoded byte array
     */
    default byte[] toBytes() {
        EncoderOutput out = encode();
        return out.buf.asArray();
    }

    /**
     * Encodes this object to a new {@link EncoderOutput}, writing the version header,
     * class name, and object data.
     *
     * @return the encoder output containing the encoded data
     */
    default EncoderOutput encode() {
        EncoderOutput encoderOutput = new EncoderOutput();
        encoderOutput.writeInt(FIRST_VERSION);
        encoderOutput.writeString(this.getClass().getName());
        encode(encoderOutput);
        return encoderOutput;
    }

    /** A singleton {@link Encodable} implementation that encodes and decodes to {@code null}. */
    static NullEncodable nullEncodable = new NullEncodable();

    /**
     * An {@link Encodable} that writes no data and decodes to {@code null}.
     * Used as a placeholder when no meaningful encodable value exists.
     */
    class NullEncodable implements Encodable {
        /** Constructs a NullEncodable instance. */
        NullEncodable() {
            // default constructor
        }

        /**
         * {@inheritDoc}
         * This implementation writes no data.
         */
        @Override
        public void encode(EncoderOutput out) {
            //No data to write.
        }

        /**
         * Decodes a null value from the given input after verifying the version.
         *
         * @param in the decoder input to read from
         * @return {@code null} always
         */
        @Decoder
        public static Object decode(DecoderInput in) {
            Encodable.checkVersion(in);
            return null;
        }
    }

}