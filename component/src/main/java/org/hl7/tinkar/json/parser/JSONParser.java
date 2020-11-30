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
package org.hl7.tinkar.json.parser;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hl7.tinkar.json.InstantUtil;
import org.hl7.tinkar.json.JSONArray;
import org.hl7.tinkar.json.JSONObject;
import org.hl7.tinkar.uuid.UuidUtil;


/**
 * Original obtained from: https://github.com/fangyidong/json-simple under Apache 2 license
 * Original project had no support for Java Platform Module System, and not updated for 8 years.
 * Integrated here to integrate with Java Platform Module System.
 * <p>
 * Parser for JSON text. Please note that JSONParser is NOT thread-safe.
 *
 * @author FangYidong<fangyidong @ yahoo.com.cn>
 */
public class JSONParser {
    public static final int S_INIT = 0;
    public static final int S_IN_FINISHED_VALUE = 1;//string,number,boolean,null,object,array
    public static final int S_IN_OBJECT = 2;
    public static final int S_IN_ARRAY = 3;
    public static final int S_PASSED_PAIR_KEY = 4;
    public static final int S_IN_PAIR_VALUE = 5;
    public static final int S_END = 6;
    public static final int S_IN_ERROR = -1;

    private LinkedList<Integer> handlerStatusStack;
    private final Yylex lexer = new Yylex((Reader) null);
    private Yytoken token = null;
    private int status = S_INIT;

    protected int peekStatus(LinkedList<Integer> statusStack) {
        if (statusStack.isEmpty()) {
            return -1;
        }
        return statusStack.getFirst();
    }

    /**
     * Reset the parser to the initial state without resetting the underlying reader.
     */
    public void reset() {
        token = null;
        status = S_INIT;
        handlerStatusStack = null;
    }

    /**
     * Reset the parser to the initial state with a new character reader.
     *
     * @param in - The new character reader.
     */
    public void reset(Reader in) {
        lexer.yyreset(in);
        reset();
    }

    /**
     * @return The position of the beginning of the current token.
     */
    public int getPosition() {
        return lexer.getPosition();
    }

    public Object parse(String s) throws ParseException {
        return parse(s, (ContainerFactory) null);
    }

    public Object parse(String s, ContainerFactory containerFactory) throws ParseException {
        StringReader in = new StringReader(s);
        return parse(in, containerFactory);
    }

    public Object parse(Reader in) throws ParseException {
        return parse(in, (ContainerFactory) null);
    }

    /**
     * Parse JSON text into java object from the input source.
     *
     * @param in
     * @param containerFactory - Use this factory to createyour own JSON object and JSON array containers.
     * @return Instance of the following:
     * org.hl7.tinkar.JSONObject,
     * org.hl7.tinkar.JSONArray,
     * java.lang.String,
     * java.lang.Number,
     * java.lang.Boolean,
     * null
     * @throws ParseException
     */
    public Object parse(Reader in, ContainerFactory containerFactory) throws ParseException {
        reset(in);
        LinkedList<Integer> statusStack = new LinkedList<>();
        LinkedList<Object> valueStack = new LinkedList<>();
            do {
                nextToken();
                switch (status) {
                    case S_INIT -> handleS_INIT(containerFactory, statusStack, valueStack);

                    case S_IN_FINISHED_VALUE -> {
                        return handleS_IN_FINISHED_VALUE(valueStack);
                    }

                    case S_IN_OBJECT -> handleS_IN_OBJECT(statusStack, valueStack);


                    case S_PASSED_PAIR_KEY -> handleS_PASSED_PAIR_KEY(containerFactory, statusStack, valueStack);

                    case S_IN_ARRAY -> handleS_IN_ARRAY(containerFactory, statusStack, valueStack);

                    case S_IN_ERROR -> throw new ParseException(getPosition(), ParseException.ErrorType.UNEXPECTED_TOKEN, token);

                    default -> throw new ParseException(getPosition(), ParseException.ErrorType.UNEXPECTED_EXCEPTION, token);
                }

                if (status == S_IN_ERROR) {
                    throw new ParseException(getPosition(), ParseException.ErrorType.UNEXPECTED_TOKEN, token);
                }
            } while (token.type != Yytoken.Type.TYPE_EOF);

        throw new ParseException(getPosition(), ParseException.ErrorType.UNEXPECTED_TOKEN, token);
    }

