/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.common.util.text;

/**
 * Utility for converting human-readable description text into a token-safe string
 * suitable for use as identifiers or enum-like constants. Punctuation and special
 * characters are stripped or replaced with underscore-based equivalents.
 */
public class DescriptionToToken {

    /** Private constructor to prevent instantiation of this utility class. */
    private DescriptionToToken() {
        // utility class
    }

    /**
     * Converts the given description text to a token string by replacing or removing
     * punctuation and special characters.
     *
     * @param descriptionText the human-readable description to convert
     * @return the token-safe string
     */
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
