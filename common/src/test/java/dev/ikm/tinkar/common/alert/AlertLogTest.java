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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The alert log records alerts, counts repeats, and keeps a knowledge base's alerts in its folder. */
class AlertLogTest {

    @Test
    void anAdvisoryIsAWarningThatInterruptsNoOne() {
        AlertObject advisory = AlertObject.makeAdvisory("UUIDs added to a component", "Component 7 gained one.");
        assertTrue(advisory.isAdvisory());
        assertEquals(AlertType.WARNING, advisory.getAlertType());
        assertFalse(AlertObject.makeWarning("Some items stayed in the Trash", "One file is open.").isAdvisory(),
                "a warning that answers a person's action is still shown to them");
        assertFalse(AlertObject.makeError(new IllegalStateException("no store")).isAdvisory());
    }

    @Test
    void recordsAlertsOldestFirstAndCountsTheUnseen() {
        AlertLog log = new AlertLog();
        AtomicInteger changes = new AtomicInteger();
        log.addListener(changes::incrementAndGet);

        log.record(AlertObject.makeAdvisory("UUIDs added to a component", "Component 7 gained one."));
        log.record(AlertObject.makeError(new IllegalStateException("no store")));

        List<AlertLog.Entry> entries = log.entries();
        assertEquals(2, entries.size());
        assertEquals("UUIDs added to a component", entries.get(0).title());
        assertFalse(entries.get(0).isError());
        assertTrue(entries.get(1).isError());
        assertEquals("no store", entries.get(1).description());
        assertTrue(entries.get(1).detail().contains("IllegalStateException"), "an error keeps its stack trace");
        assertEquals(2, log.unseenCount());
        assertEquals(2, changes.get());

        log.markSeen();
        assertEquals(0, log.unseenCount());
        assertEquals(2, log.entries().size(), "seen alerts stay in the log");
    }

    @Test
    void anAlertThatRepeatsTheOneBeforeItIsCountedNotListedAgain() {
        AlertLog log = new AlertLog();
        for (int i = 0; i < 3; i++) {
            log.record(AlertObject.makeWarning("Stylesheet failed", "No such file."));
        }
        log.record(AlertObject.makeWarning("Stylesheet failed", "Another file."));

        List<AlertLog.Entry> entries = log.entries();
        assertEquals(2, entries.size());
        assertEquals(3, entries.get(0).count());
        assertEquals(1, entries.get(1).count());
        assertEquals(4, log.unseenCount(), "every report is new until the log is seen");
    }

    @Test
    void keepsAKnowledgeBasesAlertsInItsFolderFromOneRunToTheNext(@TempDir Path folder) throws Exception {
        AlertLog firstRun = new AlertLog();
        firstRun.record(AlertObject.makeWarning("Before any knowledge base", "Not about this one."));
        Instant opening = Instant.now().plusMillis(1);
        Thread.sleep(5);
        firstRun.record(AlertObject.makeAdvisory("Reported while opening", "Line one.\nLine two\twith a tab \\ and a slash."));
        firstRun.open(folder, opening);
        IllegalStateException failure = new IllegalStateException("import failed");
        firstRun.record(AlertObject.makeError(failure));
        firstRun.record(AlertObject.makeError(failure));
        firstRun.close();
        assertTrue(Files.exists(folder.resolve(AlertLog.FILE_NAME)));

        AlertLog secondRun = new AlertLog();
        secondRun.open(folder, Instant.now());
        List<AlertLog.Entry> entries = secondRun.entries();
        assertEquals(2, entries.size(), "what was reported before the knowledge base began to open is not its own");
        assertEquals("Reported while opening", entries.get(0).title());
        assertEquals("Line one.\nLine two\twith a tab \\ and a slash.", entries.get(0).description());
        assertEquals(AlertType.WARNING, entries.get(0).type());
        assertTrue(entries.get(0).earlierRun());
        assertEquals("import failed", entries.get(1).description());
        assertEquals(2, entries.get(1).count(), "the repeat is counted when the file is read back too");
        assertTrue(entries.get(1).detail().contains("IllegalStateException"));
        assertEquals(0, secondRun.unseenCount(), "an earlier run's alerts are not new");

        secondRun.record(AlertObject.makeAdvisory("Reported today", ""));
        assertEquals(3, secondRun.entries().size());
        assertFalse(secondRun.entries().get(2).earlierRun());
        secondRun.close();
        assertEquals(1, secondRun.entries().size(), "a closed knowledge base takes its earlier alerts with it");
    }

    @Test
    void theSameErrorInTwoRunsStaysTwoEntries(@TempDir Path folder) {
        AlertLog firstRun = new AlertLog();
        firstRun.open(folder, Instant.now());
        firstRun.record(AlertObject.makeAdvisory("Components share UUIDs", "Two are one."));
        firstRun.close();

        AlertLog secondRun = new AlertLog();
        secondRun.open(folder, Instant.now());
        secondRun.record(AlertObject.makeAdvisory("Components share UUIDs", "Two are one."));
        assertEquals(2, secondRun.entries().size());
        secondRun.close();
    }

    @Test
    void clearEmptiesTheLogAndTheFile(@TempDir Path folder) {
        AlertLog log = new AlertLog();
        log.open(folder, Instant.now());
        log.record(AlertObject.makeAdvisory("Stamp versions dropped", "Kept two."));
        log.clear();
        assertTrue(log.entries().isEmpty());
        assertEquals(0, log.unseenCount());
        log.record(AlertObject.makeAdvisory("After the clear", ""));
        log.close();

        AlertLog next = new AlertLog();
        next.open(folder, Instant.now());
        assertEquals(1, next.entries().size());
        assertEquals("After the clear", next.entries().get(0).title());
        next.close();
    }

    @Test
    void aFileOverTheLimitIsCutToItsLatestEntries(@TempDir Path folder) throws Exception {
        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < AlertLog.LIMIT + 10; i++) {
            lines.append(AlertLog.format(new AlertLog.Entry(Instant.ofEpochMilli(i), Instant.ofEpochMilli(i), 1,
                    AlertType.WARNING, "Advisory " + i, "", "", true))).append('\n');
        }
        lines.append("not a line of the log\n");
        Files.writeString(folder.resolve(AlertLog.FILE_NAME), lines);

        AlertLog log = new AlertLog();
        log.open(folder, Instant.now());
        assertEquals(AlertLog.LIMIT, log.entries().size());
        assertEquals("Advisory 10", log.entries().get(0).title());
        log.close();
        long kept = Files.readAllLines(folder.resolve(AlertLog.FILE_NAME)).stream()
                .filter(line -> AlertLog.parse(line) != null).count();
        assertEquals(AlertLog.LIMIT, kept);
    }

    @Test
    void theRootLogFollowsTheRootAlertStream() {
        AlertLog root = AlertLog.root();
        int before = root.entries().stream().mapToInt(AlertLog.Entry::count).sum();
        AlertStreams.getRoot().dispatch(AlertObject.makeAdvisory("Sent to the root stream", "And recorded."));
        int after = root.entries().stream().mapToInt(AlertLog.Entry::count).sum();
        assertEquals(before + 1, after);
    }
}
