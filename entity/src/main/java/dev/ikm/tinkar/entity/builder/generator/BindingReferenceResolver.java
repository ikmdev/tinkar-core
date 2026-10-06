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
package dev.ikm.tinkar.entity.builder.generator;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.PatternFacade;
import dev.ikm.tinkar.terms.KernelTerm;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves an established identity to the Java source expression the ledger generator
 * (IKE-Network/ike-issues#869) should emit for it: a binding constant, such as
 * {@code KernelTerm.CONSTANT_NAME}, when one of the resolver's binding classes binds the
 * identity, or a declared-identity expression otherwise. Generated source then names the
 * kernel the way hand-authored code does, and states every other identity in full.
 */
public final class BindingReferenceResolver {

    private final Map<UUID, String> constantsByUuid;
    private final List<Class<?>> bindingClasses;

    private BindingReferenceResolver(Map<UUID, String> constantsByUuid, List<Class<?>> bindingClasses) {
        this.constantsByUuid = constantsByUuid;
        this.bindingClasses = bindingClasses;
    }

    /**
     * Builds a resolver over the kernel ({@link KernelTerm}).
     *
     * @return the built resolver
     */
    public static BindingReferenceResolver build() {
        return build(KernelTerm.class);
    }

    /**
     * Builds a resolver by reflecting over every public static {@link EntityFacade} field
     * the binding classes declare, once. Every UUID of every constant is registered, so a
     * lookup by any of a component's UUIDs finds its constant. Classes are taken in the
     * order given and fields in name order within each, so when two constants bind one
     * component, which name wins is deterministic across runs.
     *
     * @param bindingClasses the binding classes whose constants generated source may name
     * @return the built resolver
     */
    public static BindingReferenceResolver build(Class<?>... bindingClasses) {
        Map<UUID, String> constantsByUuid = new HashMap<>();
        for (Class<?> bindingClass : bindingClasses) {
            List<Field> fields = new ArrayList<>();
            for (Field field : bindingClass.getFields()) {
                if (Modifier.isStatic(field.getModifiers()) && EntityFacade.class.isAssignableFrom(field.getType())) {
                    fields.add(field);
                }
            }
            fields.sort(Comparator.comparing(Field::getName));
            for (Field field : fields) {
                try {
                    EntityFacade facade = (EntityFacade) field.get(null);
                    for (UUID uuid : facade.publicId().asUuidArray()) {
                        constantsByUuid.putIfAbsent(uuid, bindingClass.getSimpleName() + "." + field.getName());
                    }
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Cannot read " + bindingClass.getName() + "." + field.getName(), e);
                }
            }
        }
        return new BindingReferenceResolver(constantsByUuid, List.of(bindingClasses));
    }

    /**
     * The binding classes this resolver's constants come from: generated source that
     * embeds a resolved expression imports each of them.
     *
     * @return the binding classes, in resolution order
     */
    public List<Class<?>> bindingClasses() {
        return bindingClasses;
    }

    /**
     * The Java source expression for an established identity: a binding constant, such as
     * {@code KernelTerm.NAME}, when one binds it, or a declared-identity fallback otherwise — an
     * {@code EntityProxy.Concept.make(...)} or {@code EntityProxy.Pattern.make(...)}
     * expression (matching the facade's own kind), directly usable anywhere that kind
     * of facade is expected (the kernel binds only what the engine itself names, so most
     * of a set's components take the fallback). Callers emitting either form must import
     * {@code dev.ikm.tinkar.terms.EntityProxy}, {@code dev.ikm.tinkar.common.id.PublicIds}
     * and the {@linkplain #bindingClasses() binding classes} unconditionally, since whether
     * any given identity needs the fallback is a runtime fact about the source store, not
     * known until resolved.
     *
     * @param facade the identity to resolve — a concept, pattern, or other entity facade
     * @return {@code true} paired with the source expression when a binding constant
     *         covers this identity, {@code false} when the fallback is used
     */
    public Resolved resolve(EntityFacade facade) {
        PublicId publicId = facade.publicId();
        for (UUID uuid : publicId.asUuidArray()) {
            String constant = constantsByUuid.get(uuid);
            if (constant != null) {
                return new Resolved(true, constant);
            }
        }
        String escapedDescription = escapeForJavaStringLiteral(facade.description());
        String factory = facade instanceof PatternFacade ? "EntityProxy.Pattern.make(\"" : "EntityProxy.Concept.make(\"";
        return new Resolved(false, factory + escapedDescription + "\", " + publicIdLiteral(publicId) + ")");
    }

    /**
     * The Java source expression for a {@code PublicId} literal: the compact
     * {@code PublicIds.of("...", ...)} String form (IKE-Network/ike-issues#914) —
     * semantically identical to the {@code UUID.fromString} chain it replaces, since
     * {@code PublicIds.of(String...)} maps through {@code UUID.fromString} itself.
     * Shared by every call site in this package that emits a declared identity, so
     * the UUID-literal rendering (and its escaping) has exactly one definition.
     */
    static String publicIdLiteral(PublicId publicId) {
        List<String> uuidLiterals = new ArrayList<>();
        for (UUID uuid : publicId.asUuidArray()) {
            uuidLiterals.add("\"" + uuid + "\"");
        }
        return "PublicIds.of(" + String.join(", ", uuidLiterals) + ")";
    }

    /**
     * Escapes text for embedding in a Java string literal: backslashes, double
     * quotes, and the control characters ({@code \n}, {@code \r}, {@code \t}) that
     * would otherwise produce unterminated or malformed literals in generated source.
     *
     * @param text the raw text
     * @return the escaped text, safe to place between double quotes
     */
    static String escapeForJavaStringLiteral(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * The result of resolving one identity: whether a binding constant covered it, and
     * the Java source expression to emit either way.
     *
     * @param isBindingConstant {@code true} when {@link #sourceExpression()} names a
     *                          binding constant, such as {@code KernelTerm.NAME}
     * @param sourceExpression  the Java source expression to embed
     */
    public record Resolved(boolean isBindingConstant, String sourceExpression) {
    }
}
