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
package dev.ikm.tinkar.fixtures;

import dev.ikm.tinkar.common.id.IntIdList;
import dev.ikm.tinkar.common.id.IntIdSet;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.FieldDefinitionForEntity;
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.entity.PatternEntityVersion;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampEntityVersion;
import dev.ikm.tinkar.entity.graph.DiGraphEntity;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.terms.EntityFacade;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

/**
 * What a store holds, in a form two stores can be compared by: how many entities of each
 * kind, how many semantics of each pattern, how many versions, and one hash over every
 * entity.
 *
 * <p>The hash does not depend on the order entities are visited in, on the order of an
 * entity's versions, on the order of an id set's members, or on any nid: an entity is
 * rendered as text that names components by their public ids (sorted UUIDs), the SHA-256
 * of each rendering is taken, and the hashes are added. Two stores with the same digest
 * hold the same entities, versions and field values, whatever provider or nid layout each
 * uses, which is what an export and import, a migration, or a change of nid width must
 * preserve.
 *
 * <p>A directed graph that is not a tree is rendered as the multiset of its vertices,
 * without its edges; a field value of a type not known here is rendered as its class name
 * and counted in {@link #unrendered()}, which a comparison should expect to be zero.
 *
 * @param semanticsByPattern semantic count for each pattern, keyed by the pattern's public id
 */
