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
 * Severity levels for an {@link AlertObject}, modeled as a subset of
 * {@code javafx.scene.control.Alert.AlertType}. Each level carries a flag
 * indicating whether an alert of that severity should prevent a commit
 * from succeeding.
 */
public enum AlertType {
    /**
     * An information alert.
     */
    INFORMATION(false),

    /**
     * A warning alert.
     */
    WARNING(false),

    /**
     * An error alert.
     */
    ERROR(true),

    /**
     * A confirmation alert. Not sure about this one...
     * confirmation alerts would need some type of time out perhaps...
     */
    CONFIRMATION(false),

    /**
     * Indicate success of an activity such as a commit or another automated process.
     */
    SUCCESS(false);

    /** Whether an alert of this type prevents a commit from succeeding. */
    private boolean alertPreventsCommit;

    /**
     * Constructs an {@code AlertType} with the specified commit-blocking behavior.
     *
     * @param alertPreventsCommit {@code true} if alerts of this type should block commits
     */
    private AlertType(boolean alertPreventsCommit) {
        this.alertPreventsCommit = alertPreventsCommit;
    }

    /**
     * For integration of alerts into the Commit API, we need to know if an alert is fatal to a commit or not.
     *
     * @return {@code true} if an alert of this type should prevent a commit from succeeding
     */
    public boolean preventsCheckerPass() {
        return this.alertPreventsCommit;
    }
}

