/*
 * Copyright 2020 HL7.
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

@SuppressWarnings("module")
// 7 in HL7 is not a version reference
module org.hl7.tinkar.component {
    requires java.base;
    requires org.eclipse.collections.api;
    requires org.hl7.tinkar.common;
    exports org.hl7.tinkar.component;
    exports org.hl7.tinkar.component.graph;
    exports org.hl7.tinkar.component.location;
}
