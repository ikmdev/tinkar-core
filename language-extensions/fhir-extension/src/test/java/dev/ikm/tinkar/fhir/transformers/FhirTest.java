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

import dev.ikm.tinkar.common.id.IntIds;
import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ServiceKeys;
import dev.ikm.tinkar.common.service.ServiceProperties;
import dev.ikm.tinkar.coordinate.stamp.*;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;

public class FhirTest {
    private final String JSON_STRING = "{\n" +
            "  \"resourceType\": \"Bundle\",\n" +
            "  \"type\": \"collection\",\n" +
            "  \"entry\": [ {\n" +
            "    \"fullUrl\": \"snomedctVAExtension\",\n" +
            "    \"resource\": {\n" +
            "      \"resourceType\": \"CodeSystem\",\n" +
            "      \"id\": \"snomedctVAExtension\",\n" +
            "      \"meta\": {\n" +
            "        \"lastUpdated\": \"2024-03-27T16:26:28.500-04:00\"\n" +
            "      },\n" +
            "      \"extension\": [ {\n" +
            "        \"url\": \"http://hl7.org/fhir/StructureDefinition/structuredefinition-wg\",\n" +
            "        \"valueCode\": \"fhir\"\n" +
            "      } ],\n" +
            "      \"url\": \"http://snomed.info/sctVAExtension\",\n" +
            "      \"identifier\": [ {\n" +
            "        \"system\": \"urn:ietf:rfc:3986\",\n" +
            "        \"value\": \"urn:oid:2.16.840.1.113883.6.96\"\n" +
            "      } ],\n" +
            "      \"name\": \"SNOMED_CT\",\n" +
            "      \"title\": \"SNOMED CT (all versions)\",\n" +
            "      \"status\": \"active\",\n" +
            "      \"experimental\": false,\n" +
            "      \"publisher\": \"IHTSDO\",\n" +
            "      \"contact\": [ {\n" +
            "        \"telecom\": [ {\n" +
            "          \"system\": \"url\",\n" +
            "          \"value\": \"http://ihtsdo.org\"\n" +
            "        } ]\n" +
            "      } ],\n" +
            "      \"description\": \"SNOMED CT is the most comprehensive and precise clinical health terminology product in the world, owned and distributed around the world by The International Health Terminology Standards Development Organisation (IHTSDO).\",\n" +
            "      \"copyright\": \"© 2002-2016 International Health Terminology Standards Development Organisation (IHTSDO). All rights reserved. SNOMED CT®, was originally created by The College of American Pathologists. \\\\\\\"SNOMED\\\\\\\" and \\\\\\\"SNOMED CT\\\\\\\" are registered trademarks of the IHTSDO http://www.ihtsdo.org/snomed-ct/get-snomed-ct\",\n" +
            "      \"caseSensitive\": false,\n" +
            "      \"hierarchyMeaning\": \"is-a\",\n" +
            "      \"compositional\": true,\n" +
            "      \"versionNeeded\": false,\n" +
            "      \"content\": \"fragment\",\n" +
            "      \"filter\": [ {\n" +
            "        \"code\": \"concept\",\n" +
            "        \"description\": \"Filter that includes concepts based on their logical definition. e.g. [concept] [is-a] [x] - include all concepts with an is-a relationship to concept x, or [concept] [in] [x]- include all concepts in the reference set identified by concept x\",\n" +
            "        \"operator\": [ \"is-a\", \"in\" ],\n" +
            "        \"value\": \"A SNOMED CT code\"\n" +
            "      }, {\n" +
            "        \"code\": \"expression\",\n" +
            "        \"description\": \"The result of the filter is the result of executing the given SNOMED CT Expression Constraint\",\n" +
            "        \"operator\": [ \"=\" ],\n" +
            "        \"value\": \"A SNOMED CT ECL expression (see http://snomed.org/ecl)\"\n" +
            "      }, {\n" +
            "        \"code\": \"expressions\",\n" +
            "        \"description\": \"Whether post-coordinated expressions are included in the value set\",\n" +
            "        \"operator\": [ \"=\" ],\n" +
            "        \"value\": \"true or false\"\n" +
            "      } ],\n" +
            "      \"property\": [ {\n" +
            "        \"code\": \"Priority\",\n" +
            "        \"uri\": \"http://snomed.info/id/260870009\",\n" +
            "        \"type\": \"code\"\n" +
            "      }, {\n" +
            "        \"code\": \"Access\",\n" +
            "        \"uri\": \"http://snomed.info/id/260507000\",\n" +
            "        \"type\": \"code\"\n" +
            "      }, {\n" +
            "        \"code\": \"Procedure site\",\n" +
            "        \"uri\": \"http://snomed.info/id/363704007\",\n" +
            "        \"type\": \"code\"\n" +
            "      }, {\n" +
            "        \"code\": \"Is a\",\n" +
            "        \"uri\": \"http://snomed.info/id/116680003\",\n" +
            "        \"type\": \"code\"\n" +
            "      }, {\n" +
            "        \"code\": \"Status\",\n" +
            "        \"uri\": \"Status value: [10b873e2-8247-5ab5-9dec-4edef37fc219]\",\n" +
            "        \"type\": \"code\"\n" +
            "      }, {\n" +
            "        \"code\": \"OWL Stated Axiom\",\n" +
            "        \"type\": \"string\"\n" +
            "      } ]\n" +
            "    }\n" +
            "  } ]\n" +
            "}";

   // @BeforeEach
    public void init() {
       File dataStore = new File(System.getProperty("user.home") + "/Solor/starter-data-export");
        String controller = "Open SpinedArrayStore";
        CachingService.clearAll();
        ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, dataStore);
        PrimitiveData.selectControllerByName(controller);
        PrimitiveData.start();
    }


    @Test
    public void testStaticCodeSystem_ExtensionContent() throws Exception {
        StampCalculatorWithCache stampCalculatorWithCache = initStampCalculator();
        FhirCodeSystemTransform fhirCodeSystemTransform= new FhirCodeSystemTransform(stampCalculatorWithCache, new ArrayList<>(), (fhirString) -> {
            Assertions.assertNotNull(fhirString);
            Assertions.assertFalse(fhirString.isEmpty());
        });
        try {
            fhirCodeSystemTransform.compute();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private StampCalculatorWithCache initStampCalculator(Integer pathNid, long toTimeStamp) {
        StampPositionRecord stampPositionRecord = StampPositionRecordBuilder.builder().time(toTimeStamp).pathForPositionNid(pathNid).build();
        StampCoordinateRecord stampCoordinateRecord = StampCoordinateRecordBuilder.builder()
                .allowedStates(StateSet.ACTIVE_AND_INACTIVE)
                .stampPosition(stampPositionRecord)
                .moduleNids(IntIds.set.empty())
                .build().withStampPositionTime(toTimeStamp);
        return stampCoordinateRecord.stampCalculator();
    }

    private StampCalculatorWithCache initStampCalculator() {
        return initStampCalculator(TinkarTerm.DEVELOPMENT_PATH.nid(), Long.MAX_VALUE);
    }
}