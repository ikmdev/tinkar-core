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
package org.hl7.tinkar.json;

import java.io.StringWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.UUID;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.json.parser.ParseException;

/**
 *
 * Template for JsonMarshalable implementations classes
 *
 *
 * // Using a static method rather than a constructor eliminates the need for //
 * a readResolve method, but allows the implementation to decide how // to
 * handle special cases.
 *
 * @JsonUnmarshaler public static Object make(JSONObject jsonObject) {
 *
 * }
 *
 * @Override
 * @JsonMarshaler public void marshal(Writer out) { final JSONObject json = new
 * JSONObject(); json.put("class", this.getClass().getCanonicalName());
 *
 * json.writeJSONString(writer); throw new UnsupportedOperationException(); }
 *
 *
 *
 *
 *
 */
public interface JsonMarshalable {

    @JsonMarshaler
    void jsonMarshal(Writer out);

    default String toJsonString() {
        StringWriter writer = new StringWriter();
        jsonMarshal(writer);
        return writer.toString();
    }


    static <T> T makeSemanticVersion(Class<T> objectClass, String makerString, ImmutableList<UUID> componentUuidList,
                                     ImmutableList<UUID> definitionForSemanticUuids, ImmutableList<UUID> referencedComponentUuids) {
        try {
            JSONObject jsonObject = (JSONObject) JSONValue.parse(makerString);
            ArrayList<Method> unmarshalMethodList = new ArrayList<>();
            for (Method method: objectClass.getDeclaredMethods()) {
                for (Annotation annotation: method.getAnnotations()) {
                    if (annotation instanceof JsonSemanticVersionUnmarshaler) {
                        if (Modifier.isStatic(method.getModifiers())) {
                            unmarshalMethodList.add(method);
                        } else {
                            throw new RuntimeException("Marshaler method for class: " + objectClass
                                    + " is not static: " + method);
                        }
                    }
                }
            }
            if (unmarshalMethodList.isEmpty()) {
                throw new IllegalStateException("No unmarshal method for class: " + objectClass);
            } else if (unmarshalMethodList.size() == 1) {
                Method unmarshalMethod = unmarshalMethodList.get(0);
                return (T) unmarshalMethod.invoke(null, jsonObject, componentUuidList,
                        definitionForSemanticUuids, referencedComponentUuids);
            }
            throw new RuntimeException("More than one unmarshal method for class: " + objectClass
                    + " methods: " + unmarshalMethodList);

        } catch (ParseException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }


    static <T> T makeVersion(Class<T> objectClass, String makerString, ImmutableList<UUID> componentUuidList ) {
        try {
            JSONObject jsonObject = (JSONObject) JSONValue.parse(makerString);
            ArrayList<Method> unmarshalMethodList = new ArrayList<>();
            for (Method method: objectClass.getDeclaredMethods()) {
                for (Annotation annotation: method.getAnnotations()) {
                    if (annotation instanceof JsonVersionUnmarshaler) {
                        if (Modifier.isStatic(method.getModifiers())) {
                            unmarshalMethodList.add(method);
                        } else {
                            throw new RuntimeException("Marshaler method for class: " + objectClass
                                    + " is not static: " + method);
                        }
                    }
                }
            }
            if (unmarshalMethodList.isEmpty()) {
                throw new IllegalStateException("No unmarshal method for class: " + objectClass);
            } else if (unmarshalMethodList.size() == 1) {
                Method unmarshalMethod = unmarshalMethodList.get(0);
                return (T) unmarshalMethod.invoke(null, jsonObject, componentUuidList);
            }
            throw new RuntimeException("More than one unmarshal method for class: " + objectClass
                    + " methods: " + unmarshalMethodList);

        } catch (ParseException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }

    static <T> T make(Class<T> objectClass, String makerString) {
        try {
            JSONObject jsonObject = (JSONObject) JSONValue.parse(makerString);
            ArrayList<Method> unmarshalMethodList = new ArrayList<>();
            for (Method method: objectClass.getDeclaredMethods()) {
                for (Annotation annotation: method.getAnnotations()) {
                    if (annotation instanceof JsonChronologyUnmarshaler) {
                        if (Modifier.isStatic(method.getModifiers())) {
                            unmarshalMethodList.add(method);
                        } else {
                            throw new RuntimeException("Marshaler method for class: " + objectClass
                                    + " is not static: " + method);
                        }
                    }
                }
            }
            if (unmarshalMethodList.isEmpty()) {
                throw new IllegalStateException("No unmarshal method for class: " + objectClass);
            } else if (unmarshalMethodList.size() == 1) {
                Method unmarshalMethod = unmarshalMethodList.get(0);
                     return (T) unmarshalMethod.invoke(null, jsonObject);
             }
            throw new RuntimeException("More than one unmarshal method for class: " + objectClass
                    + " methods: " + unmarshalMethodList);
            
        } catch (ParseException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }
        
}
