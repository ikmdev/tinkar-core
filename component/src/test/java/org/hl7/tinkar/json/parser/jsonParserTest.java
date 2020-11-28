package org.hl7.tinkar.json.parser;

import org.hl7.tinkar.json.JSONValue;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;

import static org.hl7.tinkar.json.parser.JSONParser.S_END;
import static org.junit.jupiter.api.Assertions.*;

public class jsonParserTest {

    @Test
    public void testPeekStatus() {
        JSONParser parser = new JSONParser();
        LinkedList emptyStack = new LinkedList();
        assertTrue(parser.peekStatus(emptyStack) == -1);
        emptyStack.push(S_END);
        assertTrue(parser.peekStatus(emptyStack) == S_END);
    }

    @Test
    public void testIOException() {
        JSONParser parser = new JSONParser();
        assertThrows(ParseException.class, () -> parser.parse("", (ContainerFactory) null));
    }

    @Test
    public void testEscape() {
        String test = "\" \\ \b \f \n \r \t / ₠";
        String expected = "\\\" \\\\ \\b \\f \\n \\r \\t \\/ \\u20A0";
        assertEquals(expected, JSONValue.escape(test));
    }
}

