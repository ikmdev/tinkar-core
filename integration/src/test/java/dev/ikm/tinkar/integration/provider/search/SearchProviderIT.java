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

import network.ike.foundation.ike.bindings.IkeConcept;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.common.id.LongIdSet;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.terms.KernelPattern;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.provider.search.Searcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        // The children the store holds, read from the inferred navigation semantics directly
        // rather than through a navigation calculator, as the searcher reads them; the starter
        // set gains users as it gains content (the registry's Registrar, for one).
        List<PublicId> expectedUserChildren = inferredChildrenOf(KernelConcept.USER.nid());
        List<PublicId> knownUsers = List.of(
                KernelConcept.KOMET_USER.publicId(),
                IkeConcept.IKE_COMMUNITY.publicId(),
                IkeConcept.BASELINE_STARTER_DATA_AUTHOR.publicId(),
                IkeConcept.GRETEL.publicId(),
                IkeConcept.GIT_AUTHOR.publicId(),
                IkeConcept.KOMET_ASSISTANT.publicId()
        );
        assertTrue(expectedUserChildren.containsAll(knownUsers), "the store's users include " + knownUsers);

        List<PublicId> actualUserChildren = Searcher.childrenOf(KernelConcept.USER.publicId());

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
                IkeConcept.ROLE_RESTRICTION.publicId(),
                KernelConcept.INTERVAL_ROLE.publicId()
        );

        List<PublicId> actualUserDescendants = Searcher.descendantsOf(KernelConcept.ROLE.publicId());

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
                KernelConcept.ROOT_VERTEX.publicId(),
                IkeConcept.MEANING.publicId(),
                IkeConcept.PURPOSE.publicId()
        );

        List<String> actualFQNs = Searcher.descriptionsOf(conceptsWithFQNs);

        assertEquals(expectedFQNs, actualFQNs,
                "Descriptions returned are not as expected.\n" +
                        "   Expected: " + expectedFQNs + "\n" +
                        "   Actual: " + actualFQNs
        );
    }

    /**
     * The concepts whose latest active inferred navigation names the parent among their
     * parents (the pattern's "origin" field; navigation runs root to leaf).
     */
    private static List<PublicId> inferredChildrenOf(long parentNid) {
        List<PublicId> children = new ArrayList<>();
        Calculators.Stamp.DevelopmentLatestActiveOnly().forEachSemanticVersionOfPattern(
                KernelPattern.INFERRED_NAVIGATION_PATTERN, (version, pattern) -> {
                    for (int index = 0; index < pattern.fieldDefinitions().size(); index++) {
                        String meaning = PrimitiveData.text(pattern.fieldDefinitions().get(index).meaningNid());
                        if (meaning.toLowerCase().contains("origin")
                                && version.fieldValues().get(index) instanceof LongIdSet parents
                                && parents.contains(parentNid)) {
                            children.add(EntityHandle.get(version.referencedComponentNid()).expectEntity().publicId());
                        }
                    }
                });
        return children;
    }

    private void setupSnomedLoincLidrData() {
        File dataStore = new File(System.getProperty("user.home") + "/Solor/snomedLidrLoinc-data-5-6-2024-withCollabData-dev");
        TestHelper.stopDatabase();
        TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, dataStore);
    }

}
