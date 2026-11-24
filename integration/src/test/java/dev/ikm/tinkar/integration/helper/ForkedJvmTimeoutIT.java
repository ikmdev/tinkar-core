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
package dev.ikm.tinkar.integration.helper;

import dev.ikm.tinkar.fixtures.ForkedJvm;
import dev.ikm.tinkar.fixtures.TestTags;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stage that runs out of time says where its threads were, the virtual ones included, before it
 * is ended (IKE-Network/ike-issues#1405): a stage that hangs once in a thousand runs leaves
 * nothing else to find the cause by.
 */
@Tag(TestTags.STAGED)
class ForkedJvmTimeoutIT {

    private static final int WAITING = 40;

    @Test
    void aStageThatRunsOutOfTimeSaysWhereItsThreadsWere() {
        PrintStream console = System.out;
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        AssertionError error;
        System.setOut(new PrintStream(printed, true, StandardCharsets.UTF_8));
        try {
            error = assertThrows(AssertionError.class,
                    () -> ForkedJvm.run(StalledStage.class, new Properties(), Duration.ofSeconds(15)));
        } finally {
            System.setOut(console);
        }
        String output = printed.toString(StandardCharsets.UTF_8);
        console.print(output);

        assertTrue(error.getMessage().contains("Stage StalledStage did not finish within PT15S"), error::getMessage);
        assertTrue(error.getMessage().contains("where its threads were"), error::getMessage);
        assertTrue(output.contains("stage is stalled"), "the stage ran until it stalled");
        assertTrue(output.contains("StalledStage.waitsForMain"), "the platform thread's stack is in the output");
        assertTrue(output.contains("StalledStage.waitsForTheLock"), "the virtual threads' stack is in the output");
        assertTrue(output.contains(WAITING + " virtual threads"), "with the count of those that are at one place");
        assertEquals(1, Pattern.compile("^\\[StalledStage].*StalledStage\\.waitsForTheLock", Pattern.MULTILINE)
                .matcher(output).results().count(), "and their stack once, not once for each");
    }

    /** Threads that wait for each other: main for the virtual threads, each of them for the lock, its owner for main. */
    static class StalledStage implements ForkedJvm.Stage {
        private static final Object LOCK = new Object();

        @Override
        public void run(Properties in, Properties out) throws Exception {
            CountDownLatch locked = new CountDownLatch(1);
            Thread main = Thread.currentThread();
            Thread owner = new Thread(() -> waitsForMain(main, locked), "owner of the lock");
            owner.setDaemon(true);
            owner.start();
            locked.await();
            List<Thread> waiting = new ArrayList<>();
            for (int i = 0; i < WAITING; i++) {
                waiting.add(Thread.ofVirtual().start(StalledStage::waitsForTheLock));
            }
            System.out.println("the stage is stalled");
            for (Thread thread : waiting) {
                thread.join();
            }
        }

        private static void waitsForMain(Thread main, CountDownLatch locked) {
            synchronized (LOCK) {
                locked.countDown();
                try {
                    main.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private static void waitsForTheLock() {
            synchronized (LOCK) {
                System.out.println("never reached");
            }
        }
    }
}
