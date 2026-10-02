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
     *
     * <p>The first version. In it a stamp position and a stamp path hold their path as a nid of
     * the store that wrote them; every other nid is written as its public id. Streams of this
     * version are still read, and no longer written.
     */
    int FIRST_VERSION = 10;

    /**
     * From this version a stamp position and a stamp path hold their path as a public id, so
     * no encoded object holds a nid ({@code IKE-Network/ike-issues#1172}). A nid is local to
     * one store, and an encoded object can be read against a store other than the one it was
     * written from.
     */
    int PATH_AS_PUBLIC_ID_VERSION = 11;

    /** The version every stream is written with, and the highest that is read. */
    int LATEST_VERSION = PATH_AS_PUBLIC_ID_VERSION;

    /**
     * Returns the version of the stream being decoded, after checking that this build can read
     * it. A decoder switches on the result where the layout it reads differs between versions.
     *
     * @param in the stream being decoded
     * @return the stream's version, from {@link #FIRST_VERSION} to {@link #LATEST_VERSION}
     * @throws EncodingExceptionUnchecked if the stream's version is outside that range, so that
     *                                    a stream from a later build is refused and not read
     *                                    with a layout it was not written in
     */
    static int checkVersion(DecoderInput in) {
        if (in.encodingFormatVersion < FIRST_VERSION || in.encodingFormatVersion > LATEST_VERSION) {
            throw EncodingExceptionUnchecked.makeWrongVersionException(FIRST_VERSION, LATEST_VERSION, in);
        }
        return in.encodingFormatVersion;
    }

    static <T> T decode(byte[] bytes) {
        try {
            DecoderInput input = new DecoderInput(bytes);
            String objectClassString = input.readString();
            return (T) decode(PluggableService.forName(objectClassString), Decoder.class, new Object[]{input});

        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | ClassNotFoundException ex) {
            throw new EncodingExceptionUnchecked(ex);
        }
    }

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

    static <T> T decode(Class<T> objectClass, byte[] bytes) {

        try {
            DecoderInput input = new DecoderInput(bytes);
            return decode(objectClass, Decoder.class, new Object[]{input});

        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new EncodingExceptionUnchecked(ex);
        }
    }

    static <T> T decode(Class<T> objectClass, DecoderInput input) {
        try {
            return decode(objectClass, Decoder.class, new Object[]{input});

        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new EncodingExceptionUnchecked(ex);
        }
    }

    default void addToEncodable(EncoderOutput out) {
        out.writeString(this.getClass().getName());
        encode(out);
    }

    @Encoder
    void encode(EncoderOutput out);

    default byte[] toBytes() {
        EncoderOutput out = encode();
        return out.buf.asArray();
    }

    /**
     * Encodes this object as a whole stream: the version, the class name, then the object.
     * The stream is stamped with {@link #LATEST_VERSION}, the layout every encoder writes.
     *
     * @return the output holding the stream
     */
    default EncoderOutput encode() {
        EncoderOutput encoderOutput = new EncoderOutput();
        encoderOutput.writeInt(LATEST_VERSION);
        encoderOutput.writeString(this.getClass().getName());
        encode(encoderOutput);
        return encoderOutput;
    }

    static NullEncodable nullEncodable = new NullEncodable();

    class NullEncodable implements Encodable {
        @Override
        public void encode(EncoderOutput out) {
            //No data to write.
        }
        @Decoder
        public static Object decode(DecoderInput in) {
            Encodable.checkVersion(in);
            return null;
        }
    }

}