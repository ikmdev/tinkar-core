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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Immutable value object representing a single alert raised by the system.
 * Each alert carries a unique identifier, a title and description, a severity
 * ({@link AlertType}), an optional originating exception, an {@link AlertCategory},
 * an optional resolution tester, and zero or more {@link AlertResolver} strategies.
 *
 * <p>Instances are {@link Comparable} by their randomly-generated {@link UUID} alert
 * identifiers, providing a stable but arbitrary ordering.
 */
public class AlertObject implements Comparable<AlertObject> {

    /** Unique identifier for this alert instance. */
    final UUID alertId = UUID.randomUUID();
    /** Native identifiers of the components affected by this alert. */
    final int[] affectedComponents;
    /** Short human-readable title for this alert. */
    final String alertTitle;
    /** Detailed description of the alert condition. */
    final String alertDescription;
    /** The severity level of this alert. */
    final AlertType alertType;
    /** The optional exception that caused this alert. */
    final Throwable throwable;
    /** The category classifying the origin of this alert. */
    final AlertCategory alertCategory;
    /** Optional callable that tests whether the alert condition has been resolved. */
    final Callable<Boolean> resolutionTester;
    /** Mutable list of resolution strategies associated with this alert. */
    private final List<AlertResolver> alertResolvers = new ArrayList<>();

    /**
     * Constructs an {@code AlertObject} without an associated throwable or resolution tester.
     *
     * @param alertTitle         short human-readable title for this alert
     * @param alertDescription   detailed description of the alert condition
     * @param alertType          the severity level of the alert
     * @param alertCategory      the category classifying the origin of the alert
     * @param affectedComponents native identifiers of the affected components
     */
    public AlertObject(String alertTitle, String alertDescription, AlertType alertType, AlertCategory alertCategory, int... affectedComponents) {
        this(alertTitle, alertDescription, alertType, null, alertCategory, null, affectedComponents);
    }

    /**
     * Constructs a fully specified {@code AlertObject}.
     *
     * @param alertTitle         short human-readable title for this alert
     * @param alertDescription   detailed description of the alert condition
     * @param alertType          the severity level of the alert
     * @param throwable          the exception that caused this alert, or {@code null}
     * @param alertCategory      the category classifying the origin of the alert
     * @param resolutionTester   a callable that tests whether the alert has been resolved, or {@code null}
     * @param affectedComponents native identifiers of the affected components
     */
    public AlertObject(String alertTitle,
                       String alertDescription,
                       AlertType alertType,
                       Throwable throwable,
                       AlertCategory alertCategory,
                       Callable<Boolean> resolutionTester,
                       int... affectedComponents) {
        this.affectedComponents = affectedComponents;
        this.alertTitle = alertTitle;
        this.alertDescription = alertDescription;
        this.alertType = alertType;
        this.throwable = throwable;
        this.alertCategory = alertCategory;
        this.resolutionTester = resolutionTester;
    }

    /**
     * Factory method that creates a warning-level alert with an {@link AlertCategory#UNSPECIFIED} category.
     *
     * @param alertTitle       short title for the warning
     * @param alertDescription detailed description of the warning condition
     * @return a new {@code AlertObject} with {@link AlertType#WARNING}
     */
    public static AlertObject makeWarning(String alertTitle, String alertDescription) {
        return new AlertObject(alertTitle, alertDescription, AlertType.WARNING, AlertCategory.UNSPECIFIED);
    }

    /**
     * Factory method that creates an error-level alert from a title, description, and throwable.
     *
     * @param alertTitle       short title for the error
     * @param alertDescription detailed description of the error condition
     * @param throwable        the exception that caused the error
     * @return a new {@code AlertObject} with {@link AlertType#ERROR}
     */
    public static AlertObject makeError(String alertTitle, String alertDescription, Throwable throwable) {
        return new AlertObject(alertTitle, alertDescription, AlertType.ERROR,
                throwable, AlertCategory.UNSPECIFIED, null);
    }

    /**
     * Factory method that creates an error-level alert directly from a throwable, using
     * its class name as the title and its localized message as the description.
     *
     * @param throwable the exception to convert into an alert
     * @return a new {@code AlertObject} with {@link AlertType#ERROR}
     */
    public static AlertObject makeError(Throwable throwable) {
        return new AlertObject(throwable.getClass().getSimpleName(), throwable.getLocalizedMessage(), AlertType.ERROR,
                throwable, AlertCategory.UNSPECIFIED, null);
    }

    /**
     * Returns the throwable associated with this alert, if any.
     *
     * @return an {@link Optional} containing the throwable, or empty if none
     */
    public Optional<Throwable> getThrowable() {
        return Optional.ofNullable(throwable);
    }

    /**
     * Returns the native identifiers of the components affected by this alert.
     *
     * @return an array of affected component identifiers
     */
    public int[] getAffectedComponents() {
        return affectedComponents;
    }

    /**
     * Returns the short human-readable title for this alert.
     *
     * @return the alert title
     */
    public String getAlertTitle() {
        return alertTitle;
    }

    /**
     * Returns the detailed description of the alert condition.
     *
     * @return the alert description
     */
    public String getAlertDescription() {
        return alertDescription;
    }

    /**
     * Returns the category classifying the origin of this alert.
     *
     * @return the {@link AlertCategory}
     */
    public AlertCategory getAlertCategory() {
        return alertCategory;
    }

    /**
     * Returns the optional callable that tests whether this alert's condition has been resolved.
     *
     * @return an {@link Optional} containing the resolution tester, or empty if none
     */
    public Optional<Callable<Boolean>> getResolutionTester() {
        return Optional.ofNullable(resolutionTester);
    }

    /**
     * Returns the list of {@link AlertResolver} strategies associated with this alert.
     *
     * @return the mutable list of resolvers
     */
    public List<AlertResolver> getResolvers() {
        return alertResolvers;
    }

    /**
     * Determines whether this alert should prevent a commit from succeeding.
     *
     * @return {@code true} if this alert's type prevents a checker pass, {@code false} otherwise
     */
    public Boolean failCommit() {
        return getAlertType().preventsCheckerPass();
    }

    /**
     * Returns the severity level of this alert.
     *
     * @return the {@link AlertType}
     */
    public AlertType getAlertType() {
        return alertType;
    }

    @Override
    public int compareTo(AlertObject o) {
        return this.alertId.compareTo(o.alertId);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + ", alertTitle=" + alertTitle + ", alertType=" + alertType +
                ", alertDescription=" + alertDescription + ", resolvers=" + alertResolvers + ", resolutionTester="
                + resolutionTester + " " + Arrays.toString(affectedComponents);
    }
}