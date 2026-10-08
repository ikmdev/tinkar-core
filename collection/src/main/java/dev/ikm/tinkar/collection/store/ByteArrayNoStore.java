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
package dev.ikm.tinkar.collection.store;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * A byte-array store that keeps nothing, for a spined map held in memory only: it reports no spines,
 * reads none and writes none, so the map starts empty and nothing reaches disk. The
 * ephemeral spined-array store runs on these.
 */
public class ByteArrayNoStore implements ByteArrayStore {
    @Override
    public Optional<AtomicReferenceArray<byte[]>> get(int spineIndex) {
        return Optional.empty();
    }

    @Override
    public void put(int spineIndex, AtomicReferenceArray<byte[]> spine) {
    }

    @Override
    public int sizeOnDisk() {
        return 0;
    }

    @Override
    public int getSpineCount() {
        return 0;
    }

    @Override
    public void writeSpineCount(int spineCount) {
    }
}
