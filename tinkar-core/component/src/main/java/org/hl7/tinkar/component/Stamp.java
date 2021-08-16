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
 */
package org.hl7.tinkar.component;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.util.time.DateTimeUtil;

import java.time.Instant;

/**
 * TODO should stamp become a chronology, so that uncommitted changes would use different versions of the same data
 * structure?
 *
 * @author kec
 */
public interface Stamp<T extends Stamp> extends Chronology<T>, Component, Version {

    Concept state();

    default Instant instant() {
        return DateTimeUtil.epochMsToInstant(time());
    }

    long time();

    Concept author();

    Concept module();

    Concept path();

    @Override
    default Stamp stamp() {
        return this;
    }

    @Override
    default ImmutableList<T> versions() {
        return (ImmutableList<T>) Lists.immutable.of(this);
    }

}
