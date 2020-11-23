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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.dto.ConceptChronologyDTO;
import org.hl7.tinkar.dto.ConceptVersionDTO;

/**
 *
 * @author kec
 */
public interface ConceptChronology extends Chronology, Concept {
    
     @Override
     ImmutableList<ConceptVersion> getVersions();


     default ConceptChronologyDTO toChangeSetThing() {
          MutableList<ConceptVersionDTO> versions = Lists.mutable.ofInitialCapacity(getVersions().size());
          for (ConceptVersion conceptVersion : getVersions()) {
               versions.add(conceptVersion.toChangeSetThing());
          }
          return new ConceptChronologyDTO(getComponentUuids(),
                  getChronologySet().getComponentUuids(),
                  versions.toImmutable());
     }
}
