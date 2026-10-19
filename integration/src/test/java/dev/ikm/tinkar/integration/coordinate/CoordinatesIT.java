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
package dev.ikm.tinkar.integration.coordinate;

import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import dev.ikm.tinkar.common.id.LongIdList;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.Coordinates;
import dev.ikm.tinkar.coordinate.PathService;
import dev.ikm.tinkar.coordinate.language.LanguageCoordinateRecord;
import dev.ikm.tinkar.coordinate.language.calculator.LanguageCalculatorWithCache;
import dev.ikm.tinkar.coordinate.stamp.StampCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StampPositionRecord;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.ConceptEntityVersion;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.set.ImmutableSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static dev.ikm.tinkar.terms.KernelPattern.PATH_ORIGINS_PATTERN;
import static dev.ikm.tinkar.terms.KernelPattern.PATHS_PATTERN;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CoordinatesIT {

    private static final Logger LOG = LoggerFactory.getLogger(CoordinatesIT.class);
    private static final File DATASTORE_ROOT = TestConstants.createFilePathInTargetFromClassName.apply(
            CoordinatesIT.class);

    @BeforeAll
    static void beforeAll() {
        TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, DATASTORE_ROOT);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
    }

    @Test
    @Order(2)
    void countPathOrigins() {
        // Every path originates from another but the primordial path, which is by definition
        // the origin and so has no path origin: master from development, development from
        // sandbox, sandbox from primordial, and any path the store's content adds (the
        // registry's, for one). Counted from the store as loaded, not hard-coded.
        Set<Long> paths = new HashSet<>();
        EntityService.get().semanticsOfPattern(PATHS_PATTERN.nid()).forEach(path -> paths.add(path.referencedComponentNid()));
        Assertions.assertTrue(paths.containsAll(Set.of(KernelConcept.PRIMORDIAL_PATH.nid(), KernelConcept.SANDBOX_PATH.nid(),
                KernelConcept.DEVELOPMENT_PATH.nid(), KernelConcept.MASTER_PATH.nid())), "the four kernel paths are paths");
        Set<Long> originated = new HashSet<>();
        EntityService.get().semanticsOfPattern(PATH_ORIGINS_PATTERN.nid())
                .forEach(origin -> Assertions.assertTrue(originated.add(origin.referencedComponentNid()),
                        "one path origin semantic per path"));
        paths.remove(KernelConcept.PRIMORDIAL_PATH.nid());
        Assertions.assertEquals(paths, originated, "every path but the primordial one has a path origin");
    }

    @Test
    @Order(3)
    void pathOrigins() {
        for (SemanticEntity originSemantic : EntityService.get().semanticsOfPattern(PATH_ORIGINS_PATTERN.nid()).toList()) {
            Entity pathEntity = EntityHandle.get(originSemantic.referencedComponentNid()).expectEntity();
            ImmutableSet<StampPositionRecord> origin = PathService.get().getPathOrigins(originSemantic.referencedComponentNid());
            LOG.info("Path '" + PrimitiveData.text(pathEntity.nid()) + "' has an origin of: " + origin);
        }
    }

    @Test
    @Order(4)
    void computeLatest() {
        LOG.info("computeLatest()");

        StampCoordinateRecord developmentLatestFilter = Coordinates.Stamp.DevelopmentLatest();
        LOG.info("development latest filter '" + developmentLatestFilter);
        ConceptEntity englishLanguage = EntityHandle.get(KernelConcept.ENGLISH_LANGUAGE.nid()).expectConcept();
        StampCalculatorWithCache calculator = StampCalculatorWithCache.getCalculator(developmentLatestFilter);
        Latest<ConceptEntityVersion> latest = calculator.latest(englishLanguage);
        LOG.info("Latest computed: '" + latest);

        Entity.provider().forEachSemanticForComponent(KernelConcept.ENGLISH_LANGUAGE.nid(), semanticEntity -> {
            LOG.info(semanticEntity.toString() + "\n");
            EntityService.get().forEachSemanticForComponentOfPattern(semanticEntity.nid(), KernelPattern.US_DIALECT_PATTERN.nid(), acceptibility ->
                LOG.info("  Acceptability US: \n    " + acceptibility));
        });
        Entity.provider().forEachSemanticForComponent(KernelConcept.NECESSARY_SET.nid(), semanticEntity -> {
            LOG.info(semanticEntity.toString() + "\n");
            EntityService.get().forEachSemanticForComponentOfPattern(semanticEntity.nid(), KernelPattern.US_DIALECT_PATTERN.nid(), acceptibility ->
                LOG.info("  Acceptability US: \n    " + acceptibility));
        });
    }

    @Test
    @Order(5)
    void names() {
        LOG.info("names()");
        LanguageCoordinateRecord usFqn = Coordinates.Language.UsEnglishFullyQualifiedName();
        LanguageCalculatorWithCache usFqnCalc = LanguageCalculatorWithCache.getCalculator(Coordinates.Stamp.DevelopmentLatest(), Lists.immutable.of(usFqn));
        LOG.info("fqn: " + usFqnCalc.getDescriptionText(KernelConcept.NECESSARY_SET) + "\n");
        LOG.info("reg: " + usFqnCalc.getRegularDescriptionText(KernelConcept.NECESSARY_SET) + "\n");
        LOG.info("def: " + usFqnCalc.getDefinitionDescriptionText(KernelConcept.NECESSARY_SET) + "\n");
    }

    @Test
    @Order(6)
    void navigate() {
        LOG.info("navigate()");
        ViewCalculator viewCalculator = Calculators.View.Default();
        LongIdList children = viewCalculator.childrenOf(KernelConcept.DESCRIPTION_ACCEPTABILITY);
        StringBuilder sb = new StringBuilder("Focus: [");
        Optional<String> optionalName = viewCalculator.getRegularDescriptionText(KernelConcept.DESCRIPTION_ACCEPTABILITY);
        optionalName.ifPresentOrElse(name -> sb.append(name), () -> sb.append(KernelConcept.DESCRIPTION_ACCEPTABILITY.nid()));
        sb.append("]\nchildren: [");
        for (long childNid : children.toArray()) {
            optionalName = viewCalculator.getRegularDescriptionText(childNid);
            optionalName.ifPresentOrElse(name -> sb.append(name), () -> sb.append(childNid));
            sb.append(", ");
        }
        sb.delete(sb.length() - 2, sb.length());
        sb.append("]\nparents: [");
        LongIdList parents = viewCalculator.parentsOf(KernelConcept.DESCRIPTION_ACCEPTABILITY);
        for (long parentNid : parents.toArray()) {
            optionalName = viewCalculator.getRegularDescriptionText(parentNid);
            optionalName.ifPresentOrElse(name -> sb.append(name), () -> sb.append(parentNid));
            sb.append(", ");
        }
        sb.delete(sb.length() - 2, sb.length());
        sb.append("]\n");
        LOG.info(sb.toString());
    }

    @Test
    @Order(7)
    void sortedNavigate() {
        LOG.info("sortedNavigate()");
        ViewCalculator viewCalculator = Calculators.View.Default();
        LongIdList children = viewCalculator.sortedChildrenOf(KernelConcept.DESCRIPTION_ACCEPTABILITY);
        StringBuilder sb = new StringBuilder("Focus: [");
        Optional<String> optionalName = viewCalculator.getRegularDescriptionText(KernelConcept.DESCRIPTION_ACCEPTABILITY);
        optionalName.ifPresentOrElse(name -> sb.append(name), () -> sb.append(KernelConcept.DESCRIPTION_ACCEPTABILITY.nid()));
        sb.append("]\nsorted children: [");
        for (long childNid : children.toArray()) {
            optionalName = viewCalculator.getRegularDescriptionText(childNid);
            optionalName.ifPresentOrElse(name -> sb.append(name), () -> sb.append(childNid));
            sb.append(", ");
        }
        sb.delete(sb.length() - 2, sb.length());
        sb.append("]\nsorted parents: [");
        LongIdList parents = viewCalculator.sortedParentsOf(KernelConcept.DESCRIPTION_ACCEPTABILITY);
        for (long parentNid : parents.toArray()) {
            optionalName = viewCalculator.getRegularDescriptionText(parentNid);
            optionalName.ifPresentOrElse(name -> sb.append(name), () -> sb.append(parentNid));
            sb.append(", ");
        }
        sb.delete(sb.length() - 2, sb.length());
        sb.append("]\n");
        LOG.info(sb.toString());
    }
}
