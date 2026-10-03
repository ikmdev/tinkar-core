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
package dev.ikm.tinkar.fixtures;

/**
 * The JUnit tags this working set uses to say what a test is for, so that a build runs
 * the tests its purpose calls for and leaves the rest out, rather than skipping them.
 * A test that cannot run in the default build carries one of these and is excluded
 * there; {@code @Disabled} is for a test under repair, with the issue named.
 *
 * <p>Use the constant where the module depends on test-fixtures and the same literal
 * where it does not.
 */
public final class TestTags {

    private TestTags() {
    }

    /**
     * Needs SNOMED CT test data, which only some machines can resolve. The reasoner
     * integration tests carry it; {@code -Psnomed} in tinkar-core's reasoner modules
     * unpacks the data and runs them.
     */
    public static final String SNOMED = "snomed";

    /**
     * Needs something outside the build: a network service, an API key. Excluded by
     * default; {@code -Pexternal} includes it, and the test still checks for what it
     * needs.
     */
    public static final String EXTERNAL = "external";

    /**
     * Takes more than one JVM. A JVM starts the store services once, so a test that
     * needs a second store lifetime — close and reopen, or write from one store and read
     * into another — runs each lifetime as a stage in a spawned JVM, with
     * {@link ForkedJvm}. The older producer and consumer pairs, one class per stage in
     * class-name order, carry the tag too. Staged tests that need only starter data run in
     * the default build.
     */
    public static final String STAGED = "staged";

    /** Measures time or size and asserts a bound or records a baseline. */
    public static final String PERFORMANCE = "performance";

    /**
     * The provider conformance suite: one set of tests run against every
     * {@code PrimitiveDataService} implementation, from whichever module owns one.
     */
    public static final String CONFORMANCE = "conformance";
}
