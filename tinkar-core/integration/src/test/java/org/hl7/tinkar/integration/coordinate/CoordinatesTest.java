package org.hl7.tinkar.integration.coordinate;

import org.eclipse.collections.api.set.ImmutableSet;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.service.ServiceKeys;
import org.hl7.tinkar.common.service.ServiceProperties;
import org.hl7.tinkar.coordinate.Coordinates;
import org.hl7.tinkar.coordinate.stamp.PathProvider;
import org.hl7.tinkar.coordinate.stamp.RelativePositionCalculator;
import org.hl7.tinkar.coordinate.stamp.StampFilterRecord;
import org.hl7.tinkar.coordinate.stamp.StampPositionImmutable;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.entity.calculator.LatestVersion;
import org.hl7.tinkar.integration.TestConstants;
import org.hl7.tinkar.terms.TinkarTerm;
import org.testng.Assert;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import java.util.logging.Logger;

import static org.hl7.tinkar.terms.TinkarTerm.PATH_ORIGINS_PATTERN;

class CoordinatesTest {
    private static Logger LOG = Logger.getLogger(CoordinatesTest.class.getName());

    @BeforeSuite
    public void setupSuite() {
        LOG.info("setupSuite: " + this.getClass().getSimpleName());
        LOG.info(ServiceProperties.jvmUuid());
        ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, TestConstants.MVSTORE_ROOT);
        PrimitiveData.selectControllerByName(TestConstants.MV_STORE_NAME);
        PrimitiveData.start();
    }

    @Test
    void countPathOrigins() {
        Assert.assertEquals(PrimitiveData.get().entityNidsOfType(PATH_ORIGINS_PATTERN.nid()).length, 3);
    }

    @Test
    void pathOrigins() {
        for (int pathNid: PrimitiveData.get().entityNidsOfType(PATH_ORIGINS_PATTERN.nid())) {
            SemanticEntity originSemantic = EntityService.get().getEntityFast(pathNid);
            Entity pathEntity = EntityService.get().getEntityFast(originSemantic.referencedComponentNid());
            ImmutableSet<StampPositionImmutable> origin = PathProvider.getPathOrigins(originSemantic.referencedComponentNid());
            LOG.info("Path '" + PrimitiveData.text(pathEntity.nid()) + "' has an origin of: " + origin);
        }
    }

    @Test
    void computeLatest() {
        StampFilterRecord developmentLatestFilter = Coordinates.Filter.DevelopmentLatest();
        LOG.info("development latest filter '" + developmentLatestFilter);
        ConceptEntity englishLanguage = Entity.getFast(TinkarTerm.ENGLISH_LANGUAGE);
        RelativePositionCalculator calculator = RelativePositionCalculator.getCalculator(developmentLatestFilter);
        LatestVersion<EntityVersion> latest = calculator.getLatestVersion(englishLanguage);
        LOG.info("Latest computed: '" + latest);

        Entity.provider().forEachSemanticForComponent(TinkarTerm.ENGLISH_LANGUAGE.nid(), semanticEntity -> LOG.info(semanticEntity.toString() + "\n"));
    }



}
