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

/**
 * Listener interface for receiving notifications when an {@link AlertObject} is raised.
 * Implementations define how individual alerts are handled (e.g., displayed to the user,
 * logged, or forwarded to another subsystem).
 */
public interface AlertListener {

    /**
     * Invoked when an alert is raised that this listener should process.
     *
     * @param alert the {@link AlertObject} describing the alert condition
     */
    void handleAlert(AlertObject alert);
}
