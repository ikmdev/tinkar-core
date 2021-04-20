package org.hl7.tinkar.common.util.thread;

//~--- JDK imports ------------------------------------------------------------

import java.util.concurrent.ThreadFactory;

//~--- classes ----------------------------------------------------------------

/**
 * A factory for creating NamedThread objects.
 *
 * @author <a href="mailto:daniel.armbrust.list@gmail.com">Dan Armbrust</a>
 */
public class NamedThreadFactory
        implements ThreadFactory {
    /** The thread group. */
    private ThreadGroup threadGroup = null;

    /** The thread name prefix. */
    private String threadNamePrefix = null;

    /** The thread priority. */
    private final int threadPriority;

    /** The daemon. */
    private final boolean daemon;

    //~--- constructors --------------------------------------------------------

    /**
     * Instantiates a new named thread factory.
     *
     * @param daemon the daemon
     */
    public NamedThreadFactory(boolean daemon) {
        this(null, null, Thread.NORM_PRIORITY, daemon);
    }

    /**
     * Instantiates a new named thread factory.
     *
     * @param threadNamePrefix optional
     * @param daemon the daemon
     */
    public NamedThreadFactory(String threadNamePrefix, boolean daemon) {
        this(null, threadNamePrefix, Thread.NORM_PRIORITY, daemon);
    }

    /**
     * Instantiates a new named thread factory.
     *
     * @param threadGroup optional
     * @param threadNamePrefix optional
     */
    public NamedThreadFactory(ThreadGroup threadGroup, String threadNamePrefix) {
        this(threadGroup, threadNamePrefix, Thread.NORM_PRIORITY, true);
    }

    /**
     * Instantiates a new named thread factory.
     *
     * @param threadGroup optional
     * @param threadNamePrefix optional
     * @param threadPriority the thread priority
     * @param daemon the daemon
     */
    public NamedThreadFactory(ThreadGroup threadGroup, String threadNamePrefix, int threadPriority, boolean daemon) {
        super();
        this.threadGroup      = threadGroup;
        this.threadNamePrefix = threadNamePrefix;
        this.threadPriority   = threadPriority;
        this.daemon           = daemon;

        if ((threadGroup != null) && (threadGroup.getMaxPriority() < threadPriority)) {
            threadGroup.setMaxPriority(threadPriority);
        }
    }

    //~--- methods -------------------------------------------------------------

    /**
     * New thread.
     *
     * @param r the r
     * @return the thread
     */
    @Override
    public Thread newThread(Runnable r) {
        final Thread t = (this.threadGroup == null) ? new Thread(r)
                : new Thread(this.threadGroup, r);

        t.setName(((this.threadNamePrefix == null) ? ""
                : this.threadNamePrefix + " ") + t.getId());
        t.setPriority(this.threadPriority);
        t.setDaemon(this.daemon);
        return t;
    }
}

