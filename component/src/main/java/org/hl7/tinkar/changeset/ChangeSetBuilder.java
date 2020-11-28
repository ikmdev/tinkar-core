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
package org.hl7.tinkar.changeset;

import java.util.ArrayList;

/**
 *
 * @author kec
 */
public class ChangeSetBuilder {
    ArrayList<ChangeSetThing> changeSetObjects = new ArrayList();


    public ChangeSetBuilder add(ChangeSetThing changeSetObject) {
        changeSetObjects.add(changeSetObject);
        return this;
    }
    
    public String build() {
        throw new UnsupportedOperationException();
    }
    
}
