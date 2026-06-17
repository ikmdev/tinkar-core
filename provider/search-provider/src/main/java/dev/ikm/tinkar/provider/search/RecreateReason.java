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
package dev.ikm.tinkar.provider.search;

/**
 * Reason a {@link RecreateIndex} run was triggered. Each value carries a
 * short {@link #title()} (used as the user-visible progress-dialog title for
 * the duration of the rebuild) and a longer {@link #description()} explaining
 * why it's happening (used as the initial progress message before per-entity
 * progress updates take over).
 *
 * <p>Surface visibility matters: rebuilds can run for several minutes on
 * large databases, and a user staring at "Indexing Semantics" with no other
 * context can't tell whether something is wrong, whether the rebuild is
 * expected, or what triggered it. Each trigger in {@link SearchProvider}
 * carries a distinct reason so the dialog tells the right story.
 */
public enum RecreateReason {

    /**
     * Database has data on disk but no Lucene index — typical "fresh open
     * after import" or "first time opening a snapshot" path.
     */
    INITIAL_BUILD(
            "Building Search Index",
            "Creating the search index from your data — first-time setup."),

    /**
     * The on-disk Lucene segments were written by an earlier Lucene major
     * version whose codec class is not on the current classpath. The old
     * index was wiped (it's unreadable here) and is being rebuilt from the
     * entity store.
     *
     * <p>One-time event after a Lucene upgrade. The entity store itself was
     * untouched.
     */
    LUCENE_FORMAT_INCOMPATIBLE(
            "Updating Search Index",
            "The search index is from an earlier Lucene version. Rebuilding from your data — this is a one-time update after the upgrade."),

    /**
     * The on-disk index has a {@link IndexerSchema#VERSION schema version}
     * older than this build's. The shape of indexed documents changed in a
     * way that makes old hits mis-rank or mis-decode; rebuild is the
     * compatibility path.
     *
     * <p>One-time event per schema bump.
     */
    SCHEMA_OUTDATED(
            "Updating Search Index",
            "The search-index format has been updated. Rebuilding from your data — this is a one-time update."),

    /**
     * The on-disk index is present and current-version, but contains zero
     * documents while the entity store has data. Indicates a previously
     * interrupted load or a manual delete; rebuild restores the expected
     * state.
     */
    EMPTY_INDEX_RECOVERY(
            "Rebuilding Search Index",
            "The search index is empty while data exists. Rebuilding from your data store."),

    /**
     * Caller explicitly requested a rebuild (e.g. via a maintenance UI
     * action or a post-import {@code commitSearchIndexIfAvailable} call).
     * Used as the default for {@code SearchService.recreateIndex()}.
     */
    USER_REQUESTED(
            "Recreating Search Index",
            "Rebuilding the search index from your data store.");

    private final String title;
    private final String description;

    RecreateReason(String title, String description) {
        this.title = title;
        this.description = description;
    }

    /** @return short title for the progress dialog (a few words). */
    public String title() {
        return title;
    }

    /** @return one-sentence explanation of why this rebuild is happening. */
    public String description() {
        return description;
    }
}
