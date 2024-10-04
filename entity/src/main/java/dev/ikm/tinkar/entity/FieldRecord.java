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

import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.Validator;
import dev.ikm.tinkar.component.FieldDefinition;
import io.soabase.recordbuilder.core.RecordBuilder;

import java.util.Objects;

/**
 * TODO, create an entity data type that combines concept and FieldDataType like the Status enum?
 *
 * @param <T>
 */
@RecordBuilder
public record FieldRecord<T>(T value, int semanticNid, int semanticVersionStampNid,
                             FieldDefinitionForEntity fieldDefinition)
        implements FieldDefinition, Field<T>,
                   ImmutableField<T>, FieldRecordBuilder.With {


    public FieldRecord {
        Validator.notZero(semanticNid);
        Validator.notZero(semanticVersionStampNid);
        Objects.requireNonNull(fieldDefinition);
    }
    @Override
    public int meaningNid() {
        return fieldDefinition.meaningNid();
    }

    @Override
    public int purposeNid() {
        return fieldDefinition.purposeNid();
    }

    @Override
    public int dataTypeNid() {
        return fieldDefinition.dataTypeNid();
    }

    public int fieldIndex() {
        return fieldDefinition.indexInPattern();
    }

    @Override
    public String toString() {
        return "FieldRecord{value: " + value +
                ", for semantic entity: " + PrimitiveData.textWithNid(semanticNid) +
                " of version: " + Entity.getStamp(semanticVersionStampNid).lastVersion().describe() +
                " with index: " + fieldIndex() +
                ", defined as " + fieldDefinition +
                '}';
    }

}
