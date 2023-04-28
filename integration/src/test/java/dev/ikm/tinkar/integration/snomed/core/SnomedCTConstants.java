package dev.ikm.tinkar.integration.snomed.core;

import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;

import java.util.UUID;

public class SnomedCTConstants {

    public static final UUID SNOMED_CT_NAMESPACE_UUID = UUID.fromString("48b004d4-6457-4648-8d58-e3287126d96b");
    public static final UUID DEVELOPMENT_PATH_UUID = UuidT5Generator.get("Development Path");
    public static final UUID SNOMED_CT_AUTHOR_UUID = UuidT5Generator.get("SNOMED CT Author");
    public static final UUID SNOMED_TEXT_MODULE_ID_UUID = UuidT5Generator.get(SNOMED_CT_NAMESPACE_UUID, "900000000000207008");
    public static final UUID ACTIVE_UUID = UuidT5Generator.get("Active");
    public static final UUID INACTIVE_UUID = UuidT5Generator.get("Inactive");
    public static final UUID DELOITTE_USER_UUID = UuidT5Generator.get("Deloitte User");
    public static final UUID IDENTIFIER_PATTERN_UUID = UuidT5Generator.get("Identifier Pattern");
    public static final UUID SNOMED_CT_IDENTIFIER_UUID = UuidT5Generator.get("SNOMED CT identifier");
    public static final UUID RELATIONSHIP_PATTERN_UUID = UuidT5Generator.get("Relationship Pattern");
    public static final String RELATIONSHIP_PATTERN = "Relationship Pattern";
    public static final String TEST_SNOMEDCT_MOCK_DATA_JSON ="mock-data.json";
    public static final String JSON_CONCEPTS_ARRAY_PROP = "concepts";
    public static final String JSON_MODULES_ARRAY_PROP = "modules";
    public static final String JSON_ENTITY_REF_ARRAY_PROP = "entityRefs";




}
