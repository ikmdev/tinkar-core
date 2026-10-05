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

import dev.ikm.tinkar.terms.KernelTerm;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.provider.search.Searcher;
import dev.ikm.tinkar.terms.TinkarTerm;
import dev.ikm.tinkar.terms.TinkarTermV2;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SearchProviderIT {

    private static final Logger LOG = LoggerFactory.getLogger(SearchProviderIT.class);

    @BeforeAll
    public void beforeAll() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
    }

    @Test
    public void getChildrenIT() {
        List<PublicId> expectedUserChildren = Arrays.asList(
                TinkarTerm.ORDER_FOR_AXIOM_ATTACHMENTS.publicId(),
                TinkarTerm.ORDER_FOR_CONCEPT_ATTACHMENTS.publicId(),
                TinkarTerm.ORDER_FOR_DESCRIPTION_ATTACHMENTS.publicId(),
                KernelTerm.KOMET_USER.publicId(),
                TinkarTerm.KOMET_USER_LIST.publicId(),
                TinkarTerm.MODULE_FOR_USER.publicId(),
                TinkarTerm.PATH_FOR_USER.publicId(),
                TinkarTerm.STARTER_DATA_AUTHORING.publicId(),
                TinkarTermV2.TINKAR_STARTER_DATA_AUTHOR_OPENPARENTHESIS_USER_CLOSEPARENTHESIS_.publicId(),
                TinkarTermV2.GRETEL_OPENPARENTHESIS_USER_CLOSEPARENTHESIS_.publicId()
        );

        List<PublicId> actualUserChildren = Searcher.childrenOf(KernelTerm.USER.publicId());

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
                KernelTerm.ROLE_TYPE.publicId(),
                TinkarTerm.ROLE_RESTRICTION.publicId(),
                KernelTerm.INTERVAL_ROLE.publicId(),
                KernelTerm.INTERVAL_ROLE_TYPE.publicId(),
                TinkarTermV2.FEATURE_ROLE_TYPE.publicId()
        );

        List<PublicId> actualUserDescendants = Searcher.descendantsOf(KernelTerm.ROLE.publicId());

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
                "Meaning",
                "Purpose"
        );

        List<PublicId> conceptsWithFQNs = List.of(
                KernelTerm.ROOT_VERTEX.publicId(),
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

    private void setupSnomedLoincLidrData() {
        File dataStore = new File(System.getProperty("user.home") + "/Solor/snomedLidrLoinc-data-5-6-2024-withCollabData-dev");
        TestHelper.stopDatabase();
        TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, dataStore);
    }

}
