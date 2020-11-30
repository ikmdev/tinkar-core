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

/**
 * Original obtained from: https://github.com/fangyidong/json-simple under Apache 2 license
 * Original project had no support for Java Platform Module System, and not updated for 8 years.
 * Integrated here to integrate with Java Platform Module System.
 * <p>
 * ParseException explains why and where the error occurs in source JSON text.
 *
 * @author FangYidong<fangyidong @ yahoo.com.cn>
 */
public class ParseException extends Exception {
    private static final long serialVersionUID = -7880698968187728547L;

    public enum ErrorType {
        UNEXPECTED_CHAR,
        UNEXPECTED_TOKEN,
        UNEXPECTED_EXCEPTION,
        UNUSED; // UNUSED is for testing.
    }

    private final ErrorType errorType;
    private final String unexpectedObjectString;
    private final int position;

    public ParseException(int position, ErrorType errorType, Object unexpectedObject) {
        this.position = position;
        this.errorType = errorType;
        this.unexpectedObjectString = unexpectedObject.toString();
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    /**
     * @return The character position (starting with 0) of the input where the error occurs.
     * @see org.hl7.tinkar.parser.JSONParser#getPosition()
     */
    public int getPosition() {
        return position;
    }

    /**
     * @return One of the following base on the value of errorType:
     * ERROR_UNEXPECTED_CHAR		java.lang.Character
     * ERROR_UNEXPECTED_TOKEN		org.hl7.tinkar.parser.Yytoken
     * ERROR_UNEXPECTED_EXCEPTION	java.lang.Exception
     * @see org.hl7.tinkar.parser.Yytoken
     */
    public Object getUnexpectedObjectString() {
        return unexpectedObjectString;
    }


    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder();

        switch (errorType) {
            case UNEXPECTED_CHAR -> sb.append("Unexpected character (").append(unexpectedObjectString).append(") at position ").append(position).append(".");
            case UNEXPECTED_TOKEN -> sb.append("Unexpected token ").append(unexpectedObjectString).append(" at position ").append(position).append(".");
            case UNEXPECTED_EXCEPTION -> sb.append("Unexpected exception at position ").append(position).append(": ").append(unexpectedObjectString);
            default -> throw new UnsupportedOperationException(sb.append("Unkown error at position ").append(position).append(".").toString());
        }
        return sb.toString();
    }
}