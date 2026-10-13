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
package dev.ikm.tinkar.provider.spinedarray;

import dev.ikm.tinkar.collection.SpineFileUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The spine count marker of a spined store directory ({@code IKE-Network/ike-issues#1294}). A
 * marker a save could not remove — on Windows, one another process held open — once made every
 * later open fail with "spineCount- > 1"; the store must open, at the newest count.
 */
class SpineCountMarkerTest {

    @TempDir
    Path directory;

    @Test
    void aStaleMarkerLeftBehindDoesNotStopTheStoreOpening() throws IOException {
        // What a save leaves when deleting the old marker failed: the old and the new together.
        marker("spineCount-41");
        marker("spineCount-42");

        assertEquals(42, SpineFileUtil.readSpineCount(directory.toFile()), "spines only grow: the highest is the newest");
    }

    @Test
    void writingACountLeavesExactlyThatMarker() throws IOException {
        marker("spineCount-41");
        marker("spineCount-40");

        SpineFileUtil.writeSpineCount(directory.toFile(), 42);

        assertEquals(List.of("spineCount-42"), markers());
        assertEquals(42, SpineFileUtil.readSpineCount(directory.toFile()));
    }

    @Test
    void rewritingTheSameCountKeepsItsMarker() throws IOException {
        SpineFileUtil.writeSpineCount(directory.toFile(), 7);
        SpineFileUtil.writeSpineCount(directory.toFile(), 7);

        assertEquals(List.of("spineCount-7"), markers());
    }

    @Test
    void aDirectoryWithoutAMarkerHasNoSpines() {
        assertEquals(0, SpineFileUtil.readSpineCount(directory.toFile()));
        assertEquals(0, SpineFileUtil.readSpineCount(new File(directory.toFile(), "absent")));
    }

    @Test
    void aMarkerThatIsNotACountIsIgnoredBesideAReadableOne() throws IOException {
        marker("spineCount-12");
        marker("spineCount-12.bak");

        assertEquals(12, SpineFileUtil.readSpineCount(directory.toFile()));
    }

    private void marker(String name) throws IOException {
        Files.createFile(directory.resolve(name));
    }

    private List<String> markers() {
        String[] names = directory.toFile().list((dir, name) -> name.startsWith(SpineFileUtil.SPINE_COUNT_PREFIX));
        return names == null ? List.of() : Arrays.stream(names).sorted().toList();
    }
}
