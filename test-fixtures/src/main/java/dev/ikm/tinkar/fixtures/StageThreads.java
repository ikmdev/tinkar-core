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
package dev.ikm.tinkar.fixtures;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where the threads of a JVM that does not end are, read with its JDK's {@code jcmd}
 * (IKE-Network/ike-issues#1405).
 *
 * <p>Two dumps, since neither says everything. {@code Thread.print} gives the platform threads, a
 * virtual thread's carriers among them, with the class a thread is waiting to see initialized and
 * the deadlocks the JVM finds. {@code Thread.dump_to_file} gives the virtual threads, which an
 * import runs by the hundred. Those at one place with one lock are given once, with their count.
 */
final class StageThreads {

    /** The header of a virtual thread in the dump: its number, its name, its state. */
    private static final Pattern VIRTUAL = Pattern.compile("#(\\d+) \"([^\"]*)\" virtual (\\w+).*");

    /** How long {@code jcmd} may take: a JVM that answers at all answers at once. */
    private static final long ANSWER_SECONDS = 30;

    /** How many of the threads at one place are named. */
    private static final int NAMED = 5;

    private StageThreads() {
    }

    /**
     * The threads of the JVM, as lines of text. What cannot be read is said in place of it: the
     * caller is about to report a stage that ran out of time, and that report must be made.
     *
     * @param pid       the JVM's process
     * @param directory where to write the files the dumps pass through
     */
    static List<String> of(long pid, Path directory) {
        List<String> lines = new ArrayList<>();
        lines.add("The platform threads of the stage:");
        Path printed = directory.resolve("threads-printed.txt");
        lines.addAll(jcmd(pid, printed, "Thread.print", "-l"));
        lines.add("The virtual threads of the stage:");
        Path dumped = directory.resolve("threads-dumped.txt");
        List<String> answer = jcmd(pid, directory.resolve("threads-answer.txt"),
                "Thread.dump_to_file", "-overwrite", dumped.toString());
        lines.addAll(Files.exists(dumped) ? virtualThreads(read(dumped)) : answer);
        return lines;
    }

    /** The virtual threads of the dump, grouped by state, stack and locks, the largest group first. */
    static List<String> virtualThreads(List<String> dump) {
        Map<List<String>, List<String>> numbersByPlace = new LinkedHashMap<>();
        for (int i = 0; i < dump.size(); i++) {
            Matcher header = VIRTUAL.matcher(dump.get(i));
            if (!header.matches()) {
                continue;
            }
            List<String> place = new ArrayList<>();
            place.add(header.group(3));
            while (i + 1 < dump.size() && !dump.get(i + 1).isBlank()) {
                place.add(dump.get(++i));
            }
            numbersByPlace.computeIfAbsent(place, _ -> new ArrayList<>()).add("#" + header.group(1)
                    + (header.group(2).isEmpty() ? "" : " \"" + header.group(2) + "\""));
        }
        List<String> lines = new ArrayList<>();
        numbersByPlace.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<List<String>, List<String>> entry) -> entry.getValue().size()).reversed())
                .forEach(entry -> {
                    List<String> numbers = entry.getValue();
                    lines.add(numbers.size() + (numbers.size() == 1 ? " virtual thread, " : " virtual threads, ")
                            + entry.getKey().getFirst() + ": " + String.join(", ", numbers.subList(0, Math.min(NAMED, numbers.size())))
                            + (numbers.size() > NAMED ? ", …" : ""));
                    lines.addAll(entry.getKey().subList(1, entry.getKey().size()));
                });
        if (lines.isEmpty()) {
            lines.add("none");
        }
        return lines;
    }

    /** What {@code jcmd} answers, or why it did not. */
    private static List<String> jcmd(long pid, Path answer, String... command) {
        Path bin = Path.of(System.getProperty("java.home"), "bin");
        Path jcmd = Files.exists(bin.resolve("jcmd.exe")) ? bin.resolve("jcmd.exe") : bin.resolve("jcmd");
        List<String> line = new ArrayList<>(List.of(jcmd.toString(), Long.toString(pid)));
        line.addAll(List.of(command));
        Process process = null;
        try {
            process = new ProcessBuilder(line).redirectErrorStream(true).redirectOutput(answer.toFile()).start();
            if (!process.waitFor(ANSWER_SECONDS, TimeUnit.SECONDS)) {
                return List.of("(the stage's JVM did not answer " + command[0] + " within " + ANSWER_SECONDS + " seconds)");
            }
            return read(answer);
        } catch (IOException e) {
            return List.of("(could not run " + jcmd + ": " + e.getMessage() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of("(interrupted while asking the stage's JVM for " + command[0] + ")");
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    private static List<String> read(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return List.of("(could not read " + file.getFileName() + ": " + e.getMessage() + ")");
        }
    }
}
