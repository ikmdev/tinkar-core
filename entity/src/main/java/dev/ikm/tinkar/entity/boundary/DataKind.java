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
package dev.ikm.tinkar.entity.boundary;

import java.util.Locale;
import java.util.Optional;

/**
 * The kind of data a component is, named by who else gets it, and decided by the module its first
 * version is written under (IKE-Network/ike-issues#1313; design
 * {@code design-2026-11-22-knowledge-shared-local}, settled by KEC on 2026-11-22). There are three
 * kinds, knowledge, shared and local; local data saved in a file is told apart as {@link #FILE},
 * because a copy of it is dropped and read from its file again while other local data has no
 * other record.
 *
 * <p>Data refers only to data shared at least as widely ({@link #mayReferTo}). Local data saved in a
 * file and other local data do not refer to each other, as the ephemeral and instance data of
 * 2026-10-20 did not.
 *
 * <p>The order of the constants is written to the data-use index as each kind's ordinal, so it
 * keeps the order of 2026-10-20 (knowledge, ephemeral, instance), and a new kind goes last.
 */
public enum DataKind {

    /** Synced with the knowledge base, append-only and governed: every module that is a kind of none of the parents. */
    KNOWLEDGE,

    /**
     * Local data saved in a file outside the knowledge base, a kind of {@code KernelConcept.FILE_MODULE}:
     * a copy, read from the file and dropped when it is no longer open. Called ephemeral before 2026-11-22.
     */
    FILE,

    /**
     * Local data with no record but this knowledge base, a kind of {@code KernelConcept.LOCAL_MODULE}
     * and not of the File module: it never syncs. Called instance before 2026-11-22.
     */
    LOCAL,

    /** Shared data, a kind of {@code KernelConcept.SHARED_MODULE}: synced with the knowledge base in change sets of its own. */
    SHARED;

    /** @return whether data of this kind never syncs with the knowledge base: local data, saved in a file or not */
    public boolean local() {
        return this == LOCAL || this == FILE;
    }

    /**
     * Whether data of this kind may refer to data of another: only to data shared at least as
     * widely, and local data saved in a file and other local data not to each other.
     *
     * @param target the kind of the data referred to
     * @return whether the reference is allowed
     */
    public boolean mayReferTo(DataKind target) {
        if (target == this || target == KNOWLEDGE) {
            return true;
        }
        return switch (this) {
            case KNOWLEDGE -> false;
            case SHARED -> false;
            case LOCAL, FILE -> target == SHARED;
        };
    }

    /** @return the kind in words, for messages: knowledge, shared, local or file */
    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Reads a kind by its name, as a scope manifest records it, including the names of 2026-10-20:
     * {@code EPHEMERAL} is {@link #FILE} and {@code INSTANCE} is {@link #LOCAL}.
     *
     * @param name the recorded name
     * @return the kind, or empty when the name is none of them
     */
    public static Optional<DataKind> named(String name) {
        String key = name.strip().toUpperCase(Locale.ROOT);
        return switch (key) {
            case "EPHEMERAL" -> Optional.of(FILE);
            case "INSTANCE" -> Optional.of(LOCAL);
            default -> {
                for (DataKind kind : values()) {
                    if (kind.name().equals(key)) {
                        yield Optional.of(kind);
                    }
                }
                yield Optional.empty();
            }
        };
    }
}
