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

public class AlertObject implements Comparable<AlertObject> {

    final UUID alertId = UUID.randomUUID();
    final int[] affectedComponents;
    final String alertTitle;
    final String alertDescription;
    final AlertType alertType;
    final Throwable throwable;
    final AlertCategory alertCategory;
    final Callable<Boolean> resolutionTester;
    final boolean advisory;
    private final List<AlertResolver> alertResolvers = new ArrayList<>();
    public AlertObject(String alertTitle, String alertDescription, AlertType alertType, AlertCategory alertCategory, int... affectedComponents) {
        this(alertTitle, alertDescription, alertType, null, alertCategory, null, affectedComponents);
    }
    public AlertObject(String alertTitle,
                       String alertDescription,
                       AlertType alertType,
                       Throwable throwable,
                       AlertCategory alertCategory,
                       Callable<Boolean> resolutionTester,
                       int... affectedComponents) {
        this(false, alertTitle, alertDescription, alertType, throwable, alertCategory, resolutionTester,
                affectedComponents);
    }

    private AlertObject(boolean advisory,
                        String alertTitle,
                        String alertDescription,
                        AlertType alertType,
                        Throwable throwable,
                        AlertCategory alertCategory,
                        Callable<Boolean> resolutionTester,
                        int... affectedComponents) {
        this.advisory = advisory;
        this.affectedComponents = affectedComponents;
        this.alertTitle = alertTitle;
        this.alertDescription = alertDescription;
        this.alertType = alertType;
        this.throwable = throwable;
        this.alertCategory = alertCategory;
        this.resolutionTester = resolutionTester;
    }

    public static AlertObject makeWarning(String alertTitle, String alertDescription) {
        return new AlertObject(alertTitle, alertDescription, AlertType.WARNING, AlertCategory.UNSPECIFIED);
    }

    /**
     * Makes an advisory: a warning that asks nothing of the person now. It tells of something
     * the software met and dealt with, as when an import adds UUIDs to a component, so it is
     * recorded in the {@link AlertLog} for reading later and no dialog interrupts the work.
     *
     * @param alertTitle       what happened, in a few words
     * @param alertDescription what happened, in full
     * @return the advisory
     */
    public static AlertObject makeAdvisory(String alertTitle, String alertDescription) {
        return new AlertObject(true, alertTitle, alertDescription, AlertType.WARNING, null,
                AlertCategory.UNSPECIFIED, null);
    }

    /**
     * @return true if this alert only needs recording: nothing waits on the person, so a
     * subscriber that shows dialogs leaves it to the {@link AlertLog}
     */
    public boolean isAdvisory() {
        return advisory;
    }

    public static AlertObject makeError(String alertTitle, String alertDescription, Throwable throwable) {
        return new AlertObject(alertTitle, alertDescription, AlertType.ERROR,
                throwable, AlertCategory.UNSPECIFIED, null);
    }

    public static AlertObject makeError(Throwable throwable) {
        return new AlertObject(throwable.getClass().getSimpleName(), throwable.getLocalizedMessage(), AlertType.ERROR,
                throwable, AlertCategory.UNSPECIFIED, null);
    }

    public Optional<Throwable> getThrowable() {
        return Optional.ofNullable(throwable);
    }

    public int[] getAffectedComponents() {
        return affectedComponents;
    }

    public String getAlertTitle() {
        return alertTitle;
    }

    public String getAlertDescription() {
        return alertDescription;
    }

    public AlertCategory getAlertCategory() {
        return alertCategory;
    }

    public Optional<Callable<Boolean>> getResolutionTester() {
        return Optional.ofNullable(resolutionTester);
    }

    public List<AlertResolver> getResolvers() {
        return alertResolvers;
    }

    public Boolean failCommit() {
        return getAlertType().preventsCheckerPass();
    }

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