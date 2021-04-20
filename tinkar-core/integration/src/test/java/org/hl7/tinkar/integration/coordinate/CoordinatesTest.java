package org.hl7.tinkar.integration.coordinate;

import org.eclipse.collections.api.set.ImmutableSet;
import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.common.id.IntIdSet;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.service.ServiceKeys;
import org.hl7.tinkar.common.service.ServiceProperties;
import org.hl7.tinkar.coordinate.Coordinates;
import org.hl7.tinkar.coordinate.language.LanguageCoordinateImmutable;
import org.hl7.tinkar.coordinate.stamp.PathProvider;
import org.hl7.tinkar.coordinate.stamp.StampCalculator;
import org.hl7.tinkar.coordinate.stamp.StampFilterRecord;
import org.hl7.tinkar.coordinate.stamp.StampPositionImmutable;
import org.hl7.tinkar.coordinate.view.View;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.entity.calculator.Latest;
import org.hl7.tinkar.entity.internal.Get;
import org.hl7.tinkar.integration.TestConstants;
import org.hl7.tinkar.terms.TinkarTerm;
import org.testng.Assert;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import java.util.Optional;
import java.util.logging.Logger;

import static org.hl7.tinkar.terms.TinkarTerm.PATH_ORIGINS_PATTERN;

class CoordinatesTest {
    private static Logger LOG = Logger.getLogger(CoordinatesTest.class.getName());

    @BeforeSuite
    public void setupSuite() {
        LOG.info("setupSuite: " + this.getClass().getSimpleName());
        LOG.info(ServiceProperties.jvmUuid());
        ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, TestConstants.MVSTORE_ROOT);
        PrimitiveData.selectControllerByName(TestConstants.MV_STORE_OPEN_NAME);
        PrimitiveData.start();
    }

    @Test
    void countPathOrigins() {
        Assert.assertEquals(PrimitiveData.get().entityNidsOfPattern(PATH_ORIGINS_PATTERN.nid()).length, 3);
    }

    @Test
    void pathOrigins() {
        for (int pathNid : PrimitiveData.get().entityNidsOfPattern(PATH_ORIGINS_PATTERN.nid())) {
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
        StampCalculator calculator = StampCalculator.getCalculator(developmentLatestFilter);
        Latest<ConceptEntityVersion> latest = calculator.latest(englishLanguage);
        LOG.info("Latest computed: '" + latest);

        Entity.provider().forEachSemanticForComponent(TinkarTerm.ENGLISH_LANGUAGE.nid(), semanticEntity -> {
            LOG.info(semanticEntity.toString() + "\n");
            for (int acceptibilityNid : Get.entityService().semanticNidsForComponentOfPattern(semanticEntity.nid(), TinkarTerm.US_DIALECT_PATTERN.nid())) {
                LOG.info("  Acceptability US: \n    " + Get.entityService().getEntityFast(acceptibilityNid));
            }
        });
        Entity.provider().forEachSemanticForComponent(TinkarTerm.NECESSARY_SET.nid(), semanticEntity -> {
            LOG.info(semanticEntity.toString() + "\n");
            for (int acceptibilityNid : Get.entityService().semanticNidsForComponentOfPattern(semanticEntity.nid(), TinkarTerm.US_DIALECT_PATTERN.nid())) {
                LOG.info("  Acceptability US: \n    " + Get.entityService().getEntityFast(acceptibilityNid));
            }
        });
    }

    @Test
    void names() {
        LanguageCoordinateImmutable usFqn = Coordinates.Language.UsEnglishFullyQualifiedName();
        LOG.info("fqn: " + usFqn.getDescriptionText(TinkarTerm.NECESSARY_SET, Coordinates.Filter.DevelopmentLatest()) + "\n");
        LOG.info("reg: " + usFqn.getRegularDescriptionText(TinkarTerm.NECESSARY_SET, Coordinates.Filter.DevelopmentLatest()) + "\n");
        LOG.info("def: " + usFqn.getDefinitionDescriptionText(TinkarTerm.NECESSARY_SET, Coordinates.Filter.DevelopmentLatest()) + "\n");
    }

    @Test
    void navigate() {
        View view = Coordinates.View.DefaultView();
        IntIdSet children = view.children(TinkarTerm.DESCRIPTION_ACCEPTABILITY);
        StringBuilder sb = new StringBuilder("Focus: [");
        Optional<String> optionalName = view.language().getRegularDescriptionText(TinkarTerm.DESCRIPTION_ACCEPTABILITY, Coordinates.Filter.DevelopmentLatest());
        optionalName.ifPresentOrElse(name -> sb.append(name), () -> sb.append(TinkarTerm.DESCRIPTION_ACCEPTABILITY.nid()));
        sb.append("]\nchildren: [");
        for (int childNid: children.toArray()) {
            optionalName = view.language().getRegularDescriptionText(childNid, Coordinates.Filter.DevelopmentLatest());
            optionalName.ifPresentOrElse(name -> sb.append(name), () -> sb.append(childNid));
            sb.append(", ");
        }
        sb.delete(sb.length() - 2, sb.length());
        sb.append("]\nparents: [");
        IntIdSet parents = view.parents(TinkarTerm.DESCRIPTION_ACCEPTABILITY);
        for (int parentNid: parents.toArray()) {
            optionalName = view.language().getRegularDescriptionText(parentNid, Coordinates.Filter.DevelopmentLatest());
            optionalName.ifPresentOrElse(name -> sb.append(name), () -> sb.append(parentNid));
            sb.append(", ");
        }
        sb.delete(sb.length() - 2, sb.length());
        sb.append("]\n");
        LOG.info(sb.toString());
    }

    @Test
    void sortedNavigate() {
        View view = Coordinates.View.DefaultView();
        IntIdList children = view.sortedChildren(TinkarTerm.DESCRIPTION_ACCEPTABILITY);
        StringBuilder sb = new StringBuilder("Focus: [");
        Optional<String> optionalName = view.language().getRegularDescriptionText(TinkarTerm.DESCRIPTION_ACCEPTABILITY, Coordinates.Filter.DevelopmentLatest());
        optionalName.ifPresentOrElse(name -> sb.append(name), () -> sb.append(TinkarTerm.DESCRIPTION_ACCEPTABILITY.nid()));
        sb.append("]\nsorted children: [");
        for (int childNid: children.toArray()) {
            optionalName = view.language().getRegularDescriptionText(childNid, Coordinates.Filter.DevelopmentLatest());
            optionalName.ifPresentOrElse(name -> sb.append(name), () -> sb.append(childNid));
            sb.append(", ");
        }
        sb.delete(sb.length() - 2, sb.length());
        sb.append("]\nsorted parents: [");
        IntIdList parents = view.sortedParents(TinkarTerm.DESCRIPTION_ACCEPTABILITY);
        for (int parentNid: parents.toArray()) {
            optionalName = view.language().getRegularDescriptionText(parentNid, Coordinates.Filter.DevelopmentLatest());
            optionalName.ifPresentOrElse(name -> sb.append(name), () -> sb.append(parentNid));
            sb.append(", ");
        }
        sb.delete(sb.length() - 2, sb.length());
        sb.append("]\n");
        LOG.info(sb.toString());
    }

}
