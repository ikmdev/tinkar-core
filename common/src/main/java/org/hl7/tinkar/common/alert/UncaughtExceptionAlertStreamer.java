package org.hl7.tinkar.common.alert;

public class UncaughtExceptionAlertStreamer implements Thread.UncaughtExceptionHandler {
    @Override
    public void uncaughtException(Thread t, Throwable e) {
        AlertStreams.getRoot().dispatch(AlertObject.makeError(e));
    }
}

// org.hl7.tinkar.common.alert.UncaughtExceptionAlertStreamer