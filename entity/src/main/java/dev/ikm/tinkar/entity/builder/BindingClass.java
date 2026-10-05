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
package dev.ikm.tinkar.entity.builder;

import java.util.UUID;

/**
 * A class of bindings a knowledge set generates: a vocabulary someone works in as a whole,
 * such as the constructs of one expression language. A set declares each of its binding
 * classes once, with {@link KnowledgeSet#bindingClass(String)}, and its declarations bind
 * components into them with {@link ConceptBuilder#binding(BindingClass, String)} and
 * {@link PatternBuilder#binding(BindingClass, String)}.
 * <p>
 * A component may be bound in each class it belongs to, a concept shared by two expression
 * languages named in each. Within one class a component has one name, and a name one
 * component.
 *
 * <p>
 * A binding class generated into another package than the set's bindings, such as the
 * kernel tinkar-core commits into {@code dev.ikm.tinkar.terms}, names that package; the set's
 * bindings leave it out, and it is written on its own ({@link BindingsWriter#writeBindingClass}).
 *
 * @param setUuid     the identity of the set that declared the class
 * @param packageName the package the class is generated into, or {@code null} for the package
 *                    of the set's bindings
 * @param name        the generated class's simple name
 */
public record BindingClass(UUID setUuid, String packageName, String name) {

    /**
     * @throws IllegalArgumentException if the name is not a Java identifier, or the package
     *                                  is not a dotted sequence of them
     */
    public BindingClass {
        if (!isJavaIdentifier(name)) {
            throw new IllegalArgumentException("A binding class name must be a Java identifier: \"" + name + "\"");
        }
        if (packageName != null) {
            for (String segment : packageName.split("\\.", -1)) {
                if (!isJavaIdentifier(segment)) {
                    throw new IllegalArgumentException("Not a package name: \"" + packageName + "\"");
                }
            }
        }
    }

    /**
     * Whether the class is generated with the set's bindings, into their package.
     *
     * @param bindingsPackage the package of the set's bindings
     * @return whether the class belongs with them
     */
    public boolean generatedWith(String bindingsPackage) {
        return packageName == null || packageName.equals(bindingsPackage);
    }

    static boolean isJavaIdentifier(String text) {
        if (text == null || text.isEmpty() || !Character.isJavaIdentifierStart(text.charAt(0))) {
            return false;
        }
        for (int i = 1; i < text.length(); i++) {
            if (!Character.isJavaIdentifierPart(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
