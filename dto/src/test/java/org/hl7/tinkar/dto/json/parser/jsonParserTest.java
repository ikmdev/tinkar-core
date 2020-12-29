package org.hl7.tinkar.dto.json.parser;

import org.hl7.tinkar.dto.json.JSONValue;
import org.hl7.tinkar.dto.json.parser.ContainerFactory;
import org.hl7.tinkar.dto.json.parser.JSONParser;
import org.hl7.tinkar.dto.json.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;

import static org.hl7.tinkar.dto.json.parser.JSONParser.S_END;
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

    @Test
    public void testParseException() {
        assertEquals("Unexpected character (unexpected char) at position 1.", new ParseException(1, ParseException.ErrorType.UNEXPECTED_CHAR, "unexpected char").getMessage());
        assertEquals("Unexpected exception at position 2: unexpected ex", new ParseException(2, ParseException.ErrorType.UNEXPECTED_EXCEPTION, "unexpected ex").getMessage());
        assertEquals("Unexpected token unexpected token at position 3.", new ParseException(3, ParseException.ErrorType.UNEXPECTED_TOKEN, "unexpected token").getMessage());
        assertThrows(UnsupportedOperationException.class, () -> new ParseException(4, ParseException.ErrorType.UNUSED, "unused").getMessage());
    }
}

