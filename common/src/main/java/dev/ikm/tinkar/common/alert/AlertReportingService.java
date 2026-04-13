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
package dev.ikm.tinkar.common.alert;

import dev.ikm.tinkar.common.util.broadcast.Subscriber;

/**
 * Service interface for reporting alerts to the user or to a logging subsystem.
 * Extends {@link Subscriber} so that it can subscribe to {@link AlertObject} broadcasts
 * from an {@link dev.ikm.tinkar.common.util.broadcast.Broadcaster}.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader} through
 * {@link AlertReportingServiceFinder}.
 */
public interface AlertReportingService extends Subscriber<AlertObject> {

    /**
     * Returns the platform's {@link AlertReportingService} implementation discovered
     * by {@link AlertReportingServiceFinder}.
     *
     * @return the singleton {@link AlertReportingService} provider
     */
    static AlertReportingService provider() {
        return AlertReportingServiceFinder.INSTANCE.get();
    }
}
