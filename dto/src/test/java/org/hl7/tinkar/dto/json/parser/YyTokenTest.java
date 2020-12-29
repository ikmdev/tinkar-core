package org.hl7.tinkar.dto.json.parser;

import org.hl7.tinkar.dto.json.parser.Yytoken;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class YyTokenTest {

    @Test
    public void testToString() {
        Assertions.assertEquals("VALUE(type value)", new Yytoken(Yytoken.Type.TYPE_VALUE, "type value").toString());
        Assertions.assertEquals("LEFT BRACE({)", new Yytoken(Yytoken.Type.TYPE_LEFT_BRACE, "lb value").toString());
        Assertions.assertEquals("RIGHT BRACE(})", new Yytoken(Yytoken.Type.TYPE_RIGHT_BRACE, "rb value").toString());
        Assertions.assertEquals("LEFT SQUARE([)", new Yytoken(Yytoken.Type.TYPE_LEFT_SQUARE, "ls value").toString());
        Assertions.assertEquals("RIGHT SQUARE(])", new Yytoken(Yytoken.Type.TYPE_RIGHT_SQUARE, "rs value").toString());
        Assertions.assertEquals("COMMA(,)", new Yytoken(Yytoken.Type.TYPE_COMMA, "comma value").toString());
        Assertions.assertEquals("COLON(:)", new Yytoken(Yytoken.Type.TYPE_COLON, "colon value").toString());
        Assertions.assertEquals("END OF FILE", new Yytoken(Yytoken.Type.TYPE_EOF, "eof value").toString());
        Assertions.assertThrows(NullPointerException.class, () -> new Yytoken(null, "null value").toString());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> new Yytoken(Yytoken.Type.UNUSED, "unused value").toString());
    }
}
