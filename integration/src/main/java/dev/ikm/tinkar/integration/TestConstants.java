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
package dev.ikm.tinkar.integration;

import java.io.File;
import java.util.function.Function;

public class TestConstants {
    public static final Function<String, File> createFilePathInTarget = (pathName) -> new File("%s/target/%s".formatted(System.getProperty("user.dir"), pathName));

    public static final Function<Class, File>
            createFilePathInTargetFromClassName = (clazz) -> createFilePathInTarget.apply("generated-datastores/%s".formatted(clazz.getSimpleName()));

    public static final File TINK_TEST_FILE = createFilePathInTarget.apply("data/tinkar-test-dto-1.1.0.zip");
    public static final File PB_TEST_FILE = createFilePathInTarget.apply("data/tinkar-export-test.pb.zip");
    public static final File PB_STARTER_DATA = createFilePathInTarget.apply("data/tinkar-starter-data-1.0.0-pb.zip");
    public static final File PB_STARTER_DATA_REASONED = createFilePathInTarget.apply("data/tinkar-starter-data-reasoned-1.0.0-pb.zip");

    public static final File PB_ROUNDTRIP_TEST_FILE = createFilePathInTarget.apply("data/tinkar-export-test-roundtrip.pb.zip");
    public static final File PB_PERFORMANCE_TEST_FILE = createFilePathInTarget.apply("data/tinkar-export-test-performance.pb.zip");

    public static final String MV_STORE_OPEN_NAME = "Open MV Store";
    public static final String EPHEMERAL_STORE_NAME = "Load Ephemeral Store";
    public static final String SA_STORE_OPEN_NAME = "Open SpinedArrayStore";
    public static final File SNOMED_CT_DATA = createFilePathInTarget.apply("data/snomedct-2023.09.01.zip");

}

