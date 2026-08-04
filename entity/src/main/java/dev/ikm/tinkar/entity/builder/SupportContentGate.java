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

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIdList;
import dev.ikm.tinkar.common.id.PublicIdSet;
import dev.ikm.tinkar.component.FieldDataType;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.ConceptToDataType;
import dev.ikm.tinkar.terms.DefaultsTemplateTerm;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.SemanticFacade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Compose-time gates shared by the defaults/template support verbs
 * ({@link KnowledgeSet#fieldDefaults}, {@link KnowledgeSet#template},
 * {@link KnowledgeSet#templatePurpose} — IKE-Network/ike-issues#885). Two rules of the
 * decided design are enforced here, loudly and before any session mutation:
 * <ul>
 *   <li><b>The module gate.</b> Every version of defaults/template support content is
 *       stamped in {@link DefaultsTemplateTerm#DEFAULTS_AND_TEMPLATES_MODULE} — the
 *       category's bidirectional live-and-die invariant. The verbs <em>validate</em> the
 *       declared stamp rather than supplying the module themselves (KEC, 2026-07-18):
 *       stamps stay fully explicit in the append-only stamp discipline, and a stamp
 *       declared in any other module fails compose with the invariant named.</li>
 *   <li><b>The complete typed tuple.</b> A default value or template semantic states one
 *       value per declared field, in declared order, no field null, each value's
 *       compose-time form conforming to the field's declared data type. The declared
 *       data-type concept maps through {@link ConceptToDataType} to a
 *       {@link FieldDataType}, whose expected compose form is checked per value — so a
 *       wrong-typed tuple fails the build, never the store.</li>
 * </ul>
 * Both checks are store-free, matching the composing-stays-store-free discipline of the
 * ledger DSL (identity comparison by declared UUIDs, type mapping by public id).
 */
final class SupportContentGate {

    private SupportContentGate() {
    }

    /**
     * Requires the declared stamp's module to be the Defaults and templates module.
     *
     * @param stamp the declared stamp a support verb's scope is binding to
     * @param verb  the verb name, for the failure message
     * @throws IllegalArgumentException if the stamp declares any other module — the
     *                                  live-and-die invariant would be violated at birth
     */
    static void requireDefaultsModule(Stamp stamp, String verb) {
        for (UUID declared : stamp.module().publicId().asUuidArray()) {
            for (UUID moduleUuid
                    : DefaultsTemplateTerm.DEFAULTS_AND_TEMPLATES_MODULE.publicId().asUuidArray()) {
                if (declared.equals(moduleUuid)) {
                    return;
                }
            }
        }
        throw new IllegalArgumentException(verb + " requires a stamp declared in the Defaults and"
                + " templates module — the category's live-and-die invariant: every version of"
                + " defaults/template support content is stamped in that module, and the module holds"
                + " only such content (declared module: \"" + stamp.module().description() + "\")");
    }

    /**
     * Requires a complete, conforming tuple: one value per declared field, in declared
     * order, no field null, each value's compose-time form matching the field's declared
     * data type.
     *
     * @param patternFqn the pattern's birth FQN, for failure messages
     * @param dataTypes  the pattern's declared field data-type concepts, in field order
     * @param values     the tuple's values, in field order
     * @param what       what is being authored ("the default value tuple" or
     *                   "the template tuple"), for failure messages
     * @throws IllegalArgumentException if the arity differs from the declared field
     *                                  count, any value is null, a declared data type has
     *                                  no {@link FieldDataType} mapping, or a value's
     *                                  form does not conform to its field's declared type
     */
    static void requireCompleteTuple(String patternFqn, List<ConceptFacade> dataTypes,
                                     Object[] values, String what) {
        if (values.length != dataTypes.size()) {
            throw new IllegalArgumentException(what + " for " + patternFqn + " must state the"
                    + " complete tuple — " + dataTypes.size() + " declared field(s), "
                    + values.length + " value(s) given");
        }
        for (int index = 0; index < values.length; index++) {
            ConceptFacade dataType = dataTypes.get(index);
            Object value = values[index];
            if (value == null) {
                throw new IllegalArgumentException(what + " for " + patternFqn + " must state the"
                        + " complete tuple — field " + index + " (declared data type \""
                        + dataType.description() + "\") is null");
            }
            FieldDataType expected = fieldDataTypeOf(dataType, patternFqn, index);
            if (!conforms(expected, value)) {
                throw new IllegalArgumentException(what + " for " + patternFqn + " — field " + index
                        + " declares data type \"" + dataType.description() + "\" (" + expected
                        + "), which takes " + expectedForm(expected) + "; got "
                        + value.getClass().getName());
            }
        }
    }

    private static FieldDataType fieldDataTypeOf(ConceptFacade dataType, String patternFqn, int index) {
        try {
            return ConceptToDataType.convert(dataType);
        } catch (UnsupportedOperationException e) {
            throw new IllegalArgumentException("Field " + index + " of " + patternFqn
                    + " declares data type \"" + dataType.description() + "\", which has no"
                    + " FieldDataType mapping — compose-time conformance requires a"
                    + " ConceptToDataType-mapped data type concept", e);
        }
    }

    /**
     * Whether a compose-time value conforms to the expected field data type. The forms
     * are the ledger DSL's identity-carrying compose forms (see
     * {@link ConceptBuilder.ActiveScope#semantic}): entity references as facades or
     * public ids, graphs as {@link GraphFieldValue} specs, id collections as
     * {@code PublicIdList}/{@code PublicIdSet}, scalars as their Java types.
     */
    private static boolean conforms(FieldDataType expected, Object value) {
        return switch (expected) {
            case STRING -> value instanceof String;
            case BOOLEAN -> value instanceof Boolean;
            case INTEGER -> value instanceof Integer;
            case LONG -> value instanceof Long;
            case FLOAT -> value instanceof Float;
            case DECIMAL -> value instanceof BigDecimal;
            case INSTANT -> value instanceof Instant;
            case BYTE_ARRAY -> value instanceof byte[];
            case OBJECT_ARRAY -> value instanceof Object[];
            case CONCEPT -> value instanceof ConceptFacade || value instanceof PublicId;
            case SEMANTIC -> value instanceof SemanticFacade || value instanceof PublicId;
            case IDENTIFIED_THING -> value instanceof EntityFacade || value instanceof PublicId;
            case COMPONENT_ID_LIST -> value instanceof PublicIdList;
            case COMPONENT_ID_SET -> value instanceof PublicIdSet;
            case DITREE -> value instanceof GraphFieldValue.Tree;
            case DIGRAPH -> value instanceof GraphFieldValue.Graph;
            // ConceptToDataType maps only the cases above; anything it may map in the
            // future defers to the write-path validation rather than wrongly failing here.
            default -> true;
        };
    }

    private static String expectedForm(FieldDataType expected) {
        return switch (expected) {
            case STRING -> "a String";
            case BOOLEAN -> "a Boolean";
            case INTEGER -> "an Integer";
            case LONG -> "a Long";
            case FLOAT -> "a Float";
            case DECIMAL -> "a BigDecimal";
            case INSTANT -> "an Instant";
            case BYTE_ARRAY -> "a byte[]";
            case OBJECT_ARRAY -> "an Object[]";
            case CONCEPT -> "a ConceptFacade or PublicId";
            case SEMANTIC -> "a SemanticFacade or PublicId";
            case IDENTIFIED_THING -> "an EntityFacade or PublicId";
            case COMPONENT_ID_LIST -> "a PublicIdList";
            case COMPONENT_ID_SET -> "a PublicIdSet";
            case DITREE -> "a GraphFieldValue.Tree";
            case DIGRAPH -> "a GraphFieldValue.Graph";
            default -> "a value the write path accepts";
        };
    }
}
