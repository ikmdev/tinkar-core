package dev.ikm.tinkar.provider.changeset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.ikm.tinkar.schema.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Transforms TinkarMsg protobuf objects directly to JSON format without requiring EntityService.
 * This is a standalone converter that works independently of any database or entity provider.
 */
public class TinkarMsgToJsonTransformer {
    private static final Logger LOG = LoggerFactory.getLogger(TinkarMsgToJsonTransformer.class);
    private static final TinkarMsgToJsonTransformer INSTANCE = new TinkarMsgToJsonTransformer();

    private final ObjectMapper objectMapper;

    private TinkarMsgToJsonTransformer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public static TinkarMsgToJsonTransformer getInstance() {
        return INSTANCE;
    }

    /**
     * Transforms a TinkarMsg to JSON and writes it to the buffered writer.
     *
     * @param tinkarMsg the protobuf message to transform
     * @param writer    the buffered writer to write JSON to
     * @throws IOException if writing fails
     */
    public void writeMessage(TinkarMsg tinkarMsg, BufferedWriter writer) throws IOException {
        ObjectNode jsonNode = transformToJson(tinkarMsg);
        if (jsonNode != null) {
            writer.write(objectMapper.writeValueAsString(jsonNode));
            writer.write('\n'); // Add newline separator between entities
        }
    }

    /**
     * Transforms a TinkarMsg to a JSON ObjectNode.
     *
     * @param tinkarMsg the protobuf message to transform
     * @return the JSON representation, or null if message type is unknown
     */
    private ObjectNode transformToJson(TinkarMsg tinkarMsg) {
        return switch (tinkarMsg.getValueCase()) {
            case CONCEPT_CHRONOLOGY -> conceptChronologyToJson(tinkarMsg.getConceptChronology());
            case SEMANTIC_CHRONOLOGY -> semanticChronologyToJson(tinkarMsg.getSemanticChronology());
            case PATTERN_CHRONOLOGY -> patternChronologyToJson(tinkarMsg.getPatternChronology());
            case STAMP_CHRONOLOGY -> stampChronologyToJson(tinkarMsg.getStampChronology());
            case VALUE_NOT_SET -> {
                LOG.warn("Encountered TinkarMsg with VALUE_NOT_SET");
                yield null;
            }
        };
    }

    /**
     * Converts a ConceptChronology to JSON.
     */
    private ObjectNode conceptChronologyToJson(ConceptChronology conceptChronology) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("entityType", "Concept");
        root.set("publicId", publicIdToJson(conceptChronology.getPublicId()));

        ArrayNode versionsArray = objectMapper.createArrayNode();
        for (ConceptVersion version : conceptChronology.getConceptVersionsList()) {
            ObjectNode versionNode = objectMapper.createObjectNode();
            versionNode.put("versionType", "ConceptVersion");
            versionNode.set("stampPublicId", publicIdToJson(version.getStampChronologyPublicId()));
            versionsArray.add(versionNode);
        }
        root.set("versions", versionsArray);

