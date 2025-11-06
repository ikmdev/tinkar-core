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

/**
 * JUnit 5 extension that initializes the Tinkar entity provider using
 * the Open SpinedArrayStore controller.
 * <p>
 * Usage: Add {@code @ExtendWith(OpenSpinedArrayKeyValueProvider.class)} to test classes.
 */
public class OpenSpinedArrayKeyValueProvider extends KeyValueProviderExtension {

    @Override
    protected String getControllerName() {
        return TestConstants.OPEN_SPINED_ARRAY_STORE;
    }
}