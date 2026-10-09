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
package dev.ikm.tinkar.entity.changeset;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import dev.ikm.tinkar.schema.PublicId;
import dev.ikm.tinkar.schema.TinkarMsg;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Rewrites every reference of a record, a {@link PublicId} anywhere in it other than the
 * record's own, through a mapping: sequences to UUID words for an expansion, UUID words to
 * sequences for a compaction. Works by reflection over the schema, so a field added to it
 * later is rewritten too. A record's own public id, the {@code public_id} of its chronology,
 * is left as it is: a record always names itself by UUID words.
 */
public final class PublicIdRewriter {
    private static final String OWN_ID = "public_id";

    private PublicIdRewriter() {
    }

    /** The record with every reference mapped; the same record when the mapping changes nothing. */
    public static TinkarMsg rewriteReferences(TinkarMsg record, UnaryOperator<PublicId> mapping) {
        Message.Builder chronologyHolder = record.toBuilder();
        boolean[] changed = {false};
        for (Map.Entry<FieldDescriptor, Object> field : record.getAllFields().entrySet()) {
            FieldDescriptor descriptor = field.getKey();
            if (descriptor.getJavaType() != FieldDescriptor.JavaType.MESSAGE || descriptor.isRepeated()) {
                continue;
            }
            Message chronology = (Message) field.getValue();
            Message rewritten = rewrite(chronology, mapping, true, changed);
            if (rewritten != chronology) {
                chronologyHolder.setField(descriptor, rewritten);
            }
        }
        return changed[0] ? (TinkarMsg) chronologyHolder.build() : record;
    }

    /** Visits every public id of a record, the record's own included. */
    public static void forEachPublicId(TinkarMsg record, Consumer<PublicId> visitor) {
        visit(record, null, (field, id) -> visitor.accept(id));
    }

    /**
     * Visits every public id of a record with the field holding it, the record's own included:
     * a compaction tells a stamp from a pattern from a component by the field a reference sits
     * in.
     */
    public static void forEachPublicId(TinkarMsg record, BiConsumer<FieldDescriptor, PublicId> visitor) {
        visit(record, null, visitor);
    }

    private static void visit(Message message, FieldDescriptor holder, BiConsumer<FieldDescriptor, PublicId> visitor) {
        if (message instanceof PublicId publicId) {
            visitor.accept(holder, publicId);
            return;
        }
        for (Map.Entry<FieldDescriptor, Object> field : message.getAllFields().entrySet()) {
            FieldDescriptor descriptor = field.getKey();
            if (descriptor.getJavaType() != FieldDescriptor.JavaType.MESSAGE) {
                continue;
            }
            if (descriptor.isRepeated()) {
                for (Object element : (List<?>) field.getValue()) {
                    visit((Message) element, descriptor, visitor);
                }
            } else {
                visit((Message) field.getValue(), descriptor, visitor);
            }
        }
    }

    /** The record with its own public id replaced: a format-1 record's text UUIDs, written as words. */
    public static TinkarMsg withOwnId(TinkarMsg record, PublicId ownId) {
        Message.Builder holder = record.toBuilder();
        for (Map.Entry<FieldDescriptor, Object> field : record.getAllFields().entrySet()) {
            FieldDescriptor descriptor = field.getKey();
            if (descriptor.getJavaType() != FieldDescriptor.JavaType.MESSAGE || descriptor.isRepeated()) {
                continue;
            }
            Message chronology = (Message) field.getValue();
            FieldDescriptor own = chronology.getDescriptorForType().findFieldByName(OWN_ID);
            if (own != null) {
                holder.setField(descriptor, chronology.toBuilder().setField(own, ownId).build());
            }
        }
        return (TinkarMsg) holder.build();
    }

    private static Message rewrite(Message message, UnaryOperator<PublicId> mapping, boolean chronology, boolean[] changed) {
        if (message instanceof PublicId publicId) {
            PublicId mapped = mapping.apply(publicId);
            if (mapped != publicId && !mapped.equals(publicId)) {
                changed[0] = true;
                return mapped;
            }
            return message;
        }
        Message.Builder builder = null;
        for (Map.Entry<FieldDescriptor, Object> field : message.getAllFields().entrySet()) {
            FieldDescriptor descriptor = field.getKey();
            if (descriptor.getJavaType() != FieldDescriptor.JavaType.MESSAGE) {
                continue;
            }
            if (chronology && OWN_ID.equals(descriptor.getName()) && !descriptor.isRepeated()) {
                continue; // the record's own id stays as written
            }
            if (descriptor.isRepeated()) {
                List<?> elements = (List<?>) field.getValue();
                List<Message> rewritten = new ArrayList<>(elements.size());
                boolean any = false;
                for (Object element : elements) {
                    Message mapped = rewrite((Message) element, mapping, false, changed);
                    any |= mapped != element;
                    rewritten.add(mapped);
                }
                if (any) {
                    if (builder == null) {
                        builder = message.toBuilder();
                    }
                    builder.clearField(descriptor);
                    for (Message element : rewritten) {
                        builder.addRepeatedField(descriptor, element);
                    }
                }
            } else {
                Message value = (Message) field.getValue();
                Message mapped = rewrite(value, mapping, false, changed);
                if (mapped != value) {
                    if (builder == null) {
                        builder = message.toBuilder();
                    }
                    builder.setField(descriptor, mapped);
                }
            }
        }
        return builder == null ? message : builder.build();
    }
}
