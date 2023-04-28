package dev.ikm.tinkar.integration.snomed.core;
/* MockDataType classifies three types of values which has different ways to compute UUIDS, i.e.
      Modules: UuidT5Generator.get(SNOMED_CT_NAMESPACE+this.value);
      Concepts: UuidT5Generator.get(this.value);
      EntityRefs: UUID.fromString(this.value);
   */
public enum MockDataType {
    CONCEPT("Concept"),
    MODULE("Module"),
    ENTITYREF("EntityRef"),
    PATTERN ("Pattern");
    private String name;
    MockDataType(String name) {
        this.name = name;
    }

    static MockDataType getEnumType(String value) {
        for(MockDataType type: MockDataType.values()) {
            if(type.name.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }
}