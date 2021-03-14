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

import org.hl7.tinkar.lombok.dto.json.JSONObject;
import org.hl7.tinkar.lombok.dto.json.JSONValue;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

public class JSONValueTest {

    @org.junit.jupiter.api.Test
    public void testByteArrayToString() throws IOException {
        assertEquals("null", JSONValue.toJSONString((byte[]) null));
        assertEquals("\"" + JSONObject.DATA_APPLICATION_OCTET_STREAM_BASE_64 + "\"", JSONValue.toJSONString(new byte[0]));
        assertEquals("\"" + JSONObject.DATA_APPLICATION_OCTET_STREAM_BASE_64 + "DA==\"", JSONValue.toJSONString(new byte[]{12}));
        assertEquals("\"" + JSONObject.DATA_APPLICATION_OCTET_STREAM_BASE_64 + "+RZWnQ==\"", JSONValue.toJSONString(new byte[]{-7, 22, 86, -99}));

        StringWriter writer;

        writer = new StringWriter();
        JSONValue.writeJSONString((byte[]) null, writer);
        assertEquals("null", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new byte[0], writer);
        Assertions.assertEquals("\"" + JSONObject.DATA_APPLICATION_OCTET_STREAM_BASE_64 + "\"", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new byte[]{12}, writer);
        Assertions.assertEquals("\"" + JSONObject.DATA_APPLICATION_OCTET_STREAM_BASE_64 + "DA==\"", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new byte[]{-7, 22, 86, -99}, writer);
        Assertions.assertEquals("\"" + JSONObject.DATA_APPLICATION_OCTET_STREAM_BASE_64 + "+RZWnQ==\"", writer.toString());
    }

    @org.junit.jupiter.api.Test
    public void testShortArrayToString() throws IOException {
        assertEquals("null", JSONValue.toJSONString((short[]) null));
        assertEquals("[]", JSONValue.toJSONString(new short[0]));
        assertEquals("[12]", JSONValue.toJSONString(new short[]{12}));
        assertEquals("[-7,22,86,-99]", JSONValue.toJSONString(new short[]{-7, 22, 86, -99}));

        StringWriter writer;

        writer = new StringWriter();
        JSONValue.writeJSONString((short[]) null, writer);
        assertEquals("null", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new short[0], writer);
        assertEquals("[]", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new short[]{12}, writer);
        assertEquals("[12]", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new short[]{-7, 22, 86, -99}, writer);
        assertEquals("[-7,22,86,-99]", writer.toString());
    }

    @org.junit.jupiter.api.Test
    public void testIntArrayToString() throws IOException {
        assertEquals("null", JSONValue.toJSONString((int[]) null));
        assertEquals("[]", JSONValue.toJSONString(new int[0]));
        assertEquals("[12]", JSONValue.toJSONString(new int[]{12}));
        assertEquals("[-7,22,86,-99]", JSONValue.toJSONString(new int[]{-7, 22, 86, -99}));

        StringWriter writer;

        writer = new StringWriter();
        JSONValue.writeJSONString((int[]) null, writer);
        assertEquals("null", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new int[0], writer);
        assertEquals("[]", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new int[]{12}, writer);
        assertEquals("[12]", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new int[]{-7, 22, 86, -99}, writer);
        assertEquals("[-7,22,86,-99]", writer.toString());
    }

    @org.junit.jupiter.api.Test
    public void testLongArrayToString() throws IOException {
        assertEquals("null", JSONValue.toJSONString((long[]) null));
        assertEquals("[]", JSONValue.toJSONString(new long[0]));
        assertEquals("[12]", JSONValue.toJSONString(new long[]{12}));
        assertEquals("[-7,22,9223372036854775807,-99]", JSONValue.toJSONString(new long[]{-7, 22, 9223372036854775807L, -99}));

        StringWriter writer;

        writer = new StringWriter();
        JSONValue.writeJSONString((long[]) null, writer);
        assertEquals("null", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new long[0], writer);
        assertEquals("[]", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new long[]{12}, writer);
        assertEquals("[12]", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new long[]{-7, 22, 86, -99}, writer);
        assertEquals("[-7,22,86,-99]", writer.toString());
    }

    @org.junit.jupiter.api.Test
    public void testFloatArrayToString() throws IOException {
        assertEquals("null", JSONValue.toJSONString((float[]) null));
        assertEquals("[]", JSONValue.toJSONString(new float[0]));
        assertEquals("[12.8]", JSONValue.toJSONString(new float[]{12.8f}));
        assertEquals("[-7.1,22.234,86.7,-99.02]", JSONValue.toJSONString(new float[]{-7.1f, 22.234f, 86.7f, -99.02f}));

        StringWriter writer;

        writer = new StringWriter();
        JSONValue.writeJSONString((float[]) null, writer);
        assertEquals("null", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new float[0], writer);
        assertEquals("[]", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new float[]{12.8f}, writer);
        assertEquals("[12.8]", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new float[]{-7.1f, 22.234f, 86.7f, -99.02f}, writer);
        assertEquals("[-7.1,22.234,86.7,-99.02]", writer.toString());
    }

