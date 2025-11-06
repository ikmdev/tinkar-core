# Implementation Notes: Dual Protobuf + JSON Export

## Overview
The ChangeSetWriterProvider now writes entity data to both protobuf (`.pb.zip` or ` ike-cs.zip`) and JSON (`.json`) files simultaneously with the same filename prefix.

## File Naming Convention

### Protobuf File
- **Pattern:** `{USER} {TIMESTAMP} {RANDOM} ike-cs.zip`
- **Example:** `USER 2025-11-06T10∶30∶45-abc ike-cs.zip`

### JSON File
- **Pattern:** `{USER} {TIMESTAMP} {RANDOM}.json`
- **Example:** `USER 2025-11-06T10∶30∶45-abc.json`

The JSON filename is derived from the protobuf filename by replacing ` ike-cs.zip` with `.json`.

## Key Implementation Details

### 1. Dual File Creation
```java
final File zipfile = newZipFile();
final File jsonfile = new File(zipfile.getAbsolutePath()
    .replace(".pb.zip", ".json")
    .replace(" ike-cs.zip", ".json"));
```

### 2. Dual Stream Management
```java
try (FileOutputStream fos = new FileOutputStream(zipfile);
     BufferedOutputStream bos = new BufferedOutputStream(fos);
     ZipOutputStream zos = new ZipOutputStream(bos);
     FileWriter fw = new FileWriter(jsonfile);
     BufferedWriter jsonWriter = new BufferedWriter(fw)) {
    // Write entities to both streams
}
```

### 3. Dual Write Operations
Each entity is written to both formats:
```java
// Write to protobuf
TinkarMsg tinkarMsg = entityTransformer.transform(entityToWrite);
tinkarMsg.writeDelimitedTo(zos);

// Write to JSON
jsonTransformer.writeEntity(entityToWrite, jsonWriter);
```

### 4. Cleanup for Both Files
```java
finally {
    if (zipfile.exists()) {
        if (entityCount.sum() == 0) {
            zipfile.delete();
        }
    }
    if (jsonfile.exists()) {
        if (entityCount.sum() == 0) {
            jsonfile.delete();
        }
    }
}
```

## Entity Type Handling

The JSON transformer handles all Tinkar entity types:

### Concept Entities
```json
{
  "entityType": "Concept",
  "nid": 12345,
  "publicId": { "uuids": ["..."] },
  "versions": [
    {
      "stampNid": 67890,
      "versionType": "ConceptVersion"
    }
  ]
}
```

### Semantic Entities
```json
{
  "entityType": "Semantic",
  "nid": 23456,
  "publicId": { "uuids": ["..."] },
  "versions": [
    {
      "stampNid": 67890,
      "versionType": "SemanticVersion",
      "patternNid": 34567,
      "referencedComponentNid": 45678,
      "fields": [
        { "value": "example", "type": "String" },
        { "value": 123, "type": "Integer" }
      ]
    }
  ]
}
```

### Pattern Entities
```json
{
  "entityType": "Pattern",
  "nid": 34567,
  "publicId": { "uuids": ["..."] },
  "versions": [
    {
      "stampNid": 67890,
      "versionType": "PatternVersion",
      "semanticPurposeNid": 11111,
      "semanticMeaningNid": 22222,
      "fieldDefinitions": [
        {
          "meaningNid": 33333,
          "dataTypeNid": 44444,
          "purposeNid": 55555
        }
      ]
    }
  ]
}
```

### Stamp Entities
```json
{
  "entityType": "Stamp",
  "nid": 67890,
  "publicId": { "uuids": ["..."] },
  "versions": [
    {
      "stampNid": 67890,
      "versionType": "StampVersion",
      "stateNid": 11111,
      "time": 1699286400000,
      "authorNid": 22222,
      "moduleNid": 33333,
      "pathNid": 44444
    }
  ]
}
```

## Field Type Handling

The JSON transformer handles various field types in semantic entities:

- **String:** `{ "value": "text", "type": "String" }`
- **Integer:** `{ "value": 123, "type": "Integer" }`
- **Long:** `{ "value": 9876543210, "type": "Long" }`
- **Float:** `{ "value": 3.14, "type": "Float" }`
- **Double:** `{ "value": 2.718281828, "type": "Double" }`
- **Boolean:** `{ "value": true, "type": "Boolean" }`
- **Null:** `{ "value": null, "type": "null" }`
- **Other:** `{ "value": "toString() output", "type": "ClassName" }`

## Performance Considerations

1. **Buffered I/O:** Both outputs use buffered streams for optimal performance
2. **Parallel Writing:** Entities are written to both formats in the same loop iteration
3. **Memory Efficient:** Entities are written one at a time, not accumulated in memory
4. **Singleton Pattern:** Both transformers use singleton pattern to avoid repeated instantiation

## Error Handling

- Both writes are wrapped in try-catch blocks
- Failures in either format throw RuntimeException
- Resources are properly cleaned up via try-with-resources
- Empty files are deleted during cleanup

## Thread Safety

- The ChangeSetWriterProvider runs on a virtual thread
- Entity queue uses LinkedBlockingQueue for thread-safe operations
- Both file writers are local to the service thread
- No shared mutable state between transformers

## Testing the Implementation

To verify the implementation:

1. Build the project:
   ```bash
   mvn clean install -DskipTests -pl provider/changeset-writer-provider -am
   ```

2. Run an application that uses ChangeSetWriterService

3. Check the output folder for paired files:
   - `USER {timestamp} {random} ike-cs.zip` (protobuf)
   - `USER {timestamp} {random}.json` (JSON)

4. Verify JSON content is human-readable and matches protobuf entity data

## Dependencies

All required dependencies are JPMS-compliant and already in use:

- **Jackson Databind** (2.19.0): JSON serialization
- **SLF4J**: Logging
- **Eclipse Collections**: Primitive collections for entity processing

## Future Enhancements

Possible improvements:
1. Configuration option to disable JSON export (for performance)
2. JSON file compression (gzip)
3. JSON streaming API for very large changesets
4. JSON import/deserialization support
5. JSON Schema validation
6. Separate JSON file rotation threshold

