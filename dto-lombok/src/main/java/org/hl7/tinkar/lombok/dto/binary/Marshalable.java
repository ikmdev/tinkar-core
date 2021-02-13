/*
 * Copyright 2020 kec.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hl7.tinkar.lombok.dto.binary;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.util.id.PublicId;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.UUID;


/**
 *
 * Template for marshalable class implementations classes
 *

 private static final int marshalVersion = 1;

 // Using a static method rather than a constructor eliminates the need for
 // a readResolve method, but allows the implementation to decide how
 // to handle special cases.

    @Unmarshaler
    public static StampDTO make(TinkarInput in) {
        try {
            int objectMarshalVersion = in.readInt();
            switch (objectMarshalVersion) {
                case marshalVersion:
                    throw new UnsupportedOperationException();
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported version: " + objectMarshalVersion);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        try {
            out.writeInt(marshalVersion);
            throw new UnsupportedOperationException();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }



 *
 *
 */
public interface Marshalable {
    /**
     * Only use the marshalVersion at the component, not at the version level.
     * If a version serialization format changes, bump the version for the entire
     * set of marshalable objects.
     */
    public static final int marshalVersion = 3;

    @Marshaler
    void marshal(TinkarOutput out);
    
    default TinkarByteArrayOutput marshal() {
        TinkarByteArrayOutput byteArrayOutput = TinkarByteArrayOutput.make();
        marshal(byteArrayOutput);
        return byteArrayOutput;
    }

    static <T> T makeVersion(Class<T> objectClass, TinkarByteArrayOutput output, PublicId componentPublicId) {
        return makeVersion(objectClass, output.getBytes(), componentPublicId);
    }

    static <T> T makeVersion(Class<T> objectClass, byte[] input, PublicId componentPublicId) {
        return makeVersion(objectClass, TinkarInput.make(input), componentPublicId);
    }

    static <T> T makeSemanticVersion(Class<T> objectClass, TinkarInput input, PublicId componentPublicId,
                                     PublicId pattrenForSemanticPublicId, PublicId referencedComponentPublicId) {
        try {
            return unmarshal(objectClass, SemanticVersionUnmarshaler.class, new Object[] { input, componentPublicId,
                    pattrenForSemanticPublicId, referencedComponentPublicId});
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new MarshalExceptionUnchecked(ex);
        }
    }

    static <T> T makeVersion(Class<T> objectClass, TinkarInput input, PublicId componentPublicId) {
        try {
            return unmarshal(objectClass, VersionUnmarshaler.class, new Object[] { input, componentPublicId});

        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new MarshalExceptionUnchecked(ex);
        }
    }

    static <T> T make(Class<T> objectClass, TinkarInput input) {
        try {
            return unmarshal(objectClass, Unmarshaler.class, new Object[] { input });

        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new MarshalExceptionUnchecked(ex);
        }
    }

    static <T> T make(Class<T> objectClass, byte[] input, int tinkarFormatVersion) {
        return make(objectClass, TinkarInput.make(input, tinkarFormatVersion));
    }

    /**
     * Will read the first int of the byte[] to get the tinkar format version.
     * @param objectClass
     * @param input
     * @param <T>
     * @return
     */
    static <T> T make(Class<T> objectClass, byte[] input) {
        return make(objectClass, TinkarInput.make(input));
    }

    static <T> T make(Class<T> objectClass, TinkarByteArrayOutput output) {
        return make(objectClass, TinkarInput.make(output));
    }

    static <T> T unmarshal(Class<T> objectClass, Class<? extends Annotation> annotationClass,
                                   Object[] parameters) throws IllegalAccessException, InvocationTargetException {
        ArrayList<Method> unmarshalMethodList = getUnmarshalMethods(objectClass, annotationClass);
        if (unmarshalMethodList.isEmpty()) {
            throw new MarshalExceptionUnchecked("No " + annotationClass.getSimpleName() +
                    " method for class: " + objectClass);
        } else if (unmarshalMethodList.size() == 1) {
            Method unmarshalMethod = unmarshalMethodList.get(0);
            return (T) unmarshalMethod.invoke(null, parameters);
        }
        throw new MarshalExceptionUnchecked("More than one unmarshal method for class: " + objectClass
                + " methods: " + unmarshalMethodList);
    }

    static <T> ArrayList<Method> getUnmarshalMethods(Class<T> objectClass, Class<? extends Annotation> annotationClass) {
        ArrayList<Method> unmarshalMethodList = new ArrayList<>();
        for (Method method: objectClass.getDeclaredMethods()) {
            for (Annotation annotation: method.getAnnotations()) {
                if (annotation.annotationType().equals(annotationClass)) {
                    if (Modifier.isStatic(method.getModifiers())) {
                        unmarshalMethodList.add(method);
                    } else {
                        throw new MarshalExceptionUnchecked(annotationClass.getSimpleName() + " method for class: " + objectClass
                                + " is not static: " + method);
                    }
                }
            }
        }
        return unmarshalMethodList;
    }

}