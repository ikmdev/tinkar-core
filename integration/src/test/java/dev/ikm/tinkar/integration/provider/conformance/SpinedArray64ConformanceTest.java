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
package dev.ikm.tinkar.integration.provider.conformance;

import dev.ikm.tinkar.common.id.Nid;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.id.impl.NidLayout;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ScopeErasure;
import dev.ikm.tinkar.common.service.ServiceKeys;
import dev.ikm.tinkar.common.service.ServiceProperties;
import dev.ikm.tinkar.common.service.internal.EntityStore;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.fixtures.PrimitiveDataServiceConformance;
import dev.ikm.tinkar.fixtures.WithKeyValueProvider;
import dev.ikm.tinkar.provider.spinedarray64.SpinedArray64Provider;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The provider conformance suite against an empty 64-bit spined-array store, persistent, on an
 * empty directory under {@code target} (IKE-Network/ike-issues#1313); and the store's own
 * promises: 64-bit nids that name their pattern, and a scope's pattern erased as one range.
 */
@WithKeyValueProvider(controllerClass = SpinedArray64Provider.OpenController.class, cleanOnStart = true)
class SpinedArray64ConformanceTest extends PrimitiveDataServiceConformance {

    @Test
    void aNidResolvesBackToThePublicIdItWasMintedFrom() {
        PublicId id = PublicIds.newRandom();
        long nid = EntityService.get().nidForConcept(id);
        assertArrayEquals(id.asUuidArray(), PrimitiveData.get().publicIdForNid(nid).asUuidArray());
    }

    /** A nid is its pattern's sequence over its element's: a semantic's upper half is its pattern's element sequence. */
    @Test
    void aNidNamesItsPattern() {
        assertEquals(NidLayout.SIXTY_FOUR_BIT, NidLayout.active());
        World world = makeWorld();
        assertEquals(1, Nid.patternSequence64(world.pattern().nid()), "a pattern is an element of the pattern-of-patterns");
        assertEquals(Nid.elementSequence64(world.pattern().nid()), Nid.patternSequence64(world.semantic().nid()));
        assertEquals(2, Nid.patternSequence64(world.concept().nid()), "concepts are the concept pattern's elements");
        assertEquals(3, Nid.patternSequence64(world.stamp().nid()), "STAMPs are the stamp pattern's elements");
    }

    /**
     * A pattern erased with every element it holds is dropped as one range: its records and its
     * citations go, the concept its semantic referenced no longer lists it, its directory is
     * deleted at the save, and its sequence is never issued again.
     */
    @Test
    void aWholePatternIsErasedAsOneRange() {
        World world = makeWorld();
        long pattern = world.pattern().nid();
        long semantic = world.semantic().nid();
        int sequence = Nid.elementSequence64(pattern);
        ((SpinedArray64Provider) PrimitiveData.get()).save();
        File root = ServiceProperties.<File>get(ServiceKeys.DATA_STORE_ROOT).orElseThrow();
        File patternDirectory = new File(root, "patterns/" + sequence);
        assertTrue(patternDirectory.isDirectory(), patternDirectory + " is written by the save");

        ((ScopeErasure) PrimitiveData.get()).eraseComponents(new long[]{semantic, pattern});

        assertNull(EntityStore.current().getBytes(semantic));
        assertNull(EntityStore.current().getBytes(pattern));
        assertFalse(PrimitiveData.get().hasPublicId(world.semantic().publicId()));
        assertFalse(PrimitiveData.get().hasPublicId(world.pattern().publicId()));
        assertEquals(0, EntityStore.current().semanticNidsForComponent(world.concept().nid()).length);
        assertFalse(Enumerations.contains(EntityStore.current()::forEachPatternNid, pattern));
        assertTrue(EntityStore.current().getBytes(world.concept().nid()) != null, "the concept outside the set stays");

        ((SpinedArray64Provider) PrimitiveData.get()).save();
        assertFalse(patternDirectory.exists(), patternDirectory + " is deleted by the save after the drop");

        World next = makeWorld();
        assertTrue(Nid.elementSequence64(next.pattern().nid()) > sequence, "a dropped pattern's sequence is not reused");
    }

    /** A semantic erased alone clears its slot and its citation; its pattern keeps its range. */
    @Test
    void aSemanticErasedAloneLeavesItsPattern() {
        World world = makeWorld();
        ((ScopeErasure) PrimitiveData.get()).eraseComponents(new long[]{world.semantic().nid()});
        assertNull(EntityStore.current().getBytes(world.semantic().nid()));
        assertTrue(EntityStore.current().getBytes(world.pattern().nid()) != null);
        assertEquals(0, EntityStore.current().semanticNidsForComponent(world.concept().nid()).length);
        assertEquals(0, EntityStore.current().countSemanticsOfPattern(world.pattern().nid()));
    }

    /** Whether an enumeration yields a nid. */
    private static final class Enumerations {
        interface Enumeration {
            void forEach(org.eclipse.collections.api.block.procedure.primitive.LongProcedure procedure);
        }

        static boolean contains(Enumeration enumeration, long nid) {
            boolean[] found = {false};
            enumeration.forEach(each -> found[0] |= each == nid);
            return found[0];
        }
    }
}
