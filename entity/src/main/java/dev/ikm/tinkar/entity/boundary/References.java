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
package dev.ikm.tinkar.entity.boundary;

import dev.ikm.tinkar.common.id.LongIdCollection;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.FieldDefinitionForEntity;
import dev.ikm.tinkar.entity.PatternEntityVersion;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.graph.DiGraphAbstract;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.terms.EntityFacade;

import java.util.List;

/**
 * Every place a version carries a nid (IKE-Network/ike-issues#1313): a semantic's pattern and
 * referenced component; field values typed as components, component id lists and sets, including
 * inside object arrays; vertex meanings, property keys and property values in DiGraph, DiTree and
 * vertex fields; and a pattern version's meaning, purpose and field definitions. The version's
 * own STAMP is not a reference. A nid stored as a plain {@code long} cannot be told from a number
 * and is not seen: the boundary forbids storing one.
 */
public final class References {

    /** Where a semantic's pattern is found: what the pattern index holds. */
    public static final String PATTERN = "pattern";

    /** Where a semantic's referenced component is found: what the referencing index holds. */
    public static final String REFERENCED_COMPONENT = "referenced component";

    /** Receives one reference: where it was found, and the nid it leads to. */
    @FunctionalInterface
    public interface Sink {
        void accept(String where, long targetNid);
    }

    private References() {
    }

    /**
     * Visits every reference a version carries.
     *
     * @param version the version
     * @param sink    receives each reference
     */
    public static void of(EntityVersion version, Sink sink) {
        switch (version) {
            case SemanticEntityVersion semantic -> {
                SemanticEntity<?> chronology = semantic.chronology();
                sink.accept(PATTERN, chronology.patternNid());
                sink.accept(REFERENCED_COMPONENT, chronology.referencedComponentNid());
                List<Object> values = semantic.fieldValues().castToList();
                for (int i = 0; i < values.size(); i++) {
                    String where = "field " + (i + 1);
                    walk(values.get(i), target -> sink.accept(where, target));
                }
            }
            case PatternEntityVersion pattern -> {
                sink.accept("pattern meaning", pattern.semanticMeaningNid());
                sink.accept("pattern purpose", pattern.semanticPurposeNid());
                int i = 0;
                for (FieldDefinitionForEntity field : pattern.fieldDefinitions()) {
                    i++;
                    sink.accept("field " + i + " meaning", field.meaningNid());
                    sink.accept("field " + i + " purpose", field.purposeNid());
                    sink.accept("field " + i + " data type", field.dataTypeNid());
                }
            }
            default -> { }
        }
    }

    private interface Target {
        void accept(long nid);
    }

    private static void walk(Object value, Target target) {
        switch (value) {
            case null -> { }
            case EntityFacade facade -> target.accept(facade.nid());
            case LongIdCollection ids -> {
                for (long id : ids.toArray()) {
                    target.accept(id);
                }
            }
            case DiGraphAbstract<?> graph -> {
                for (Object vertex : graph.vertexMap()) {
                    walk(vertex, target);
                }
            }
            case EntityVertex vertex -> {
                target.accept(vertex.getMeaningNid());
                vertex.properties().forEachKeyValue((key, property) -> {
                    target.accept(key);
                    walk(property, target);
                });
            }
            case Object[] array -> {
                for (Object element : array) {
                    walk(element, target);
                }
            }
            default -> { }
        }
    }
}
