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
package dev.ikm.tinkar.entity;

import dev.ikm.tinkar.common.util.Validator;
import dev.ikm.tinkar.component.ConceptVersion;
import io.soabase.recordbuilder.core.RecordBuilder;

import java.util.Objects;

@RecordBuilder
public record ConceptVersionRecord(ConceptRecord chronology, int stampNid)
        implements ConceptEntityVersion, ImmutableVersion, ConceptVersionRecordBuilder.With {


    public ConceptVersionRecord {
        Validator.notZero(stampNid);
        Objects.requireNonNull(chronology);
    }
    public ConceptVersionRecord(ConceptRecord chronology, ConceptVersion version) {
        this(chronology, Entity.nid(version.stamp()));
    }

    @Override
    public ConceptRecord entity() {
        return chronology();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConceptVersionRecord that = (ConceptVersionRecord) o;
        return stampNid == that.stampNid;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(stampNid);
    }

    @Override
    public String toString() {
        return stamp().describe();
    }

}
