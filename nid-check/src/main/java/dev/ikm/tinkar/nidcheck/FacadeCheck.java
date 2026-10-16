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
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A javac plugin that reports the comparisons and declarations that let one component, reached in
 * two forms, be treated as two (evaluation {@code 2026-10-15-entityproxy-facade-evaluation} in
 * {@code ~/ike-dev/notes}). A component reaches code as an {@code EntityProxy}, a binding enum
 * ({@code KernelConcept}, a {@code ConceptEnumerationFacade} or {@code PatternEnumerationFacade})
 * or an entity record; {@code ==} is false whenever the forms differ, and {@code equals} answers by
 * the receiver's class ({@code Enum.equals} is identity). {@code EntityFacade.same(a, b)} compares
 * by nid whatever the forms. It reports five findings:
 * <ol>
 *   <li><b>F1, {@code ==} or {@code !=} on a component</b>: either operand's static type is an
 *   {@code EntityFacade}. Not reported: two operands of the same enum type (identity is their
 *   equality), {@code null}, and {@code this} (the shortcut at the top of an {@code equals}).</li>
 *   <li><b>F2, {@code equals} called on a binding enum</b>: the receiver's static type is a
 *   {@code ConceptEnumerationFacade} or {@code PatternEnumerationFacade} and the argument's is not
 *   that enum type. It is true only when the argument is the enum constant itself.</li>
 *   <li><b>F3, {@code equals} whose answer depends on the receiver's class</b>: called on a
 *   receiver whose static type is a facade interface (or a type variable bounded by one), which at
 *   run time may be an enum; or {@code Objects.equals} with a facade argument.</li>
 *   <li><b>F4, {@code EntityProxy} in a public or protected signature</b>: a method's return or
 *   parameter type, or a field's type, that mentions {@code EntityProxy} (or a nested
 *   {@code EntityProxy.X}) anywhere, type arguments included. Not reported: {@code static final}
 *   fields (constants), overriding methods (their signature is the supertype's), members of a
 *   proxy type itself, and test sources.</li>
 *   <li><b>F5, a cast or {@code instanceof} to {@code EntityProxy}</b>: either skips or throws
 *   for an enum or a record of the same component. Not reported in a proxy type itself or in test
 *   sources.</li>
 * </ol>
 * <p>Use: {@code -Xplugin:"FacadeCheck report=warning allow=<prefix>,<prefix>"} with the plugin on
 * the processor path. Each prefix is matched against the fully qualified name of the top-level
 * class, and a matching class is not checked at all: the decoders and constant holders that need
 * proxies. {@code dev.ikm.tinkar.terms.} is always allowed. {@code report=warning} reports the
 * findings as warnings, for a census; the default is {@code report=error}. Where the facade types
 * are not on the compile path (a module below {@code terms}) the plugin reports nothing.
 * Test sources are recognized by {@code /src/test/} in their path; generated sources
 * ({@code /generated-sources/}) are not checked. {@code @SuppressWarnings("facade-check")} on a
 * declaration suppresses the findings inside it, for code that shows a hazard on purpose.
 */
public final class FacadeCheck implements Plugin {

    /** The name given to {@code -Xplugin}. */
    public static final String NAME = "FacadeCheck";

    /** Prefixes every message, so the findings can be searched for in a build log. */
    public static final String PREFIX = "[facade-check] ";

    static final String TERMS_PACKAGE = "dev.ikm.tinkar.terms.";
    static final String ENTITY_FACADE = TERMS_PACKAGE + "EntityFacade";
    static final String ENTITY_PROXY = TERMS_PACKAGE + "EntityProxy";
    static final Set<String> ENUMERATION_FACADES = Set.of(
            TERMS_PACKAGE + "ConceptEnumerationFacade", TERMS_PACKAGE + "PatternEnumerationFacade");

    /** The {@code @SuppressWarnings} value that silences the findings in a declaration. */
    public static final String SUPPRESSION = "facade-check";

    private static final String SAME = "EntityFacade.same(a, b)";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void init(JavacTask task, String... args) {
        List<String> allowed = new ArrayList<>(List.of(TERMS_PACKAGE));
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
                    FacadeTypes facadeTypes = FacadeTypes.find(types, elements);
                    if (facadeTypes != null && !isGenerated(event.getCompilationUnit())) {
                        new Scanner(trees, types, facadeTypes, event.getCompilationUnit(), List.copyOf(allowed),
                                reportKind).scan(new TreePath(event.getCompilationUnit()), null);
                    }
                }
            }
        });
    }

    private static boolean isGenerated(CompilationUnitTree unit) {
        String path = unit.getSourceFile().toUri().getPath();
        return path != null && path.contains("/generated-sources/");
    }

    /** The erased facade types, or none when {@code terms} is not on the compile path. */
    private record FacadeTypes(TypeMirror entityFacade, TypeMirror entityProxy, List<TypeMirror> enumerations,
                               TypeMirror objects) {
        static FacadeTypes find(Types types, Elements elements) {
            TypeElement facade = elements.getTypeElement(ENTITY_FACADE);
            TypeElement proxy = elements.getTypeElement(ENTITY_PROXY);
            if (facade == null || proxy == null) {
                return null;
            }
            List<TypeMirror> enumerations = new ArrayList<>();
            for (String name : ENUMERATION_FACADES) {
                TypeElement enumeration = elements.getTypeElement(name);
                if (enumeration != null) {
                    enumerations.add(types.erasure(enumeration.asType()));
                }
            }
            return new FacadeTypes(types.erasure(facade.asType()), types.erasure(proxy.asType()),
                    List.copyOf(enumerations), elements.getTypeElement("java.util.Objects").asType());
        }
    }

    private static final class Scanner extends TreePathScanner<Void, Void> {
        private final Trees trees;
        private final Types types;
        private final FacadeTypes facades;
        private final CompilationUnitTree unit;
        private final List<String> allowed;
        private final Diagnostic.Kind reportKind;
        private final boolean testSource;
        private String topLevelClass = "";
        private boolean skipped;
        private boolean inProxyType;

        Scanner(Trees trees, Types types, FacadeTypes facades, CompilationUnitTree unit, List<String> allowed,
                Diagnostic.Kind reportKind) {
            this.trees = trees;
            this.types = types;
            this.facades = facades;
            this.unit = unit;
            this.allowed = allowed;
            this.reportKind = reportKind;
            this.testSource = unit.getSourceFile().toUri().getPath() != null
                    && unit.getSourceFile().toUri().getPath().contains("/src/test/");
        }

        @Override
        public Void visitClass(ClassTree node, Void unused) {
            Element element = trees.getElement(getCurrentPath());
            if (getCurrentPath().getParentPath().getLeaf().getKind() == Tree.Kind.COMPILATION_UNIT
                    && element instanceof TypeElement type) {
                topLevelClass = type.getQualifiedName().toString();
                skipped = allowed.stream().anyMatch(topLevelClass::startsWith);
            }
            if (skipped) {
                return null;
            }
            boolean outer = inProxyType;
            inProxyType = outer || (element != null && isProxy(element.asType()));
            try {
                return super.visitClass(node, unused);
            } finally {
                inProxyType = outer;
            }
        }

        // ---------- F1: == and != ----------

        @Override
        public Void visitBinary(BinaryTree node, Void unused) {
            if (node.getKind() == Tree.Kind.EQUAL_TO || node.getKind() == Tree.Kind.NOT_EQUAL_TO) {
                ExpressionTree left = node.getLeftOperand();
                ExpressionTree right = node.getRightOperand();
                TypeMirror leftType = typeOf(left);
                TypeMirror rightType = typeOf(right);
                if ((isFacade(leftType) || isFacade(rightType))
                        && !isNullOrThis(left) && !isNullOrThis(right)
                        && !sameEnum(leftType, rightType)) {
                    report(node, "F1 " + node.getKind().name().toLowerCase().replace("_", " ")
                            + " comparison of components; it is false whenever the two are different forms "
                            + "(a proxy, a binding enum, a record) of one component. Use " + SAME
                            + " or compare nid()s");
                }
            }
            return super.visitBinary(node, unused);
        }

        // ---------- F2, F3: equals ----------

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
            if (trees.getElement(getCurrentPath()) instanceof ExecutableElement method
                    && method.getSimpleName().contentEquals("equals")) {
                if (method.getParameters().size() == 1 && node.getArguments().size() == 1
                        && node.getMethodSelect() instanceof MemberSelectTree select) {
                    checkEquals(node, select.getExpression(), node.getArguments().getFirst());
                } else if (method.getParameters().size() == 2 && node.getArguments().size() == 2
                        && method.getEnclosingElement() instanceof TypeElement owner
                        && types.isSameType(owner.asType(), facades.objects())) {
                    checkObjectsEquals(node, node.getArguments().get(0), node.getArguments().get(1));
                }
            }
            return super.visitMethodInvocation(node, unused);
        }

        private void checkEquals(MethodInvocationTree node, ExpressionTree receiver, ExpressionTree argument) {
            TypeMirror receiverType = typeOf(receiver);
            TypeMirror argumentType = typeOf(argument);
            if (isEnumeration(receiverType)) {
                if (!sameEnum(receiverType, argumentType)) {
                    report(node, "F2 equals called on a binding enum; Enum.equals is identity, so it is false "
                            + "for a proxy or a record of the same component. Use " + SAME);
                }
            } else if (isFacade(receiverType) && isInterfaceOrTypeVariable(receiverType)) {
                report(node, "F3 equals on a " + receiverType + ", whose answer depends on the receiver's "
                        + "class: a binding enum there equals only itself. Use " + SAME);
            }
        }

        private void checkObjectsEquals(MethodInvocationTree node, ExpressionTree first, ExpressionTree second) {
            TypeMirror firstType = typeOf(first);
            TypeMirror secondType = typeOf(second);
            if ((isFacade(firstType) || isFacade(secondType)) && !sameEnum(firstType, secondType)) {
                report(node, "F3 Objects.equals of components answers by the first argument's class: a "
                        + "binding enum there equals only itself. Use " + SAME);
            }
        }

        // ---------- F4: EntityProxy in signatures ----------

        @Override
        public Void visitMethod(MethodTree node, Void unused) {
            if (signatureChecked() && trees.getElement(getCurrentPath()) instanceof ExecutableElement method
                    && exposed(method) && !overrides(method)) {
                List<String> places = new ArrayList<>();
                if (method.getKind() != ElementKind.CONSTRUCTOR && mentionsProxy(method.getReturnType())) {
                    places.add("returns " + method.getReturnType());
                }
                method.getParameters().stream().filter(parameter -> mentionsProxy(parameter.asType()))
                        .forEach(parameter -> places.add("takes " + parameter.asType() + " " + parameter.getSimpleName()));
                if (!places.isEmpty()) {
                    report(node, "F4 " + method.getSimpleName() + " " + String.join(", ", places)
                            + "; a public signature should name the narrowest facade, which takes every "
                            + "form of a component. Convert with toProxy() where PublicId behaviour is needed");
                }
            }
            return super.visitMethod(node, unused);
        }

        @Override
        public Void visitVariable(VariableTree node, Void unused) {
            if (signatureChecked() && trees.getElement(getCurrentPath()) instanceof Element field
                    && field.getKind() == ElementKind.FIELD && exposed(field)
                    && !(field.getModifiers().contains(Modifier.STATIC) && field.getModifiers().contains(Modifier.FINAL))
                    && mentionsProxy(field.asType())) {
                report(node, "F4 field " + field.getSimpleName() + " of type " + field.asType()
                        + "; a public field should name the narrowest facade");
            }
            return super.visitVariable(node, unused);
        }

        private boolean signatureChecked() {
            return !testSource && !inProxyType;
        }

        private boolean exposed(Element element) {
            Set<Modifier> modifiers = element.getModifiers();
            return modifiers.contains(Modifier.PUBLIC) || modifiers.contains(Modifier.PROTECTED);
        }

        private boolean overrides(ExecutableElement method) {
            return method.getAnnotationMirrors().stream().anyMatch(annotation ->
                    annotation.getAnnotationType().toString().equals("java.lang.Override"));
        }

        private boolean mentionsProxy(TypeMirror type) {
            return switch (type) {
                case ArrayType array -> mentionsProxy(array.getComponentType());
                case WildcardType wildcard -> (wildcard.getExtendsBound() != null && mentionsProxy(wildcard.getExtendsBound()))
                        || (wildcard.getSuperBound() != null && mentionsProxy(wildcard.getSuperBound()));
                case DeclaredType declared -> isProxy(declared)
                        || declared.getTypeArguments().stream().anyMatch(this::mentionsProxy);
                default -> false;
            };
        }

        // ---------- F5: casts and instanceof ----------

        @Override
        public Void visitTypeCast(TypeCastTree node, Void unused) {
            if (signatureChecked() && isProxy(typeOf(node.getType()))) {
                report(node, "F5 cast to " + typeOf(node.getType()) + "; it throws for a binding enum or a record "
                        + "of the same component. Cast to the facade (EntityFacade, ConceptFacade, ...) and use "
                        + "toProxy() where a proxy is needed");
            }
            return super.visitTypeCast(node, unused);
        }

        @Override
        public Void visitInstanceOf(InstanceOfTree node, Void unused) {
            Tree type = node.getType();
            if (signatureChecked() && type != null && isProxy(typeOf(type))) {
                report(node, "F5 instanceof " + typeOf(type) + "; it is false for a binding enum or a record of "
                        + "the same component, so the branch is skipped for them. Test for the facade "
                        + "(ConceptFacade, PatternFacade, ...) instead");
            }
            return super.visitInstanceOf(node, unused);
        }

        // ---------- types ----------

        private boolean isFacade(TypeMirror type) {
            return isReference(type) && types.isAssignable(types.erasure(type), facades.entityFacade());
        }

        private boolean isProxy(TypeMirror type) {
            return isReference(type) && types.isAssignable(types.erasure(type), facades.entityProxy());
        }

        private boolean isEnumeration(TypeMirror type) {
            return isReference(type) && facades.enumerations().stream()
                    .anyMatch(enumeration -> types.isAssignable(types.erasure(type), enumeration));
        }

        /** Two operands of one enum type, whose identity is their equality. */
        private boolean sameEnum(TypeMirror a, TypeMirror b) {
            return a instanceof DeclaredType declared
                    && declared.asElement().getKind() == ElementKind.ENUM
                    && isReference(b) && types.isSameType(types.erasure(a), types.erasure(b));
        }

        private boolean isInterfaceOrTypeVariable(TypeMirror type) {
            return type instanceof TypeVariable
                    || (type instanceof DeclaredType declared && declared.asElement().getKind() == ElementKind.INTERFACE);
        }

        private static boolean isReference(TypeMirror type) {
            return type.getKind() == TypeKind.DECLARED || type.getKind() == TypeKind.TYPEVAR;
        }

        private static boolean isNullOrThis(ExpressionTree expression) {
            return expression.getKind() == Tree.Kind.NULL_LITERAL
                    || (expression instanceof IdentifierTree identifier && identifier.getName().contentEquals("this"));
        }

        private TypeMirror typeOf(Tree tree) {
            TypeMirror type = trees.getTypeMirror(new TreePath(getCurrentPath(), tree));
            return type == null ? types.getNoType(TypeKind.NONE) : type;
        }

        private void report(Tree tree, String message) {
            if (!suppressed()) {
                trees.printMessage(reportKind, PREFIX + message, tree, unit);
            }
        }

        /** Whether a declaration enclosing the current node says {@code @SuppressWarnings("facade-check")}. */
        private boolean suppressed() {
            for (TreePath path = getCurrentPath(); path != null; path = path.getParentPath()) {
                Tree.Kind kind = path.getLeaf().getKind();
                if (kind == Tree.Kind.METHOD || kind == Tree.Kind.VARIABLE || path.getLeaf() instanceof ClassTree) {
                    Element element = trees.getElement(path);
                    SuppressWarnings suppression = element == null ? null : element.getAnnotation(SuppressWarnings.class);
                    if (suppression != null && List.of(suppression.value()).contains(SUPPRESSION)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
