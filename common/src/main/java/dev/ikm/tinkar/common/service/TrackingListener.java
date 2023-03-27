package dev.ikm.tinkar.common.service;

public interface TrackingListener<V> {

    void updateValue(V result);

    void updateMessage(String message);

    void updateTitle(String title);

    void updateProgress(double workDone, double max);



}
