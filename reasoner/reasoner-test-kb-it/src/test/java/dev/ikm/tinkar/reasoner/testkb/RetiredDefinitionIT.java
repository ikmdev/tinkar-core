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
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.InactiveStamp;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.importer.StoreWriter;
import dev.ikm.tinkar.reasoner.service.ReasonerService;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import org.eclipse.collections.impl.factory.primitive.LongSets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A concept a definition names, whose own definition is retired, is reasoned with the definition
 * it last had, as a layer built on an earlier edition meant it; one with no definition in any
 * version is primitive directly under the root. Either way it, and every concept whose definition
 * names it, gets a necessary normal form.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetiredDefinitionIT {

    private static final UUID NAMESPACE = UUID.fromString("5b2f0c8e-0d43-5c1e-b7a4-3e6f9d1c2a70");

    @BeforeAll
    void loadStarterSet() throws Exception {
        CachingService.clearAll();
        ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT,
                Files.createDirectories(Path.of("target", "stores", getClass().getSimpleName())).toFile());
        PrimitiveData.selectControllerByName("Load Memory-Only 64-bit Store");
        PrimitiveData.start();
        new dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile(
                Path.of("target", "data", "ike-starter-set-unreasoned-pb.zip").toFile()).compute();
    }

    @AfterAll
    void stop() {
        PrimitiveData.stop();
    }

    @Test
    void retiredAndUndefinedConceptsAreReasonedAndNamedOnes() throws Exception {
        PublicId p = id("P"), a = id("A"), b = id("B"), c = id("C"), d = id("D");
        long now = System.currentTimeMillis();
        Stamp defined = new ActiveStamp(now - 2_000, KernelConcept.USER, KernelConcept.SOLOR_OVERLAY_MODULE,
                KernelConcept.DEVELOPMENT_PATH);
        Stamp retired = new InactiveStamp(now - 1_000, KernelConcept.USER, KernelConcept.SOLOR_OVERLAY_MODULE,
                KernelConcept.DEVELOPMENT_PATH);
        StoreWriter writer = new StoreWriter(Calculators.Stamp.DevelopmentLatest());
        EntityService.get().beginLoadPhase();
        try {
            for (PublicId concept : new PublicId[] {p, a, b, c, d}) {
                writer.concept(defined, concept);
            }
            writer.statedParent(defined, p, KernelConcept.ROOT_VERTEX.publicId());
            // A was a kind of P, until its definition was retired; B, still active, is a kind of A.
            writer.statedParent(defined, a, p);
            writer.statedParent(retired, a, p);
            writer.statedParent(defined, b, a);
            // C has never had a definition; D is a kind of C.
            writer.statedParent(defined, d, c);
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

        assertEquals(LongSets.immutable.of(nid(p)), reasoner.getParents(nid(a)), "A, by its last active definition");
        assertEquals(LongSets.immutable.of(nid(a)), reasoner.getParents(nid(b)));
        assertEquals(LongSets.immutable.of(KernelConcept.ROOT_VERTEX.nid()), reasoner.getParents(nid(c)),
                "C, with no definition, under the root");
        assertEquals(LongSets.immutable.of(nid(c)), reasoner.getParents(nid(d)));
        for (PublicId concept : new PublicId[] {a, b, c, d}) {
            assertNotNull(reasoner.getNecessaryNormalForm(nid(concept)), "a necessary normal form for " + concept);
        }
    }

    private static PublicId id(String name) {
        return PublicIds.of(UuidT5Generator.get(NAMESPACE, name));
    }

    private static long nid(PublicId concept) {
        return EntityService.get().nidForPublicId(concept);
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
