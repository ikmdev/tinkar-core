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

import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.PatternFacade;
import dev.ikm.tinkar.terms.TinkarTerm;

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
 * (IKE-Network/ike-issues#869) should emit for it: {@code TinkarTerm.CONSTANT_NAME} when
 * one exists for the identity, or a declared-identity fallback otherwise. The starter
 * set this generator retrofits (IKE-Network/ike-issues#867) <em>is</em> what
 * {@code TinkarTerm} documents, so the constant form is the common case — readable
 * generated source referencing the same constants hand-authored ledgers already use —
 * while the fallback keeps the generator honest for content {@code TinkarTerm} doesn't
 * cover, present or future.
 */
public final class TinkarTermReferenceResolver {

    private final Map<UUID, String> constantNamesByUuid;

    private TinkarTermReferenceResolver(Map<UUID, String> constantNamesByUuid) {
        this.constantNamesByUuid = constantNamesByUuid;
    }

    /**
     * Builds a resolver by reflecting over every public static {@link EntityFacade}
     * field {@link TinkarTerm} declares, once. Every UUID of every constant is
     * registered — not just the first — because {@code EntityProxy}'s multi-UUID
     * constructor sorts its argument list, so a {@code TinkarTerm} constant's own
     * "primordial" (index 0) UUID commonly differs from the store's actual primordial
     * UUID for the same identity (the store preserves declaration/wire order
     * verbatim); indexing every UUID makes lookup independent of which one either
     * side treats as first. Fields are processed in name order so that on the rare
     * occasion two constants share a UUID (this platform has one such pair today),
     * which name wins is deterministic and reproducible across JVM runs, not an
     * artifact of unspecified reflection ordering.
     *
     * @return the built resolver
     */
    public static TinkarTermReferenceResolver build() {
        Map<UUID, String> constantNamesByUuid = new HashMap<>();
        List<Field> fields = new ArrayList<>();
        for (Field field : TinkarTerm.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && EntityFacade.class.isAssignableFrom(field.getType())) {
                fields.add(field);
            }
        }
        fields.sort(Comparator.comparing(Field::getName));
        for (Field field : fields) {
            try {
                EntityFacade facade = (EntityFacade) field.get(null);
                for (UUID uuid : facade.publicId().asUuidArray()) {
                    constantNamesByUuid.putIfAbsent(uuid, field.getName());
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot read TinkarTerm." + field.getName(), e);
            }
        }
        return new TinkarTermReferenceResolver(constantNamesByUuid);
    }

    /**
     * The Java source expression for an established identity: {@code TinkarTerm.NAME}
     * when a matching constant exists, or a declared-identity fallback otherwise — an
     * {@code EntityProxy.Concept.make(...)} or {@code EntityProxy.Pattern.make(...)}
     * expression (matching the facade's own kind), directly usable anywhere that kind
     * of facade is expected (this starter set's own concepts document nearly all of
     * it, but a few meta-schema concepts — for example "Concept field" — have no
     * {@code TinkarTerm} constant, so the fallback is load-bearing, not a
     * defensive-only path). Callers emitting either form must import
     * {@code dev.ikm.tinkar.terms.EntityProxy} and {@code dev.ikm.tinkar.common.id.
     * PublicIds} unconditionally, since whether any given identity needs the fallback
     * is a runtime fact about the source store, not known until resolved.
     *
     * @param facade the identity to resolve — a concept, pattern, or other entity facade
     * @return {@code true} paired with the source expression when a {@code TinkarTerm}
     *         constant covers this identity, {@code false} when the fallback is used
     */
    public Resolved resolve(EntityFacade facade) {
        UUID[] uuids = facade.publicId().asUuidArray();
        for (UUID uuid : uuids) {
            String constantName = constantNamesByUuid.get(uuid);
            if (constantName != null) {
                return new Resolved(true, "TinkarTerm." + constantName);
            }
        }
        String escapedDescription = escapeForJavaStringLiteral(facade.description());
        String factory = facade instanceof PatternFacade ? "EntityProxy.Pattern.make(\"" : "EntityProxy.Concept.make(\"";
        List<String> uuidLiterals = new ArrayList<>(uuids.length);
        for (UUID uuid : uuids) {
            uuidLiterals.add("UUID.fromString(\"" + uuid + "\")");
        }
        return new Resolved(false, factory + escapedDescription + "\", PublicIds.of("
                + String.join(", ", uuidLiterals) + "))");
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
     * The result of resolving one identity: whether a {@code TinkarTerm} constant
     * covered it, and the Java source expression to emit either way.
     *
     * @param isTinkarTermConstant {@code true} when {@link #sourceExpression()} is a
     *                             {@code TinkarTerm.NAME} reference
     * @param sourceExpression     the Java source expression to embed
     */
    public record Resolved(boolean isTinkarTermConstant, String sourceExpression) {
    }
}