    public void handleS_INIT(ContainerFactory containerFactory, LinkedList<Integer> statusStack, LinkedList<Object> valueStack) {
        switch (token.type) {
            case TYPE_VALUE -> {
                status = S_IN_FINISHED_VALUE;
                statusStack.addFirst(status);
                valueStack.addFirst(token.value);
            }
            case TYPE_LEFT_BRACE -> {
                status = S_IN_OBJECT;
                statusStack.addFirst(status);
                valueStack.addFirst(createObjectContainer(containerFactory));
            }
            case TYPE_LEFT_SQUARE -> {
                status = S_IN_ARRAY;
                statusStack.addFirst(status);
                valueStack.addFirst(createArrayContainer(containerFactory));
            }
            default -> status = S_IN_ERROR;
        }
    }

    public Object handleS_IN_FINISHED_VALUE(LinkedList<Object> valueStack) throws ParseException {
        if (token.type == Yytoken.Type.TYPE_EOF)
            return valueStack.removeFirst();
        else
            throw new ParseException(getPosition(), ParseException.ErrorType.UNEXPECTED_TOKEN, token);
    }

    public void handleS_IN_OBJECT(LinkedList<Integer> statusStack, LinkedList<Object> valueStack) {
        switch (token.type) {
            case TYPE_COMMA -> {
            }
            case TYPE_VALUE -> {
                if (token.value instanceof String key) {
                    valueStack.addFirst(key);
                    status = S_PASSED_PAIR_KEY;
                    statusStack.addFirst(status);
                } else {
                    status = S_IN_ERROR;
                }
            }
            case TYPE_RIGHT_BRACE -> {
                if (valueStack.size() > 1) {
                    statusStack.removeFirst();
                    valueStack.removeFirst();
                    status = peekStatus(statusStack);
                } else {
                    status = S_IN_FINISHED_VALUE;
                }
            }
            default -> status = S_IN_ERROR;
        }
    }

    public void handleS_PASSED_PAIR_KEY(ContainerFactory containerFactory, LinkedList<Integer> statusStack, LinkedList<Object> valueStack) {
        switch (token.type) {
            case TYPE_COLON -> {
            }
            case TYPE_VALUE -> {
                statusStack.removeFirst();
                String key = (String) valueStack.removeFirst();
                Map parent = (Map) valueStack.getFirst();
                parent.put(key, token.value);
                status = peekStatus(statusStack);
            }
            case TYPE_LEFT_SQUARE -> {
                statusStack.removeFirst();
                String key = (String) valueStack.removeFirst();
                Map parent = (Map) valueStack.getFirst();
                List newArray = createArrayContainer(containerFactory);
                parent.put(key, newArray);
                status = S_IN_ARRAY;
                statusStack.addFirst(status);
                valueStack.addFirst(newArray);
            }
            case TYPE_LEFT_BRACE -> {
                statusStack.removeFirst();
                String key = (String) valueStack.removeFirst();
                Map parent = (Map) valueStack.getFirst();
                Map newObject = createObjectContainer(containerFactory);
                parent.put(key, newObject);
                status = S_IN_OBJECT;
                statusStack.addFirst(status);
                valueStack.addFirst(newObject);
            }
            default -> status = S_IN_ERROR;
        }
    }

    public void handleS_IN_ARRAY(ContainerFactory containerFactory, LinkedList<Integer> statusStack, LinkedList<Object> valueStack) {
        switch (token.type) {
            case TYPE_COMMA -> {
            }
            case TYPE_VALUE -> {
                List val = (List) valueStack.getFirst();
                if (token.value instanceof String string) {
                    if (UuidUtil.isUUID(string)) {
                        val.add(UUID.fromString(string));
                    } else if (InstantUtil.parse(string).isPresent()) {
                        val.add(Instant.parse(string));
                    } else {
                        val.add(token.value);
                    }
                } else if (token.value instanceof Double doubleVal) {
                    val.add(doubleVal.floatValue());
                } else if (token.value instanceof Long longVal) {
                    val.add(longVal.intValue());
                } else {
                    val.add(token.value);
                }
            }
            case TYPE_RIGHT_SQUARE -> {
                if (valueStack.size() > 1) {
                    statusStack.removeFirst();
                    valueStack.removeFirst();
                    status = peekStatus(statusStack);
                } else {
                    status = S_IN_FINISHED_VALUE;
                }
            }
            case TYPE_LEFT_BRACE -> {
                List<Object> val = (List<Object>) valueStack.getFirst();
                Map<String, Object> newObject = createObjectContainer(containerFactory);
                val.add(newObject);
                status = S_IN_OBJECT;
                statusStack.addFirst(status);
                valueStack.addFirst(newObject);
            }
            case TYPE_LEFT_SQUARE -> {
                List<Object> val = (List<Object>) valueStack.getFirst();
                List<Object> newArray = createArrayContainer(containerFactory);
                val.add(newArray);
                status = S_IN_ARRAY;
                statusStack.addFirst(status);
                valueStack.addFirst(newArray);
            }
            default -> status = S_IN_ERROR;
        }//inner switch
    }

