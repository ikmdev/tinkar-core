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
package dev.ikm.tinkar.nidcheck;

import com.sun.source.util.JavacTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each finding of {@link NidCheck}, and the code it must leave alone. Sources are compiled in
 * memory with the plugin, beside a stand-in for {@code dev.ikm.tinkar.common.id.Nid}.
 */
class NidCheckTest {

    private static final String NID = """
            package dev.ikm.tinkar.common.id;
            public interface Nid {
                long NOT_APPLICABLE_64 = Long.MAX_VALUE;
                static boolean isNotApplicable(long nid) {
                    return nid == Integer.MAX_VALUE || nid == NOT_APPLICABLE_64;
                }
                static int narrowChecked(long nid) {
                    int narrowed = (int) nid;
                    if (narrowed != nid) {
                        throw new IllegalStateException();
                    }
                    return narrowed;
                }
            }
            """;

    @TempDir
    Path output;

    // ---------- a long passed to a collection of Integer ----------

    @Test
    void longPassedToContainsOfASetOfIntegerIsReported() {
        List<String> findings = check("""
                package sample;
                import java.util.Set;
                class Sample {
                    boolean member(Set<Integer> nids, long conceptNid) {
                        return nids.contains(conceptNid);
                    }
                }
                """);
        assertEquals(1, findings.size(), findings.toString());
        assertTrue(findings.getFirst().contains("a long passed to contains of a collection of Integer"));
    }

    @Test
    void longPassedToGetOfAMapWithIntegerKeysIsReported() {
        List<String> findings = check("""
                package sample;
                import java.util.Map;
                class Sample {
                    String name(Map<Integer, String> names, long nid) {
                        return names.get(nid);
                    }
                }
                """);
        assertEquals(1, findings.size(), findings.toString());
        assertTrue(findings.getFirst().contains("get"));
    }

    @Test
    void boxedLongAndEveryLookupOfSubtypesAreReported() {
        List<String> findings = check("""
                package sample;
                import java.util.ArrayList;
                import java.util.HashMap;
                import java.util.concurrent.ConcurrentHashMap;
                class Sample {
                    void lookups(ArrayList<Integer> list, HashMap<Integer, String> map,
                                 ConcurrentHashMap<Integer, String> concurrent, Long boxed, long nid) {
                        list.contains(boxed);
                        list.remove(boxed);
                        list.indexOf(nid);
                        list.lastIndexOf(nid);
                        map.containsKey(nid);
                        map.remove(nid);
                        map.getOrDefault(nid, "");
                        concurrent.get(nid);
                    }
                }
                """);
        assertEquals(8, findings.size(), findings.toString());
    }

    @Test
    void lookupsThatCanMatchAreNotReported() {
        List<String> findings = check("""
                package sample;
                import java.util.List;
                import java.util.Map;
                import java.util.Set;
                class Sample {
                    void lookups(Set<Integer> ints, Set<Long> longs, Map<Long, String> byLong,
                                 Map<String, Integer> byName, List<Integer> list, int nid, long longNid) {
                        ints.contains(nid);
                        longs.contains(longNid);
                        byLong.get(longNid);
                        byName.containsValue(longNid);
                        list.remove(nid);
                    }
                }
                """);
        assertEquals(List.of(), findings);
    }

    // ---------- an (int) cast of a nid ----------

    @Test
    void intCastOfANidIsReported() {
        List<String> findings = check("""
                package sample;
                class Sample {
                    interface Component { long nid(); }
                    int narrow(Component component, long conceptNid, long nid) {
                        int a = (int) component.nid();
                        int b = (int) conceptNid;
                        int c = (int) (nid);
                        return a + b + c;
                    }
                }
                """);
        assertEquals(3, findings.size(), findings.toString());
        assertTrue(findings.getFirst().contains("(int) cast of a nid"));
    }

    @Test
    void intCastsOfOtherLongsAreNotReported() {
        List<String> findings = check("""
                package sample;
                class Sample {
                    int narrow(long time, long unidentified, long count, int intNid, float x) {
                        return (int) time + (int) unidentified + (int) count + (int) intNid + (int) x;
                    }
                }
                """);
        assertEquals(List.of(), findings);
    }

    @Test
    void nidMayCastItself() {
        assertEquals(List.of(), check());
    }

    // ---------- narrowChecked outside the allowed classes ----------

    @Test
    void narrowCheckedOutsideTheAllowedClassesIsReported() {
        List<String> findings = check(List.of("allow=sample.provider."), """
                package sample;
                import dev.ikm.tinkar.common.id.Nid;
                class Sample {
                    int narrow(long nid) {
                        return Nid.narrowChecked(nid);
                    }
                }
                """);
        assertEquals(1, findings.size(), findings.toString());
        assertTrue(findings.getFirst().contains("Nid.narrowChecked in sample.Sample"));
    }

    @Test
    void narrowCheckedInAnAllowedClassIsNotReported() {
        List<String> findings = check(List.of("allow=sample.provider.,sample.codec.EntityCodec1"), """
                package sample.provider;
                import static dev.ikm.tinkar.common.id.Nid.narrowChecked;
                class Store {
                    int key(long nid) {
                        return narrowChecked(nid);
                    }
                }
                """, """
                package sample.codec;
                import dev.ikm.tinkar.common.id.Nid;
                class EntityCodec1 {
                    static class Writer {
                        int write(long nid) {
                            return Nid.narrowChecked(nid);
                        }
                    }
                }
                """);
        assertEquals(List.of(), findings);
    }

