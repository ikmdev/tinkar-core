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

import dev.ikm.tinkar.terms.DefaultsTemplateTerm;

/**
 * Ledger-form minting of a <em>template purpose concept</em>
 * (IKE-Network/ike-issues#885): a concept identifying what a template is for, minted as
 * a child of {@link DefaultsTemplateTerm#TEMPLATE_CONCEPT} <em>by construction</em> — a
 * purpose cannot be minted detached from the template taxonomy, which is what makes
 * per-purpose retrieval ({@code StampCalculator.getTemplate(pattern, purpose)}) and
 * purpose enumeration under the template parent coherent.
 * <p>
 * Purpose concepts are support content: they live in the Defaults and templates module
 * (KEC, 2026-07-18) so a preference export of the module is self-contained — the
 * templates and the concepts that name their purposes travel together, and hide
 * together under the category's exclusion. Every scope therefore validates its declared
 * stamp against the module, exactly as {@link FieldDefaultsBuilder} and
 * {@link TemplateBuilder} do (validation, not substitution).
 * <p>
 * Obtained from {@link KnowledgeSet#templatePurpose(String)}; identity follows the
 * knowledge set's ordinary birth-FQN derivation, and resuming the same FQN continues
 * the same declaration (the {@code isA} parentage is stated once, in the birth scope).
 * The scope exposes the description verbs a purpose needs; anything beyond them is a
 * sign the concept is not a pure purpose.
 *
 * <pre>{@code
 * IKE.templatePurpose("Dialect authoring template (IkeFoundation)")
 *     .at(defaultsStamp)                       // must declare the Defaults module
 *     .synonym("Dialect authoring template")
 *     .definition("Identifies templates that pre-fill dialect acceptability authoring.");
 * }</pre>
 */
public final class TemplatePurposeBuilder {

    private final ConceptBuilder conceptBuilder;

    TemplatePurposeBuilder(ConceptBuilder conceptBuilder) {
        this.conceptBuilder = conceptBuilder;
    }

    /**
     * Opens a content scope at the given active stamp, validating the module gate. The
     * first scope of the declaration is the birth scope: it mints the concept, creates
     * its fully qualified name description, and states the {@code isA} parentage under
     * {@link DefaultsTemplateTerm#TEMPLATE_CONCEPT} — the guaranteed attachment to the
     * template taxonomy.
     *
     * @param stamp the declared active stamp — its module must be the Defaults and
     *              templates module
     * @return the content scope
     * @throws IllegalArgumentException if the stamp declares any other module — purpose
     *                                  concepts are support content, and the category's
     *                                  live-and-die invariant covers them
     */
    public Scope at(ActiveStamp stamp) {
        SupportContentGate.requireDefaultsModule(stamp, "templatePurpose");
        boolean birthScope = !conceptBuilder.ledger().born();
        ConceptBuilder.ActiveScope inner = conceptBuilder.at(stamp);
        if (birthScope) {
            inner.isA(DefaultsTemplateTerm.TEMPLATE_CONCEPT);
        }
        return new Scope(inner);
    }

    /**
     * The content verbs a template purpose concept needs — descriptions only. Every verb
     * records a change bound to this scope's stamp and returns the scope for chaining.
     */
    public final class Scope {
        private final ConceptBuilder.ActiveScope inner;

        private Scope(ConceptBuilder.ActiveScope inner) {
            this.inner = inner;
        }

        /**
         * Adds a new synonym (regular-name description) in English, with the default
         * US-dialect acceptability of {@code PREFERRED}.
         *
         * @param text the synonym text
         * @return this scope, for chaining
         */
        public Scope synonym(String text) {
            inner.synonym(text);
            return this;
        }

        /**
         * Adds a new definition description in English, with the default US-dialect
         * acceptability of {@code PREFERRED}.
         *
         * @param text the definition text
         * @return this scope, for chaining
         */
        public Scope definition(String text) {
            inner.definition(text);
            return this;
        }

        /**
         * Opens the next content scope. Delegates to
         * {@link TemplatePurposeBuilder#at(ActiveStamp)} — the module gate applies to
         * every scope.
         *
         * @param nextStamp the declared active stamp for the next scope
         * @return the next content scope
         * @throws IllegalArgumentException if the stamp declares any other module
         */
        public Scope at(ActiveStamp nextStamp) {
            return TemplatePurposeBuilder.this.at(nextStamp);
        }
    }
}
