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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each finding of {@link FacadeCheck}, and the code it must leave alone. Sources are compiled in
 * memory with the plugin, beside stand-ins for the {@code dev.ikm.tinkar.terms} facades, the proxy
 * and a binding enum.
 */
class FacadeCheckTest {

    private static final List<String> TERMS = List.of("""
            package dev.ikm.tinkar.terms;
            public interface EntityFacade {
                long nid();
                static boolean same(EntityFacade a, EntityFacade b) {
                    return a == b || (a != null && b != null && a.nid() == b.nid());
                }
            }
            """, """
            package dev.ikm.tinkar.terms;
            public interface ConceptFacade extends EntityFacade {}
            """, """
            package dev.ikm.tinkar.terms;
            public interface ConceptEnumerationFacade<E extends Enum<E>> extends ConceptFacade {}
            """, """
            package dev.ikm.tinkar.terms;
            public interface PatternEnumerationFacade<E extends Enum<E>> extends EntityFacade {}
            """, """
            package dev.ikm.tinkar.terms;
            public class EntityProxy implements EntityFacade {
                public long nid() { return 1; }
                public static class Concept extends EntityProxy implements ConceptFacade {
                    public Concept self() { return this; }
                }
            }
            """, """
            package dev.ikm.tinkar.terms;
            public enum KernelConcept implements ConceptEnumerationFacade<KernelConcept> {
                ENGLISH, SPANISH;
                public long nid() { return ordinal(); }
            }
            """);

    @TempDir
    Path output;

    // ---------- F1: == and != ----------

    @Test
    void identityComparisonOfComponentsIsReported() {
        List<String> findings = check("""
                package sample;
                import dev.ikm.tinkar.terms.*;
                import static dev.ikm.tinkar.terms.KernelConcept.ENGLISH;
                class Sample {
                    boolean blank(Object value, ConceptFacade concept, EntityProxy proxy) {
                        return value == ENGLISH || concept != KernelConcept.SPANISH || proxy == concept;
                    }
                }
                """);
        assertEquals(3, findings.size(), findings.toString());
        assertTrue(findings.stream().allMatch(finding -> finding.contains("F1")), findings.toString());
    }

    @Test
    void identityThatIsEqualityIsNotReported() {
        List<String> findings = check("""
                package sample;
                import dev.ikm.tinkar.terms.*;
                class Sample {
                    private ConceptFacade held;
                    boolean fine(KernelConcept a, KernelConcept b, ConceptFacade concept) {
                        return a == b || concept == null || null != concept
                                || concept.nid() == KernelConcept.ENGLISH.nid()
                                || EntityFacade.same(concept, KernelConcept.ENGLISH);
                    }
                    @Override
                    public boolean equals(Object other) {
                        return this == other;
                    }
                }
                """);
        assertEquals(List.of(), findings);
    }

    // ---------- F2, F3: equals ----------

    @Test
    void equalsOnABindingEnumIsReported() {
        List<String> findings = check("""
                package sample;
                import dev.ikm.tinkar.terms.*;
                import static dev.ikm.tinkar.terms.KernelConcept.ENGLISH;
                class Sample {
                    boolean english(Object value, ConceptFacade concept) {
                        return ENGLISH.equals(value) || KernelConcept.SPANISH.equals(concept);
                    }
                }
                """);
        assertEquals(2, findings.size(), findings.toString());
        assertTrue(findings.stream().allMatch(finding -> finding.contains("F2")), findings.toString());
    }

    @Test
    void equalsOnAFacadeInterfaceAndObjectsEqualsAreReported() {
        List<String> findings = check("""
                package sample;
                import dev.ikm.tinkar.terms.*;
                import java.util.Objects;
                class Sample {
                    <T extends ConceptFacade> boolean same(ConceptFacade concept, T typed, Object value) {
                        return concept.equals(KernelConcept.ENGLISH) || typed.equals(value)
                                || Objects.equals(value, KernelConcept.ENGLISH);
                    }
                }
                """);
        assertEquals(3, findings.size(), findings.toString());
        assertTrue(findings.stream().allMatch(finding -> finding.contains("F3")), findings.toString());
    }

