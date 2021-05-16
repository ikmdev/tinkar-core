package org.hl7.tinkar.common.util.text;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;

public class EscapeUtil {
    /**
     Escape characters for text appearing as XML data, between tags.

     <P>The following characters are replaced with corresponding character entities :
     <table border='1' cellpadding='3' cellspacing='0'>
     <tr><th> Character </th><th> Encoding </th></tr>
     <tr><td> < </td><td> &lt; </td></tr>
     <tr><td> > </td><td> &gt; </td></tr>
     <tr><td> & </td><td> &amp; </td></tr>
     <tr><td> " </td><td> &quot;</td></tr>
     <tr><td> ' </td><td> &#039;</td></tr>
     </table>

     */
    public static String forXML(String aText){
        final StringBuilder result = new StringBuilder();
        final StringCharacterIterator iterator = new StringCharacterIterator(aText);
        char character =  iterator.current();
        while (character != CharacterIterator.DONE ){
            if (character == '<') {
                result.append("&lt;");
            }
            else if (character == '>') {
                result.append("&gt;");
            }
            else if (character == '\"') {
                result.append("&quot;");
            }
            else if (character == '\'') {
                result.append("&#039;");
            }
            else if (character == '&') {
                result.append("&amp;");
            }
            else {
                //the char is not a special one
                //add it to the result as is
                result.append(character);
            }
            character = iterator.next();
        }
        return result.toString();
    }

    public static String fromXML(String aText) {
        aText = aText.replace("&lt;", "<");
        aText = aText.replace("&quot;", ">");
        aText = aText.replace("&lt;", "\"");
        return aText.replace("&amp;", "&");
    }
}