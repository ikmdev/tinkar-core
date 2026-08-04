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
import dev.ikm.tinkar.terms.ConceptFacade;

/**
 * Ledger-form authoring of a pattern's <em>template semantic</em> for a purpose
 * (IKE-Network/ike-issues#885) — the two-key shape of the decided design: a purpose
 * concept may host templates of more than one pattern, so the (pattern, purpose) pair —
 * not the purpose alone — identifies a template, exactly matching
 * {@code StampCalculator.getTemplate(pattern, purpose)}.
 * <p>
 * The writer rules are enforced as in {@link FieldDefaultsBuilder}: identity is the
 * computed {@code UuidT5Generator.singleSemanticUuid(pattern, purpose)}, never named;
 * the referenced component is fixed to the purpose concept; the tuple is complete and
 * typed against the pattern's declared field definitions; and every scope validates the
 * declared stamp's module against the Defaults and templates module — the category's
 * live-and-die invariant (validation, not substitution — KEC, 2026-07-18).
 * <p>
 * Obtained from {@link KnowledgeSet#template(String, String)} (purpose by birth FQN,
 * typically minted with {@link KnowledgeSet#templatePurpose}) or
 * {@link KnowledgeSet#template(String, ConceptFacade)} (a purpose whose identity was
 * established elsewhere). The pattern must be declared in the same knowledge set,
 * because compose-time conformance reads its declared field definitions.
 *
 * <pre>{@code
 * IKE.template("GB dialect pattern (IkeFoundation)", "Dialect authoring template (IkeFoundation)")
 *     .at(defaultsStamp)                       // must declare the Defaults module
 *     .values(IkeTerm.PREFERRED);              // one value per declared field, typed
 * }</pre>
 */
public final class TemplateBuilder {

    private final PatternBuilder patternBuilder;
    private final ConceptFacade purpose;

    TemplateBuilder(PatternBuilder patternBuilder, ConceptFacade purpose) {
        this.patternBuilder = patternBuilder;
        this.purpose = purpose;
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
        SupportContentGate.requireDefaultsModule(stamp, "template");
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
        SupportContentGate.requireDefaultsModule(stamp, "template");
        return new RetireScope(stamp);
    }

    private PublicId computedIdentity() {
        return PublicIds.of(UuidT5Generator.singleSemanticUuid(
                patternBuilder.publicId(), purpose.publicId()));
    }

    /** The content verbs available under an {@link ActiveStamp}. */
    public final class ActiveScope {
        private final ActiveStamp stamp;

        private ActiveScope(ActiveStamp stamp) {
            this.stamp = stamp;
        }

        /**
         * States the template's values for this (pattern, purpose) pair: a version of
         * the computed-identity template semantic carrying one value per declared field,
         * in declared order — the compose forms of
         * {@link ConceptBuilder.ActiveScope#semantic}. Restating in a later scope
         * appends a version of the same chronology; per-user variants are per-path
         * versions of this same computed identity, per the decided design.
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
        public TemplateBuilder values(Object... fieldValues) {
            requireShape();
            SupportContentGate.requireCompleteTuple(patternBuilder.ref().description(),
                    patternBuilder.currentFieldDataTypes(), fieldValues, "the template tuple");
            patternBuilder.ledger().addGenericVersion(patternBuilder.ref(), computedIdentity(),
                    purpose.publicId(), stamp, fieldValues);
            return TemplateBuilder.this;
        }
    }

    /** The retirement verbs available under an {@link InactiveStamp}. */
    public final class RetireScope {
        private final InactiveStamp stamp;

        private RetireScope(InactiveStamp stamp) {
            this.stamp = stamp;
        }

        /**
         * Retires the template semantic for this (pattern, purpose) pair: an inactive
         * version of the same computed-identity chronology, the prior version's fields
         * restated.
         *
         * @return the builder, for a later scope
         * @throws IllegalArgumentException if no template tuple was stated in this
         *                                  session — retirement follows declaration, as
         *                                  everywhere in the ledger DSL — or this stamp
         *                                  already carries a version of the semantic
         */
        public TemplateBuilder retire() {
            patternBuilder.ledger().retireGenericVersion(patternBuilder.ref(), computedIdentity(),
                    stamp, new Object[0]);
            return TemplateBuilder.this;
        }
    }

    private void requireShape() {
        if (!patternBuilder.shapeDeclared()) {
            throw new IllegalStateException("template for " + patternBuilder.ref().description()
                    + " requires the pattern's shape (meaning, purpose, fields) to be declared first"
                    + " — compose-time conformance reads the declared field definitions");
        }
    }
}
