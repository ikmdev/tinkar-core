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
package dev.ikm.tinkar.dto;

import dev.ikm.tinkar.dto.binary.*;
import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.component.Concept;
import dev.ikm.tinkar.dto.binary.*;

import java.util.UUID;

@RecordBuilder
public record ConceptDTO(PublicId publicId)
        implements Concept, DTO, Marshalable {
    private static final int localMarshalVersion = 3;

    @Unmarshaler
    public static ConceptDTO make(TinkarInput in) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            PublicId componentPublicId = in.getPublicId();
            return new ConceptDTO(componentPublicId);
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + marshalVersion);
        }
    }

    public static ConceptDTO make(String uuidListString) {
        uuidListString = uuidListString.replace("[", "");
        uuidListString = uuidListString.replace("]", "");
        uuidListString = uuidListString.replace(",", "");
        uuidListString = uuidListString.replace("\"", "");
        String[] uuidStrings = uuidListString.split(" ");
        MutableList<UUID> componentUuids = Lists.mutable.ofInitialCapacity(uuidStrings.length);
        for (String uuidString: uuidStrings) {
            componentUuids.add(UUID.fromString(uuidString));
        }
        return new ConceptDTO(PublicIds.of(componentUuids));
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        out.putPublicId(publicId());
    }
}
