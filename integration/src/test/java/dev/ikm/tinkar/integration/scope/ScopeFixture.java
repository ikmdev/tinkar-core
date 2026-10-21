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
package dev.ikm.tinkar.integration.scope;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpressionBuilder;
import dev.ikm.tinkar.entity.transaction.StampedWriter;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import dev.ikm.tinkar.terms.State;
import network.ike.foundation.ike.bindings.IkeConcept;

/**
 * Documents for the scope tests: each its own pattern, a root referencing the pattern, a line
 * referencing the root, a line referencing a knowledge concept, and a comment on the first line
 * in the shared comment pattern, as an annotation sits in a knowledge pattern. All are written
 * under a module that is a kind of Ephemeral module.
 */
public final class ScopeFixture {

    /** One document's components. */
    public record Doc(String name, PublicId pattern, PublicId root, PublicId line, PublicId cited, PublicId comment) { }

    /** The ephemeral module the documents are written under, and a knowledge concept they reference. */
    public final ConceptFacade documents = EntityProxy.Concept.make(PublicIds.newRandom());
    public final ConceptFacade term = EntityProxy.Concept.make(PublicIds.newRandom());
    private long time = 1_760_000_000_000L;

    /** Writes the module and its parents, and the knowledge concept, as knowledge. */
    public void seed() {
        try (StampedWriter writer = writer(IkeConcept.DEVELOPMENT_MODULE, "scope fixture knowledge")) {
            module(writer, KernelConcept.EPHEMERAL_MODULE, KernelConcept.SOLOR_MODULE);
            module(writer, KernelConcept.INSTANCE_MODULE, KernelConcept.SOLOR_MODULE);
            module(writer, documents, KernelConcept.EPHEMERAL_MODULE);
            writer.concept(term.publicId());
            writer.fullyQualifiedName(term.publicId(), "Scope fixture term");
            writer.commit();
        }
    }

    /** Writes documents under one STAMP, so they share it. */
    public Doc[] documents(String... names) {
        Doc[] docs = new Doc[names.length];
        try (StampedWriter writer = writer(documents, "scope fixture documents")) {
            for (int i = 0; i < names.length; i++) {
                docs[i] = document(writer, names[i]);
            }
            writer.commit();
        }
        return docs;
    }

    /** Revises a document's comment under a STAMP of its own. */
    public void revise(Doc doc, String text) {
        try (StampedWriter writer = writer(documents, "scope fixture revision")) {
            writer.semantic(doc.comment(), KernelPattern.COMMENT_PATTERN.publicId(), doc.line(), text);
            writer.commit();
        }
    }

    /** Writes a semantic of one document's pattern citing another document's line: a reference across scopes. */
    public void cite(Doc from, Doc to) {
        try (StampedWriter writer = writer(documents, "scope fixture citation")) {
            writer.semantic(PublicIds.newRandom(), from.pattern(), to.line(), "cites " + to.name());
            writer.commit();
        }
    }

    /**
     * Writes a document whose element lists another document's line in a component-list field, as
     * a block listed another document's lines in the 2026-10-11 line-identity collision: a reference
     * into that document the referencing index does not hold.
     *
     * @return the linking document's pattern
     */
    public PublicId link(Doc into) {
        PublicId pattern = PublicIds.newRandom();
        PublicId root = PublicIds.newRandom();
        try (StampedWriter writer = writer(documents, "scope fixture link")) {
            writer.pattern(pattern, KernelConcept.STRING, KernelConcept.STRING,
                    StampedWriter.field(KernelConcept.STRING, KernelConcept.STRING, KernelConcept.COMPONENT_ID_LIST_FIELD));
            EntityService.get().nidForSemantic(pattern, root);
            writer.semantic(root, pattern, pattern, dev.ikm.tinkar.common.id.LongIds.list.empty());
            writer.semantic(PublicIds.newRandom(), pattern, root,
                    dev.ikm.tinkar.common.id.LongIds.list.of(EntityService.get().nidForPublicId(into.line())));
            writer.commit();
        }
        return pattern;
    }

    private Doc document(StampedWriter writer, String name) {
        Doc doc = new Doc(name, PublicIds.newRandom(), PublicIds.newRandom(), PublicIds.newRandom(), PublicIds.newRandom(), PublicIds.newRandom());
        writer.pattern(doc.pattern(), KernelConcept.STRING, KernelConcept.STRING,
                StampedWriter.field(KernelConcept.STRING, KernelConcept.STRING, KernelConcept.STRING));
        writer.fullyQualifiedName(doc.pattern(), "Document " + name);
        EntityService.get().nidForSemantic(doc.pattern(), doc.root());
        writer.semantic(doc.root(), doc.pattern(), doc.pattern(), "root of " + name);
        writer.semantic(doc.line(), doc.pattern(), doc.root(), "a line of " + name);
        writer.semantic(doc.cited(), doc.pattern(), term.publicId(), "a line of " + name + " citing a term");
        writer.semantic(doc.comment(), KernelPattern.COMMENT_PATTERN.publicId(), doc.line(), "zebracorn" + name + " annotation");
        return doc;
    }

    private StampedWriter writer(ConceptFacade module, String name) {
        return StampedWriter.open(name, State.ACTIVE, ++time, KernelConcept.USER, module, KernelConcept.DEVELOPMENT_PATH);
    }

    /** Writes a module concept whose stated axioms make it a kind of its parents. */
    private static void module(StampedWriter writer, ConceptFacade module, ConceptFacade... parents) {
        writer.concept(module.publicId());
        LogicalExpressionBuilder leb = new LogicalExpressionBuilder();
        dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiom.Atom.ConceptAxiom[] atoms =
                new dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiom.Atom.ConceptAxiom[parents.length];
        for (int i = 0; i < parents.length; i++) {
            atoms[i] = leb.ConceptAxiom(parents[i]);
        }
        leb.NecessarySet(leb.And(atoms));
        writer.semantic(PublicIds.newRandom(), KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.publicId(), module.publicId(),
                (DiTreeEntity) leb.build().sourceGraph());
    }
}