    @org.junit.jupiter.api.Test
    public void testDoubleArrayToString() throws IOException {
        assertEquals("null", JSONValue.toJSONString((double[]) null));
        assertEquals("[]", JSONValue.toJSONString(new double[0]));
        assertEquals("[12.8]", JSONValue.toJSONString(new double[]{12.8}));
        assertEquals("[-7.1,22.234,86.7,-99.02]", JSONValue.toJSONString(new double[]{-7.1, 22.234, 86.7, -99.02}));

        StringWriter writer;

        writer = new StringWriter();
        JSONValue.writeJSONString((double[]) null, writer);
        assertEquals("null", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new double[0], writer);
        assertEquals("[]", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new double[]{12.8}, writer);
        assertEquals("[12.8]", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new double[]{-7.1, 22.234, 86.7, -99.02}, writer);
        assertEquals("[-7.1,22.234,86.7,-99.02]", writer.toString());
    }

    @org.junit.jupiter.api.Test
    public void testBooleanArrayToString() throws IOException {
        assertEquals("null", JSONValue.toJSONString((boolean[]) null));
        assertEquals("[]", JSONValue.toJSONString(new boolean[0]));
        assertEquals("[true]", JSONValue.toJSONString(new boolean[]{true}));
        assertEquals("[true,false,true]", JSONValue.toJSONString(new boolean[]{true, false, true}));

        StringWriter writer;

        writer = new StringWriter();
        JSONValue.writeJSONString((boolean[]) null, writer);
        assertEquals("null", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new boolean[0], writer);
        assertEquals("[]", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new boolean[]{true}, writer);
        assertEquals("[true]", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new boolean[]{true, false, true}, writer);
        assertEquals("[true,false,true]", writer.toString());
    }

    @org.junit.jupiter.api.Test
    public void testCharArrayToString() throws IOException {
        assertEquals("null", JSONValue.toJSONString((char[]) null));
        assertEquals("[]", JSONValue.toJSONString(new char[0]));
        assertEquals("[\"a\"]", JSONValue.toJSONString(new char[]{'a'}));
        assertEquals("[\"a\",\"b\",\"c\"]", JSONValue.toJSONString(new char[]{'a', 'b', 'c'}));

        StringWriter writer;

        writer = new StringWriter();
        JSONValue.writeJSONString((char[]) null, writer);
        assertEquals("null", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new char[0], writer);
        assertEquals("[]", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new char[]{'a'}, writer);
        assertEquals("[\"a\"]", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new char[]{'a', 'b', 'c'}, writer);
        assertEquals("[\"a\",\"b\",\"c\"]", writer.toString());
    }

    @org.junit.jupiter.api.Test
    public void testObjectArrayToString() throws IOException {
        assertEquals("null", JSONValue.toJSONString((Object[]) null));
        assertEquals("[]", JSONValue.toJSONString(new Object[0]));
        assertEquals("[\"Hello\"]", JSONValue.toJSONString(new Object[]{"Hello"}));
        assertEquals("[\"Hello\",12,[1,2,3]]", JSONValue.toJSONString(new Object[]{"Hello", 12, new int[]{1, 2, 3}}));

        StringWriter writer;

        writer = new StringWriter();
        JSONValue.writeJSONString((Object[]) null, writer);
        assertEquals("null", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new Object[0], writer);
        assertEquals("[]", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new Object[]{"Hello"}, writer);
        assertEquals("[\"Hello\"]", writer.toString());

        writer = new StringWriter();
        JSONValue.writeJSONString(new Object[]{"Hello", 12, new int[]{1, 2, 3}}, writer);
        assertEquals("[\"Hello\",12,[1,2,3]]", writer.toString());
    }

    @org.junit.jupiter.api.Test
    public void testArraysOfArrays() throws IOException {

        StringWriter writer;

        final int[][][] nestedIntArray = new int[][][]{{{1}, {5}}, {{2}, {6}}};
        final String expectedNestedIntString = "[[[1],[5]],[[2],[6]]]";

        assertEquals(expectedNestedIntString, JSONValue.toJSONString(nestedIntArray));

        writer = new StringWriter();
        JSONValue.writeJSONString(nestedIntArray, writer);
        assertEquals(expectedNestedIntString, writer.toString());

        final String[][] nestedStringArray = new String[][]{{"a", "b"}, {"c", "d"}};
        final String expectedNestedStringString = "[[\"a\",\"b\"],[\"c\",\"d\"]]";

        assertEquals(expectedNestedStringString, JSONValue.toJSONString(nestedStringArray));

        writer = new StringWriter();
        JSONValue.writeJSONString(nestedStringArray, writer);
        assertEquals(expectedNestedStringString, writer.toString());
    }
}
