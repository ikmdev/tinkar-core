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

import dev.ikm.tinkar.common.util.broadcast.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The record of every alert sent to the root alert stream: the warnings and errors of a
 * knowledge base's startup, of its imports and of the work on it, kept so a person can read
 * them when they choose rather than answer a dialog for each.
 * <p>
 * The log keeps the alerts of this run in memory. While a knowledge base is open it also
 * appends them to {@value #FILE_NAME} in the knowledge base's folder, and reads back what
 * earlier runs wrote there, so what an import reported last week can still be read today.
 * An alert that repeats the one before it is counted on that entry, not listed again.
 * <p>
 * An {@linkplain AlertObject#isAdvisory() advisory} reaches a person only through this log;
 * any other alert may also be shown at once by a subscriber that presents dialogs.
 */
public final class AlertLog {
    private static final Logger LOG = LoggerFactory.getLogger(AlertLog.class);

    /** The file, in a knowledge base's folder, that keeps its alerts from one run to the next. */
    public static final String FILE_NAME = "warnings-and-errors.tsv";

    /** The most entries kept, in memory and in the file; the oldest go first. */
    public static final int LIMIT = 5_000;

    /** The most characters of a stack trace kept as an entry's detail. */
    private static final int DETAIL_LIMIT = 16_000;

    private static final String HEADER = "# Warnings and errors reported while this knowledge base was open:"
            + " time (ms since 1970), kind, title, description, detail.";

    private static final AlertLog ROOT = new AlertLog();
    private static boolean following;

    /**
     * One alert, or a run of the same alert.
     *
     * @param first       when the alert was first reported
     * @param last        when it was last reported; {@code first} unless it repeated
     * @param count       how many times in a row it was reported
     * @param type        whether it is a warning, an error or another kind
     * @param title       what happened, in a few words
     * @param description what happened, in full; empty if the alert gave none
     * @param detail      a stack trace, for an alert that carried a throwable; otherwise empty
     * @param earlierRun  true if an earlier run of the application reported it
     */
    public record Entry(Instant first, Instant last, int count, AlertType type, String title,
                        String description, String detail, boolean earlierRun) {

        /** @return true if the alert reports that something failed */
        public boolean isError() {
            return type == AlertType.ERROR;
        }

        private boolean sameAlert(Entry other) {
            return type == other.type && title.equals(other.title)
                    && description.equals(other.description) && detail.equals(other.detail);
        }

        private Entry repeatedBy(Entry next) {
            return new Entry(first, next.last, count + next.count, type, title, description, detail, earlierRun);
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    /** Kept here so the root stream's weak reference to it lasts as long as the log. */
    private final Subscriber<AlertObject> subscriber = this::record;
    private Path file;
    private BufferedWriter writer;
    private int unseen;

    /** Makes a log that records only what it is given; {@link #root()} is the one that follows the root stream. */
    public AlertLog() {
    }

    /**
     * @return the log of the root alert stream, which it follows from the first call on, so an
     * application calls this once as it starts
     */
    public static synchronized AlertLog root() {
        if (!following) {
            AlertStreams.getRoot().addSubscriberWithWeakReference(ROOT.subscriber);
            following = true;
        }
        return ROOT;
    }

    /**
     * Records an alert, and appends it to the knowledge base's file if one is open.
     *
     * @param alert the alert
     */
    public void record(AlertObject alert) {
        Instant now = Instant.now();
        String title = alert.getAlertTitle() == null ? "" : alert.getAlertTitle();
        String description = alert.getAlertDescription() == null ? "" : alert.getAlertDescription();
        String detail = alert.getThrowable().map(AlertLog::stackTrace).orElse("");
        Entry entry = new Entry(now, now, 1, alert.getAlertType(), title, description, detail, false);
        synchronized (this) {
            add(entry);
            unseen++;
            write(entry);
        }
        changed();
    }

    /** Adds an entry, counting it on the last one if it repeats it, and keeps to the limit. */
    private void add(Entry entry) {
        if (!entries.isEmpty()) {
            Entry previous = entries.getLast();
            if (previous.earlierRun() == entry.earlierRun() && previous.sameAlert(entry)) {
                entries.set(entries.size() - 1, previous.repeatedBy(entry));
                return;
            }
        }
        entries.add(entry);
        if (entries.size() > LIMIT) {
            entries.removeFirst();
        }
    }

    /** @return the entries, oldest first */
    public synchronized List<Entry> entries() {
        return List.copyOf(entries);
    }

    /** @return how many alerts were recorded since the log was last {@linkplain #markSeen() seen} */
    public synchronized int unseenCount() {
        return unseen;
    }

    /** Says a person has looked at the log, so nothing in it is new any more. */
    public void markSeen() {
        synchronized (this) {
            if (unseen == 0) {
                return;
            }
            unseen = 0;
        }
        changed();
    }

    /**
     * Starts keeping the log in a knowledge base's folder. Reads the alerts earlier runs wrote
     * there, then writes this run's alerts reported at or after {@code since}, which are the
     * ones of this knowledge base's startup, and every alert from now on.
     *
     * @param folder the knowledge base's folder
     * @param since  when this knowledge base began to open
     */
    public void open(Path folder, Instant since) {
        synchronized (this) {
            closeWriter();
            entries.removeIf(Entry::earlierRun);
            file = folder.resolve(FILE_NAME);
            List<Entry> thisRun = new ArrayList<>(entries);
            entries.clear();
            int linesRead = readEarlierRuns();
            try {
                if (linesRead > LIMIT) {
                    rewrite();
                }
                boolean isNew = !Files.exists(file);
                writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                if (isNew) {
                    writer.write(HEADER);
                    writer.newLine();
                    writer.flush();
                }
            } catch (IOException e) {
                LOG.warn("The warnings and errors of {} will not be kept: {}", folder, e.toString());
                writer = null;
            }
            for (Entry entry : thisRun) {
                add(entry);
                if (!entry.last().isBefore(since)) {
                    write(entry);
                }
            }
        }
        changed();
    }

    /** Stops keeping the log in a knowledge base's folder, and forgets what earlier runs wrote there. */
    public void close() {
        synchronized (this) {
            closeWriter();
            file = null;
            entries.removeIf(Entry::earlierRun);
        }
        changed();
    }

    /** Empties the log, and the knowledge base's file if one is open. */
    public void clear() {
        synchronized (this) {
            entries.clear();
            unseen = 0;
            if (file != null) {
                closeWriter();
                try {
                    writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    writer.write(HEADER);
                    writer.newLine();
                    writer.flush();
                } catch (IOException e) {
                    LOG.warn("Could not empty {}: {}", file, e.toString());
                    writer = null;
                }
            }
        }
        changed();
    }

    /**
     * Asks to be told of every change to the log. The listener runs on whichever thread
     * changed it, so a listener that draws moves to its own thread first.
     *
     * @param listener what to run after a change
     */
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    /** @param listener a listener no longer to be told of changes */
    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void changed() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                LOG.warn("A listener of the alert log failed", e);
            }
        }
    }

    /** Reads the file into the entries as an earlier run's. @return the lines that were alerts */
    private int readEarlierRuns() {
        if (!Files.exists(file)) {
            return 0;
        }
        int lines = 0;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                Entry entry = parse(line);
                if (entry != null) {
                    add(entry);
                    lines++;
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("Could not read {}: {}", file, e.toString());
        }
        return lines;
    }

    /** Writes the file afresh from the entries, which leaves out what passed the limit. */
    private void rewrite() throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (Entry entry : entries) {
            lines.add(format(entry));
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private void write(Entry entry) {
        if (writer == null) {
            return;
        }
        try {
            writer.write(format(entry));
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            LOG.warn("Could not write to {}; no more is kept there: {}", file, e.toString());
            closeWriter();
        }
    }

    private void closeWriter() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                LOG.warn("Could not close {}: {}", file, e.toString());
            }
            writer = null;
        }
    }

    /** One line for one entry; a run of the same alert is written with its count and both times. */
    static String format(Entry entry) {
        return entry.first().toEpochMilli() + "\t" + entry.last().toEpochMilli() + "\t" + entry.count()
                + "\t" + entry.type().name() + "\t" + escape(entry.title()) + "\t" + escape(entry.description())
                + "\t" + escape(entry.detail());
    }

    /** @return the entry a line of the file contains, or null for a comment or a line that is not one */
    static Entry parse(String line) {
        if (line.isBlank() || line.startsWith("#")) {
            return null;
        }
        String[] fields = line.split("\t", -1);
        if (fields.length != 7) {
            return null;
        }
        try {
            return new Entry(Instant.ofEpochMilli(Long.parseLong(fields[0])),
                    Instant.ofEpochMilli(Long.parseLong(fields[1])), Integer.parseInt(fields[2]),
                    AlertType.valueOf(fields[3]), unescape(fields[4]), unescape(fields[5]), unescape(fields[6]), true);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\t", "\\t").replace("\r\n", "\\n")
                .replace("\n", "\\n").replace("\r", "\\n");
    }

    private static String unescape(String text) {
        if (text.indexOf('\\') < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);
                out.append(switch (next) {
                    case 't' -> '\t';
                    case 'n' -> '\n';
                    default -> next;
                });
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String stackTrace(Throwable throwable) {
        StringWriter text = new StringWriter();
        throwable.printStackTrace(new PrintWriter(text));
        String trace = text.toString().strip();
        return trace.length() <= DETAIL_LIMIT ? trace : trace.substring(0, DETAIL_LIMIT) + "\n…";
    }
}
