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
package org.hl7.tinkar.lombok.dto.json;

import java.io.*;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.eclipse.collections.impl.list.mutable.primitive.ByteArrayList;
import org.hl7.tinkar.lombok.dto.json.parser.JSONParser;
import org.hl7.tinkar.lombok.dto.json.parser.ParseException;

/**
 * Original obtained from: https://github.com/fangyidong/json-simple under
 * Apache 2 license Original project had no support for Java Platform Module
 * System, and not updated for 8 years. Integrated here to integrate with Java
 * Platform Module System.
 *
 * @author FangYidong<fangyidong @ yahoo.com.cn>
 */
public class JSONValue {

    private JSONValue() {
        // private constructor to prevent instantiation. 
    }

    /**
     * Parse JSON text into java object from the input source.
     *
     * @param in
     * @return Instance of the following: org.hl7.tinkar.JSONObject,
     * org.hl7.tinkar.JSONArray, java.lang.String, java.lang.Number,
     * java.lang.Boolean, null
     * @throws IOException
     * @throws ParseException
     * @see org.hl7.tinkar.parser.JSONParser
     */
    public static Object parse(Reader in) throws ParseException {
        JSONParser parser = new JSONParser();
        return parser.parse(in);
    }

    public static Object parse(String s) throws ParseException {
        JSONParser parser = new JSONParser();
        return parser.parse(s);
    }

    /**
     * Encode an object into JSON text and write it to out.
     * <p>
     * If this object is a Map or a List, and it's also a JSONStreamAware or a
     * JSONAware, JSONStreamAware or JSONAware will be considered firstly.
     * <p>
     * DO NOT call this method from writeJSONString(Writer) of a class that
     * implements both JSONStreamAware and (Map or List) with "this" as the
     * first parameter, use JSONObject.writeJSONString(Map, Writer) or
     * JSONArray.writeJSONString(List, Writer) instead.
     *
     * @param value
     * @param out
     * @throws java.io.IOException
     * @see org.hl7.tinkar.JSONObject#writeJSONString(Map, Writer)
     * @see org.hl7.tinkar.JSONArray#writeJSONString(List, Writer)
     */
    public static void writeJSONString(Object value, Writer out) throws IOException {
        if (value == null) {
            out.write("null");
        } else if (value instanceof String) {
            writeQuotedString(out, escape((String) value));
        } else if (value instanceof Number) {
            handleNumber((Number) value, out);
        } else if (value instanceof Boolean) {
            out.write(value.toString());
        } else if (value instanceof UUID) {
            writeQuotedString(out, value.toString());
        } else if (value instanceof Instant) {
            writeQuotedString(out, value.toString());
        } else if (value instanceof ByteArrayList) {
            ByteArrayList bal = (ByteArrayList) value;
            writeQuotedString(out, JSONObject.DATA_APPLICATION_OCTET_STREAM_BASE_64 + Base64.getEncoder().encodeToString(bal.toArray()));
        } else if (value instanceof byte[]) {
            byte[] byteArray = (byte[]) value;
            writeQuotedString(out, JSONObject.DATA_APPLICATION_OCTET_STREAM_BASE_64 + Base64.getEncoder().encodeToString(byteArray));
        } else if ((value instanceof JSONStreamAware)) {
            ((JSONStreamAware) value).writeJSONString(out);
        } else if ((value instanceof JSONAware)) {
            out.write(((JSONAware) value).toJSONString());
        } else if (value instanceof Map) {
            JSONObject.writeJSONString(((Map) value), out);
        } else if (value.getClass().isArray()) {
            handleArray(value, out);
        } else if (value instanceof Collection) {
            JSONArray.writeJSONString((Collection<Object>) value, out);
        } else if (value instanceof JsonMarshalable) {
            out.write(((JsonMarshalable) value).toJsonString());
        } else {
            out.write(value.toString());
        }
    }


    private static void handleArray(Object array, Writer out) throws IOException {
        if (array instanceof byte[]) {
            JSONArray.writeJSONString((byte[]) array, out);
        } else if (array instanceof short[]) {
            JSONArray.writeJSONString((short[]) array, out);
        } else if (array instanceof int[]) {
            JSONArray.writeJSONString((int[]) array, out);
        } else if (array instanceof long[]) {
            JSONArray.writeJSONString((long[]) array, out);
        } else if (array instanceof float[]) {
            JSONArray.writeJSONString((float[]) array, out);
        } else if (array instanceof double[]) {
            JSONArray.writeJSONString((double[]) array, out);
        } else if (array instanceof boolean[]) {
            JSONArray.writeJSONString((boolean[]) array, out);
        } else if (array instanceof char[]) {
            JSONArray.writeJSONString((char[]) array, out);
        } else if (array instanceof Object[]) {
            JSONArray.writeJSONString((Object[]) array, out);
        }
    }

    private static void handleNumber(Number num, Writer out) throws IOException {
        if (num instanceof Double) {
            Double double1 =  num.doubleValue();
            if (double1.isInfinite() || double1.isNaN()) {
                out.write("null");
            } else {
                out.write(num.toString());
            }
        } else if (num instanceof Float) {
            Float float1 = num.floatValue();
            if (float1.isInfinite() || float1.isNaN()) {
                out.write("null");
            } else {
                out.write(num.toString());
            }
        } else {
            out.write(num.toString());
        }
    }

    public static void writeQuotedString(Writer out, String s) throws IOException {
        out.write('\"');
        out.write(s);
        out.write('\"');
    }

    /**
     * Convert an object to JSON text.
     * <p>
     * If this object is a Map or a List, and it's also a JSONAware, JSONAware
     * will be considered firstly.
     * <p>
     * DO NOT call this method from toJSONString() of a class that implements
     * both JSONAware and Map or List with "this" as the parameter, use
     * JSONObject.toJSONString(Map) or JSONArray.toJSONString(List) instead.
     *
     * @param value
     * @return JSON text, or "null" if value is null or it's an NaN or an INF
     * number.
     * @see org.hl7.tinkar.JSONObject#toJSONString(Map)
     * @see org.hl7.tinkar.JSONArray#toJSONString(List)
     */
    public static String toJSONString(Object value) {
        final StringWriter writer = new StringWriter();

        try {
            writeJSONString(value, writer);
            return writer.toString();
        } catch (IOException e) {
            // This should never happen for a StringWriter
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Escape quotes, \, /, \r, \n, \b, \f, \t and other control characters
     * (U+0000 through U+001F).
     *
     * @param s
     * @return
     */
    public static String escape(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        escape(s, sb);
        return sb.toString();
    }

    /**
     * @param s  - Must not be null.
     * @param sb
     */
    static void escape(String s, StringBuilder sb) {
        final int len = s.length();
        for (int i = 0; i < len; i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '/':
                    sb.append("\\/");
                    break;
                default:
                    {
                    if (isSpecial(ch)) {
                        String ss = Integer.toHexString(ch);
                        sb.append("\\u");
                        for (int k = 0; k < 4 - ss.length(); k++) {
                            sb.append('0');
                        }
                        sb.append(ss.toUpperCase());
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
    }

    private static boolean isSpecial(Character ch) {
        if (Character.isISOControl(ch)) {
            return true;
        }
        // UnicodeBlock GENERAL_PUNCTUATION
        // UnicodeBlock SUPERSCRIPTS_AND_SUBSCRIPTS
        // UnicodeBlock CURRENCY_SYMBOLS
        // UnicodeBlock COMBINING_MARKS_FOR_SYMBOLS
        return (ch >= '\u2000' && ch <= '\u20FF');
    }
}