    @Test
    void equalsThatDoesNotDependOnTheFormIsNotReported() {
        List<String> findings = check("""
                package sample;
                import dev.ikm.tinkar.terms.*;
                import java.util.Objects;
                class Sample {
                    boolean fine(EntityProxy proxy, KernelConcept a, KernelConcept b, String text, Object value) {
                        return proxy.equals(value) || a.equals(b) || text.equals(value)
                                || Objects.equals(a, b) || Objects.equals(text, value);
                    }
                }
                """);
        assertEquals(List.of(), findings);
    }

    // ---------- F4: EntityProxy in public signatures ----------

    @Test
    void entityProxyInAPublicSignatureIsReported() {
        List<String> findings = check("""
                package sample;
                import dev.ikm.tinkar.terms.*;
                import java.util.List;
                import java.util.function.Function;
                public class Sample {
                    public EntityProxy value;
                    public EntityProxy.Concept concept() { return null; }
                    protected void pick(List<? extends EntityProxy> options) {}
                    public Sample(Function<EntityProxy, String> renderer) {}
                }
                """);
        assertEquals(4, findings.size(), findings.toString());
        assertTrue(findings.stream().allMatch(finding -> finding.contains("F4")), findings.toString());
    }

    @Test
    void constantsPrivateMembersOverridesAndProxyTypesAreNotReported() {
        List<String> findings = check("""
                package sample;
                import dev.ikm.tinkar.terms.*;
                public class Sample {
                    public static final EntityProxy.Concept CONSTANT = null;
                    private EntityProxy held;
                    EntityProxy packagePrivate() { return held; }
                    public EntityFacade facade() { return held; }
                    @Override
                    public String toString() { return ""; }
                    public static class Special extends EntityProxy {
                        public EntityProxy self() { return this; }
                    }
                }
                """);
        assertEquals(List.of(), findings);
    }

    // ---------- F5: casts and instanceof ----------

    @Test
    void castAndInstanceofToAProxyAreReported() {
        List<String> findings = check("""
                package sample;
                import dev.ikm.tinkar.terms.*;
                class Sample {
                    long nid(Object value) {
                        if (value instanceof EntityProxy.Concept concept) {
                            return concept.nid();
                        }
                        return ((EntityProxy) value).nid();
                    }
                }
                """);
        assertEquals(2, findings.size(), findings.toString());
        assertTrue(findings.stream().allMatch(finding -> finding.contains("F5")), findings.toString());
    }

    @Test
    void castsToAFacadeAreNotReported() {
        List<String> findings = check("""
                package sample;
                import dev.ikm.tinkar.terms.*;
                class Sample {
                    long nid(Object value) {
                        if (value instanceof ConceptFacade concept) {
                            return concept.nid();
                        }
                        return ((EntityFacade) value).nid();
                    }
                }
                """);
        assertEquals(List.of(), findings);
    }

    // ---------- scope ----------

    @Test
    void testSourcesAreExemptFromF4AndF5ButNotFromF1() {
        List<String> findings = compile(List.of(), "/src/test/java/", """
                package sample;
                import dev.ikm.tinkar.terms.*;
                public class Sample {
                    public EntityProxy proxy() { return (EntityProxy) null; }
                    boolean blank(ConceptFacade concept) { return concept == KernelConcept.ENGLISH; }
                }
                """);
        assertEquals(1, findings.size(), findings.toString());
        assertTrue(findings.getFirst().contains("F1"), findings.toString());
    }

    @Test
    void allowedClassesAndTheTermsPackageAreNotChecked() {
        String source = """
                package sample.decoding;
                import dev.ikm.tinkar.terms.*;
                public class Decoder {
                    public EntityProxy decode(Object value) { return (EntityProxy) value; }
                }
                """;
        assertEquals(2, compile(List.of(), "/", source).size());
        assertEquals(List.of(), compile(List.of("allow=sample.decoding.,other."), "/", source));
    }

