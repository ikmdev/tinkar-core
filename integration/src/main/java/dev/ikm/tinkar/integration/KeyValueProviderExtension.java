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

import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ServiceKeys;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Abstract JUnit 5 extension that initializes the Tinkar entity provider for tests.
 * <p>
 * This extension solves the "No provider. Call Select provider prior to get()" error
 * by ensuring the Tinkar entity provider is initialized before tests run.
 * <p>
 * Subclasses specify which controller to use via {@link #getControllerName()}.
 * <p>
 * Usage: Add {@code @ExtendWith(OpenSpinedArrayKeyValueProvider.class)} or another
 * concrete subclass to test classes that access Tinkar entities.
 */
public abstract class KeyValueProviderExtension implements BeforeAllCallback, AfterAllCallback {
    private static final Logger LOG = LoggerFactory.getLogger(OpenSpinedArrayKeyValueProvider.class);
    private static final Object LOCK = new Object();
    private static int referenceCount = 0;

    /**
     * Returns the controller name to be used for this provider.
     * 
     * @return the controller name (e.g., "Open SpinedArrayStore")
     */
    protected abstract String getControllerName();

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        synchronized (LOCK) {
            referenceCount++;
            if (referenceCount == 1) {
                LOG.info("Initializing Tinkar provider for tests with controller: {}", getControllerName());

                // Clean up any stale lock files from previous test runs
                cleanupStaleLocks();

                // Clear any existing caches
                CachingService.clearAll();

                // Select and start provider
                PrimitiveData.selectControllerByName(getControllerName());

                // Only start if not already running
                if (!PrimitiveData.running()) {
                    PrimitiveData.start();
                    LOG.info(getControllerName() + " provider started successfully with controller: {}", getControllerName());
                } else {
                    LOG.info(getControllerName() + " provider already running, skipping start");
                }
            } else {
                LOG.info(getControllerName() + " provider already initialized (reference count: {})", referenceCount);
            }
        }
    }

    /**
     * Removes stale Lucene lock files from previous test runs.
     * This prevents LockObtainFailedException when tests don't shut down cleanly.
     */
    private void cleanupStaleLocks() {
        try {
            // Try to get the configured data root, but don't set a default yet
            String dataRootPath = System.getProperty(ServiceKeys.DATA_STORE_ROOT.name());

            if (dataRootPath == null) {
                // Check common test locations
                File targetDir = new File("target/spinedarrays");
                if (targetDir.exists()) {
                    dataRootPath = targetDir.getAbsolutePath();
                    LOG.info("Using detected data root: {}", dataRootPath);
                } else {
                    LOG.debug("No data root configured yet, skipping lock cleanup");
                    return;
                }
            }

            File dataRoot = new File(dataRootPath);
            Path lucenePath = Path.of(dataRoot.getPath(), "lucene");
            Path lockFile = lucenePath.resolve("write.lock");

            if (Files.exists(lockFile)) {
                LOG.info("Removing stale Lucene lock file: {}", lockFile);
                try {
                    Files.delete(lockFile);
                    LOG.info("Successfully removed lock file");
                } catch (IOException e) {
                    LOG.warn("Failed to delete lock file, will attempt forced cleanup", e);
                    // Try to forcibly delete
                    lockFile.toFile().delete();
                }
            } else {
                LOG.debug("No lock file found at: {}", lockFile);
            }
        } catch (Exception e) {
            LOG.warn("Failed to clean up stale lock files (this may cause test failures)", e);
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        synchronized (LOCK) {
            referenceCount--;
            LOG.info("KeyValueProviderExtension.afterAll - referenceCount now: {}", referenceCount);
            // Don't stop the provider between test classes - let it stay running
            // The provider will be stopped when the JVM exits
            // This prevents Lucene lock issues when test classes share the same provider instance
        }
    }
}