public record StoreDigest(long concepts, long patterns, long stamps, long semantics, long versions,
                          SortedMap<String, Long> semanticsByPattern, long unrendered, String hash) {

    /** The number of entities of every kind. */
    public long entities() {
        return concepts + patterns + stamps + semantics;
    }

    /** The digest of the store that is open in this JVM. */
    public static StoreDigest ofOpenStore() {
        Accumulator accumulator = new Accumulator();
        EntityService.get().forEachEntity(accumulator::add);
        return accumulator.digest();
    }

    /**
     * A component named the way the digest names it: its public id as sorted UUIDs, the
     * same in every store.
     */
    public static String ids(int nid) {
        return Accumulator.ids(nid);
    }

    /**
     * A field value rendered the way the digest renders it: components by public id, sets
     * sorted, a tree by its vertices' meanings and properties with children sorted. For
     * digests of things that are not stores, such as a reasoner's results.
     */
    public static String render(Object value) {
        return new Accumulator().value(value);
    }

    /** The digest as properties whose keys start with the prefix, to hand from one JVM to another. */
    public void store(Properties properties, String prefix) {
        properties.setProperty(prefix + "concepts", Long.toString(concepts));
        properties.setProperty(prefix + "patterns", Long.toString(patterns));
        properties.setProperty(prefix + "stamps", Long.toString(stamps));
        properties.setProperty(prefix + "semantics", Long.toString(semantics));
        properties.setProperty(prefix + "versions", Long.toString(versions));
        properties.setProperty(prefix + "unrendered", Long.toString(unrendered));
        properties.setProperty(prefix + "hash", hash);
        semanticsByPattern.forEach((pattern, count) ->
                properties.setProperty(prefix + "pattern." + pattern, Long.toString(count)));
    }

    /** The digest {@link #store} wrote under the prefix. */
    public static StoreDigest load(Properties properties, String prefix) {
        SortedMap<String, Long> byPattern = new TreeMap<>();
        String patternPrefix = prefix + "pattern.";
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(patternPrefix)) {
                byPattern.put(key.substring(patternPrefix.length()), Long.parseLong(properties.getProperty(key)));
            }
        }
        return new StoreDigest(
                Long.parseLong(properties.getProperty(prefix + "concepts")),
                Long.parseLong(properties.getProperty(prefix + "patterns")),
                Long.parseLong(properties.getProperty(prefix + "stamps")),
                Long.parseLong(properties.getProperty(prefix + "semantics")),
                Long.parseLong(properties.getProperty(prefix + "versions")),
                byPattern,
                Long.parseLong(properties.getProperty(prefix + "unrendered")),
                properties.getProperty(prefix + "hash"));
    }

    /**
     * Where this digest and another differ, one line each, for an assertion message; empty
     * when they are equal.
     */
    public List<String> differencesFrom(StoreDigest other) {
        List<String> differences = new ArrayList<>();
        difference(differences, "concepts", concepts, other.concepts);
        difference(differences, "patterns", patterns, other.patterns);
        difference(differences, "stamps", stamps, other.stamps);
        difference(differences, "semantics", semantics, other.semantics);
        difference(differences, "versions", versions, other.versions);
        SortedMap<String, Long> all = new TreeMap<>(semanticsByPattern);
        other.semanticsByPattern.forEach(all::putIfAbsent);
        for (String pattern : all.keySet()) {
            difference(differences, "semantics of pattern " + pattern,
                    semanticsByPattern.getOrDefault(pattern, 0L), other.semanticsByPattern.getOrDefault(pattern, 0L));
        }
        if (differences.isEmpty() && !hash.equals(other.hash)) {
            differences.add("same counts, different content: hash " + hash + " and " + other.hash);
        }
        return differences;
    }

    private static void difference(List<String> differences, String what, long mine, long theirs) {
        if (mine != theirs) {
            differences.add(what + ": " + mine + " and " + theirs);
        }
    }

    private static final class Accumulator {
        private long concepts, patterns, stamps, semantics, versions, unrendered;
        private long hashHigh, hashLow;
        private final SortedMap<String, Long> semanticsByPattern = new TreeMap<>();
        private final MessageDigest sha256 = sha256();

        void add(Entity<?> entity) {
            StringBuilder text = new StringBuilder();
            switch (entity) {
                case ConceptEntity<?> concept -> {
                    concepts++;
                    text.append("concept ").append(ids(concept.publicId()));
                }
                case PatternEntity<?> pattern -> {
                    patterns++;
                    text.append("pattern ").append(ids(pattern.publicId()));
                }
                case StampEntity<?> stamp -> {
                    stamps++;
                    text.append("stamp ").append(ids(stamp.publicId()));
                }
                case SemanticEntity<?> semantic -> {
                    semantics++;
                    String pattern = ids(semantic.patternNid());
                    semanticsByPattern.merge(pattern, 1L, Long::sum);
                    text.append("semantic ").append(ids(semantic.publicId()))
                            .append(" of ").append(pattern)
                            .append(" for ").append(ids(semantic.referencedComponentNid()));
                }
                default -> text.append(entity.getClass().getSimpleName()).append(' ').append(ids(entity.publicId()));
            }
            List<String> versionTexts = new ArrayList<>();
            for (EntityVersion version : entity.versions()) {
                versions++;
                versionTexts.add(version(version));
            }
            versionTexts.sort(null);
            text.append(" versions ").append(versionTexts);

            byte[] hash = sha256.digest(text.toString().getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.wrap(hash);
            hashHigh += buffer.getLong();
            hashLow += buffer.getLong();
        }

        private String version(EntityVersion version) {
            StringBuilder text = new StringBuilder();
            switch (version) {
                case StampEntityVersion stamp -> text.append("state ").append(ids(stamp.stateNid()))
                        .append(" time ").append(stamp.time())
                        .append(" author ").append(ids(stamp.authorNid()))
                        .append(" module ").append(ids(stamp.moduleNid()))
                        .append(" path ").append(ids(stamp.pathNid()));
                case PatternEntityVersion pattern -> {
                    text.append("at ").append(ids(version.stamp().publicId()))
                            .append(" meaning ").append(ids(pattern.semanticMeaningNid()))
                            .append(" purpose ").append(ids(pattern.semanticPurposeNid()))
                            .append(" fields [");
                    for (FieldDefinitionForEntity field : pattern.fieldDefinitions()) {
                        text.append("(").append(ids(field.meaningNid())).append(' ')
                                .append(ids(field.purposeNid())).append(' ')
                                .append(ids(field.dataTypeNid())).append(")");
                    }
                    text.append("]");
                }
                case SemanticEntityVersion semantic -> {
                    text.append("at ").append(ids(version.stamp().publicId())).append(" values [");
                    for (Object value : semantic.fieldValues()) {
                        text.append(value(value)).append("; ");
                    }
                    text.append("]");
                }
                default -> text.append("at ").append(ids(version.stamp().publicId()));
            }
            return text.toString();
        }

        private String value(Object value) {
            return switch (value) {
                case null -> "null";
                case String string -> "S:" + string;
                case Boolean bool -> "Z:" + bool;
                case Number number -> number.getClass().getSimpleName() + ":" + number;
                case java.time.Instant instant -> "T:" + instant;
                case byte[] bytes -> "B:" + bytes.length + ":" + Arrays.hashCode(bytes);
                case IntIdSet set -> {
                    List<String> members = new ArrayList<>();
                    set.intStream().forEach(nid -> members.add(ids(nid)));
                    members.sort(null);
                    yield "SET" + members;
                }
                case IntIdList list -> {
                    List<String> members = new ArrayList<>();
                    list.intStream().forEach(nid -> members.add(ids(nid)));
                    yield "LIST" + members;
                }
                case DiTreeEntity tree -> "TREE " + vertex(tree, tree.root());
                case DiGraphEntity<?> graph -> {
                    List<String> vertices = new ArrayList<>();
                    for (EntityVertex vertex : graph.vertexMap()) {
                        vertices.add(vertexAlone(vertex));
                    }
                    vertices.sort(null);
                    yield "GRAPH" + vertices;
                }
                case EntityVertex vertex -> "VERTEX " + vertexAlone(vertex);
                case Object[] array -> {
                    List<String> elements = new ArrayList<>();
                    for (Object element : array) {
                        elements.add(value(element));
                    }
                    yield "ARRAY" + elements;
                }
                case EntityFacade facade -> ids(facade.publicId());
                case PublicId publicId -> ids(publicId);
                default -> {
                    unrendered++;
                    yield "?" + value.getClass().getName();
                }
            };
        }

        private String vertex(DiTreeEntity tree, EntityVertex vertex) {
            List<String> children = new ArrayList<>();
            for (EntityVertex child : tree.successors(vertex)) {
                children.add(vertex(tree, child));
            }
            children.sort(null);
            return vertexAlone(vertex) + children;
        }

        private String vertexAlone(EntityVertex vertex) {
            SortedMap<String, String> properties = new TreeMap<>();
            vertex.properties().forEachKeyValue((key, propertyValue) -> properties.put(ids(key), value(propertyValue)));
            StringBuilder text = new StringBuilder("(").append(ids(vertex.meaning().publicId()));
            for (Map.Entry<String, String> property : properties.entrySet()) {
                text.append(' ').append(property.getKey()).append('=').append(property.getValue());
            }
            return text.append(")").toString();
        }

        private static String ids(int nid) {
            return ids(PrimitiveData.publicId(nid));
        }

        private static String ids(PublicId publicId) {
            UUID[] uuids = publicId.asUuidArray().clone();
            Arrays.sort(uuids);
            return Arrays.toString(uuids);
        }

        StoreDigest digest() {
            return new StoreDigest(concepts, patterns, stamps, semantics, versions, semanticsByPattern, unrendered,
                    "%016x%016x".formatted(hashHigh, hashLow));
        }

        private static MessageDigest sha256() {
            try {
                return MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
