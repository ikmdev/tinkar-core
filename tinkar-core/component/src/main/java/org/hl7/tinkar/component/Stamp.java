/*
 * Copyright 2020-2021 HL7.
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
 */package org.hl7.tinkar.component;

import org.hl7.tinkar.common.util.time.DateTimeUtil;

import java.time.Instant;

/**
 * TODO should stamp become a chronology, so that uncommitted changes would use different versions of the same data
 * structure?
 * @author kec
 */
public interface Stamp extends Component {

    Concept state();

    long time();

    default Instant instant() {
        return DateTimeUtil.epochMsToInstant(time());
    }

    Concept author();

    Concept module();

    Concept path();

}
