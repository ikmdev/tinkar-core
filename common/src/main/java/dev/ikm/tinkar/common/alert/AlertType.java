package dev.ikm.tinkar.common.alert;

/**
 * A subset of javafx.scene.control.Alert.AlertType
 *
 * @author kec
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

    private boolean alertPreventsCommit;

    private AlertType(boolean alertPreventsCommit) {
        this.alertPreventsCommit = alertPreventsCommit;
    }

    /**
     * For integration of alerts into the Commit API, we need to know if an alert is fatal to a commit or not.
     *
     * @return
     */
    public boolean preventsCheckerPass() {
        return this.alertPreventsCommit;
    }
}

