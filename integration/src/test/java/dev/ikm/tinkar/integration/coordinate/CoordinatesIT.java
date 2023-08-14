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

import dev.ikm.tinkar.integration.TestConstants;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.set.ImmutableSet;
import dev.ikm.tinkar.common.id.IntIdList;
import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ServiceKeys;
import dev.ikm.tinkar.common.service.ServiceProperties;
import dev.ikm.tinkar.common.util.io.FileUtil;
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
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromDtoFile;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static dev.ikm.tinkar.terms.TinkarTerm.PATH_ORIGINS_PATTERN;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CoordinatesIT {
    private static final Logger LOG = LoggerFactory.getLogger(CoordinatesIT.class);

    @BeforeAll
    static void setupSuite() throws IOException {
        LOG.info("Clear caches");
        CachingService.clearAll();
        LOG.info("Setup Suite: " + LOG.getName());
        LOG.info(ServiceProperties.jvmUuid());
        ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, TestConstants.SAP_ROOT);
        FileUtil.recursiveDelete(TestConstants.SAP_ROOT);
        PrimitiveData.selectControllerByName(TestConstants.SA_STORE_OPEN_NAME);
        PrimitiveData.start();
        File file = TestConstants.TINK_TEST_FILE;
        LoadEntitiesFromDtoFile loadTink = new LoadEntitiesFromDtoFile(file);
        int count = loadTink.compute();
        LOG.info("Loaded. " + loadTink.report());
    }

    @AfterAll
    static void teardownSuite() {
        LOG.info("Teardown Suite: " + LOG.getName());
        PrimitiveData.stop();
    }

    @Test
    @Order(2)
    void countPathOrigins() {
        Assertions.assertEquals(PrimitiveData.get().semanticNidsOfPattern(PATH_ORIGINS_PATTERN.nid()).length, 3);
    }

    @Test
    @Order(3)
    void pathOrigins() {
        for (int pathNid : PrimitiveData.get().semanticNidsOfPattern(PATH_ORIGINS_PATTERN.nid())) {
            SemanticEntity originSemantic = EntityService.get().getEntityFast(pathNid);
            Entity pathEntity = EntityService.get().getEntityFast(originSemantic.referencedComponentNid());
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
        ConceptEntity englishLanguage = Entity.getFast(TinkarTerm.ENGLISH_LANGUAGE);
        StampCalculatorWithCache calculator = StampCalculatorWithCache.getCalculator(developmentLatestFilter);
        Latest<ConceptEntityVersion> latest = calculator.latest(englishLanguage);
        LOG.info("Latest computed: '" + latest);

        Entity.provider().forEachSemanticForComponent(TinkarTerm.ENGLISH_LANGUAGE.nid(), semanticEntity -> {
            LOG.info(semanticEntity.toString() + "\n");
            for (int acceptibilityNid : EntityService.get().semanticNidsForComponentOfPattern(semanticEntity.nid(), TinkarTerm.US_DIALECT_PATTERN.nid())) {
                LOG.info("  Acceptability US: \n    " + EntityService.get().getEntityFast(acceptibilityNid));
            }
        });
        Entity.provider().forEachSemanticForComponent(TinkarTerm.NECESSARY_SET.nid(), semanticEntity -> {
            LOG.info(semanticEntity.toString() + "\n");
            for (int acceptibilityNid : EntityService.get().semanticNidsForComponentOfPattern(semanticEntity.nid(), TinkarTerm.US_DIALECT_PATTERN.nid())) {
                LOG.info("  Acceptability US: \n    " + EntityService.get().getEntityFast(acceptibilityNid));
            }
        });
    }

    @Test
    @Order(5)
    void names() {
        LOG.info("names()");
        LanguageCoordinateRecord usFqn = Coordinates.Language.UsEnglishFullyQualifiedName();
        LanguageCalculatorWithCache usFqnCalc = LanguageCalculatorWithCache.getCalculator(Coordinates.Stamp.DevelopmentLatest(), Lists.immutable.of(usFqn));
        LOG.info("fqn: " + usFqnCalc.getDescriptionText(TinkarTerm.NECESSARY_SET) + "\n");
        LOG.info("reg: " + usFqnCalc.getRegularDescriptionText(TinkarTerm.NECESSARY_SET) + "\n");
        LOG.info("def: " + usFqnCalc.getDefinitionDescriptionText(TinkarTerm.NECESSARY_SET) + "\n");
    }

    @Test
    @Order(6)
    void navigate() {
        LOG.info("navigate()");
        ViewCalculator viewCalculator = Calculators.View.Default();
        IntIdList children = viewCalculator.childrenOf(TinkarTerm.DESCRIPTION_ACCEPTABILITY);
        StringBuilder sb = new StringBuilder("Focus: [");
        Optional<String> optionalName = viewCalculator.getRegularDescriptionText(TinkarTerm.DESCRIPTION_ACCEPTABILITY);
        optionalName.ifPresentOrElse(name -> sb.append(name), () -> sb.append(TinkarTerm.DESCRIPTION_ACCEPTABILITY.nid()));
        sb.append("]\nchildren: [");
        for (int childNid : children.toArray()) {
            optionalName = viewCalculator.getRegularDescriptionText(childNid);
            optionalName.ifPresentOrElse(name -> sb.append(name), () -> sb.append(childNid));
            sb.append(", ");
        }
        sb.delete(sb.length() - 2, sb.length());
        sb.append("]\nparents: [");
        IntIdList parents = viewCalculator.parentsOf(TinkarTerm.DESCRIPTION_ACCEPTABILITY);
        for (int parentNid : parents.toArray()) {
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
        IntIdList children = viewCalculator.sortedChildrenOf(TinkarTerm.DESCRIPTION_ACCEPTABILITY);
        StringBuilder sb = new StringBuilder("Focus: [");
        Optional<String> optionalName = viewCalculator.getRegularDescriptionText(TinkarTerm.DESCRIPTION_ACCEPTABILITY);
        optionalName.ifPresentOrElse(name -> sb.append(name), () -> sb.append(TinkarTerm.DESCRIPTION_ACCEPTABILITY.nid()));
        sb.append("]\nsorted children: [");
        for (int childNid : children.toArray()) {
            optionalName = viewCalculator.getRegularDescriptionText(childNid);
            optionalName.ifPresentOrElse(name -> sb.append(name), () -> sb.append(childNid));
            sb.append(", ");
        }
        sb.delete(sb.length() - 2, sb.length());
        sb.append("]\nsorted parents: [");
        IntIdList parents = viewCalculator.sortedParentsOf(TinkarTerm.DESCRIPTION_ACCEPTABILITY);
        for (int parentNid : parents.toArray()) {
            optionalName = viewCalculator.getRegularDescriptionText(parentNid);
            optionalName.ifPresentOrElse(name -> sb.append(name), () -> sb.append(parentNid));
            sb.append(", ");
        }
        sb.delete(sb.length() - 2, sb.length());
        sb.append("]\n");
        LOG.info(sb.toString());
    }
}
