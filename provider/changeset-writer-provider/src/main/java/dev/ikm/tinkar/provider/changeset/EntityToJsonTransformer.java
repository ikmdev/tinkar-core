package dev.ikm.tinkar.provider.changeset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.ConceptEntityVersion;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.entity.PatternEntityVersion;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampVersionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;

/**
 * Transforms Entity objects to JSON format for change set persistence.
 * This transformer creates JSON representations of Tinkar entities including
 * concepts, semantics, patterns, and stamps with all their versions.
 */
public class EntityToJsonTransformer {
    private static final Logger LOG = LoggerFactory.getLogger(EntityToJsonTransformer.class);
    private static final EntityToJsonTransformer INSTANCE = new EntityToJsonTransformer();

    private final ObjectMapper objectMapper;

    private EntityToJsonTransformer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public static EntityToJsonTransformer getInstance() {
        return INSTANCE;
    }

    /**
     * Transforms an entity to JSON and writes it to the buffered writer.
     *
     * @param entity the entity to transform
     * @param writer the buffered writer to write JSON to
     * @throws IOException if writing fails
     */
    public void writeEntity(Entity<EntityVersion> entity, BufferedWriter writer) throws IOException {
        ObjectNode jsonNode = transformToJson(entity);
        writer.write(objectMapper.writeValueAsString(jsonNode));
        writer.write('\n'); // Add newline separator between entities
        writer.flush();
    }

    /**
     * Transforms an entity to a JSON ObjectNode.
     *
     * @param entity the entity to transform
     * @return the JSON representation
     */
    private ObjectNode transformToJson(Entity<EntityVersion> entity) {
        ObjectNode root = objectMapper.createObjectNode();

        // Common fields
        root.put("entityType", getEntityType(entity));
        root.put("nid", entity.nid());
        root.set("publicId", publicIdToJson(entity.publicId()));

        // Add versions
        ArrayNode versionsArray = objectMapper.createArrayNode();
        for (EntityVersion version : entity.versions()) {
            versionsArray.add(versionToJson(version, entity));
        }
        root.set("versions", versionsArray);

        return root;
    }

    /**
     * Converts a PublicId to JSON.
     */
    private ObjectNode publicIdToJson(PublicId publicId) {
        ObjectNode node = objectMapper.createObjectNode();
        ArrayNode uuidsArray = objectMapper.createArrayNode();
        for (int i = 0; i < publicId.uuidCount(); i++) {
            uuidsArray.add(publicId.asUuidArray()[i].toString());
        }
        node.set("uuids", uuidsArray);
        return node;
    }

    /**
     * Converts an entity version to JSON based on its type.
     */
    private ObjectNode versionToJson(EntityVersion version, Entity<EntityVersion> entity) {
        ObjectNode versionNode = objectMapper.createObjectNode();
        versionNode.put("stampNid", version.stampNid());

        switch (entity) {
            case ConceptEntity conceptEntity -> {
                if (version instanceof ConceptEntityVersion conceptVersion) {
                    versionNode.put("versionType", "ConceptVersion");
                }
            }
            case SemanticEntity semanticEntity -> {
                if (version instanceof SemanticEntityVersion semanticVersion) {
                    versionNode.put("versionType", "SemanticVersion");
                    versionNode.put("patternNid", semanticEntity.patternNid());
                    versionNode.put("referencedComponentNid", semanticEntity.referencedComponentNid());

                    // Add fields
                    ArrayNode fieldsArray = objectMapper.createArrayNode();
                    for (Object field : semanticVersion.fieldValues()) {
                        fieldsArray.add(fieldToJson(field));
                    }
                    versionNode.set("fields", fieldsArray);
                }
            }
            case PatternEntity patternEntity -> {
                if (version instanceof PatternEntityVersion patternVersion) {
                    versionNode.put("versionType", "PatternVersion");
                    versionNode.put("semanticPurposeNid", patternVersion.semanticPurposeNid());
                    versionNode.put("semanticMeaningNid", patternVersion.semanticMeaningNid());

                    // Add field definitions
                    ArrayNode fieldDefsArray = objectMapper.createArrayNode();
                    patternVersion.fieldDefinitions().forEach(fieldDef -> {
                        ObjectNode fieldDefNode = objectMapper.createObjectNode();
                        fieldDefNode.put("meaningNid", fieldDef.meaningNid());
                        fieldDefNode.put("dataTypeNid", fieldDef.dataTypeNid());
                        fieldDefNode.put("purposeNid", fieldDef.purposeNid());
                        fieldDefsArray.add(fieldDefNode);
                    });
                    versionNode.set("fieldDefinitions", fieldDefsArray);
                }
            }
            case StampEntity stampEntity -> {
                if (version instanceof StampVersionRecord stampVersion) {
                    versionNode.put("versionType", "StampVersion");
                    versionNode.put("stateNid", stampVersion.stateNid());
                    versionNode.put("time", stampVersion.time());
                    versionNode.put("authorNid", stampVersion.authorNid());
                    versionNode.put("moduleNid", stampVersion.moduleNid());
                    versionNode.put("pathNid", stampVersion.pathNid());
                }
            }
            default -> {
                versionNode.put("versionType", "Unknown");
            }
        }

        return versionNode;
    }

    /**
     * Converts a field value to JSON representation.
     */
    private ObjectNode fieldToJson(Object field) {
        ObjectNode fieldNode = objectMapper.createObjectNode();

        if (field == null) {
            fieldNode.putNull("value");
            fieldNode.put("type", "null");
        } else if (field instanceof String) {
            fieldNode.put("value", (String) field);
            fieldNode.put("type", "String");
        } else if (field instanceof Integer) {
            fieldNode.put("value", (Integer) field);
            fieldNode.put("type", "Integer");
        } else if (field instanceof Long) {
            fieldNode.put("value", (Long) field);
            fieldNode.put("type", "Long");
        } else if (field instanceof Float) {
            fieldNode.put("value", (Float) field);
            fieldNode.put("type", "Float");
        } else if (field instanceof Double) {
            fieldNode.put("value", (Double) field);
            fieldNode.put("type", "Double");
        } else if (field instanceof Boolean) {
            fieldNode.put("value", (Boolean) field);
            fieldNode.put("type", "Boolean");
        } else {
            fieldNode.put("value", field.toString());
            fieldNode.put("type", field.getClass().getSimpleName());
        }

        return fieldNode;
    }

    /**
     * Gets the entity type as a string.
     */
    private String getEntityType(Entity<?> entity) {
        return switch (entity) {
            case ConceptEntity _ -> "Concept";
            case SemanticEntity _ -> "Semantic";
            case PatternEntity _ -> "Pattern";
            case StampEntity _ -> "Stamp";
            default -> "Unknown";
        };
    }
}

