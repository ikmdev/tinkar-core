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
package dev.ikm.tinkar.integration.langext.binding;

import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ServiceKeys;
import dev.ikm.tinkar.common.service.ServiceProperties;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.coordinate.Coordinates;
import dev.ikm.tinkar.coordinate.language.calculator.LanguageCalculator;
import dev.ikm.tinkar.coordinate.language.calculator.LanguageCalculatorWithCache;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.PatternEntityVersion;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.ext.binding.BindingHelper;
import dev.ikm.tinkar.ext.binding.GenerateJavaBindingTask;
import dev.ikm.tinkar.integration.TestConstants;
import org.eclipse.collections.api.factory.Lists;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.*;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import static dev.ikm.tinkar.integration.TestConstants.createFilePathInTarget;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GenerateJavaBindingIT {

    private static final File datastore = TestConstants.createFilePathInTargetFromClassName.apply(GenerateJavaBindingIT.class);
    public static final File PB_STARTER_DATA = createFilePathInTarget.apply("data/tinkar-starter-data-1.0.0-pb.zip");

    private StampCalculator stampCalculator;
    private LanguageCalculator languageCalculator;
    private final String className = "BindingTest";

    @BeforeAll
    public void setup() {
        CachingService.clearAll();
		ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, datastore);
		FileUtil.recursiveDelete(datastore);
        PrimitiveData.selectControllerByName(TestConstants.SA_STORE_OPEN_NAME);
        PrimitiveData.start();
		LoadEntitiesFromProtobufFile loadEntitiesFromProtobufFile = new LoadEntitiesFromProtobufFile(PB_STARTER_DATA);
		loadEntitiesFromProtobufFile.compute();

        stampCalculator = Coordinates.Stamp.DevelopmentLatest().stampCalculator();
        languageCalculator = LanguageCalculatorWithCache
                .getCalculator(Coordinates.Stamp.DevelopmentLatest(),
                        Lists.mutable.of(Coordinates.Language.UsEnglishFullyQualifiedName()).toImmutableList());
    }

    @AfterAll
    public void teardown() {
        PrimitiveData.stop();
    }

    @Test
    public void generateConceptAndPatternBinding() {
        File bindingJavaOutput = Path.of(System.getProperty("user.dir"))
                .resolve("target/generated-test-sources/")
                .resolve(className + ".java")
                .toFile();

        try (DataOutputStream dataOutputStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(bindingJavaOutput)))) {

            //Given a stream of Concepts and Patterns from the Tinkar Starter Data
            Stream.Builder<Entity<? extends EntityVersion>> conceptStreamBuilder = Stream.builder();
            Stream.Builder<Entity<? extends EntityVersion>> patternStreamBuilder = Stream.builder();
            PrimitiveData.get().forEachConceptNid(nid -> conceptStreamBuilder.add(EntityService.get().getEntityFast(nid)));
            PrimitiveData.get().forEachPatternNid(nid -> patternStreamBuilder.add(EntityService.get().getEntityFast(nid)));

            //When interpolating the concept and pattern streams to a java file
            GenerateJavaBindingTask generateJavaBindingTask = new GenerateJavaBindingTask(
                    conceptStreamBuilder.build(),
                    patternStreamBuilder.build(),
                    Stream.empty(),
                    "IKM Author",
                    "dev.ikm.tinkar.integration.langext.binding",
                    "BindingsTest",
                    UUID.randomUUID(),
                    interpolationConsumer -> {
                        try {
                            dataOutputStream.writeBytes(interpolationConsumer);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    },
                    new BindingHelper(languageCalculator, stampCalculator, fqn -> {
                        if (fqn.contains("+")){
                            return fqn.replace("+", " PLUS");
                        } else if (fqn.contains("/")){
                            return fqn.replace("/", "_SLASH_");
                        }
                        return fqn;
                    })
            );
            generateJavaBindingTask.call();

        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }

        //Then a java file should be written
        assertTrue(bindingJavaOutput.exists());
        assertTrue(bindingJavaOutput.isFile());
        assertTrue(bindingJavaOutput.length() > 0);
    }

}
