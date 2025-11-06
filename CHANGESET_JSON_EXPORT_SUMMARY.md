# ChangeSet Writer JSON Export Enhancement

## Summary

The `ChangeSetWriterProvider` has been updated to write entity data to both protobuf files (`.pb.zip`) and JSON files (`.json`) simultaneously. This enhancement provides human-readable JSON output alongside the existing binary protobuf format without introducing any non-JPMS-compliant dependencies.

## Changes Made

### 1. New File: `EntityToJsonTransformer.java`
**Location:** `provider/changeset-writer-provider/src/main/java/dev/ikm/tinkar/provider/changeset/EntityToJsonTransformer.java`

This new class provides JSON transformation functionality for Tinkar entities:
- Transforms `Entity<EntityVersion>` objects to JSON format
- Supports all entity types: Concept, Semantic, Pattern, and Stamp
- Uses Jackson's `ObjectMapper` for JSON serialization
- Writes JSON data to a `BufferedWriter` with proper formatting
- Implements singleton pattern for efficient resource usage
- Handles complex entity structures including:
  - Public IDs with UUID arrays
  - Entity versions with stamps
  - Semantic entity fields
  - Pattern entity field definitions
  - Stamp entity temporal and provenance data

### 2. Updated: `ChangeSetWriterProvider.java`
**Location:** `provider/changeset-writer-provider/src/main/java/dev/ikm/tinkar/provider/changeset/ChangeSetWriterProvider.java`

**Key modifications:**
- Added import for `BufferedWriter` and `FileWriter`
- Added import for `ImmutableIntList` from Eclipse Collections
- Created `EntityToJsonTransformer` instance alongside the protobuf transformer
- Modified `startService()` method to:
  - Create a JSON file with the same prefix as the protobuf file (e.g., `USER 2025-11-06T10∶30∶00-abc.json`)
  - Open both ZIP and JSON output streams in parallel
  - Pass both writers to the `writeEntity()` method
  - Flush and close JSON writer properly
  - Clean up JSON file if no entities were written
- Updated `writeEntity()` method signature to accept:
  - `EntityToTinkarSchemaTransformer entityTransformer` (existing)
  - `ZipOutputStream zos` (existing)
  - `EntityToJsonTransformer jsonTransformer` (new)
  - `BufferedWriter jsonWriter` (new)
- Modified `writeEntity()` implementation to:
  - Write entity to protobuf format (existing behavior)
  - Write entity to JSON format (new behavior)
  - Log both write operations separately

### 3. Updated: `module-info.java`
**Location:** `provider/changeset-writer-provider/src/main/java/module-info.java`

Added required modules:
- `requires com.fasterxml.jackson.databind;` - For JSON processing
- `requires org.slf4j;` - For logging in EntityToJsonTransformer

### 4. Updated: `pom.xml`
**Location:** `provider/changeset-writer-provider/pom.xml`

Added dependency:
```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

## Output Format

### Protobuf File (Existing)
- **Filename pattern:** `USER YYYY-MM-DDTHH∶MM∶SS-XXX ike-cs.zip`
- **Format:** Binary protobuf messages in ZIP archive
- **Content:** Compressed entity data with manifest

### JSON File (New)
- **Filename pattern:** `USER YYYY-MM-DDTHH∶MM∶SS-XXX.json`
- **Format:** Human-readable JSON, one entity per line
- **Content:** Complete entity data including:
  - Entity type (Concept, Semantic, Pattern, Stamp)
  - Entity NID
  - Public ID with UUIDs
  - All entity versions with:
    - Stamp NIDs
    - Version-specific data (fields, definitions, etc.)

### Example JSON Output
```json
{
  "entityType": "Concept",
  "nid": 12345,
  "publicId": {
    "uuids": ["550e8400-e29b-41d4-a716-446655440000"]
  },
  "versions": [
    {
      "stampNid": 67890,
      "versionType": "ConceptVersion"
    }
  ]
}
```

## Dependencies

All dependencies are JPMS-compliant:
- **Jackson Databind** (com.fasterxml.jackson.databind) - Already used in the project, JPMS-compatible
- **SLF4J** (org.slf4j) - Already used throughout the project, JPMS-compatible
- **Eclipse Collections** (org.eclipse.collections.api) - Already used, JPMS-compatible

## Benefits

1. **Human-Readable Format:** JSON files can be easily inspected, debugged, and processed by standard tools
2. **No Breaking Changes:** Existing protobuf functionality remains unchanged
3. **JPMS Compliance:** All dependencies are module-system compatible
4. **Parallel Writing:** Both formats are written simultaneously for consistency
5. **Efficient:** Uses buffered writers for optimal I/O performance
6. **Complete Data:** JSON captures all entity information including versions and metadata

## Testing

The module compiles successfully with:
```bash
mvn clean install -DskipTests -pl provider/changeset-writer-provider -am
```

## Future Enhancements

Potential improvements for consideration:
1. Add configuration option to enable/disable JSON export
2. Implement JSON streaming for very large datasets
3. Add JSON schema validation
4. Create JSON import functionality to read back JSON changesets
5. Add compression for JSON files (e.g., gzip)

