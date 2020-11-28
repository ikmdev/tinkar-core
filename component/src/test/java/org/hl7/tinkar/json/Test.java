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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.hl7.tinkar.json.parser.ContainerFactory;
import org.hl7.tinkar.json.parser.ContentHandler;
import org.hl7.tinkar.json.parser.JSONParser;
import org.hl7.tinkar.json.parser.ParseException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author FangYidong<fangyidong@yahoo.com.cn>
 */
public class Test {

    @org.junit.jupiter.api.Test
    public void testDecode() throws Exception {
        System.out.println("=======decode=======");

        String s = "[0,{\"1\":{\"2\":{\"3\":{\"4\":[5,{\"6\":7}]}}}}]";
        Object obj = JSONValue.parse(s);
        JSONArray array = (JSONArray) obj;
        System.out.println("======the 2nd element of array======");
        System.out.println(array.get(1));
        System.out.println();
        assertEquals("{\"1\":{\"2\":{\"3\":{\"4\":[5,{\"6\":7}]}}}}", array.get(1).toString());

        JSONObject obj2 = (JSONObject) array.get(1);
        System.out.println("======field \"1\"==========");
        System.out.println(obj2.get("1"));
        assertEquals("{\"2\":{\"3\":{\"4\":[5,{\"6\":7}]}}}", obj2.get("1").toString());

        s = "{}";
        obj = JSONValue.parse(s);
        assertEquals("{}", obj.toString());

        s = "[5,]";
        obj = JSONValue.parse(s);
        assertEquals("[5]", obj.toString());

        s = "[5,,2]";
        obj = JSONValue.parse(s);
        assertEquals("[5,2]", obj.toString());

        s = "[\"hello\\bworld\\\"abc\\tdef\\\\ghi\\rjkl\\n123\\u4e2d\"]";
        obj = JSONValue.parse(s);
        assertEquals("hello\bworld\"abc\tdef\\ghi\rjkl\n123中", ((List) obj).get(0).toString());

        JSONParser parser = new JSONParser();
        s = "{\"name\":";
        try {
            parser.parse(s);
        } catch (ParseException pe) {
            assertEquals(ParseException.ErrorType.UNEXPECTED_TOKEN, pe.getErrorType());
            assertEquals(8, pe.getPosition());
        }

        s = "{\"name\":}";
        try {
            parser.parse(s);
        } catch (ParseException pe) {
            assertEquals(ParseException.ErrorType.UNEXPECTED_TOKEN, pe.getErrorType());
            assertEquals(8, pe.getPosition());
        }

        s = "{\"name";
        try {
            parser.parse(s);
        } catch (ParseException pe) {
            assertEquals(ParseException.ErrorType.UNEXPECTED_TOKEN, pe.getErrorType());
            assertEquals(6, pe.getPosition());
        }

        s = "[[null, 123.45, \"a\\\tb c\"}, true]";
        try {
            parser.parse(s);
        } catch (ParseException pe) {
            assertEquals(24, pe.getPosition());
            System.out.println("Error at character position: " + pe.getPosition());
            switch (pe.getErrorType()) {
                case UNEXPECTED_TOKEN ->
                    System.out.println("Unexpected token: " + pe.getUnexpectedObject());
                case UNEXPECTED_CHAR ->
                    System.out.println("Unexpected character: " + pe.getUnexpectedObject());
                case UNEXPECTED_EXCEPTION ->
                    ((Exception) pe.getUnexpectedObject()).printStackTrace();
            }
        }

        s = "{\"first\": 123, \"second\": [4, 5, 6], \"third\": 789}";
        ContainerFactory containerFactory = new ContainerFactory() {
            @Override
            public List creatArrayContainer() {
                return new LinkedList();
            }

            @Override
            public Map createObjectContainer() {
                return new LinkedHashMap();
            }

        };

        try {
            Map json = (Map) parser.parse(s, containerFactory);
            Iterator iter = json.entrySet().iterator();
            System.out.println("==iterate result==");
            while (iter.hasNext()) {
                Map.Entry entry = (Map.Entry) iter.next();
                System.out.println(entry.getKey() + "=>" + entry.getValue());
            }

            System.out.println("==toJSONString()==");
            System.out.println(JSONValue.toJSONString(json));
            assertEquals("{\"first\":123,\"second\":[4,5,6],\"third\":789}", JSONValue.toJSONString(json));
        } catch (ParseException pe) {
            pe.printStackTrace();
        }

        s = "{\"first\": 123, \"second\": [{\"s1\":{\"s11\":\"v11\"}}, 4, 5, 6], \"third\": 789}";
        ContentHandler myHandler = new ContentHandler() {

            @Override
            public boolean endArray() throws ParseException {
                System.out.println("endArray()");
                return true;
            }

            @Override
            public void endJSON() throws ParseException {
                System.out.println("endJSON()");
            }

            @Override
            public boolean endObject() throws ParseException {
                System.out.println("endObject()");
                return true;
            }

            @Override
            public boolean endObjectEntry() throws ParseException {
                System.out.println("endObjectEntry()");
                return true;
            }

            @Override
            public boolean primitive(Object value) throws ParseException {
                System.out.println("primitive(): " + value);
                return true;
            }

            @Override
            public boolean startArray() throws ParseException {
                System.out.println("startArray()");
                return true;
            }

            @Override
            public void startJSON() throws ParseException {
                System.out.println("startJSON()");
            }

            @Override
            public boolean startObject() throws ParseException {
                System.out.println("startObject()");
                return true;
            }

            @Override
            public boolean startObjectEntry(String key) throws ParseException {
                System.out.println("startObjectEntry(), key:" + key);
                return true;
            }

        };
        try {
            parser.parse(s, myHandler);
        } catch (ParseException pe) {
            pe.printStackTrace();
        }

        class KeyFinder implements ContentHandler {

            private Object value;
            private boolean found = false;
            private boolean end = false;
            private String key;
            private String matchKey;

            public void setMatchKey(String matchKey) {
                this.matchKey = matchKey;
            }

            public Object getValue() {
                return value;
            }

            public boolean isEnd() {
                return end;
            }

            public void setFound(boolean found) {
                this.found = found;
            }

            public boolean isFound() {
                return found;
            }

            @Override
            public void startJSON() throws ParseException {
                found = false;
                end = false;
            }

            @Override
            public void endJSON() throws ParseException {
                end = true;
            }

            @Override
            public boolean primitive(Object value) throws ParseException {
                if (key != null) {
                    if (key.equals(matchKey)) {
                        found = true;
                        this.value = value;
                        key = null;
                        return false;
                    }
                }
                return true;
            }

            @Override
            public boolean startArray() throws ParseException {
                return true;
            }

            @Override
            public boolean startObject() throws ParseException {
                return true;
            }

            @Override
            public boolean startObjectEntry(String key) throws ParseException {
                this.key = key;
                return true;
            }

            @Override
            public boolean endArray() throws ParseException {
                return false;
            }

            @Override
            public boolean endObject() throws ParseException {
                return true;
            }

            @Override
            public boolean endObjectEntry() throws ParseException {
                return true;
            }
        }

        s = "{\"first\": 123, \"second\": [{\"k1\":{\"id\":\"id1\"}}, 4, 5, 6, {\"id\": 123}], \"third\": 789, \"id\": null}";
        parser.reset();
        KeyFinder keyFinder = new KeyFinder();
        keyFinder.setMatchKey("id");
        int i = 0;
        try {
            while (!keyFinder.isEnd()) {
                parser.parse(s, keyFinder, true);
                if (keyFinder.isFound()) {
                    i++;
                    keyFinder.setFound(false);
                    System.out.println("found id:");
                    System.out.println(keyFinder.getValue());
                    if (i == 1) {
                        assertEquals("id1", keyFinder.getValue());
                    }
                    if (i == 2) {
                        assertTrue(keyFinder.getValue() instanceof Number);
                        assertEquals("123", String.valueOf(keyFinder.getValue()));
                    }
                    if (i == 3) {
                        assertTrue(null == keyFinder.getValue());
                    }
                }
            }
        } catch (ParseException pe) {
            pe.printStackTrace();
        }
    }

