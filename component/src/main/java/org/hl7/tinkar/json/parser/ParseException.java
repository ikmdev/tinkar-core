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
 * ParseException explains why and where the error occurs in source JSON text.
 * 
 * @author FangYidong<fangyidong@yahoo.com.cn>
 *
 */
public class ParseException extends Exception {
	private static final long serialVersionUID = -7880698968187728547L;
	
	public static final int ERROR_UNEXPECTED_CHAR = 0;
	public static final int ERROR_UNEXPECTED_TOKEN = 1;
	public static final int ERROR_UNEXPECTED_EXCEPTION = 2;

	private int errorType;
	private transient Object unexpectedObject;
	private int position;
	
	public ParseException(int errorType){
		this(-1, errorType, null);
	}
	
	public ParseException(int errorType, Object unexpectedObject){
		this(-1, errorType, unexpectedObject);
	}
	
	public ParseException(int position, int errorType, Object unexpectedObject){
		this.position = position;
		this.errorType = errorType;
		this.unexpectedObject = unexpectedObject;
	}
	
	public int getErrorType() {
		return errorType;
	}
	
	public void setErrorType(int errorType) {
		this.errorType = errorType;
	}
	
	/**
	 * @see org.hl7.tinkar.parser.JSONParser#getPosition()
	 * 
	 * @return The character position (starting with 0) of the input where the error occurs.
	 */
	public int getPosition() {
		return position;
	}
	
	public void setPosition(int position) {
		this.position = position;
	}
	
	/**
	 * @see org.hl7.tinkar.parser.Yytoken
	 * 
	 * @return One of the following base on the value of errorType:
	 * 		   	ERROR_UNEXPECTED_CHAR		java.lang.Character
	 * 			ERROR_UNEXPECTED_TOKEN		org.hl7.tinkar.parser.Yytoken
	 * 			ERROR_UNEXPECTED_EXCEPTION	java.lang.Exception
	 */
	public Object getUnexpectedObject() {
		return unexpectedObject;
	}
	
	public void setUnexpectedObject(Object unexpectedObject) {
		this.unexpectedObject = unexpectedObject;
	}
	
        @Override
	public String getMessage() {
		StringBuilder sb = new StringBuilder();
		
		switch(errorType){
		case ERROR_UNEXPECTED_CHAR -> sb.append("Unexpected character (").append(unexpectedObject).append(") at position ").append(position).append(".");
		case ERROR_UNEXPECTED_TOKEN -> sb.append("Unexpected token ").append(unexpectedObject).append(" at position ").append(position).append(".");
		case ERROR_UNEXPECTED_EXCEPTION -> sb.append("Unexpected exception at position ").append(position).append(": ").append(unexpectedObject);
		default -> sb.append("Unkown error at position ").append(position).append(".");
		}
		return sb.toString();
	}
}