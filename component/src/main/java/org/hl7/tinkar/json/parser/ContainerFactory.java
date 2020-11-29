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

import java.util.List;
import java.util.Map;

/**
 * Original obtained from: https://github.com/fangyidong/json-simple under Apache 2 license
 * Original project had no support for Java Platform Module System, and not updated for 8 years. 
 * Integrated here to integrate with Java Platform Module System. 

 * 
 * Container factory for creating containers for JSON object and JSON array.
 * 
 * @see org.hl7.tinkar.parser.JSONParser#parse(java.io.Reader, ContainerFactory)
 * 
 * @author FangYidong<fangyidong@yahoo.com.cn>
 */
public interface ContainerFactory {
	/**
	 * @return A Map instance to store JSON object, or null if you want to use org.hl7.tinkar.JSONObject.
	 */
	Map<String, Object> createObjectContainer();
	
	/**
	 * @return A List instance to store JSON array, or null if you want to use org.hl7.tinkar.JSONArray. 
	 */
	List<Object> creatArrayContainer();
}