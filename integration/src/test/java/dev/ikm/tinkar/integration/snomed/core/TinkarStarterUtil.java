package dev.ikm.tinkar.integration.snomed.core;

import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;

import java.util.UUID;

public class TinkarStarterUtil {
    public static final UUID SNOMED_CT_NAMESPACE = UUID.fromString("48b004d4-6457-4648-8d58-e3287126d96b");
    public static final UUID DEVELOPMENT_PATH = UuidT5Generator.get("Development Path");
    public static final UUID SNOMED_CT_AUTHOR = UuidT5Generator.get("SNOMED CT Author");
    public static final UUID SNOMED_CT_STARTER_DATA_MODULE = UuidT5Generator.get("SNOMED CT Starter Data Module");
    public static final UUID ACTIVE = UuidT5Generator.get("Active");
    public static final UUID INACTIVE = UuidT5Generator.get("Inactive");
    public static final UUID DELOITTE_USER = UuidT5Generator.get("Deloitte User");
    public static final UUID IDENTIFIER_PATTERN = UuidT5Generator.get("Identifier Pattern");
    public static final UUID SNOMED_CT_IDENTIFIER = UuidT5Generator.get("SNOMED CT identifier");
}