    @org.junit.jupiter.api.Test
    public void testEncode() throws Exception {
        System.out.println("=======encode=======");

        JSONArray array1 = new JSONArray();
        array1.add("abc\u0010a/");
        array1.add(123);
        array1.add(222.123);
        array1.add(true);
        System.out.println("======array1==========");
        System.out.println(array1);
        System.out.println();
        assertEquals("[\"abc\\u0010a\\/\",123,222.123,true]", array1.toString());

        JSONObject obj1 = new JSONObject();
        obj1.put("array1", array1);
        System.out.println("======obj1 with array1===========");
        System.out.println(obj1);
        System.out.println();
        assertEquals("{\"array1\":[\"abc\\u0010a\\/\",123,222.123,true]}", obj1.toString());

        obj1.remove("array1");
        array1.add(obj1);
        System.out.println("======array1 with obj1========");
        System.out.println(array1);
        System.out.println();
        assertEquals("[\"abc\\u0010a\\/\",123,222.123,true,{}]", array1.toString());

        List list = new ArrayList();
        list.add("abc\u0010a/");
        list.add(123);
        list.add(222.123);
        list.add(true);
        list.add(null);
        System.out.println("======list==========");
        System.out.println(JSONArray.toJSONString(list));
        System.out.println();
        assertEquals("[\"abc\\u0010a\\/\",123,222.123,true,null]", JSONArray.toJSONString(list));

        Map map = new HashMap();
        map.put("array1", list);
        System.out.println("======map with list===========");
        System.out.println(map);
        System.out.println();
        assertEquals("{\"array1\":[\"abc\\u0010a\\/\",123,222.123,true,null]}", JSONObject.toJSONString(map));

        Map m1 = new LinkedHashMap();
        Map m2 = new LinkedHashMap();
        List l1 = new LinkedList();

        m1.put("k11", "v11");
        m1.put("k12", "v12");
        m1.put("k13", "v13");
        m2.put("k21", "v21");
        m2.put("k22", "v22");
        m2.put("k23", "v23");
        l1.add(m1);
        l1.add(m2);
        String jsonString = JSONValue.toJSONString(l1);
        System.out.println(jsonString);
        assertEquals("[{\"k11\":\"v11\",\"k12\":\"v12\",\"k13\":\"v13\"},{\"k21\":\"v21\",\"k22\":\"v22\",\"k23\":\"v23\"}]", jsonString);

        StringWriter out = new StringWriter();
        JSONValue.writeJSONString(l1, out);
        jsonString = out.toString();
        System.out.println(jsonString);
        assertEquals("[{\"k11\":\"v11\",\"k12\":\"v12\",\"k13\":\"v13\"},{\"k21\":\"v21\",\"k22\":\"v22\",\"k23\":\"v23\"}]", jsonString);

        List l2 = new LinkedList();
        Map m3 = new LinkedHashMap();
        m3.put("k31", "v3");
        m3.put("k32", 123.45);
        m3.put("k33", false);
        m3.put("k34", null);
        l2.add("vvv");
        l2.add("1.23456789123456789");
        l2.add(true);
        l2.add(null);
        m3.put("k35", l2);
        m1.put("k14", m3);
        out = new StringWriter();
        JSONValue.writeJSONString(l1, out);
        jsonString = out.toString();
        System.out.println(jsonString);
        assertEquals("[{\"k11\":\"v11\",\"k12\":\"v12\",\"k13\":\"v13\",\"k14\":{\"k31\":\"v3\",\"k32\":123.45,\"k33\":false,\"k34\":null,\"k35\":[\"vvv\",\"1.23456789123456789\",true,null]}},{\"k21\":\"v21\",\"k22\":\"v22\",\"k23\":\"v23\"}]", jsonString);
    }
}