    private void nextToken() throws ParseException {
        token = lexer.yylex();
        if (token == null)
            token = new Yytoken(Yytoken.Type.TYPE_EOF, null);
    }

    private Map<String, Object> createObjectContainer(ContainerFactory containerFactory) {
        if (containerFactory == null)
            return new JSONObject();
        Map<String, Object> m = containerFactory.createObjectContainer();

        if (m == null)
            return new JSONObject();
        return m;
    }

    private List<Object> createArrayContainer(ContainerFactory containerFactory) {
        if (containerFactory == null)
            return new JSONArray();
        List<Object> l = containerFactory.creatArrayContainer();

        if (l == null)
            return new JSONArray();
        return l;
    }

    public void parse(String s, ContentHandler contentHandler) throws ParseException {
        parse(s, contentHandler, false);
    }

    public void parse(String s, ContentHandler contentHandler, boolean isResume) throws ParseException {
        StringReader in = new StringReader(s);
        parse(in, contentHandler, isResume);
    }

    public void parse(Reader in, ContentHandler contentHandler) throws ParseException {
        parse(in, contentHandler, false);
    }

    /**
     * Stream processing of JSON text.
     *
     * @param in
     * @param contentHandler
     * @param isResume       - Indicates if it continues previous parsing operation.
     *                       If set to true, resume parsing the old stream, and parameter 'in' will be ignored.
     *                       If this method is called for the first time in this instance, isResume will be ignored.
     * @throws IOException
     * @throws ParseException
     * @see ContentHandler
     */
    public void parse(Reader in, ContentHandler contentHandler, boolean isResume) throws ParseException {
        if (isResume) {
            if (handlerStatusStack == null) {
                reset(in);
                handlerStatusStack = new LinkedList<>();
            }
        } else {
            reset(in);
            handlerStatusStack = new LinkedList<>();
        }

        LinkedList<Integer> statusStack = handlerStatusStack;

        try {
            do {
                if (handleParse(contentHandler, statusStack)) return;
            } while (token.type != Yytoken.Type.TYPE_EOF);
        } catch (ParseException | RuntimeException ex) {
            status = S_IN_ERROR;
            throw ex;
        }

        status = S_IN_ERROR;
        throw new ParseException(getPosition(), ParseException.ErrorType.UNEXPECTED_TOKEN, token);
    }

    public boolean handleParse(ContentHandler contentHandler, LinkedList<Integer> statusStack) throws ParseException {
        switch (status) {
            case S_INIT -> {
                if (handleS_INIT(contentHandler, statusStack)) return true;
            }

            case S_IN_FINISHED_VALUE -> {
                handleS_IN_FINISHED_VALUE(contentHandler);
                return true;
            }

            case S_IN_OBJECT -> {
                if (handleS_IN_OBJECT(contentHandler, statusStack)) return true;
            }

            case S_PASSED_PAIR_KEY -> {
                if (handleS_PASSED_PAIR_KEY(contentHandler, statusStack)) return true;
            }

            case S_IN_PAIR_VALUE -> {
                if (handleS_IN_PAIR_VALUE(contentHandler, statusStack)) return true;
            }

            case S_IN_ARRAY -> {
                if (handleS_IN_ARRAY(contentHandler, statusStack)) return true;
            }

            case S_END -> {
                return true;
            }

            default -> throw new ParseException(getPosition(), ParseException.ErrorType.UNEXPECTED_TOKEN, token);

        }//switch
        if (status == S_IN_ERROR) {
            throw new ParseException(getPosition(), ParseException.ErrorType.UNEXPECTED_TOKEN, token);
        }
        return false;
    }

