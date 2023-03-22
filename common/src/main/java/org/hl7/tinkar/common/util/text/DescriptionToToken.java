package org.hl7.tinkar.common.util.text;

public class DescriptionToToken {
    public static String get(String descriptionText) {
        String tokenString = descriptionText;
        tokenString = tokenString.replace(".", "");
        tokenString = tokenString.replace(",", "");
        tokenString = tokenString.replace("®", "");
        tokenString = tokenString.replace("©", "C");
        tokenString = tokenString.replace("(", "___");
        tokenString = tokenString.replace(")", "");
        tokenString = tokenString.replace(" ", "_");
        tokenString = tokenString.replace("-", "_");
        tokenString = tokenString.replace("+", "_PLUS");
        tokenString = tokenString.replace("/", "_AND_OR_");
        return tokenString;
    }
}
