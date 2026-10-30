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
package dev.ikm.tinkar.provider.search;

import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.FieldDefinitionForEntity;
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.entity.PatternEntityVersion;
import dev.ikm.tinkar.terms.KernelConcept;
import org.eclipse.collections.api.factory.primitive.LongSets;
import org.eclipse.collections.api.set.primitive.ImmutableLongSet;

import java.util.Optional;

/**
 * Which patterns' semantics the text index holds documents for: those with a field whose data
 * type may hold text, in any version of the pattern. Every other pattern's semantics (navigation,
 * logical definitions, memberships, identifiers held as components) have no document, so a store
 * passes them by before the index buffers a delete for them or a load phase counts them.
 *
 * <p>A pattern is declined only when every field of every version has a data type known to hold
 * no text; a data type not known here is taken to hold text, so a new one costs an index its
 * speed, never its content. The decision is made once per pattern and kept in two immutable sets,
 * replaced whole when a pattern is decided or forgotten: a store asks on every write, from many
 * threads, and reads them without a lock or a boxed key. Patterns are few, so replacing a set is
 * cheap.
 */
final class TextPatterns {

    /** Patterns with a field that may hold text. */
    private volatile ImmutableLongSet withText = LongSets.immutable.empty();
    /** Patterns whose every field holds something other than text. */
    private volatile ImmutableLongSet withoutText = LongSets.immutable.empty();
    /** The field data types known to hold no text; read once the kernel concepts resolve. */
    private volatile ImmutableLongSet textFreeDataTypes;

    /**
     * Whether the index holds a document for semantics of the pattern.
     *
     * @param patternNid the pattern's nid
     * @return {@code false} only when the pattern is known and no field of it may hold text
     */
    boolean indexes(long patternNid) {
        if (withText.contains(patternNid)) {
            return true;
        }
        if (withoutText.contains(patternNid)) {
            return false;
        }
        return decide(patternNid);
    }

    /**
     * Forgets the decision for a pattern, so its next semantic decides again.
     *
     * @param patternNid the pattern's nid
     */
    synchronized void forget(long patternNid) {
        withText = withText.newWithout(patternNid);
        withoutText = withoutText.newWithout(patternNid);
    }

    private synchronized boolean decide(long patternNid) {
        if (withText.contains(patternNid)) {
            return true;
        }
        if (withoutText.contains(patternNid)) {
            return false;
        }
        Optional<PatternEntity> pattern = EntityHandle.get(patternNid).asPattern();
        if (pattern.isEmpty()) {
            // Not written yet, as when a parallel load writes a semantic before its pattern:
            // index it, and decide once the pattern is there.
            return true;
        }
        boolean text = mayHoldText(pattern.get());
        if (text) {
            withText = withText.newWith(patternNid);
        } else {
            withoutText = withoutText.newWith(patternNid);
        }
        return text;
    }

    private boolean mayHoldText(PatternEntity<? extends PatternEntityVersion> pattern) {
        ImmutableLongSet textFree = textFreeDataTypes();
        for (PatternEntityVersion version : pattern.versions()) {
            for (FieldDefinitionForEntity field : version.fieldDefinitions()) {
                if (!textFree.contains(field.dataTypeNid())) {
                    return true;
                }
            }
        }
        return false;
    }

    private ImmutableLongSet textFreeDataTypes() {
        ImmutableLongSet types = textFreeDataTypes;
        if (types == null) {
            types = LongSets.immutable.of(
                    KernelConcept.COMPONENT_FIELD.nid(),
                    KernelConcept.CONCEPT_FIELD.nid(),
                    KernelConcept.COMPONENT_ID_LIST_FIELD.nid(),
                    KernelConcept.COMPONENT_ID_SET_FIELD.nid(),
                    KernelConcept.DITREE_FIELD.nid(),
                    KernelConcept.DIGRAPH_FIELD.nid(),
                    KernelConcept.VERTEX_FIELD.nid(),
                    KernelConcept.BOOLEAN_FIELD.nid(),
                    KernelConcept.INTEGER_FIELD.nid(),
                    KernelConcept.LONG.nid(),
                    KernelConcept.FLOAT_FIELD.nid(),
                    KernelConcept.DECIMAL_FIELD.nid(),
                    KernelConcept.BYTE_ARRAY_FIELD.nid(),
                    KernelConcept.INSTANT_LITERAL.nid());
            textFreeDataTypes = types;
        }
        return types;
    }
}
