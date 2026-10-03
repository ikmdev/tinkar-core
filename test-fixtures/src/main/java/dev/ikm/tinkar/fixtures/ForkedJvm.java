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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Runs a stage of a test in a JVM of its own.
 *
 * <p>A JVM starts the store services once: after they are shut down they do not start
 * again. A test that needs more than one store lifetime — import and close; open and
 * export; import the export into a fresh store — therefore runs each lifetime as a
 * {@link Stage} in a spawned JVM, and stays one JUnit test that reads top to bottom:
 *
 * <pre>{@code
 * Properties imported = ForkedJvm.run(ImportStarterData.class, in);
 * Properties exported = ForkedJvm.run(ExportStore.class, imported);
 * Properties restored = ForkedJvm.run(ImportIntoFreshStore.class, exported);
 * assertEquals(exported.getProperty("count"), restored.getProperty("count"));
 * }</pre>
 *
 * <p>The JVM running the test never starts a store itself. A stage receives the
 * properties the test passes and returns the properties it sets; files it writes go where
 * those properties say. The child runs with the parent's class path and JVM options, on
 * the class path whichever way the parent was launched, and its output is printed through
 * the parent's, each line marked with its stage, so a test's log holds its stages' logs in
 * order. A stage that throws fails the
 * test with the stage's stack trace.
 */
public final class ForkedJvm {

    /** Code that runs in its own JVM. Needs a no-argument constructor; a nested class must be static. */
    @FunctionalInterface
    public interface Stage {
        /**
         * @param in  what the test passed to {@link ForkedJvm#run}
         * @param out what {@link ForkedJvm#run} returns to the test
         */
        void run(Properties in, Properties out) throws Exception;
    }

    /** How long a stage may run before the test fails, unless the test says otherwise. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);

    private ForkedJvm() {
    }

    /** Runs the stage in a new JVM and returns the properties it set. */
    public static Properties run(Class<? extends Stage> stage, Properties in) {
        return run(stage, in, DEFAULT_TIMEOUT);
    }

