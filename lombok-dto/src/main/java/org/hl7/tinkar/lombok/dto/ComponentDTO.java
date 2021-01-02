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
package org.hl7.tinkar.lombok.dto;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.NonFinal;
import org.eclipse.collections.api.list.ImmutableList;

import java.util.Objects;
import java.util.UUID;

/**
 *
 * @author kec
 */
@Value
@Accessors(fluent = true)
@NonFinal
public class ComponentDTO
        implements DTO {
    @NonNull
    final ImmutableList<UUID> componentUuids;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ComponentDTO)) return false;
        ComponentDTO that = (ComponentDTO) o;
        return componentUuids.equals(that.componentUuids);
    }

    @Override
    public int hashCode() {
        return Objects.hash(componentUuids);
    }
}
