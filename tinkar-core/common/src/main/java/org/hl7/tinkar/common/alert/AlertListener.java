package org.hl7.tinkar.common.alert;

public interface AlertListener {
    void handleAlert(AlertObject alert);
}