    /** Runs the stage in a new JVM, failing if it has not finished within the timeout. */
    public static Properties run(Class<? extends Stage> stage, Properties in, Duration timeout) {
        try {
            Path exchange = Files.createTempDirectory("forked-" + stage.getSimpleName() + "-");
            Path inFile = exchange.resolve("in.properties");
            Path outFile = exchange.resolve("out.properties");
            Path errorFile = exchange.resolve("error.txt");
            try (OutputStream stream = Files.newOutputStream(inFile)) {
                in.store(stream, stage.getName());
            }

            List<String> command = new ArrayList<>();
            command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            command.addAll(jvmOptions());
            command.add("-cp");
            command.add(classPath());
            command.add(ForkedJvm.class.getName());
            command.add(stage.getName());
            command.add(inFile.toString());
            command.add(outFile.toString());
            command.add(errorFile.toString());

            // Not inheritIO: under surefire the test JVM's native stdout is its channel to
            // Maven. The child's output is printed through System.out, which surefire
            // captures, so it lands in the test's own log, each line marked with its stage.
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String mark = "[" + stage.getSimpleName() + "] ";
            Thread pump = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                        System.out.println(mark + line);
                    }
                } catch (IOException e) {
                    System.out.println(mark + "output lost: " + e);
                }
            }, "forked-jvm-output-" + stage.getSimpleName());
            pump.setDaemon(true);
            pump.start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new AssertionError("Stage " + stage.getSimpleName() + " did not finish within " + timeout);
            }
            pump.join(TimeUnit.SECONDS.toMillis(10));
            if (process.exitValue() != 0) {
                String error = Files.exists(errorFile) ? Files.readString(errorFile) : "(the stage left no stack trace)";
                throw new AssertionError("Stage " + stage.getSimpleName() + " failed, exit code "
                        + process.exitValue() + ":\n" + error);
            }
            Properties out = new Properties();
            try (InputStream stream = Files.newInputStream(outFile)) {
                out.load(stream);
            }
            return out;
        } catch (IOException e) {
            throw new AssertionError("Could not run stage " + stage.getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while stage " + stage.getSimpleName() + " ran", e);
        }
    }

    /**
     * The entry point of the spawned JVM: stage class name, then the files for the
     * properties in, the properties out, and the stack trace of a failure.
     */
    public static void main(String[] args) {
        Path errorFile = Path.of(args[3]);
        int exitCode = 0;
        try {
            Properties in = new Properties();
            try (InputStream stream = Files.newInputStream(Path.of(args[1]))) {
                in.load(stream);
            }
            Constructor<?> constructor = Class.forName(args[0]).getDeclaredConstructor();
            constructor.setAccessible(true);
            Properties out = new Properties();
            ((Stage) constructor.newInstance()).run(in, out);
            try (OutputStream stream = Files.newOutputStream(Path.of(args[2]))) {
                out.store(stream, args[0]);
            }
        } catch (Throwable t) {
            exitCode = 1;
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(errorFile))) {
                t.printStackTrace(writer);
            } catch (IOException e) {
                t.printStackTrace();
            }
        }
        // Service threads a stage started must not keep the JVM, and the test, waiting.
        System.exit(exitCode);
    }

    /**
     * The parent's class path, module path and patched-in test classes as one class path,
     * so the child is launched the same way under Maven, which runs tests on the class
     * path, and under an IDE that runs them on the module path.
     */
    private static String classPath() {
        Set<String> entries = new LinkedHashSet<>();
        List<String> options = ManagementFactory.getRuntimeMXBean().getInputArguments();
        for (int i = 0; i < options.size(); i++) {
            String value = optionValue(options, i, "--patch-module");
            if (value != null && value.contains("=")) {
                addAll(entries, value.substring(value.indexOf('=') + 1));
            }
        }
        addAll(entries, System.getProperty("java.class.path"));
        addAll(entries, System.getProperty("jdk.module.path"));
        return String.join(File.pathSeparator, entries);
    }

    private static void addAll(Set<String> entries, String path) {
        if (path != null) {
            for (String entry : path.split(File.pathSeparator)) {
                if (!entry.isBlank()) {
                    entries.add(entry);
                }
            }
        }
    }

    /**
     * The parent's JVM options without the ones that describe a module graph (the child
     * runs on the class path) or attach a debugger (the port is taken).
     */
    private static List<String> jvmOptions() {
        List<String> kept = new ArrayList<>();
        List<String> options = ManagementFactory.getRuntimeMXBean().getInputArguments();
        for (int i = 0; i < options.size(); i++) {
            String option = options.get(i);
            if (option.startsWith("-agentlib:jdwp") || option.startsWith("-Xrunjdwp") || option.startsWith("-Xdebug")) {
                continue;
            }
            String name = option.contains("=") ? option.substring(0, option.indexOf('=')) : option;
            boolean takesValue = !option.contains("=");
            switch (name) {
                case "--module-path", "-p", "--patch-module", "--add-reads", "--add-opens", "--add-exports",
                     "--upgrade-module-path", "--limit-modules", "--module", "-m" -> {
                    if (takesValue) {
                        i++;
                    }
                }
                case "--add-modules" -> {
                    String modules = takesValue ? options.get(++i) : option.substring(option.indexOf('=') + 1);
                    // JDK modules outside the default graph (jdk.incubator.vector) are still wanted.
                    List<String> jdkModules = new ArrayList<>();
                    for (String module : modules.split(",")) {
                        if (module.startsWith("jdk.") || module.startsWith("java.")) {
                            jdkModules.add(module);
                        }
                    }
                    if (!jdkModules.isEmpty()) {
                        kept.add("--add-modules=" + String.join(",", jdkModules));
                    }
                }
                default -> kept.add(option);
            }
        }
        return kept;
    }

    /** The value of a two-form option ({@code --opt value} or {@code --opt=value}) at this index, or null. */
    private static String optionValue(List<String> options, int index, String name) {
        String option = options.get(index);
        if (option.equals(name) && index + 1 < options.size()) {
            return options.get(index + 1);
        }
        if (option.startsWith(name + "=")) {
            return option.substring(name.length() + 1);
        }
        return null;
    }
}
