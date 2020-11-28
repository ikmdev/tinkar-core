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
 *
 * @author FangYidong<fangyidong @ yahoo.com.cn>
 */
public class Yytoken {
    public enum Type {
        TYPE_VALUE,//JSON primitive value: string,number,boolean,null
        TYPE_LEFT_BRACE,
        TYPE_RIGHT_BRACE,
        TYPE_LEFT_SQUARE,
        TYPE_RIGHT_SQUARE,
        TYPE_COMMA,
        TYPE_COLON,
        TYPE_EOF, //end of file
        UNUSED; // for unit tests only.
    }

    public final Type type;
    public final Object value;

    public Yytoken(Type type, Object value) {
        this.type = type;
        this.value = value;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        switch (type) {
            case TYPE_VALUE -> sb.append("VALUE(").append(value).append(")");
            case TYPE_LEFT_BRACE -> sb.append("LEFT BRACE({)");
            case TYPE_RIGHT_BRACE -> sb.append("RIGHT BRACE(})");
            case TYPE_LEFT_SQUARE -> sb.append("LEFT SQUARE([)");
            case TYPE_RIGHT_SQUARE -> sb.append("RIGHT SQUARE(])");
            case TYPE_COMMA -> sb.append("COMMA(,)");
            case TYPE_COLON -> sb.append("COLON(:)");
            case TYPE_EOF -> sb.append("END OF FILE");
            default -> throw new UnsupportedOperationException("Can't handle; " + type);
        }
        return sb.toString();
    }
}