    @Test
    void withNoAllowanceOnlyNidMayNarrow() {
        List<String> findings = check("""
                package sample.provider;
                import dev.ikm.tinkar.common.id.Nid;
                class Store {
                    int key(long nid) {
                        return Nid.narrowChecked(nid);
                    }
                }
                """);
        assertEquals(1, findings.size(), findings.toString());
    }

    // ---------- a nid compared with a sentinel ----------

    @Test
    void nidComparedWithEitherFormOfASentinelIsReported() {
        List<String> findings = check("""
                package sample;
                class Sample {
                    static final long WILDCARD = Long.MAX_VALUE;
                    boolean test(int patternNid, long nid, long conceptNid) {
                        return patternNid != Integer.MAX_VALUE
                                || nid == Long.MAX_VALUE
                                || Integer.MIN_VALUE == conceptNid
                                || nid == Long.MIN_VALUE
                                || conceptNid == WILDCARD;
                    }
                }
                """);
        assertEquals(5, findings.size(), findings.toString());
        assertTrue(findings.getFirst().contains("Nid.isNotApplicable or Nid.isNone"));
    }

    @Test
    void otherComparisonsAreNotReported() {
        List<String> findings = check("""
                package sample;
                class Sample {
                    boolean test(long nid, long time, int count, long otherNid) {
                        return time == Long.MAX_VALUE
                                || count != Integer.MIN_VALUE
                                || nid == 0
                                || nid == -1
                                || nid == otherNid
                                || nid < Integer.MAX_VALUE;
                    }
                }
                """);
        assertEquals(List.of(), findings);
    }

    // ---------- the plugin as javac finds it ----------

    @Test
    void javacLoadsThePluginByName() throws Exception {
        Path classes = Path.of(NidCheck.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<String> options = List.of("-d", output.toString(), "-processorpath", classes.toString(),
                "-Xplugin:" + NidCheck.NAME + " allow=sample.provider.");
        List<String> findings = compile(options, null, """
                package sample;
                import java.util.Set;
                class Sample {
                    boolean member(Set<Integer> nids, long nid) {
                        return nids.contains(nid);
                    }
                }
                """);
        assertEquals(1, findings.size(), findings.toString());
    }

    @Test
    void findingsAreErrorsUnlessAWarningIsAskedFor() {
        String sample = """
                package sample;
                class Sample {
                    int narrow(long nid) {
                        return (int) nid;
                    }
                }
                """;
        assertEquals(List.of(Diagnostic.Kind.ERROR), kinds(List.of(), sample));
        assertEquals(List.of(Diagnostic.Kind.WARNING), kinds(List.of("report=warning"), sample));
    }

    @Test
    void anUnknownArgumentIsRefused() {
        JavacTask task = task(List.of("-d", output.toString()), List.of(source(NID)), new DiagnosticCollector<>());
        IllegalArgumentException refused = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new NidCheck().init(task, "allowed=sample."));
        assertTrue(refused.getMessage().contains("allowed=sample."));
    }

    @Test
    void namesThatSayNid() {
        for (String name : List.of("nid", "conceptNid", "patternNid", "nidForPublicId", "NID", "STAMP_NID", "nid64")) {
            assertTrue(NidCheck.isNidName(name), name);
        }
        for (String name : List.of("nids", "conceptNids", "unidentified", "android", "nidus", "count", "time", "id")) {
            assertFalse(NidCheck.isNidName(name), name);
        }
    }

    // ---------- support ----------

    private List<String> check(String... sources) {
        return check(List.of(), sources);
    }

    private List<String> check(List<String> args, String... sources) {
        return compile(List.of("-d", output.toString()), args, sources);
    }

    /**
     * Compiles the sources beside the {@code Nid} stand-in and returns the plugin's findings.
     * With {@code args}, the plugin is installed directly; without, javac must load it by name.
     */
    private List<String> compile(List<String> options, List<String> args, String... sources) {
        List<JavaFileObject> files = new ArrayList<>();
        files.add(source(NID));
        for (String source : sources) {
            files.add(source(source));
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavacTask task = task(options, files, diagnostics);
        if (args != null) {
            new NidCheck().init(task, args.toArray(String[]::new));
        }
        task.call();
        List<String> findings = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            String message = diagnostic.getMessage(Locale.ROOT);
            if (message.startsWith(NidCheck.PREFIX)) {
                findings.add(message);
            } else if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                throw new AssertionError("the sample does not compile: " + message);
            }
        }
        return findings;
    }

    private List<Diagnostic.Kind> kinds(List<String> args, String source) {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavacTask task = task(List.of("-d", output.toString()), List.of(source(NID), source(source)), diagnostics);
        new NidCheck().init(task, args.toArray(String[]::new));
        task.call();
        return diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getMessage(Locale.ROOT).startsWith(NidCheck.PREFIX))
                .<Diagnostic.Kind>map(Diagnostic::getKind).toList();
    }

    private static JavacTask task(List<String> options, List<JavaFileObject> files,
                                  DiagnosticCollector<JavaFileObject> diagnostics) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        return (JavacTask) compiler.getTask(null, null, diagnostics, options, null, files);
    }

    private static JavaFileObject source(String text) {
        String packageName = text.lines().filter(line -> line.startsWith("package "))
                .findFirst().orElseThrow().substring("package ".length()).replace(";", "").strip();
        String className = text.lines().map(String::strip)
                .filter(line -> line.matches("(public )?(final )?(class|interface|record) \\w+.*"))
                .findFirst().orElseThrow().replaceAll("^(public )?(final )?(class|interface|record) (\\w+).*", "$4");
        URI uri = URI.create("string:///" + packageName.replace('.', '/') + "/" + className + ".java");
        return new SimpleJavaFileObject(uri, JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return text;
            }
        };
    }
}