    public boolean handleS_IN_ARRAY(ContentHandler contentHandler, LinkedList<Integer> statusStack) throws ParseException {
        nextToken();
        switch (token.type) {
            case TYPE_COMMA -> {
            }
            case TYPE_VALUE -> {
                if (!contentHandler.primitive(token.value))
                    return true;
            }
            case TYPE_RIGHT_SQUARE -> {
                if (statusStack.size() > 1) {
                    statusStack.removeFirst();
                    status = peekStatus(statusStack);
                } else {
                    status = S_IN_FINISHED_VALUE;
                }
                if (!contentHandler.endArray())
                    return true;
            }
            case TYPE_LEFT_BRACE -> {
                status = S_IN_OBJECT;
                statusStack.addFirst(status);
                if (!contentHandler.startObject())
                    return true;
            }
            case TYPE_LEFT_SQUARE -> {
                status = S_IN_ARRAY;
                statusStack.addFirst(status);
                if (!contentHandler.startArray())
                    return true;
            }
            default -> status = S_IN_ERROR;
        }//inner switch
        return false;
    }

    public boolean handleS_IN_PAIR_VALUE(ContentHandler contentHandler, LinkedList<Integer> statusStack) throws ParseException {
        /*
         * S_IN_PAIR_VALUE is just a marker to indicate the end of an object entry, it doesn't proccess any token,
         * therefore delay consuming token until next round.
         */
        statusStack.removeFirst();
        status = peekStatus(statusStack);
        if (!contentHandler.endObjectEntry())
            return true;
        return false;
    }

    public boolean handleS_PASSED_PAIR_KEY(ContentHandler contentHandler, LinkedList<Integer> statusStack) throws ParseException {
        nextToken();
        switch (token.type) {
            case TYPE_COLON -> {
            }
            case TYPE_VALUE -> {
                statusStack.removeFirst();
                status = peekStatus(statusStack);
                if (!contentHandler.primitive(token.value))
                    return true;
                if (!contentHandler.endObjectEntry())
                    return true;
            }
            case TYPE_LEFT_SQUARE -> {
                statusStack.removeFirst();
                statusStack.addFirst(S_IN_PAIR_VALUE);
                status = S_IN_ARRAY;
                statusStack.addFirst(status);
                if (!contentHandler.startArray())
                    return true;
            }
            case TYPE_LEFT_BRACE -> {
                statusStack.removeFirst();
                statusStack.addFirst(S_IN_PAIR_VALUE);
                status = S_IN_OBJECT;
                statusStack.addFirst(status);
                if (!contentHandler.startObject())
                    return true;
            }
            default -> status = S_IN_ERROR;
        }
        return false;
    }

    public boolean handleS_IN_OBJECT(ContentHandler contentHandler, LinkedList<Integer> statusStack) throws ParseException {
        nextToken();
        switch (token.type) {
            case TYPE_COMMA -> {
            }
            case TYPE_VALUE -> {
                if (token.value instanceof String key) {
                    status = S_PASSED_PAIR_KEY;
                    statusStack.addFirst(status);
                    if (!contentHandler.startObjectEntry(key))
                        return true;
                } else {
                    status = S_IN_ERROR;
                }
            }

            case TYPE_RIGHT_BRACE -> {
                if (statusStack.size() > 1) {
                    statusStack.removeFirst();
                    status = peekStatus(statusStack);
                } else {
                    status = S_IN_FINISHED_VALUE;
                }
                if (!contentHandler.endObject())
                    return true;
            }
            default -> status = S_IN_ERROR;
        }//inner switch
        return false;
    }

    public void handleS_IN_FINISHED_VALUE(ContentHandler contentHandler) throws ParseException {
        nextToken();
        if (token.type == Yytoken.Type.TYPE_EOF) {
            contentHandler.endJSON();
            status = S_END;
            return;
        } else {
            status = S_IN_ERROR;
            throw new ParseException(getPosition(), ParseException.ErrorType.UNEXPECTED_TOKEN, token);
        }
    }

    public boolean handleS_INIT(ContentHandler contentHandler, LinkedList<Integer> statusStack) throws ParseException {
        contentHandler.startJSON();
        nextToken();
        switch (token.type) {
            case TYPE_VALUE -> {
                status = S_IN_FINISHED_VALUE;
                statusStack.addFirst(status);
                if (!contentHandler.primitive(token.value))
                    return true;
            }
            case TYPE_LEFT_BRACE -> {
                status = S_IN_OBJECT;
                statusStack.addFirst(status);
                if (!contentHandler.startObject())
                    return true;
            }
            case TYPE_LEFT_SQUARE -> {
                status = S_IN_ARRAY;
                statusStack.addFirst(status);
                if (!contentHandler.startArray())
                    return true;
            }
            default -> status = S_IN_ERROR;
        }//inner switch
        return false;
    }
}