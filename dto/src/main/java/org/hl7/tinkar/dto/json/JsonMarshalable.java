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
package org.hl7.tinkar.dto.json;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.dto.binary.MarshalExceptionUnchecked;
import org.hl7.tinkar.dto.binary.Marshalable;
import org.hl7.tinkar.dto.json.parser.ParseException;

import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

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
            return Marshalable.unmarshal(objectClass, JsonSemanticVersionUnmarshaler.class,
                    new Object[]{ jsonObject, componentUuidList,
                            definitionForSemanticUuids, referencedComponentUuids});

        } catch (ParseException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new MarshalExceptionUnchecked(ex);
        }
    }


    static <T> T makeVersion(Class<T> objectClass, String makerString, ImmutableList<UUID> componentUuidList ) {
        try {
            JSONObject jsonObject = (JSONObject) JSONValue.parse(makerString);
            return Marshalable.unmarshal(objectClass, JsonVersionUnmarshaler.class,
                    new Object[]{ jsonObject, componentUuidList });

        } catch (ParseException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new MarshalExceptionUnchecked(ex);
        }
    }

    static <T> T make(Class<T> objectClass, String makerString) {
        try {
            JSONObject jsonObject = (JSONObject) JSONValue.parse(makerString);
            return Marshalable.unmarshal(objectClass, JsonChronologyUnmarshaler.class,
                    new Object[]{ jsonObject });
        } catch (ParseException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new MarshalExceptionUnchecked(ex);
        }
    }

}
