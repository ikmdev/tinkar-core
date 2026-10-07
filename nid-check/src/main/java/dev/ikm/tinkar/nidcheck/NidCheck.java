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

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A javac plugin that fails the build on the nid hazards the compiler accepts when nids widen from
 * {@code int} to {@code long} (design {@code design-2026-09-30-64-bit-nids}, "What the compiler
 * will not catch"). It reports four findings, each as an error:
 * <ol>
 *   <li><b>A {@code long} passed to a collection of {@code Integer}</b>: a {@code long} or
 *   {@code Long} argument to {@code contains}, {@code remove}, {@code indexOf} or
 *   {@code lastIndexOf} of a {@code Collection<Integer>}, or to {@code get}, {@code containsKey},
 *   {@code remove} or {@code getOrDefault} of a {@code Map<Integer, ?>}. Those methods take
 *   {@code Object}, so the {@code long} boxes to a {@code Long}, which equals no {@code Integer}:
 *   the lookup compiles and never finds what was stored.</li>
 *   <li><b>An {@code (int)} cast of a nid outside {@code Nid}</b>: a cast to {@code int} of a
 *   {@code long} expression named as a nid ({@code nid}, {@code conceptNid}, {@code nid()},
 *   {@code nidForPublicId(...)}). It yields the element sequence of a 64-bit nid without a trace;
 *   {@code Nid.narrowChecked} is the sanctioned narrowing.</li>
 *   <li><b>{@code Nid.narrowChecked} outside the classes allowed to narrow</b>: the providers that
 *   keep {@code int} nids, and the format 1 entity codec.</li>
 * </ol>
 * <p>Use: {@code -Xplugin:"NidCheck allow=<prefix>,<prefix>"} with the plugin on the processor
 * path. Each prefix is matched against the fully qualified name of the top-level class that holds
 * the call, so a package prefix such as {@code dev.ikm.tinkar.provider.} allows a whole package
 * tree. {@code Nid} itself is always allowed. {@code report=warning} reports the findings as
 * warnings, for a census of a working set that does not yet pass; the default is
 * {@code report=error}.
 */
public final class NidCheck implements Plugin {

    /** The name given to {@code -Xplugin}. */
    public static final String NAME = "NidCheck";

    /** Prefixes every message, so the findings can be searched for in a build log. */
    public static final String PREFIX = "[nid-check] ";

    static final String NID_CLASS = "dev.ikm.tinkar.common.id.Nid";

    /** A name that says it holds a nid: {@code nid} at its start or {@code Nid} at a camel-case boundary. */
    private static final Pattern NID_NAME = Pattern.compile("^(nid|.*Nid|.*NID)([A-Z0-9_].*)?$");

    private static final Set<String> COLLECTION_LOOKUPS = Set.of("contains", "remove", "indexOf", "lastIndexOf");
    private static final Set<String> MAP_LOOKUPS = Set.of("get", "containsKey", "remove", "getOrDefault");

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void init(JavacTask task, String... args) {
        List<String> allowed = new ArrayList<>();
        Diagnostic.Kind kind = Diagnostic.Kind.ERROR;
        for (String arg : args) {
            if (arg.equals("report=warning")) {
                kind = Diagnostic.Kind.WARNING;
            } else if (arg.equals("report=error")) {
                kind = Diagnostic.Kind.ERROR;
            } else if (arg.startsWith("allow=")) {
                for (String prefix : arg.substring("allow=".length()).split(",")) {
                    if (!prefix.isBlank()) {
                        allowed.add(prefix.strip());
                    }
                }
            } else if (!arg.isBlank()) {
                throw new IllegalArgumentException(PREFIX + "unknown argument: " + arg);
            }
        }
        Trees trees = Trees.instance(task);
        Types types = task.getTypes();
        Elements elements = task.getElements();
        Diagnostic.Kind reportKind = kind;
        task.addTaskListener(new TaskListener() {
            @Override
            public void finished(TaskEvent event) {
                if (event.getKind() == TaskEvent.Kind.ANALYZE) {
                    new Scanner(trees, types, elements, event.getCompilationUnit(), List.copyOf(allowed), reportKind)
                            .scan(new TreePath(event.getCompilationUnit()), null);
                }
            }
        });
    }

    /** Whether a name says it holds a nid. */
    static boolean isNidName(String name) {
        return NID_NAME.matcher(name).matches();
    }

    private static final class Scanner extends TreePathScanner<Void, Void> {
        private final Trees trees;
        private final Types types;
        private final CompilationUnitTree unit;
        private final List<String> allowed;
        private final Diagnostic.Kind reportKind;
        private final TypeMirror integerType;
        private final TypeMirror longType;
        private final TypeMirror objectType;
        private final TypeMirror collectionOfInteger;
        private final TypeMirror mapOfInteger;
        private String topLevelClass = "";

        Scanner(Trees trees, Types types, Elements elements, CompilationUnitTree unit, List<String> allowed,
                Diagnostic.Kind reportKind) {
            this.trees = trees;
            this.types = types;
            this.unit = unit;
            this.allowed = allowed;
            this.reportKind = reportKind;
            this.integerType = elements.getTypeElement("java.lang.Integer").asType();
            this.longType = elements.getTypeElement("java.lang.Long").asType();
            this.objectType = elements.getTypeElement("java.lang.Object").asType();
            TypeElement collection = elements.getTypeElement("java.util.Collection");
            TypeElement map = elements.getTypeElement("java.util.Map");
            this.collectionOfInteger = types.getDeclaredType(collection, integerType);
            this.mapOfInteger = types.getDeclaredType(map, integerType, types.getWildcardType(null, null));
        }

        @Override
        public Void visitClass(ClassTree node, Void unused) {
            if (getCurrentPath().getParentPath().getLeaf().getKind() == Tree.Kind.COMPILATION_UNIT) {
                Element element = trees.getElement(getCurrentPath());
                if (element instanceof TypeElement type) {
                    topLevelClass = type.getQualifiedName().toString();
                }
            }
            return super.visitClass(node, unused);
        }

        @Override
        public Void visitBinary(BinaryTree node, Void unused) {
            if ((node.getKind() == Tree.Kind.EQUAL_TO || node.getKind() == Tree.Kind.NOT_EQUAL_TO)
                    && !topLevelClass.equals(NID_CLASS)) {
                String sentinel = sentinelName(node.getRightOperand());
                ExpressionTree nidSide = node.getLeftOperand();
                if (sentinel == null) {
                    sentinel = sentinelName(node.getLeftOperand());
                    nidSide = node.getRightOperand();
                }
                if (sentinel != null && namesANid(nidSide)) {
                    report(node, "nid compared with " + sentinel + "; the sentinel has an int form and a long form "
                            + "that mean the same, so test with Nid.isNotApplicable or Nid.isNone");
                }
            }
            return super.visitBinary(node, unused);
        }

        /** The name of the sentinel a constant holds, or null if it holds none. */
        private String sentinelName(ExpressionTree expression) {
            Element element = trees.getElement(new TreePath(getCurrentPath(), expression));
            if (element == null || element.getKind() != ElementKind.FIELD
                    || !(((VariableElement) element).getConstantValue() instanceof Number value)) {
                return null;
            }
            if (value instanceof Integer || value instanceof Long) {
                long number = value.longValue();
                if (number == Integer.MAX_VALUE || number == Long.MAX_VALUE) {
                    return "the not-applicable sentinel (" + element.getSimpleName() + ")";
                }
                if (number == Integer.MIN_VALUE || number == Long.MIN_VALUE) {
                    return "the none sentinel (" + element.getSimpleName() + ")";
                }
            }
            return null;
        }

        @Override
        public Void visitTypeCast(TypeCastTree node, Void unused) {
            if (!topLevelClass.equals(NID_CLASS)
                    && typeOf(node.getType()).getKind() == TypeKind.INT
                    && isLong(typeOf(node.getExpression()))
                    && namesANid(node.getExpression())) {
                report(node, "(int) cast of a nid; a 64-bit nid would be truncated to its element sequence. "
                        + "Use Nid.narrowChecked where narrowing is allowed, or keep the nid a long");
            }
            return super.visitTypeCast(node, unused);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
            Element element = trees.getElement(getCurrentPath());
            if (element instanceof ExecutableElement method) {
                checkNarrowChecked(node, method);
                checkIntegerCollectionLookup(node, method);
            }
            return super.visitMethodInvocation(node, unused);
        }

        private void checkNarrowChecked(MethodInvocationTree node, ExecutableElement method) {
            if (method.getSimpleName().contentEquals("narrowChecked")
                    && method.getEnclosingElement() instanceof TypeElement owner
                    && owner.getQualifiedName().contentEquals(NID_CLASS)
                    && !topLevelClass.equals(NID_CLASS)
                    && allowed.stream().noneMatch(topLevelClass::startsWith)) {
                report(node, "Nid.narrowChecked in " + topLevelClass
                        + ", which is not allowed to narrow nids: only the providers that keep int nids "
                        + "and the format 1 entity codec are");
            }
        }

        private void checkIntegerCollectionLookup(MethodInvocationTree node, ExecutableElement method) {
            if (!(node.getMethodSelect() instanceof MemberSelectTree select) || node.getArguments().isEmpty()) {
                return;
            }
            String name = method.getSimpleName().toString();
            TypeMirror receiver = typeOf(select.getExpression());
            if (!(receiver instanceof DeclaredType)) {
                return;
            }
            boolean lookup = (COLLECTION_LOOKUPS.contains(name) && types.isAssignable(receiver, collectionOfInteger))
                    || (MAP_LOOKUPS.contains(name) && types.isAssignable(receiver, mapOfInteger));
            if (!lookup) {
                return;
            }
            List<? extends VariableElement> parameters = method.getParameters();
            ExpressionTree argument = node.getArguments().getFirst();
            if (!parameters.isEmpty() && types.isSameType(parameters.getFirst().asType(), objectType)
                    && isLong(typeOf(argument))) {
                report(argument, "a long passed to " + name + " of a collection of Integer; it boxes to a Long, "
                        + "which equals no Integer, so the lookup never finds what was stored");
            }
        }

        private boolean namesANid(ExpressionTree expression) {
            Element element = trees.getElement(new TreePath(getCurrentPath(), expression));
            if (element == null && expression instanceof com.sun.source.tree.ParenthesizedTree parenthesized) {
                return namesANid(parenthesized.getExpression());
            }
            return element != null && isNidName(element.getSimpleName().toString());
        }

        private boolean isLong(TypeMirror type) {
            return type.getKind() == TypeKind.LONG || types.isSameType(type, longType);
        }

        private TypeMirror typeOf(Tree tree) {
            TypeMirror type = trees.getTypeMirror(new TreePath(getCurrentPath(), tree));
            return type == null ? types.getNoType(TypeKind.NONE) : type;
        }

        private void report(Tree tree, String message) {
            trees.printMessage(reportKind, PREFIX + message, tree, unit);
        }
    }
}
