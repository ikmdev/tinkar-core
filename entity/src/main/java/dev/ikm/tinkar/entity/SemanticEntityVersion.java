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

import dev.ikm.tinkar.component.FieldDataType;
import dev.ikm.tinkar.component.SemanticVersion;
import dev.ikm.tinkar.terms.EntityFacade;
import org.eclipse.collections.api.list.ImmutableList;

public interface SemanticEntityVersion extends EntityVersion, SemanticVersion {
    /**
     * TODO: Do we need both entity() and chronology() ?
     *
     * @return
     */
    @Override
    default SemanticEntity entity() {
        return chronology();
    }

    @Override
    SemanticEntity chronology();

    default EntityFacade referencedComponent() {
        return Entity.provider().getEntityFast(referencedComponentNid());
    }

    default int referencedComponentNid() {
        return chronology().referencedComponentNid();
    }

    default PatternEntity pattern() {
        return Entity.provider().getEntityFast(patternNid());
    }

    default int patternNid() {
        return chronology().patternNid();
    }

    default FieldDataType fieldDataType(int fieldIndex) {
        return FieldDataType.getFieldDataType(fieldValues().get(fieldIndex));
    }

    @Override
    ImmutableList<Object> fieldValues();

    ImmutableList<? extends Field> fields(PatternEntityVersion patternVersion);

}
