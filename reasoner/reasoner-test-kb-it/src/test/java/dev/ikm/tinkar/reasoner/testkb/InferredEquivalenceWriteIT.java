/*
 * Copyright © 2026 IKE Network (support@ike.network)
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
package dev.ikm.tinkar.reasoner.testkb;

import dev.ikm.tinkar.common.id.LongIdSet;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.common.service.PluggableService;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ServiceKeys;
import dev.ikm.tinkar.common.service.ServiceProperties;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculatorWithCache;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpressionBuilder;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.importer.StoreWriter;
import dev.ikm.tinkar.reasoner.service.ClassifierResults;
import dev.ikm.tinkar.reasoner.service.ReasonerService;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.impl.factory.primitive.LongSets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writing inferred results records each concept's equivalents: two concepts stated equivalent on
 * the starter set are classified and written, and each gets an inferred equivalence semantic, under
 * the identity the reasoner writes it with, naming the other and never itself.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InferredEquivalenceWriteIT {

    private static final UUID NAMESPACE = UUID.fromString("0f6c1d0e-4c4e-5f4a-9d61-6a7a1b9e2c11");

    @BeforeAll
    void loadStarterSet() throws Exception {
        CachingService.clearAll();
        ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT,
                Files.createDirectories(Path.of("target", "stores", getClass().getSimpleName())).toFile());
        PrimitiveData.selectControllerByName("Load Ephemeral Store");
        PrimitiveData.start();
        new LoadEntitiesFromProtobufFile(Path.of("target", "data", "ike-starter-set-unreasoned-pb.zip").toFile())
                .compute();
    }

    @AfterAll
    void stop() {
        PrimitiveData.stop();
    }

    @Test
    void equivalentConceptsEachRecordTheOther() throws Exception {
        PublicId a = PublicIds.of(UuidT5Generator.get(NAMESPACE, "A"));
        PublicId b = PublicIds.of(UuidT5Generator.get(NAMESPACE, "B"));
        Stamp stamp = new ActiveStamp(System.currentTimeMillis(), KernelConcept.USER,
                KernelConcept.SOLOR_OVERLAY_MODULE, KernelConcept.DEVELOPMENT_PATH);
        StoreWriter writer = new StoreWriter(Calculators.Stamp.DevelopmentLatest());
        EntityService.get().beginLoadPhase();
        try {
            long aNid = writer.concept(stamp, a);
            long bNid = writer.concept(stamp, b);
            // A is a kind of the root; B is defined as A: A ≡ B.
            writer.statedParent(stamp, a, KernelConcept.ROOT_VERTEX.publicId());
            LogicalExpressionBuilder leb = new LogicalExpressionBuilder();
            leb.SufficientSet(leb.And(leb.ConceptAxiom(EntityProxy.Concept.make(aNid))));
            writer.semantic(stamp, PublicIds.of(UuidT5Generator.get(b.asUuidArray()[0], "stated axioms")),
                    KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN, bNid,
                    Lists.immutable.of((DiTreeEntity) leb.build().sourceGraph()));
        } finally {
            EntityService.get().endLoadPhase();
        }

        ReasonerService reasoner = PluggableService.load(ReasonerService.class).stream().findFirst().orElseThrow().get();
        reasoner.init(Calculators.View.Default(), KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN,
                KernelPattern.EL_PLUS_PLUS_INFERRED_AXIOMS_PATTERN);
        reasoner.extractData(quiet());
        reasoner.loadData(quiet());
        reasoner.computeInferences();
        reasoner.buildNecessaryNormalForm();
        ClassifierResults results = reasoner.writeInferredResults();

        long aNid = EntityService.get().nidForPublicId(a);
        long bNid = EntityService.get().nidForPublicId(b);
        assertTrue(results.getEquivalentSets().stream().anyMatch(set -> set.contains(aNid) && set.contains(bNid)),
                "the classifier results report A and B as one equivalent set: " + results.getEquivalentSets());
        ViewCalculator after = ViewCalculatorWithCache.getCalculator(results.getViewCoordinate());
        assertEquals(LongSets.immutable.of(bNid), equivalents(after, a), "A's equivalents");
        assertEquals(LongSets.immutable.of(aNid), equivalents(after, b), "B's equivalents");
    }

    private static org.eclipse.collections.api.set.primitive.ImmutableLongSet equivalents(ViewCalculator view,
                                                                                          PublicId concept) {
        UUID semanticUuid = UuidT5Generator.singleSemanticUuid(KernelPattern.INFERRED_EQUIVALENCE_PATTERN.publicId(),
                concept);
        SemanticEntity<?> semantic = EntityHandle.get(PublicIds.of(semanticUuid)).asSemantic()
                .orElseThrow(() -> new AssertionError("no inferred equivalence semantic for " + concept));
        Latest<SemanticEntityVersion> latest = view.latest((SemanticEntity<SemanticEntityVersion>) semantic);
        assertTrue(latest.isPresent(), "a latest version of the equivalence semantic");
        return LongSets.immutable.of(((LongIdSet) latest.get().fieldValues().get(0)).toArray());
    }

    private static TrackingCallable<Object> quiet() {
        return new TrackingCallable<>() {
            @Override
            protected Object compute() {
                return null;
            }
        };
    }
}
