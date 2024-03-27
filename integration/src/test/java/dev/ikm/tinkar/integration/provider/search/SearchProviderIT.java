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
package dev.ikm.tinkar.integration.provider.search;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ServiceProperties;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.integration.TestConstants;
import dev.ikm.tinkar.provider.search.Searcher;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SearchProviderIT {

    private static final Logger LOG = LoggerFactory.getLogger(SearchProviderIT.class);

    @BeforeAll
    public void setUp() throws IOException {
        LOG.info("JVM Version: " + System.getProperty("java.version"));
        LOG.info("JVM Name: " + System.getProperty("java.vm.name"));
        startDatabase();
        LoadEntitiesFromProtobufFile loadEntitiesFromProtobufFile = new LoadEntitiesFromProtobufFile(TestConstants.PB_STARTER_DATA_REASONED);
        loadEntitiesFromProtobufFile.compute();

    }

    @AfterAll
    public void tearDown() {
        stopDatabase();
    }

    private void startDatabase() {
        LOG.info("Clear caches");
        CachingService.clearAll();
        LOG.info("Setup Ephemeral Protobuf Suite: " + LOG.getName());
        LOG.info(ServiceProperties.jvmUuid());
        PrimitiveData.selectControllerByName(TestConstants.EPHEMERAL_STORE_NAME);
        PrimitiveData.start();
    }
    private void stopDatabase() {
        PrimitiveData.stop();
    }

    @Test
    public void getChildrenIT() {
        List<PublicId> expectedUserChildren = Arrays.asList(
                TinkarTerm.ORDER_FOR_AXIOM_ATTACHMENTS.publicId(),
                TinkarTerm.ORDER_FOR_CONCEPT_ATTACHMENTS.publicId(),
                TinkarTerm.ORDER_FOR_DESCRIPTION_ATTACHMENTS.publicId(),
                TinkarTerm.KOMET_USER.publicId(),
                TinkarTerm.KOMET_USER_LIST.publicId(),
                TinkarTerm.MODULE_FOR_USER.publicId(),
                TinkarTerm.PATH_FOR_USER.publicId()
        );

        List<PublicId> actualUserChildren = Searcher.childrenOf(TinkarTerm.USER.publicId());

        expectedUserChildren.sort(Comparator.naturalOrder());
        actualUserChildren.sort(Comparator.naturalOrder());

        assertEquals(expectedUserChildren, actualUserChildren,
                "Children returned are not as expected.\n" +
                        "   Expected: " + expectedUserChildren + "\n" +
                        "   Actual: " + actualUserChildren
                );
    }

    @Test
    public void getDescendantsIT() {
        List<PublicId> expectedUserDescendants = Arrays.asList(
                // Children of Role
                TinkarTerm.ROLE_GROUP.publicId(),
                TinkarTerm.ROLE_OPERATOR.publicId(),
                TinkarTerm.ROLE_TYPE.publicId(),
                // Children of Role Operator
                TinkarTerm.REFERENCED_COMPONENT_SUBTYPE_RESTRICTION.publicId(),
                TinkarTerm.REFERENCED_COMPONENT_TYPE_RESTRICTION.publicId(),
                TinkarTerm.UNIVERSAL_RESTRICTION.publicId()
        );

        List<PublicId> actualUserDescendants = Searcher.descendantsOf(TinkarTerm.ROLE.publicId());

        expectedUserDescendants.sort(Comparator.naturalOrder());
        actualUserDescendants.sort(Comparator.naturalOrder());

        assertEquals(expectedUserDescendants, actualUserDescendants,
                "Descendants returned are not as expected.\n" +
                        "   Expected: " + expectedUserDescendants + "\n" +
                        "   Actual: " + actualUserDescendants
        );
    }

    @Test
    public void getDescriptionsIT() {
        List<String> expectedFQNs = List.of(
                "Integrated Knowledge Management (SOLOR)",
                "Meaning  (SOLOR)",
                "Purpose (SOLOR)"
        );

        List<PublicId> conceptsWithFQNs = List.of(
                TinkarTerm.ROOT_VERTEX.publicId(),
                TinkarTerm.MEANING.publicId(),
                TinkarTerm.PURPOSE.publicId()
        );

        List<String> actualFQNs = Searcher.descriptionsOf(conceptsWithFQNs);

        assertEquals(expectedFQNs, actualFQNs,
                "Descriptions returned are not as expected.\n" +
                        "   Expected: " + expectedFQNs + "\n" +
                        "   Actual: " + actualFQNs
        );
    }
}
