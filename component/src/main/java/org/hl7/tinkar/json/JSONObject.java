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

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.time.Instant;
import java.util.*;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.dto.ConceptVersionDTO;
import org.hl7.tinkar.dto.DefinitionForSemanticVersionDTO;

import org.hl7.tinkar.dto.FieldDefinitionDTO;
import org.hl7.tinkar.dto.SemanticVersionDTO;

/**
 * Original obtained from: https://github.com/fangyidong/json-simple under
 * Apache 2 license Original project had no support for Java Platform Module
 * System, and not updated for 8 years. Integrated here to integrate with Java
 * Platform Module System.
 *
 * A JSON object. Key value pairs are unordered. JSONObject supports
 * java.util.Map interface.
 *
 * @author FangYidong<fangyidong@yahoo.com.cn>
 */
public class JSONObject extends HashMap<String, Object>
        implements Map<String, Object>, JSONAware, JSONStreamAware {

    private static final long serialVersionUID = -503443796854799292L;

    public JSONObject() {
        super();
    }

    /**
     * Allows creation of a JSONObject from a Map. After that, both the
     * generated JSONObject and the Map can be modified independently.
     *
     * @param map
     */
    public JSONObject(Map<String, Object> map) {
        super(map);
    }

    /**
     * Encode a map into JSON text and write it to out.If this map is also a
     * JSONAware or JSONStreamAware, JSONAware or JSONStreamAware specific
     * behaviours will be ignored at this top level.
     *
     * @param map
     * @param out
     *
     * @see org.hl7.tinkar.JSONValue#writeJSONString(Object, Writer)
     *
     */
    public static void writeJSONString(Map<String, Object> map, Writer out) {
        try {
            if (map == null) {
                out.write("null");
                return;
            }

            boolean first = true;
            Iterator<Entry<String, Object>> iter = map.entrySet().stream().sorted(Map.Entry.comparingByKey()).iterator();

            out.write('{');
            while (iter.hasNext()) {
                if (first) {
                    first = false;
                } else {
                    out.write(',');
                }
                Map.Entry<String, Object> entry = iter.next();
                out.write('\"');
                out.write(escape(String.valueOf(entry.getKey())));
                out.write('\"');
                out.write(':');
                JSONValue.writeJSONString(entry.getValue(), out);
            }
            out.write('}');
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public void writeJSONString(Writer out) {
        writeJSONString(this, out);
    }

    /**
     * Convert a map to JSON text. The result is a JSON object. If this map is
     * also a JSONAware, JSONAware specific behaviours will be omitted at this
     * top level.
     *
     * @see org.hl7.tinkar.JSONValue#toJSONString(Object)
     *
     * @param map
     * @return JSON text, or "null" if map is null.
     */
    public static String toJSONString(Map<String, Object> map) {
        final StringWriter writer = new StringWriter();
            writeJSONString(map, writer);
            return writer.toString();
    }

    @Override
    public String toJSONString() {
        return toJSONString(this);
    }

    @Override
    public String toString() {
        return toJSONString();
    }

    public static String toString(String key, Object value) {
        StringBuilder sb = new StringBuilder();
        sb.append('\"');
        if (key == null) {
            sb.append("null");
        } else {
            JSONValue.escape(key, sb);
        }
        sb.append('\"').append(':');

        sb.append(JSONValue.toJSONString(value));

        return sb.toString();
    }

    /**
     * Escape quotes, \, /, \r, \n, \b, \f, \t and other control characters
     * (U+0000 through U+001F). It's the same as JSONValue.escape() only for
     * compatibility here.
     *
     * @see org.hl7.tinkar.JSONValue#escape(String)
     *
     * @param s
     * @return
     */
    public static String escape(String s) {
        return JSONValue.escape(s);
    }

    public ImmutableList<UUID> asImmutableUuidList(String key) {
        return Lists.immutable.of(asUuidArray(key));
    }

    public UUID[] asUuidArray(String key) {
        JSONArray jsonArray = (JSONArray) get(key);
        UUID[] array = new UUID[jsonArray.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = (UUID) jsonArray.get(i);
        }
        return array;
    }

    public ImmutableList<FieldDefinitionDTO> asFieldDefinitionList(String key) {
        JSONArray jsonArray = (JSONArray) get(key);
        FieldDefinitionDTO[] array = new FieldDefinitionDTO[jsonArray.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = FieldDefinitionDTO.make((JSONObject) jsonArray.get(i));
        }
        return Lists.immutable.of(array);
    }

    public Instant asInstant(String key) {
        return Instant.parse((String) get(key));
    }

    public ImmutableList<ConceptVersionDTO> asConceptVersionList(String key, ImmutableList<UUID> componentUuids) {
        JSONArray jsonArray = (JSONArray) get(key);
        ConceptVersionDTO[] array = new ConceptVersionDTO[jsonArray.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = ConceptVersionDTO.make((JSONObject) jsonArray.get(i), componentUuids);
        }
        return Lists.immutable.of(array);
    }

    public ImmutableList<DefinitionForSemanticVersionDTO> asDefinitionForSemanticVersionList(String key, ImmutableList<UUID> componentUuids) {
        JSONArray jsonArray = (JSONArray) get(key);
        DefinitionForSemanticVersionDTO[] array = new DefinitionForSemanticVersionDTO[jsonArray.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = DefinitionForSemanticVersionDTO.make((JSONObject) jsonArray.get(i), componentUuids);
        }
        return Lists.immutable.of(array);
    }



    public ImmutableList<Object> asImmutableObjectList(String key) {
        JSONArray jsonArray = (JSONArray) get(key);
        Object[] array = new Object[jsonArray.size()];
        for (int i = 0; i < array.length; i++) {
            Object obj = jsonArray.get(i);
            if (obj instanceof String string) {
                array[i] = string;
            } else if (obj instanceof Instant instant) {
                array[i] = instant;
            } else if (obj instanceof UUID uuid) {
                array[i] = uuid;
            } else if (obj instanceof Number number) {
                array[i] = number;
            } else if (obj instanceof JSONObject jsonObject) {
                handleJsonObject(array, i, jsonObject);
            } else {
               throw new UnsupportedOperationException("asImmutableObjectList can't handle: " + obj);
            }
        }
        return Lists.immutable.of(array);
    }

    private void handleJsonObject(Object[] array, int i, JSONObject jsonObject) {
        if (jsonObject.containsKey(ComponentFieldForJson.CLASS)) {
            try {
                String className = (String) jsonObject.get(ComponentFieldForJson.CLASS);
                array[i] = JsonMarshalable.make(Class.forName(className), jsonObject.toJSONString());
            } catch (ClassNotFoundException e) {
                throw new UnsupportedOperationException("JSON object has no class... " + jsonObject, e);
            }
        } else {
            throw new UnsupportedOperationException("[2] JSON object has no class... " + jsonObject);
        }
    }

    public ImmutableList<SemanticVersionDTO> asSemanticVersionList(String key, ImmutableList<UUID> componentUuids,
                                                                   ImmutableList<UUID> definitionForSemanticUuids,
                                                                   ImmutableList<UUID> referencedComponentUuids) {
        JSONArray jsonArray = (JSONArray) get(key);
        SemanticVersionDTO[] array = new SemanticVersionDTO[jsonArray.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = SemanticVersionDTO.make((JSONObject) jsonArray.get(i), componentUuids,
                    definitionForSemanticUuids,
                    referencedComponentUuids);
        }
        return Lists.immutable.of(array);
    }
}
