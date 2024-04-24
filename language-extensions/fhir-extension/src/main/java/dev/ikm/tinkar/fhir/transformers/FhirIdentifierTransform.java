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
package dev.ikm.tinkar.fhir.transformers;

import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.ikm.tinkar.fhir.transformers.FhirConstants.*;
import static dev.ikm.tinkar.fhir.transformers.FhirUtils.getSnomedIdentifierConcept;

public class FhirIdentifierTransform {

    public List<Extension> transformIdentifierSemantic(SemanticEntityVersion semanticEntityVersion) {
        List<Extension> extensions = new ArrayList<>();
        ImmutableList<Object> fields = semanticEntityVersion.fieldValues();
        Identifier identifier = new Identifier();

        if(fields.get(0) instanceof EntityProxy.Concept identifierConcept
                && (Objects.equals(getSnomedIdentifierConcept().publicId().idString(), identifierConcept.publicId().idString())
                || Objects.equals(TinkarTerm.UNIVERSALLY_UNIQUE_IDENTIFIER.publicId().idString(), identifierConcept.publicId().idString()))){
            identifier.setSystem(getIdentifierSystemURL(identifierConcept.publicId().idString()));
        }
        identifier.setValue(fields.get(1).toString());
        Extension extension = new Extension();
        extension.setUrl(CODE_CONCEPT_ADDITIONAL_IDENTIFIER_URL);
        extension.setValue(identifier);
        extensions.add(extension);
        return extensions;

    }

    private String getIdentifierSystemURL(String publicIdString) {
        return (Objects.equals(getSnomedIdentifierConcept().publicId().idString(), publicIdString)) ?  SNOMEDCT_URL : IKM_DEV_URL;
    }


}
