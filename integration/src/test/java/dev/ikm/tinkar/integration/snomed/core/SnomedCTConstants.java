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
package dev.ikm.tinkar.integration.snomed.core;

import dev.ikm.tinkar.terms.KernelTerm;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;

import java.util.UUID;

public class SnomedCTConstants {

    public static final UUID SNOMED_CT_NAMESPACE_UUID = UUID.fromString("48b004d4-6457-4648-8d58-e3287126d96b");
    public static final UUID DEVELOPMENT_PATH_UUID = KernelTerm.DEVELOPMENT_PATH.publicId().leastUuid();
    public static final UUID SNOMED_CT_AUTHOR_UUID = UuidT5Generator.get("SNOMED CT Author");
    public static final UUID SNOMED_TEXT_MODULE_ID_UUID = UuidT5Generator.get(SNOMED_CT_NAMESPACE_UUID, "900000000000207008");
    public static final UUID ACTIVE_UUID = KernelTerm.ACTIVE_STATE.publicId().leastUuid();
    public static final UUID INACTIVE_UUID = KernelTerm.INACTIVE_STATE.publicId().leastUuid();
    public static final UUID DELOITTE_USER_UUID = UuidT5Generator.get("Deloitte User");
    public static final UUID IDENTIFIER_PATTERN_UUID = KernelTerm.IDENTIFIER_PATTERN.publicId().leastUuid();
    public static final UUID SNOMED_CT_IDENTIFIER_UUID = KernelTerm.SCTID.publicId().leastUuid();
    public static final UUID RELATIONSHIP_PATTERN_UUID = UuidT5Generator.get("Relationship Pattern");
    public static final String RELATIONSHIP_PATTERN = "Relationship Pattern";
    public static final String TEST_SNOMEDCT_MOCK_DATA_JSON ="mock-data.json";
    public static final String JSON_CONCEPTS_ARRAY_PROP = "concepts";
    public static final String JSON_MODULES_ARRAY_PROP = "modules";
    public static final String JSON_ENTITY_REF_ARRAY_PROP = "entityRefs";




}