        return root;
    }

    /**
     * Converts a SemanticChronology to JSON.
     */
    private ObjectNode semanticChronologyToJson(SemanticChronology semanticChronology) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("entityType", "Semantic");
        root.set("publicId", publicIdToJson(semanticChronology.getPublicId()));
        root.set("referencedComponentPublicId", publicIdToJson(semanticChronology.getReferencedComponentPublicId()));
        root.set("patternForSemanticPublicId", publicIdToJson(semanticChronology.getPatternForSemanticPublicId()));

        ArrayNode versionsArray = objectMapper.createArrayNode();
        for (SemanticVersion version : semanticChronology.getSemanticVersionsList()) {
            ObjectNode versionNode = objectMapper.createObjectNode();
            versionNode.put("versionType", "SemanticVersion");
            versionNode.set("stampPublicId", publicIdToJson(version.getStampChronologyPublicId()));

            // Add fields
            ArrayNode fieldsArray = objectMapper.createArrayNode();
            for (Field field : version.getFieldsList()) {
                fieldsArray.add(fieldToJson(field));
            }
            versionNode.set("fields", fieldsArray);

            versionsArray.add(versionNode);
        }
        root.set("versions", versionsArray);

        return root;
    }

    /**
     * Converts a PatternChronology to JSON.
     */
    private ObjectNode patternChronologyToJson(PatternChronology patternChronology) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("entityType", "Pattern");
        root.set("publicId", publicIdToJson(patternChronology.getPublicId()));

        ArrayNode versionsArray = objectMapper.createArrayNode();
        for (PatternVersion version : patternChronology.getPatternVersionsList()) {
            ObjectNode versionNode = objectMapper.createObjectNode();
            versionNode.put("versionType", "PatternVersion");
            versionNode.set("stampPublicId", publicIdToJson(version.getStampChronologyPublicId()));
            versionNode.set("semanticPurposePublicId", publicIdToJson(version.getReferencedComponentPurposePublicId()));
            versionNode.set("semanticMeaningPublicId", publicIdToJson(version.getReferencedComponentMeaningPublicId()));

            // Add field definitions
            ArrayNode fieldDefsArray = objectMapper.createArrayNode();
            for (FieldDefinition fieldDef : version.getFieldDefinitionsList()) {
                ObjectNode fieldDefNode = objectMapper.createObjectNode();
                fieldDefNode.set("meaningPublicId", publicIdToJson(fieldDef.getMeaningPublicId()));
                fieldDefNode.set("dataTypePublicId", publicIdToJson(fieldDef.getDataTypePublicId()));
                fieldDefNode.set("purposePublicId", publicIdToJson(fieldDef.getPurposePublicId()));
                fieldDefsArray.add(fieldDefNode);
            }
            versionNode.set("fieldDefinitions", fieldDefsArray);

            versionsArray.add(versionNode);
        }
        root.set("versions", versionsArray);

        return root;
    }

    /**
     * Converts a StampChronology to JSON.
     */
    private ObjectNode stampChronologyToJson(StampChronology stampChronology) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("entityType", "Stamp");
        root.set("publicId", publicIdToJson(stampChronology.getPublicId()));

        ArrayNode versionsArray = objectMapper.createArrayNode();

        // StampChronology uses getFirstStampVersion and getSecondStampVersion
        List<StampVersion> stampVersions = new ArrayList<>();
        if (stampChronology.hasFirstStampVersion()) {
            stampVersions.add(stampChronology.getFirstStampVersion());
        }
        if (stampChronology.hasSecondStampVersion()) {
            stampVersions.add(stampChronology.getSecondStampVersion());
        }

        for (StampVersion version : stampVersions) {
            ObjectNode versionNode = objectMapper.createObjectNode();
            versionNode.put("versionType", "StampVersion");
            versionNode.set("statusPublicId", publicIdToJson(version.getStatusPublicId()));
            versionNode.put("time", version.getTime());
            versionNode.set("authorPublicId", publicIdToJson(version.getAuthorPublicId()));
            versionNode.set("modulePublicId", publicIdToJson(version.getModulePublicId()));
            versionNode.set("pathPublicId", publicIdToJson(version.getPathPublicId()));
            versionsArray.add(versionNode);
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

        // PublicId stores UUIDs as strings, not bytes
        for (String uuidString : publicId.getUuidsList()) {
            uuidsArray.add(uuidString);
        }

        node.set("uuids", uuidsArray);
        return node;
    }

    /**
     * Converts a Field to JSON representation.
     */
    private ObjectNode fieldToJson(Field field) {
        ObjectNode fieldNode = objectMapper.createObjectNode();

        switch (field.getValueCase()) {
            case STRING_VALUE -> {
                fieldNode.put("value", field.getStringValue());
                fieldNode.put("type", "String");
            }
            case BOOLEAN_VALUE -> {
                fieldNode.put("value", field.getBooleanValue());
                fieldNode.put("type", "Boolean");
            }
            case BYTES_VALUE -> {
                fieldNode.put("value", field.getBytesValue().toStringUtf8());
                fieldNode.put("type", "Bytes");
            }
            case FLOAT_VALUE -> {
                fieldNode.put("value", field.getFloatValue());
                fieldNode.put("type", "Float");
            }
            case INT_VALUE -> {
                fieldNode.put("value", field.getIntValue());
                fieldNode.put("type", "Int");
            }
            case LONG -> {
                fieldNode.put("value", String.valueOf(field.getLong()));
                fieldNode.put("type", "Long");
            }
            case PUBLIC_ID -> {
                fieldNode.set("value", publicIdToJson(field.getPublicId()));
                fieldNode.put("type", "PublicId");
            }
            case TIME_VALUE -> {
                fieldNode.put("value", field.getTimeValue());
                fieldNode.put("type", "Time");
            }
            case PLANAR_POINT -> {
                PlanarPoint point = field.getPlanarPoint();
                fieldNode.put("value", "(" + point.getX() + ", " + point.getY() + ")");
                fieldNode.put("type", "PlanarPoint");
            }
            case SPATIAL_POINT -> {
                SpatialPoint point = field.getSpatialPoint();
                fieldNode.put("value", "(" + point.getX() + ", " + point.getY() + ", " + point.getZ() + ")");
                fieldNode.put("type", "SpatialPoint");
            }
            case DI_GRAPH -> {
                fieldNode.put("value", digraphToString(field.getDiGraph()));
                fieldNode.put("type", "DiGraph");
            }
            case DI_TREE -> {
                fieldNode.put("value", ditreeToString(field.getDiTree()));
                fieldNode.put("type", "DiTree");
            }
            case VERTEX -> {
                fieldNode.put("value", vertexToString(field.getVertex()));
                fieldNode.put("type", "Vertex");
            }
            case VERTEX_UUID -> {
                fieldNode.put("value", field.getVertexUuid().getUuid());
                fieldNode.put("type", "VertexUUID");
            }
            case PUBLIC_IDS -> {
                ArrayNode publicIdArray = objectMapper.createArrayNode();
                for (PublicId publicId : field.getPublicIds().getPublicIdsList()) {
                    publicIdArray.add(publicIdToJson(publicId));
                }
                fieldNode.set("value", publicIdArray);
                fieldNode.put("type", "PublicIdList");
            }
            case PUBLIC_IDSET -> {
                ArrayNode publicIdArray = objectMapper.createArrayNode();
                for (PublicId publicId : field.getPublicIdset().getPublicIdsList()) {
                    publicIdArray.add(publicIdToJson(publicId));
                }
                fieldNode.set("value", publicIdArray);
                fieldNode.put("type", "PublicIdSet");
            }
            case INT_TO_INT_MAP -> {
                ObjectNode mapNode = objectMapper.createObjectNode();
                IntToIntMap intMap = field.getIntToIntMap();
                mapNode.put("source", intMap.getSource());
                mapNode.put("target", intMap.getTarget());
                fieldNode.set("value", mapNode);
                fieldNode.put("type", "IntToIntMap");
            }
            case INT_TO_MULTIPLE_INT_MAP -> {
                ObjectNode mapNode = objectMapper.createObjectNode();
                IntToMultipleIntMap multiMap = field.getIntToMultipleIntMap();
                mapNode.put("source", multiMap.getSource());
                ArrayNode targetsArray = objectMapper.createArrayNode();
                for (int target : multiMap.getTargetsList()) {
                    targetsArray.add(target);
                }
                mapNode.set("targets", targetsArray);
                fieldNode.set("value", mapNode);
                fieldNode.put("type", "IntToMultipleIntMap");
            }
            case BIG_DECIMAL -> {
                dev.ikm.tinkar.schema.BigDecimal bd = field.getBigDecimal();
                ObjectNode bdNode = objectMapper.createObjectNode();
                bdNode.put("value", bd.getValue());
                bdNode.put("scale", bd.getScale());
                bdNode.put("precision", bd.getPrecision());
                fieldNode.set("value", bdNode);
                fieldNode.put("type", "BigDecimal");
            }
            case GRAPH -> {
                fieldNode.put("value", "Graph[not fully supported]");
                fieldNode.put("type", "Graph");
            }
            case VALUE_NOT_SET -> {
                fieldNode.putNull("value");
                fieldNode.put("type", "NotSet");
            }
        }

        return fieldNode;
    }

    /**
     * Converts a DiGraph to a string representation.
     */
    private String digraphToString(DiGraph digraph) {
        return "DiGraph[vertices=" + digraph.getVerticesCount() +
               ", successors=" + digraph.getSuccessorMapCount() +
               ", predecessors=" + digraph.getPredecessorMapCount() + "]";
    }

    /**
     * Converts a DiTree to a string representation.
     */
    private String ditreeToString(DiTree ditree) {
        return "DiTree[vertices=" + ditree.getVerticesCount() +
               ", successors=" + ditree.getSuccessorMapCount() +
               ", predecessors=" + ditree.getPredecessorMapCount() + "]";
    }

    /**
     * Converts a Vertex to a string representation.
     */
    private String vertexToString(Vertex vertex) {
        return "Vertex[" + vertex.getVertexUuid().getUuid() +
               ", index=" + vertex.getIndex() +
               ", meaning=" + publicIdToJson(vertex.getMeaningPublicId()) + "]";
    }
}

