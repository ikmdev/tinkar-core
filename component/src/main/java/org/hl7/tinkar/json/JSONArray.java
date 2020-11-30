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
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * Original obtained from: https://github.com/fangyidong/json-simple under
 * Apache 2 license Original project had no support for Java Platform Module
 * System, and not updated for 8 years. Integrated here to integrate with Java
 * Platform Module System.
 * <p>
 * A JSON array. JSONObject supports java.util.List interface.
 *
 * @author FangYidong<fangyidong @ yahoo.com.cn>
 */
public class JSONArray extends ArrayList<Object> implements JSONAware, JSONStreamAware {

    private static final long serialVersionUID = 3957988303675231981L;

    /**
     * Constructs an empty JSONArray.
     */
    public JSONArray() {
        super();
    }

    /**
     * Constructs a JSONArray containing the elements of the specified
     * collection, in the order they are returned by the collection's iterator.
     *
     * @param c the collection whose elements are to be placed into this
     *          JSONArray
     */
    public JSONArray(Collection<Object> c) {
        super(c);
    }

    /**
     * Encode a list into JSON text and write it to out.If this list is also a
     * JSONStreamAware or a JSONAware, JSONStreamAware and JSONAware specific
     * behaviors will be ignored at this top level.
     *
     * @param collection
     * @param out
     * @throws java.io.IOException
     * @see org.hl7.tinkar.JSONValue#writeJSONString(Object, Writer)
     */
    public static void writeJSONString(Collection<Object> collection, Writer out) throws IOException {
        if (collection == null) {
            out.write("null");
            return;
        }

        boolean first = true;
        Iterator<Object> iter = collection.iterator();

        out.write('[');
        while (iter.hasNext()) {
            if (first) {
                first = false;
            } else {
                out.write(',');
            }

            Object value = iter.next();
            if (value == null) {
                out.write("null");
                continue;
            }

            JSONValue.writeJSONString(value, out);
        }
        out.write(']');
    }

    @Override
    public void writeJSONString(Writer out) throws IOException {
        writeJSONString(this, out);
    }

    /**
     * Convert a list to JSON text. The result is a JSON array. If this list is
     * also a JSONAware, JSONAware specific behaviors will be omitted at this
     * top level.
     *
     * @param collection
     * @return JSON text, or "null" if list is null.
     * @see org.hl7.tinkar.JSONValue#toJSONString(Object)
     */
    public static String toJSONString(Collection<Object> collection) {
        final StringWriter writer = new StringWriter();

        try {
            writeJSONString(collection, writer);
            return writer.toString();
        } catch (IOException e) {
            // This should never happen for a StringWriter
            throw new UncheckedIOException(e);
        }
    }

    public static void writeJSONString(byte[] array, Writer out) throws IOException {
        writeArray(array, out);
    }

    public static void writeArray(Object arrayObject, Writer out) throws IOException {
        if (arrayObject == null) {
            out.write("null");
        } else if (arrayObject.getClass().isArray()) {
            int length = Array.getLength(arrayObject);

            if (length == 0) {
                out.write("[]");
            } else {
                out.write("[");
                Class<?> componentType = arrayObject.getClass().getComponentType();
                if (char.class.equals(componentType)) {
                    writeWithQuotes(arrayObject, out, length);
                } else {
                    write(arrayObject, out, length);
                }
                out.write("]");
            }
        } else {
            throw new IllegalStateException("Expecting an array. Found: " + arrayObject);
        }
    }

    private static void write(Object arrayObject, Writer out, int length) throws IOException {
        out.write(String.valueOf(Array.get(arrayObject, 0)));
        for (int i = 1; i < length; i++) {
            out.write(",");
            out.write(String.valueOf(Array.get(arrayObject, i)));
        }
    }

    private static void writeWithQuotes(Object arrayObject, Writer out, int length) throws IOException {
        out.write('"');
        JSONValue.writeJSONString(Array.get(arrayObject, 0), out);
        out.write('"');

        for (int i = 1; i < length; i++) {
            out.write(",");
            out.write('"');
            JSONValue.writeJSONString(Array.get(arrayObject, i), out);
            out.write('"');
        }
    }

