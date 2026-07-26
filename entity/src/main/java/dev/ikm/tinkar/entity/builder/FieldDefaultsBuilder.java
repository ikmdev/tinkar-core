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
package dev.ikm.tinkar.entity.builder;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.terms.DefaultsTemplateTerm;

/**
 * Ledger-form authoring of a pattern's <em>default value semantic</em>
 * (IKE-Network/ike-issues#885) — the sugar verb that makes the decided design's writer
 * rules unexpressible to violate:
 * <ul>
 *   <li><b>Identity is computed, never named.</b> The semantic's identity is the stock
 *       single-semantic convention,
 *       {@code UuidT5Generator.singleSemanticUuid(pattern, DEFAULT_VALUE_CONCEPT)},
 *       derived internally — there is no identity literal to mistype, and replaying the
 *       ledger merges version-always onto the one chronology per pattern.</li>
 *   <li><b>Attachment is fixed.</b> The referenced component is
 *       {@link DefaultsTemplateTerm#DEFAULT_VALUE_CONCEPT}; the verb offers no way to
 *       state otherwise.</li>
 *   <li><b>The tuple is complete and typed.</b> {@link ActiveScope#values} states one
 *       value per declared field, in declared order, each checked at compose time
 *       against the field's declared data type (via the pattern's declaration in the
 *       same knowledge set) — a wrong-arity or wrong-typed tuple fails the build before
 *       anything touches a store.</li>
 *   <li><b>The module gate.</b> Every scope validates that its declared stamp is in the
 *       Defaults and templates module — the category's live-and-die invariant. The stamp
 *       itself stays fully declared (validation, not substitution — KEC, 2026-07-18).</li>
 * </ul>
 * Obtained from {@link KnowledgeSet#fieldDefaults(String)}; the pattern must be declared
 * in the same knowledge set, because compose-time conformance reads its declared field
 * definitions. Retirement follows the same discipline:
 * {@link #at(InactiveStamp)}.{@link RetireScope#retire() retire()} appends an inactive
 * version of the same computed-identity chronology, module-validated.
 *
 * <pre>{@code
 * IKE.fieldDefaults("Preferred reviewer pattern (IkeFoundation)")
 *     .at(defaultsStamp)                       // must declare the Defaults module
 *     .values(IkeTerm.GRETEL);                 // one value per declared field, typed
 * }</pre>
 */
public final class FieldDefaultsBuilder {

    private final PatternBuilder patternBuilder;

    FieldDefaultsBuilder(PatternBuilder patternBuilder) {
        this.patternBuilder = patternBuilder;
    }

    /**
     * Opens a content scope at the given active stamp, validating the module gate.
     *
     * @param stamp the declared active stamp — its module must be the Defaults and
     *              templates module
     * @return the content scope
     * @throws IllegalArgumentException if the stamp declares any other module — the
     *                                  live-and-die invariant would be violated at birth
     */
    public ActiveScope at(ActiveStamp stamp) {
        SupportContentGate.requireDefaultsModule(stamp, "fieldDefaults");
        return new ActiveScope(stamp);
    }

    /**
     * Opens a retirement scope at the given inactive stamp, validating the module gate —
     * retirement of support content is also a version, and the live-and-die invariant
     * covers every version.
     *
     * @param stamp the declared inactive stamp — its module must be the Defaults and
     *              templates module
     * @return the retirement scope
     * @throws IllegalArgumentException if the stamp declares any other module
     */
    public RetireScope at(InactiveStamp stamp) {
        SupportContentGate.requireDefaultsModule(stamp, "fieldDefaults");
        return new RetireScope(stamp);
    }

    private PublicId computedIdentity() {
        return PublicIds.of(UuidT5Generator.singleSemanticUuid(
                patternBuilder.publicId(), DefaultsTemplateTerm.DEFAULT_VALUE_CONCEPT.publicId()));
    }

    /** The content verbs available under an {@link ActiveStamp}. */
    public final class ActiveScope {
        private final ActiveStamp stamp;

        private ActiveScope(ActiveStamp stamp) {
            this.stamp = stamp;
        }

        /**
         * States the pattern's default values: a version of the computed-identity
         * default value semantic carrying one value per declared field, in declared
         * order. Values use the ledger DSL's compose forms (see
         * {@link ConceptBuilder.ActiveScope#semantic}): entity references as facades or
         * public ids, graphs as {@link GraphFieldValue} specs, id collections as
         * {@code PublicIdList}/{@code PublicIdSet}, scalars as their Java types.
         * Restating in a later scope appends a version of the same chronology.
         *
         * @param fieldValues the complete tuple, in the pattern's declared field order
         * @return the builder, for a later scope
         * @throws IllegalStateException    if the pattern has not declared its shape
         *                                  (meaning, purpose, fields) yet
         * @throws IllegalArgumentException if the tuple is incomplete, a value is null
         *                                  or does not conform to its field's declared
         *                                  data type, or this stamp already carries a
         *                                  version of the semantic
         */
        public FieldDefaultsBuilder values(Object... fieldValues) {
            requireShape();
            SupportContentGate.requireCompleteTuple(patternBuilder.ref().description(),
                    patternBuilder.currentFieldDataTypes(), fieldValues, "the default value tuple");
            patternBuilder.ledger().addGenericVersion(patternBuilder.ref(), computedIdentity(),
                    DefaultsTemplateTerm.DEFAULT_VALUE_CONCEPT.publicId(), stamp, fieldValues);
            return FieldDefaultsBuilder.this;
        }
    }

    /** The retirement verbs available under an {@link InactiveStamp}. */
    public final class RetireScope {
        private final InactiveStamp stamp;

        private RetireScope(InactiveStamp stamp) {
            this.stamp = stamp;
        }

        /**
         * Retires the pattern's default value semantic: an inactive version of the same
         * computed-identity chronology, the prior version's fields restated.
         *
         * @return the builder, for a later scope
         * @throws IllegalArgumentException if no default value tuple was stated in this
         *                                  session — retirement follows declaration, as
         *                                  everywhere in the ledger DSL — or this stamp
         *                                  already carries a version of the semantic
         */
        public FieldDefaultsBuilder retire() {
            patternBuilder.ledger().retireGenericVersion(patternBuilder.ref(), computedIdentity(),
                    stamp, new Object[0]);
            return FieldDefaultsBuilder.this;
        }
    }

    private void requireShape() {
        if (!patternBuilder.shapeDeclared()) {
            throw new IllegalStateException("fieldDefaults for " + patternBuilder.ref().description()
                    + " requires the pattern's shape (meaning, purpose, fields) to be declared first"
                    + " — compose-time conformance reads the declared field definitions");
        }
    }
}
