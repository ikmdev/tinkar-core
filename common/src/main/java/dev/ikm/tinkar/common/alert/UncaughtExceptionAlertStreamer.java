package dev.ikm.tinkar.common.alert;

public class UncaughtExceptionAlertStreamer implements Thread.UncaughtExceptionHandler {
    @Override
    public void uncaughtException(Thread t, Throwable e) {
        AlertStreams.getRoot().dispatch(AlertObject.makeError(e));
    }
}

// dev.ikm.tinkar.common.alert.UncaughtExceptionAlertStreamer