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
package dev.ikm.tinkar.common.service;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify the service lifecycle phase and optional sub-priority.
 * <p>
 * This annotation takes precedence over the {@link ServiceLifecycle#getLifecyclePhase()}
 * and {@link ServiceLifecycle#getSubPriority()} methods if present.
 * </p>
 * <p>
 * Command-line system properties take precedence over both the annotation and
 * interface methods.
 * </p>
 *
 * <h3>Example Usage</h3>
 * <pre>{@code
 * // Simple usage - just specify the phase
 * @LifecyclePhase(ServiceLifecyclePhase.DATA_STORAGE)
 * public class MyDatabaseService implements ServiceLifecycle {
 *     // Starts at effective priority 150 (100 + default 50)
 * }
 *
 * // With sub-priority for ordering within phase
 * @LifecyclePhase(value = ServiceLifecyclePhase.DATA_STORAGE, subPriority = 10)
 * public class ConnectionPoolService implements ServiceLifecycle {
 *     // Starts at effective priority 110 (100 + 10)
 *     // This will start before MyDatabaseService
 * }
 * }</pre>
 *
 * <h3>Command-Line Override</h3>
 * <pre>
 * # Override phase and sub-priority at runtime
 * java -Dservice.lifecycle.phase.MyDatabaseService=INFRASTRUCTURE \
 *      -Dservice.lifecycle.subpriority.MyDatabaseService=5 \
 *      -jar myapp.jar
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LifecyclePhase {

    /**
     * The lifecycle phase for this service.
     *
     * @return the lifecycle phase
     */
    ServiceLifecyclePhase value();

    /**
     * Sub-priority within the phase (0-99).
     * <p>
     * Lower values start first within the phase. Default is 50.
     * Only specify this if you have dependencies within the same phase.
     * </p>
     *
     * @return sub-priority within phase (default: 50)
     */
    int subPriority() default 50;
}