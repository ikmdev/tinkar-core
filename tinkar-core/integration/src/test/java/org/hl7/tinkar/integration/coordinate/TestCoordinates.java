package org.hl7.tinkar.integration.coordinate;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.set.ImmutableSet;
import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.service.ServiceKeys;
import org.hl7.tinkar.common.service.ServiceProperties;
import org.hl7.tinkar.common.util.io.FileUtil;
import org.hl7.tinkar.coordinate.Calculators;
import org.hl7.tinkar.coordinate.Coordinates;
import org.hl7.tinkar.coordinate.PathService;
import org.hl7.tinkar.coordinate.language.LanguageCoordinateRecord;
import org.hl7.tinkar.coordinate.language.calculator.LanguageCalculatorWithCache;
import org.hl7.tinkar.coordinate.stamp.StampCoordinateRecord;
import org.hl7.tinkar.coordinate.stamp.StampPositionRecord;
import org.hl7.tinkar.coordinate.stamp.calculator.Latest;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import org.hl7.tinkar.coordinate.view.calculator.ViewCalculator;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.entity.load.LoadEntitiesFromDtoFile;
import org.hl7.tinkar.integration.TestConstants;
import org.hl7.tinkar.terms.TinkarTerm;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static org.hl7.tinkar.terms.TinkarTerm.PATH_ORIGINS_PATTERN;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestCoordinates {
    private static final Logger LOG = LoggerFactory.getLogger(TestCoordinates.class);

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