    @Test
    void generatedSourcesAreNotChecked() {
        List<String> findings = compile(List.of(), "/target/generated-sources/annotations/", """
                package sample;
                import dev.ikm.tinkar.terms.*;
                class Sample {
                    boolean blank(ConceptFacade concept) { return concept == KernelConcept.ENGLISH; }
                }
                """);
        assertEquals(List.of(), findings);
    }

    @Test
    void suppressWarningsSilencesTheDeclaration() {
        List<String> findings = check("""
                package sample;
                import dev.ikm.tinkar.terms.*;
                class Sample {
                    @SuppressWarnings("facade-check")
                    boolean showsTheHazard(ConceptFacade concept) { return KernelConcept.ENGLISH.equals(concept); }
                    @SuppressWarnings("unchecked")
                    boolean reported(ConceptFacade concept) { return KernelConcept.ENGLISH.equals(concept); }
                }
                """);
        assertEquals(1, findings.size(), findings.toString());
    }

    @Test
    void reportWarningMakesTheFindingsWarnings() {
        String source = """
                package sample;
                import dev.ikm.tinkar.terms.*;
                class Sample {
                    boolean blank(ConceptFacade concept) { return concept == KernelConcept.ENGLISH; }
                }
                """;
        assertEquals(List.of(Diagnostic.Kind.ERROR), kinds(List.of(), source));
        assertEquals(List.of(Diagnostic.Kind.WARNING), kinds(List.of("report=warning"), source));
    }

    @Test
    void withoutTheFacadeTypesNothingIsReported() {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavacTask task = task(List.of(source("/", """
                package sample;
                class Sample {
                    boolean same(Object a, Object b) { return a == b; }
                }
                """)), diagnostics);
        new FacadeCheck().init(task);
        assertTrue(task.call());
        assertEquals(List.of(), diagnostics.getDiagnostics());
    }

    // ---------- support ----------

    private List<String> check(String source) {
        return compile(List.of(), "/", source);
    }

    /** Compiles the source, at a path under {@code root}, beside the terms stand-ins. */
    private List<String> compile(List<String> args, String root, String source) {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavacTask task = task(files(root, source), diagnostics);
        new FacadeCheck().init(task, args.toArray(String[]::new));
        task.call();
        List<String> findings = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            String message = diagnostic.getMessage(Locale.ROOT);
            if (message.startsWith(FacadeCheck.PREFIX)) {
                findings.add(message);
            } else if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                throw new AssertionError("the sample does not compile: " + message);
            }
        }
        return findings;
    }

    private List<Diagnostic.Kind> kinds(List<String> args, String source) {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavacTask task = task(files("/", source), diagnostics);
        new FacadeCheck().init(task, args.toArray(String[]::new));
        task.call();
        return diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getMessage(Locale.ROOT).startsWith(FacadeCheck.PREFIX))
                .<Diagnostic.Kind>map(Diagnostic::getKind).toList();
    }

    private static List<JavaFileObject> files(String root, String source) {
        List<JavaFileObject> files = new ArrayList<>();
        TERMS.forEach(term -> files.add(source("/", term)));
        files.add(source(root, source));
        return files;
    }

    private JavacTask task(List<JavaFileObject> files, DiagnosticCollector<JavaFileObject> diagnostics) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        return (JavacTask) compiler.getTask(null, null, diagnostics, List.of("-d", output.toString()), null, files);
    }

    private static final Pattern PACKAGE = Pattern.compile("package ([\\w.]+);");
    private static final Pattern TYPE = Pattern.compile("(?m)^public (?:\\w+ )*?(?:class|interface|enum|record) (\\w+)"
            + "|^(?:class|interface|enum|record) (\\w+)");

    private static JavaFileObject source(String root, String text) {
        Matcher packageName = PACKAGE.matcher(text);
        Matcher typeName = TYPE.matcher(text);
        if (!packageName.find() || !typeName.find()) {
            throw new IllegalArgumentException("no package or type in " + text);
        }
        String name = typeName.group(1) != null ? typeName.group(1) : typeName.group(2);
        URI uri = URI.create("string://" + root + packageName.group(1).replace('.', '/') + "/" + name + ".java");
        return new SimpleJavaFileObject(uri, JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return text;
            }
        };
    }
}