    public static String toJSONString(byte[] array) {
        final StringWriter writer = new StringWriter();

        try {
            writeJSONString(array, writer);
            return writer.toString();
        } catch (IOException e) {
            // This should never happen for a StringWriter
            throw new UncheckedIOException(e);
        }
    }

    public static void writeJSONString(short[] array, Writer out) throws IOException {
        writeArray(array, out);
    }

    public static String toJSONString(short[] array) {
        final StringWriter writer = new StringWriter();

        try {
            writeJSONString(array, writer);
            return writer.toString();
        } catch (IOException e) {
            // This should never happen for a StringWriter
            throw new UncheckedIOException(e);
        }
    }

    public static void writeJSONString(int[] array, Writer out) throws IOException {
        writeArray(array, out);
    }

    public static String toJSONString(int[] array) {
        final StringWriter writer = new StringWriter();

        try {
            writeJSONString(array, writer);
            return writer.toString();
        } catch (IOException e) {
            // This should never happen for a StringWriter
            throw new UncheckedIOException(e);
        }
    }

    public static void writeJSONString(long[] array, Writer out) throws IOException {
        writeArray(array, out);
    }

    public static String toJSONString(long[] array) {
        final StringWriter writer = new StringWriter();

        try {
            writeJSONString(array, writer);
            return writer.toString();
        } catch (IOException e) {
            // This should never happen for a StringWriter
            throw new UncheckedIOException(e);
        }
    }

    public static void writeJSONString(float[] array, Writer out) throws IOException {
        writeArray(array, out);
    }

    public static String toJSONString(float[] array) {
        final StringWriter writer = new StringWriter();

        try {
            writeJSONString(array, writer);
            return writer.toString();
        } catch (IOException e) {
            // This should never happen for a StringWriter
            throw new UncheckedIOException(e);
        }
    }

    public static void writeJSONString(double[] array, Writer out) throws IOException {
        writeArray(array, out);
    }

    public static String toJSONString(double[] array) {
        final StringWriter writer = new StringWriter();

        try {
            writeJSONString(array, writer);
            return writer.toString();
        } catch (IOException e) {
            // This should never happen for a StringWriter
            throw new UncheckedIOException(e);
        }
    }

    public static void writeJSONString(boolean[] array, Writer out) throws IOException {
        writeArray(array, out);
    }

    public static String toJSONString(boolean[] array) {
        final StringWriter writer = new StringWriter();

        try {
            writeJSONString(array, writer);
            return writer.toString();
        } catch (IOException e) {
            // This should never happen for a StringWriter
            throw new UncheckedIOException(e);
        }
    }

    public static void writeJSONString(char[] array, Writer out) throws IOException {
        writeArray(array, out);
    }

    public static String toJSONString(char[] array) {
        final StringWriter writer = new StringWriter();

        try {
            writeJSONString(array, writer);
            return writer.toString();
        } catch (IOException e) {
            // This should never happen for a StringWriter
            throw new UncheckedIOException(e);
        }
    }

    public static void writeJSONString(Object[] array, Writer out) throws IOException {
        if (array == null) {
            out.write("null");
        } else if (array.length == 0) {
            out.write("[]");
        } else {
            out.write("[");
            JSONValue.writeJSONString(array[0], out);

            for (int i = 1; i < array.length; i++) {
                out.write(",");
                JSONValue.writeJSONString(array[i], out);
            }

            out.write("]");
        }
    }

    public static String toJSONString(Object[] array) {
        final StringWriter writer = new StringWriter();

        try {
            writeJSONString(array, writer);
            return writer.toString();
        } catch (IOException e) {
            // This should never happen for a StringWriter
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String toJSONString() {
        return toJSONString(this);
    }

    /**
     * Returns a string representation of this array. This is equivalent to
     * calling {@link JSONArray#toJSONString()}.
     */
    @Override
    public String toString() {
        return toJSONString();
    }
}
