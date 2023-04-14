package dev.ikm.tinkar.common.service;

public class SimpleIndeterminateTracker extends TrackingCallable<Void> {
    boolean finished = false;
    String title;
    private Thread sleepingThread;

    public SimpleIndeterminateTracker(String title) {
        super(false, true);
        this.title = title;
        updateTitle(title + " executing");
    }

    public void finished() {
        this.finished = true;
        if (sleepingThread != null) {
            this.sleepingThread.interrupt();
        }
    }

    @Override
    protected Void compute() throws Exception {
        while (!finished) {
            try {
                this.sleepingThread = Thread.currentThread();
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                if (finished) {
                    break;
                }
            }
        }
        updateTitle(title + " completed");
        updateMessage("In " + durationString());
        return null;
    }
}
