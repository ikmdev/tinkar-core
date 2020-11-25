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
package org.hl7.tinkar.binary;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 *
 * @author kec
 */
public class TinkarByteArrayOutput extends TinkarOutput {

    final ByteArrayOutputStream byteArrayOutputStream;
    private TinkarByteArrayOutput(ByteArrayOutputStream byteArrayOutputStream) {
        super(byteArrayOutputStream);
        this.byteArrayOutputStream = byteArrayOutputStream;
    }
    
    public byte[] getBytes() {
        return byteArrayOutputStream.toByteArray();
    }
   
    public static TinkarByteArrayOutput make() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        return new TinkarByteArrayOutput(baos);
    }
    
    public TinkarInput toInput() {
        ByteArrayInputStream bais = new ByteArrayInputStream(this.getBytes());
        return new TinkarInput(bais);
    }
    
}
