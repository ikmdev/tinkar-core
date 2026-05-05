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
package dev.ikm.tinkar.common.service.plugin;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IkeServiceManagerTest {

    @Test
    void macOsJpackageLayoutProducesJavaHomeSiblingFirst() {
        // jpackage on macOS: java.home = .../Komet.app/Contents/runtime/Contents/Home
        String javaHome = "/Applications/Komet.app/Contents/runtime/Contents/Home";
        String userDir  = "/";

        List<Path> candidates = IkeServiceManager.pluginServiceLoaderCandidates(javaHome, userDir);

        assertEquals(
                Path.of("/Applications/Komet.app/Contents/runtime/Contents/Home/plugin-service-loader"),
                candidates.get(0),
                "java.home-anchored candidate must come first on macOS");
    }

    @Test
    void windowsJpackageLayoutProducesJavaHomeSiblingFirst() {
        // jpackage on Windows: java.home = <InstallDir>\runtime
        String javaHome = "C:\\Program Files\\Komet\\runtime";
        String userDir  = "C:\\Users\\tester";

        List<Path> candidates = IkeServiceManager.pluginServiceLoaderCandidates(javaHome, userDir);

        // Path comparison is OS-aware; on POSIX hosts a Windows-style path is
        // a single segment, so we just verify the first candidate ends with
        // the expected directory name and that the probe order is honored.
        assertTrue(candidates.get(0).toString().endsWith("plugin-service-loader"),
                "first candidate should target plugin-service-loader: " + candidates.get(0));
        assertTrue(candidates.get(0).toString().contains("runtime"),
                "Windows first candidate should anchor on the runtime directory: " + candidates.get(0));
    }

    @Test
    void linuxJpackageLayoutProducesJavaHomeSiblingFirst() {
        // jpackage on Linux: java.home = <install>/lib/runtime
        String javaHome = "/opt/komet/lib/runtime";
        String userDir  = "/home/tester";

        List<Path> candidates = IkeServiceManager.pluginServiceLoaderCandidates(javaHome, userDir);

        assertEquals(
                Path.of("/opt/komet/lib/runtime/plugin-service-loader"),
                candidates.get(0),
                "java.home-anchored candidate must come first on Linux");
    }

    @Test
    void localMavenBuildAddsTargetCandidate() {
        // Developer running tests from the module directory; java.home is the JDK,
        // so neither java.home nor parent-of-user.dir contains the artifact —
        // <user.dir>/target/plugin-service-loader is the relevant probe.
        String javaHome = "/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home";
        String userDir  = "/tmp/komet-ws/komet-desktop";

        List<Path> candidates = IkeServiceManager.pluginServiceLoaderCandidates(javaHome, userDir);

        assertTrue(candidates.contains(Path.of("/tmp/komet-ws/komet-desktop/target/plugin-service-loader")),
                "local-build candidate missing: " + candidates);
    }

    @Test
    void probeOrderIsJavaHomeThenParentThenTarget() {
        String javaHome = "/runtime";
        String userDir  = "/work/module";

        List<Path> candidates = IkeServiceManager.pluginServiceLoaderCandidates(javaHome, userDir);

        assertEquals(List.of(
                Path.of("/runtime/plugin-service-loader"),
                Path.of("/work/plugin-service-loader"),
                Path.of("/work/module/target/plugin-service-loader")
        ), candidates);
    }

    @Test
    void blankJavaHomeIsSkipped() {
        List<Path> candidates = IkeServiceManager.pluginServiceLoaderCandidates("   ", "/work/module");

        assertEquals(List.of(
                Path.of("/work/plugin-service-loader"),
                Path.of("/work/module/target/plugin-service-loader")
        ), candidates);
    }

    @Test
    void nullsAreSafe() {
        List<Path> candidates = IkeServiceManager.pluginServiceLoaderCandidates(null, null);

        assertTrue(candidates.isEmpty(), "candidates should be empty when both inputs are null");
    }
}
