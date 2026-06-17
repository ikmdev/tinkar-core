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
package dev.ikm.tinkar.entity.maintenance;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.time.DateTimeUtil;
import dev.ikm.tinkar.schema.ConceptChronology;
import dev.ikm.tinkar.schema.Field;
import dev.ikm.tinkar.schema.FieldDefinition;
import dev.ikm.tinkar.schema.PatternChronology;
import dev.ikm.tinkar.schema.PatternVersion;
import dev.ikm.tinkar.schema.SemanticChronology;
import dev.ikm.tinkar.schema.SemanticVersion;
import dev.ikm.tinkar.schema.StampChronology;
import dev.ikm.tinkar.schema.StampVersion;
import dev.ikm.tinkar.schema.TinkarMsg;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads a change set ZIP (the format produced by
 * {@code dev.ikm.tinkar.entity.export.ExportEntitiesToProtobufFile}) and emits
 * a Markdown diagnostic summary that goes beyond the manifest's raw counts.
 *
 * <p>The summarizer is read-only: it parses the protobuf {@code TinkarMsg}
 * stream directly and never writes to {@link dev.ikm.tinkar.entity.EntityService}.
 * That makes it safe to run on a change set the data store has not loaded yet,
 * and reusable from a future Maven goal that needs no running KOMET.
 *
 * <p>When a {@link PrimitiveData} service is available, public ids referenced
 * by the change set are resolved to their text descriptions for human-readable
 * output. When the service is absent or a public id is not in the data store,
 * the UUID string is rendered instead — never throws.
 *
 * <p>The report includes a per-stamp citation count (which version-bearing
 * entities cite each stamp), a per-concept synthesis section (counts of
 * descriptions, definitions, navigation semantics for each concept in the
 * change set), and a diagnostics section listing orphan semantics and
 * unreferenced stamps.
 */
public final class ChangeSetSummarizer {

    private static final Logger LOG = LoggerFactory.getLogger(ChangeSetSummarizer.class);
    private static final String MANIFEST_RELPATH = "META-INF/MANIFEST.MF";
    private static final DateTimeFormatter STAMP_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public record ManifestInfo(
            String packagerName,
            String packageDate,
            long totalCount,
            long conceptCount,
            long semanticCount,
            long patternCount,
            long stampCount,
            ImmutableList<NamedPublicId> dependentEntries) {}

    public record NamedPublicId(PublicId publicId, String description) {}

    /**
     * One stamp's journal as seen in the change set.
     *
     * <p>A stamp's PublicId can appear in multiple {@code StampChronology}
     * messages — change sets are an append-only journal, not a snapshot, so
     * the same logical stamp shows up once per writer flush. Each message
     * carries 1–2 versions (the proto schema's {@code firstStampVersion} /
     * {@code secondStampVersion} pair). {@link #entries} flattens the
     * journal in stream order, with {@code messageNumber} and
     * {@code positionInMessage} on every entry so a reader can trace each
     * row back to its source message.
     */
    public record StampInfo(
            PublicId publicId,
            int messageCount,
            int citationCount,
            ImmutableList<StampVersionEntry> entries) {

        /** The latest entry in journal order, or {@code null} if no entries. */
        public StampVersionEntry latestEntry() {
            return entries.isEmpty() ? null : entries.get(entries.size() - 1);
        }

        /** Number of entries with distinct content. */
        public int distinctEntryCount() {
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            for (StampVersionEntry e : entries) seen.add(e.contentKey());
            return seen.size();
        }

        /** True if every entry has a state PublicId equal to the first entry's. */
        public boolean stateStable() { return uniqueIdString(StampVersionEntry::statePublicId) == 1; }

        public boolean authorStable() { return uniqueIdString(StampVersionEntry::authorPublicId) == 1; }
        public boolean moduleStable() { return uniqueIdString(StampVersionEntry::modulePublicId) == 1; }
        public boolean pathStable() { return uniqueIdString(StampVersionEntry::pathPublicId) == 1; }

        /** True if at least one entry has a real (non-sentinel) commit time. */
        public boolean hasCommittedVersion() {
            for (StampVersionEntry e : entries) {
                long t = e.timeEpochMs();
                if (t != Long.MAX_VALUE && t != Long.MIN_VALUE) return true;
            }
            return false;
        }

        private int uniqueIdString(java.util.function.Function<StampVersionEntry, PublicId> getter) {
            java.util.Set<String> ids = new java.util.LinkedHashSet<>();
            for (StampVersionEntry e : entries) {
                PublicId p = getter.apply(e);
                ids.add(p == null ? "null" : p.idString());
            }
            return ids.size();
        }
    }

    /**
     * One version entry from a stamp chronology message, flattened for
     * journal-style display. Carries both the resolved label (for human
     * reading) and the raw {@link PublicId} (for manual cross-reference)
     * for each component.
     */
    public record StampVersionEntry(
            int messageNumber,
            int positionInMessage,
            PublicId statePublicId,
            String stateLabel,
            long timeEpochMs,
            PublicId authorPublicId,
            String authorLabel,
            PublicId modulePublicId,
            String moduleLabel,
            PublicId pathPublicId,
            String pathLabel) {

        /**
         * A stable string that uniquely identifies this entry's *content*
         * (PublicIds + time tuple), used to detect byte-identical duplicate
         * entries across messages.
         */
        public String contentKey() {
            return idStringOrEmpty(statePublicId)
                    + "|" + timeEpochMs
                    + "|" + idStringOrEmpty(authorPublicId)
                    + "|" + idStringOrEmpty(modulePublicId)
                    + "|" + idStringOrEmpty(pathPublicId);
        }

        private static String idStringOrEmpty(PublicId p) {
            return p == null ? "" : p.idString();
        }
    }

    public record ConceptInfo(
            PublicId publicId,
            String label,
            int versionCount,
            ImmutableList<PublicId> citedStampIds) {}

    public record PatternInfo(
            PublicId publicId,
            String label,
            String semanticPurpose,
            String semanticMeaning,
            int fieldDefinitionCount,
            int versionCount,
            ImmutableList<PublicId> citedStampIds) {}

    public record SemanticInfo(
            PublicId publicId,
            PublicId patternPublicId,
            PublicId referencedComponentPublicId,
            String referencedComponentLabel,
            int versionCount,
            ImmutableList<PublicId> citedStampIds,
            String renderedFields) {}

    public record SemanticGroup(
            PublicId patternPublicId,
            String patternLabel,
            ImmutableList<SemanticInfo> semantics) {}

    public record ConceptSynthesis(
            PublicId conceptPublicId,
            String conceptLabel,
            int descriptionCount,
            int fullyQualifiedNameCount,
            int statedDefinitionCount,
            int inferredDefinitionCount,
            int navigationStatedCount,
            int navigationInferredCount,
            int identifierCount,
            int otherSemanticCount,
            ImmutableList<String> notes) {}

    public record Diagnostic(Severity severity, String category, String message) {
        public enum Severity { INFO, WARN, ERROR }
    }

    public record Report(
            File source,
            ManifestInfo manifest,
            ImmutableList<StampInfo> stamps,
            ImmutableList<ConceptInfo> concepts,
            ImmutableList<PatternInfo> patterns,
            ImmutableList<SemanticGroup> semanticsByPattern,
            ImmutableList<ConceptSynthesis> conceptSyntheses,
            ImmutableList<Diagnostic> diagnostics,
            int observedTotal,
            int observedConcepts,
            int observedSemantics,
            int observedPatterns,
            int observedStamps) {

        public String toMarkdown() {
            StringBuilder sb = new StringBuilder();
            ZonedDateTime renderedAt = ZonedDateTime.now();

            sb.append("# Change Set Summary\n\n");
            sb.append("- **Source:** ").append(escape(source == null ? "(stream)" : source.getName())).append('\n');
            sb.append("- **Rendered:** ").append(renderedAt.format(STAMP_TIME)).append("\n\n");

            appendManifestSection(sb);
            appendCountsSection(sb);
            appendStampsSection(sb);
            appendConceptsSection(sb);
            appendPatternsSection(sb);
            appendSemanticsByPatternSection(sb);
            appendConceptSynthesisSection(sb);
            appendDiagnosticsSection(sb);

            return sb.toString();
        }

        private void appendManifestSection(StringBuilder sb) {
            sb.append("## Manifest\n\n");
            if (manifest == null) {
                sb.append("_No `META-INF/MANIFEST.MF` entry was found in the change set._\n\n");
                return;
            }
            sb.append("- **Packager:** ").append(escape(manifest.packagerName())).append('\n');
            sb.append("- **Package date:** ").append(escape(manifest.packageDate())).append('\n');
            sb.append("- **Total-Count:** ").append(String.format("%,d", manifest.totalCount())).append('\n');
            sb.append("- **Concept-Count:** ").append(String.format("%,d", manifest.conceptCount())).append('\n');
            sb.append("- **Semantic-Count:** ").append(String.format("%,d", manifest.semanticCount())).append('\n');
            sb.append("- **Pattern-Count:** ").append(String.format("%,d", manifest.patternCount())).append('\n');
            sb.append("- **Stamp-Count:** ").append(String.format("%,d", manifest.stampCount())).append("\n\n");

            if (!manifest.dependentEntries().isEmpty()) {
                sb.append("### Dependent Modules and Authors\n\n");
                sb.append("| Description | Public ID |\n");
                sb.append("|-------------|-----------|\n");
                for (NamedPublicId entry : manifest.dependentEntries()) {
                    sb.append("| ").append(escape(entry.description()))
                            .append(" | ").append(escape(entry.publicId().idString()))
                            .append(" |\n");
                }
                sb.append('\n');
            }
        }

        private void appendCountsSection(StringBuilder sb) {
            sb.append("## Manifest vs Observed\n\n");
            sb.append("| Kind | Manifest | Observed |\n");
            sb.append("|------|---------:|---------:|\n");
            long mTotal = manifest == null ? -1 : manifest.totalCount();
            long mC = manifest == null ? -1 : manifest.conceptCount();
            long mS = manifest == null ? -1 : manifest.semanticCount();
            long mP = manifest == null ? -1 : manifest.patternCount();
            long mSt = manifest == null ? -1 : manifest.stampCount();
            sb.append("| Total    | ").append(formatCount(mTotal)).append(" | ").append(String.format("%,d", observedTotal)).append(" |\n");
            sb.append("| Concept  | ").append(formatCount(mC)).append(" | ").append(String.format("%,d", observedConcepts)).append(" |\n");
            sb.append("| Semantic | ").append(formatCount(mS)).append(" | ").append(String.format("%,d", observedSemantics)).append(" |\n");
            sb.append("| Pattern  | ").append(formatCount(mP)).append(" | ").append(String.format("%,d", observedPatterns)).append(" |\n");
            sb.append("| Stamp    | ").append(formatCount(mSt)).append(" | ").append(String.format("%,d", observedStamps)).append(" |\n\n");
        }

        private static String formatCount(long manifestValue) {
            return manifestValue < 0 ? "n/a" : String.format("%,d", manifestValue);
        }

        private void appendStampsSection(StringBuilder sb) {
            sb.append("## STAMPs (").append(stamps.size()).append(")\n\n");
            if (stamps.isEmpty()) {
                sb.append("_No stamp chronologies in the change set._\n\n");
                return;
            }
            sb.append("One block per stamp PublicId. Change sets are an append-only ")
                    .append("journal — a single stamp can appear in multiple ")
                    .append("`StampChronology` messages as a transaction progresses across ")
                    .append("flushes, machines, or restarts. **Journal** shows every ")
                    .append("`(message, position)` entry in stream order so the lifecycle is ")
                    .append("visible; the *Reconstructed final state* line summarizes the most ")
                    .append("recent entry. Continuity flags catch anomalies — for the same stamp ")
                    .append("PublicId, state / author / module / path should be stable; only `time` ")
                    .append("should advance from `Latest` (uncommitted) toward a real timestamp.\n\n");

            for (StampInfo s : stamps) {
                appendOneStamp(sb, s);
            }
        }

        private void appendOneStamp(StringBuilder sb, StampInfo s) {
            sb.append("### Stamp `").append(escape(s.publicId().idString())).append("`\n\n");

            sb.append("- **Citations:** ").append(String.format("%,d", s.citationCount())).append('\n');
            sb.append("- **Journal:** ").append(s.messageCount()).append(" message")
                    .append(s.messageCount() == 1 ? "" : "s")
                    .append(", ").append(s.entries().size()).append(" total entr")
                    .append(s.entries().size() == 1 ? "y" : "ies")
                    .append(", ").append(s.distinctEntryCount()).append(" distinct\n");
            sb.append("- **Has committed version:** ")
                    .append(s.hasCommittedVersion() ? "yes" : "no (uncommitted only)")
                    .append("\n\n");

            if (s.entries().isEmpty()) {
                sb.append("_(no entries)_\n\n");
                return;
            }

            // One small table per chronology message, with one row per version
            // in that message. Easier to scan than a single combined table, and
            // copy-pastes more reliably when each table is small.
            java.util.Map<Integer, java.util.List<StampVersionEntry>> byMessage =
                    new java.util.LinkedHashMap<>();
            for (StampVersionEntry e : s.entries()) {
                byMessage.computeIfAbsent(e.messageNumber(), _ -> new java.util.ArrayList<>()).add(e);
            }
            java.util.Map<String, String> firstSeenAt = new java.util.LinkedHashMap<>();
            for (var kv : byMessage.entrySet()) {
                int msg = kv.getKey();
                java.util.List<StampVersionEntry> versions = kv.getValue();
                sb.append("**Message ").append(msg).append(" of ").append(s.messageCount())
                        .append("** — ").append(versions.size()).append(" version")
                        .append(versions.size() == 1 ? "" : "s").append("\n\n");
                sb.append("| Pos | State | Time (epoch ms) | Time | Author | Module | Path | Note |\n");
                sb.append("|----:|-------|----------------:|------|--------|--------|------|------|\n");
                for (StampVersionEntry e : versions) {
                    String coord = "(" + e.messageNumber() + "." + e.positionInMessage() + ")";
                    String key = e.contentKey();
                    String prior = firstSeenAt.putIfAbsent(key, coord);
                    String note = prior == null ? "" : "duplicate of " + prior;
                    sb.append("| ").append(e.positionInMessage())
                            .append(" | ").append(stateBadge(e.stateLabel()))
                            .append(" | ").append(formatRawEpoch(e.timeEpochMs()))
                            .append(" | ").append(timeBadge(e.timeEpochMs()))
                            .append(" | ").append(escape(e.authorLabel()))
                            .append(" | ").append(escape(e.moduleLabel()))
                            .append(" | ").append(escape(e.pathLabel()))
                            .append(" | ").append(escape(note))
                            .append(" |\n");
                }
                sb.append('\n');
            }

            // Continuity flags — anomalies become visible inline.
            sb.append("**Continuity:** ");
            sb.append(continuityToken("state", s.stateStable())).append("; ");
            sb.append(continuityToken("author", s.authorStable())).append("; ");
            sb.append(continuityToken("module", s.moduleStable())).append("; ");
            sb.append(continuityToken("path", s.pathStable())).append("\n\n");

            // Raw component PublicIds from the first entry (full UUID list).
            // When a component is unstable this is only the first observation,
            // but the Continuity line above flags that case.
            StampVersionEntry first = s.entries().get(0);
            sb.append("**Raw component PublicIds (entry ").append(first.messageNumber())
                    .append(".").append(first.positionInMessage()).append("):**\n");
            sb.append("- State: `").append(escape(idStringOrDash(first.statePublicId()))).append("`\n");
            sb.append("- Author: `").append(escape(idStringOrDash(first.authorPublicId()))).append("`\n");
            sb.append("- Module: `").append(escape(idStringOrDash(first.modulePublicId()))).append("`\n");
            sb.append("- Path: `").append(escape(idStringOrDash(first.pathPublicId()))).append("`\n\n");

            // Reconstructed final state — the latest entry in journal order.
            StampVersionEntry latest = s.latestEntry();
            sb.append("**Reconstructed final state:** ")
                    .append(stateBadge(latest.stateLabel())).append(" at ")
                    .append(timeBadge(latest.timeEpochMs())).append(" by ")
                    .append(escape(latest.authorLabel())).append(" / ")
                    .append(escape(latest.moduleLabel())).append(" / ")
                    .append(escape(latest.pathLabel())).append("\n\n");
        }

        /** Render the raw stamp time as either a sentinel or a thousands-separated decimal. */
        private static String formatRawEpoch(long epochMs) {
            if (epochMs == Long.MAX_VALUE) return "MAX_VALUE";
            if (epochMs == Long.MIN_VALUE) return "MIN_VALUE";
            return String.format("%,d", epochMs);
        }

        private static String continuityToken(String label, boolean stable) {
            return stable ? label + " stable ✓" : "**" + label + " varies ⚠**";
        }

        private static String idStringOrDash(PublicId p) {
            return p == null ? "—" : p.idString();
        }

        /**
         * Render a state name as a code-span tagged with a {@code .state-*}
         * Pandoc-style class for the renderer's stylesheet to colorize.
         */
        private static String stateBadge(String state) {
            String cls = stateClassFor(state);
            String safe = escape(state);
            if (cls == null) return "`" + safe + "`";
            return "`" + safe + "`{." + cls + "}";
        }

        /** Map an arbitrary state name onto the canonical {@code .state-*} CSS class. */
        private static String stateClassFor(String state) {
            if (state == null) return null;
            String lower = state.toLowerCase();
            if (lower.contains("withdrawn")) return "state-withdrawn";
            if (lower.contains("inactive")) return "state-inactive";
            if (lower.contains("active")) return "state-active";
            if (lower.contains("canceled") || lower.contains("cancelled")) return "state-canceled";
            if (lower.contains("primordial")) return "state-primordial";
            return null;
        }

        /**
         * Format a stamp's epoch-ms time, applying the {@code .state-uncommitted}
         * or {@code .state-canceled} CSS class to the special sentinel values
         * (Long.MAX_VALUE and Long.MIN_VALUE) so they read distinctly in the
         * rendered chronology.
         */
        private static String timeBadge(long epochMs) {
            String human = DateTimeUtil.format(epochMs);
            if (epochMs == Long.MAX_VALUE) {
                return "`" + escape(human) + "`{.state-uncommitted}";
            }
            if (epochMs == Long.MIN_VALUE) {
                return "`" + escape(human) + "`{.state-canceled}";
            }
            return escape(human);
        }

        private void appendConceptsSection(StringBuilder sb) {
            sb.append("## Concepts (").append(concepts.size()).append(")\n\n");
            if (concepts.isEmpty()) {
                sb.append("_No concept chronologies in the change set._\n\n");
                return;
            }
            sb.append("| Concept | Versions | Cited Stamps |\n");
            sb.append("|---------|---------:|-------------:|\n");
            for (ConceptInfo c : concepts) {
                sb.append("| ").append(escape(c.label()))
                        .append(" | ").append(c.versionCount())
                        .append(" | ").append(c.citedStampIds().size())
                        .append(" |\n");
            }
            sb.append('\n');
        }

        private void appendPatternsSection(StringBuilder sb) {
            sb.append("## Patterns (").append(patterns.size()).append(")\n\n");
            if (patterns.isEmpty()) {
                sb.append("_No pattern chronologies in the change set._\n\n");
                return;
            }
            sb.append("| Pattern | Purpose | Meaning | Field Defs | Versions |\n");
            sb.append("|---------|---------|---------|-----------:|---------:|\n");
            for (PatternInfo p : patterns) {
                sb.append("| ").append(escape(p.label()))
                        .append(" | ").append(escape(p.semanticPurpose()))
                        .append(" | ").append(escape(p.semanticMeaning()))
                        .append(" | ").append(p.fieldDefinitionCount())
                        .append(" | ").append(p.versionCount())
                        .append(" |\n");
            }
            sb.append('\n');
        }

        private void appendSemanticsByPatternSection(StringBuilder sb) {
            sb.append("## Semantics by Pattern\n\n");
            if (semanticsByPattern.isEmpty()) {
                sb.append("_No semantics in the change set._\n\n");
                return;
            }
            for (SemanticGroup group : semanticsByPattern) {
                sb.append("### ").append(escape(group.patternLabel()))
                        .append(" — ").append(group.semantics().size()).append(" semantic")
                        .append(group.semantics().size() == 1 ? "" : "s").append("\n\n");
                sb.append("| Referenced Component | Versions | Latest Field Values |\n");
                sb.append("|----------------------|---------:|---------------------|\n");
                for (SemanticInfo s : group.semantics()) {
                    sb.append("| ").append(escape(s.referencedComponentLabel()))
                            .append(" | ").append(s.versionCount())
                            .append(" | ").append(escape(s.renderedFields()))
                            .append(" |\n");
                }
                sb.append('\n');
            }
        }

        private void appendConceptSynthesisSection(StringBuilder sb) {
            sb.append("## Per-Concept Synthesis\n\n");
            if (conceptSyntheses.isEmpty()) {
                sb.append("_No concepts in the change set; nothing to synthesize._\n\n");
                return;
            }
            sb.append("Counts each concept's semantics that are present in this change set, ")
                    .append("grouped by what they assert. Helps confirm a single-concept add looks like ")
                    .append("\"1 description / 1 FQN / 1 stated / 1 inferred / ≥1 navigation\".\n\n");
            sb.append("| Concept | Desc | FQN | Stated | Inferred | Nav (S) | Nav (I) | Ident | Other | Notes |\n");
            sb.append("|---------|-----:|----:|-------:|---------:|--------:|--------:|------:|------:|-------|\n");
            for (ConceptSynthesis cs : conceptSyntheses) {
                sb.append("| ").append(escape(cs.conceptLabel()))
                        .append(" | ").append(cs.descriptionCount())
                        .append(" | ").append(cs.fullyQualifiedNameCount())
                        .append(" | ").append(cs.statedDefinitionCount())
                        .append(" | ").append(cs.inferredDefinitionCount())
                        .append(" | ").append(cs.navigationStatedCount())
                        .append(" | ").append(cs.navigationInferredCount())
                        .append(" | ").append(cs.identifierCount())
                        .append(" | ").append(cs.otherSemanticCount())
                        .append(" | ").append(escape(String.join("; ", cs.notes().castToList())))
                        .append(" |\n");
            }
            sb.append('\n');
        }

        private void appendDiagnosticsSection(StringBuilder sb) {
            sb.append("## Diagnostics\n\n");
            if (diagnostics.isEmpty()) {
                sb.append("_No issues detected._\n\n");
                return;
            }
            sb.append("| Severity | Category | Message |\n");
            sb.append("|----------|----------|---------|\n");
            for (Diagnostic d : diagnostics) {
                sb.append("| ").append(d.severity())
                        .append(" | ").append(escape(d.category()))
                        .append(" | ").append(escape(d.message()))
                        .append(" |\n");
            }
            sb.append('\n');
        }

        private static String escape(String s) {
            if (s == null) return "";
            return s.replace("|", "\\|").replace("\n", " ").replace("\r", " ");
        }

    }

    /**
     * Read the change set ZIP at the given path and return a {@link Report}.
     *
     * @param changeSetZip a ZIP produced by the change set exporter
     * @return a structured report; render with {@link Report#toMarkdown()}
     * @throws IOException if the ZIP cannot be opened or its entries cannot be read
     */
    public Report summarize(File changeSetZip) throws IOException {
        Objects.requireNonNull(changeSetZip, "changeSetZip");

        ManifestInfo manifest = readManifest(changeSetZip);

        // Maps keyed by primordial UUID — equals/hashCode are stable for UUID, and
        // every entity in the change set declares a primordial UUID.
        Map<UUID, ConceptAccum> concepts = new LinkedHashMap<>();
        Map<UUID, SemanticAccum> semantics = new LinkedHashMap<>();
        Map<UUID, PatternAccum> patterns = new LinkedHashMap<>();
        Map<UUID, StampAccum> stamps = new LinkedHashMap<>();

        readEntities(changeSetZip, concepts, semantics, patterns, stamps);

        Map<UUID, Integer> stampCitations = computeStampCitations(concepts, semantics, patterns, stamps);

        // Resolve labels lazily through PrimitiveData when available.
        LabelResolver resolver = new LabelResolver();

        ImmutableList<StampInfo> stampInfos = buildStampInfos(stamps, stampCitations, resolver);
        ImmutableList<ConceptInfo> conceptInfos = buildConceptInfos(concepts, resolver);
        ImmutableList<PatternInfo> patternInfos = buildPatternInfos(patterns, resolver);
        ImmutableList<SemanticGroup> semanticGroups = buildSemanticGroups(semantics, resolver);
        ImmutableList<ConceptSynthesis> conceptSyntheses =
                buildConceptSyntheses(concepts, semantics, resolver);
        ImmutableList<Diagnostic> diagnostics =
                buildDiagnostics(manifest, concepts, semantics, patterns, stamps, stampCitations);

        int observedConcepts = concepts.size();
        int observedSemantics = semantics.size();
        int observedPatterns = patterns.size();
        int observedStamps = stamps.size();
        int observedTotal = observedConcepts + observedSemantics + observedPatterns + observedStamps;

        return new Report(
                changeSetZip,
                manifest,
                stampInfos,
                conceptInfos,
                patternInfos,
                semanticGroups,
                conceptSyntheses,
                diagnostics,
                observedTotal,
                observedConcepts,
                observedSemantics,
                observedPatterns,
                observedStamps);
    }

    // -- Manifest ------------------------------------------------------------

    private ManifestInfo readManifest(File zip) throws IOException {
        try (FileInputStream fis = new FileInputStream(zip);
             BufferedInputStream bis = new BufferedInputStream(fis);
             ZipInputStream zis = new ZipInputStream(bis)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(MANIFEST_RELPATH)) {
                    Manifest mf = new Manifest(zis);
                    Attributes main = mf.getMainAttributes();
                    MutableList<NamedPublicId> entries = Lists.mutable.empty();
                    for (Map.Entry<String, Attributes> e : mf.getEntries().entrySet()) {
                        PublicId pid = parsePublicIdKey(e.getKey());
                        if (pid == null) continue;
                        String desc = e.getValue().getValue("Description");
                        entries.add(new NamedPublicId(pid, desc == null ? "" : desc));
                    }
                    return new ManifestInfo(
                            valueOrDash(main, "Packager-Name"),
                            valueOrDash(main, "Package-Date"),
                            parseLong(main.getValue("Total-Count")),
                            parseLong(main.getValue("Concept-Count")),
                            parseLong(main.getValue("Semantic-Count")),
                            parseLong(main.getValue("Pattern-Count")),
                            parseLong(main.getValue("Stamp-Count")),
                            entries.toImmutable());
                }
                zis.closeEntry();
            }
        }
        return null;
    }

    private static String valueOrDash(Attributes attrs, String key) {
        String v = attrs.getValue(key);
        return v == null ? "—" : v;
    }

    private static long parseLong(String s) {
        if (s == null) return -1;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return -1; }
    }

    private static PublicId parsePublicIdKey(String key) {
        if (key == null || key.isBlank()) return null;
        String[] parts = key.split(",");
        List<UUID> uuids = new ArrayList<>(parts.length);
        for (String p : parts) {
            try { uuids.add(UUID.fromString(p.trim())); }
            catch (IllegalArgumentException e) { return null; }
        }
        return PublicIds.of(uuids);
    }

    // -- Entity scan ---------------------------------------------------------

    private void readEntities(File zip,
                              Map<UUID, ConceptAccum> concepts,
                              Map<UUID, SemanticAccum> semantics,
                              Map<UUID, PatternAccum> patterns,
                              Map<UUID, StampAccum> stamps) throws IOException {
        try (FileInputStream fis = new FileInputStream(zip);
             BufferedInputStream bis = new BufferedInputStream(fis);
             ZipInputStream zis = new ZipInputStream(bis)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(MANIFEST_RELPATH)) {
                    zis.closeEntry();
                    continue;
                }
                while (zis.available() > 0) {
                    TinkarMsg msg = TinkarMsg.parseDelimitedFrom(zis);
                    if (msg == null) break;
                    classifyMessage(msg, concepts, semantics, patterns, stamps);
                }
                zis.closeEntry();
            }
        }
    }

    private void classifyMessage(TinkarMsg msg,
                                 Map<UUID, ConceptAccum> concepts,
                                 Map<UUID, SemanticAccum> semantics,
                                 Map<UUID, PatternAccum> patterns,
                                 Map<UUID, StampAccum> stamps) {
        switch (msg.getValueCase()) {
            case CONCEPT_CHRONOLOGY -> classifyConcept(msg.getConceptChronology(), concepts);
            case SEMANTIC_CHRONOLOGY -> classifySemantic(msg.getSemanticChronology(), semantics);
            case PATTERN_CHRONOLOGY -> classifyPattern(msg.getPatternChronology(), patterns);
            case STAMP_CHRONOLOGY -> classifyStamp(msg.getStampChronology(), stamps);
            case VALUE_NOT_SET -> LOG.warn("Encountered TinkarMsg with VALUE_NOT_SET");
        }
    }

    private void classifyConcept(ConceptChronology pb, Map<UUID, ConceptAccum> concepts) {
        PublicId pid = toPublicId(pb.getPublicId());
        if (pid == null) return;
        ConceptAccum acc = concepts.computeIfAbsent(pid.asUuidArray()[0], _ -> new ConceptAccum(pid));
        acc.versions += pb.getConceptVersionsCount();
        for (var v : pb.getConceptVersionsList()) {
            PublicId stampId = toPublicId(v.getStampChronologyPublicId());
            if (stampId != null) acc.citedStamps.add(stampId);
        }
    }

    private void classifySemantic(SemanticChronology pb, Map<UUID, SemanticAccum> semantics) {
        PublicId pid = toPublicId(pb.getPublicId());
        PublicId patternId = toPublicId(pb.getPatternForSemanticPublicId());
        PublicId refId = toPublicId(pb.getReferencedComponentPublicId());
        if (pid == null || patternId == null) return;
        SemanticAccum acc = semantics.computeIfAbsent(pid.asUuidArray()[0],
                _ -> new SemanticAccum(pid, patternId, refId));
        acc.versions += pb.getSemanticVersionsCount();
        SemanticVersion latest = null;
        for (SemanticVersion v : pb.getSemanticVersionsList()) {
            PublicId stampId = toPublicId(v.getStampChronologyPublicId());
            if (stampId != null) acc.citedStamps.add(stampId);
            // Last-listed wins as the "latest" for rendering. The exporter writes
            // versions in chronicle (creation) order, so this matches the most
            // recently authored version.
            latest = v;
        }
        acc.latestVersion = latest;
    }

    private void classifyPattern(PatternChronology pb, Map<UUID, PatternAccum> patterns) {
        PublicId pid = toPublicId(pb.getPublicId());
        if (pid == null) return;
        PatternAccum acc = patterns.computeIfAbsent(pid.asUuidArray()[0], _ -> new PatternAccum(pid));
        acc.versions += pb.getPatternVersionsCount();
        for (PatternVersion v : pb.getPatternVersionsList()) {
            PublicId stampId = toPublicId(v.getStampChronologyPublicId());
            if (stampId != null) acc.citedStamps.add(stampId);
            acc.purpose = toPublicId(v.getReferencedComponentPurposePublicId());
            acc.meaning = toPublicId(v.getReferencedComponentMeaningPublicId());
            acc.fieldDefCount = Math.max(acc.fieldDefCount, v.getFieldDefinitionsCount());
            // Latest field definitions for rendering — last write wins.
            acc.latestFieldDefs = v.getFieldDefinitionsList();
        }
    }

    private void classifyStamp(StampChronology pb, Map<UUID, StampAccum> stamps) {
        PublicId pid = toPublicId(pb.getPublicId());
        if (pid == null) return;
        StampAccum acc = stamps.computeIfAbsent(pid.asUuidArray()[0], _ -> new StampAccum(pid));
        // Each StampChronology message becomes one inner list. The schema has
        // first/second slots; either may be absent. Empty messages (zero versions)
        // are preserved so the journal count matches the on-wire message count.
        List<StampVersion> versions = new ArrayList<>(2);
        if (pb.hasFirstStampVersion()) versions.add(pb.getFirstStampVersion());
        if (pb.hasSecondStampVersion()) versions.add(pb.getSecondStampVersion());
        acc.messages.add(versions);
    }

    // -- Output construction -------------------------------------------------

    private ImmutableList<StampInfo> buildStampInfos(Map<UUID, StampAccum> stamps,
                                                     Map<UUID, Integer> citationCounts,
                                                     LabelResolver resolver) {
        MutableList<StampInfo> out = Lists.mutable.empty();
        for (StampAccum acc : stamps.values()) {
            MutableList<StampVersionEntry> entries = Lists.mutable.empty();
            int messageNumber = 1;
            for (List<StampVersion> message : acc.messages) {
                int position = 1;
                for (StampVersion v : message) {
                    PublicId statePid = toPublicId(v.getStatusPublicId());
                    PublicId authorPid = toPublicId(v.getAuthorPublicId());
                    PublicId modulePid = toPublicId(v.getModulePublicId());
                    PublicId pathPid = toPublicId(v.getPathPublicId());
                    entries.add(new StampVersionEntry(
                            messageNumber, position,
                            statePid, resolver.resolve(statePid),
                            v.getTime(),
                            authorPid, resolver.resolve(authorPid),
                            modulePid, resolver.resolve(modulePid),
                            pathPid, resolver.resolve(pathPid)));
                    position++;
                }
                messageNumber++;
            }
            int citations = citationCounts.getOrDefault(acc.publicId.asUuidArray()[0], 0);
            out.add(new StampInfo(acc.publicId, acc.messages.size(), citations, entries.toImmutable()));
        }
        // Sort stamps by their latest entry's time so the section reads in
        // chronicle order. Stamps without entries sort last.
        out.sortThis(Comparator.comparingLong(s -> {
            StampVersionEntry latest = s.latestEntry();
            return latest == null ? Long.MAX_VALUE : latest.timeEpochMs();
        }));
        return out.toImmutable();
    }

    private static Map<UUID, Integer> computeStampCitations(Map<UUID, ConceptAccum> concepts,
                                                             Map<UUID, SemanticAccum> semantics,
                                                             Map<UUID, PatternAccum> patterns,
                                                             Map<UUID, StampAccum> stamps) {
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        for (UUID stampKey : stamps.keySet()) counts.put(stampKey, 0);
        for (ConceptAccum c : concepts.values()) tally(c.citedStamps, stamps, counts);
        for (SemanticAccum s : semantics.values()) tally(s.citedStamps, stamps, counts);
        for (PatternAccum p : patterns.values()) tally(p.citedStamps, stamps, counts);
        return counts;
    }

    private static void tally(List<PublicId> citedIds, Map<UUID, StampAccum> stamps, Map<UUID, Integer> counts) {
        for (PublicId pid : citedIds) {
            UUID key = pid.asUuidArray()[0];
            if (stamps.containsKey(key)) counts.merge(key, 1, Integer::sum);
        }
    }

    private ImmutableList<ConceptInfo> buildConceptInfos(Map<UUID, ConceptAccum> concepts, LabelResolver resolver) {
        MutableList<ConceptInfo> out = Lists.mutable.empty();
        for (ConceptAccum acc : concepts.values()) {
            String label = resolver.resolve(acc.publicId);
            ImmutableList<PublicId> stampIds = Lists.immutable.withAll(acc.citedStamps);
            out.add(new ConceptInfo(acc.publicId, label, acc.versions, stampIds));
        }
        out.sortThis(Comparator.comparing(ConceptInfo::label));
        return out.toImmutable();
    }

    private ImmutableList<PatternInfo> buildPatternInfos(Map<UUID, PatternAccum> patterns, LabelResolver resolver) {
        MutableList<PatternInfo> out = Lists.mutable.empty();
        for (PatternAccum acc : patterns.values()) {
            String label = resolver.resolve(acc.publicId);
            String purpose = resolver.resolve(acc.purpose);
            String meaning = resolver.resolve(acc.meaning);
            ImmutableList<PublicId> stampIds = Lists.immutable.withAll(acc.citedStamps);
            out.add(new PatternInfo(acc.publicId, label, purpose, meaning, acc.fieldDefCount, acc.versions, stampIds));
        }
        out.sortThis(Comparator.comparing(PatternInfo::label));
        return out.toImmutable();
    }

    private ImmutableList<SemanticGroup> buildSemanticGroups(Map<UUID, SemanticAccum> semantics, LabelResolver resolver) {
        Map<UUID, MutableList<SemanticInfo>> byPattern = new LinkedHashMap<>();
        Map<UUID, PublicId> patternIds = new LinkedHashMap<>();
        for (SemanticAccum acc : semantics.values()) {
            UUID patternKey = acc.patternId.asUuidArray()[0];
            patternIds.putIfAbsent(patternKey, acc.patternId);
            String refLabel = resolver.resolve(acc.referencedComponentId);
            String rendered = renderFields(acc.patternId, acc.latestVersion, resolver);
            ImmutableList<PublicId> stampIds = Lists.immutable.withAll(acc.citedStamps);
            byPattern.computeIfAbsent(patternKey, _ -> Lists.mutable.empty())
                    .add(new SemanticInfo(
                            acc.publicId, acc.patternId, acc.referencedComponentId,
                            refLabel, acc.versions, stampIds, rendered));
        }
        MutableList<SemanticGroup> groups = Lists.mutable.empty();
        for (var e : byPattern.entrySet()) {
            PublicId patternId = patternIds.get(e.getKey());
            String patternLabel = resolver.resolve(patternId);
            MutableList<SemanticInfo> sem = e.getValue();
            sem.sortThis(Comparator.comparing(SemanticInfo::referencedComponentLabel));
            groups.add(new SemanticGroup(patternId, patternLabel, sem.toImmutable()));
        }
        groups.sortThis(Comparator.comparing(SemanticGroup::patternLabel));
        return groups.toImmutable();
    }

    private ImmutableList<ConceptSynthesis> buildConceptSyntheses(
            Map<UUID, ConceptAccum> concepts,
            Map<UUID, SemanticAccum> semantics,
            LabelResolver resolver) {
        // Index semantics by primordial UUID of their referenced component.
        Map<UUID, MutableList<SemanticAccum>> byRef = new LinkedHashMap<>();
        for (SemanticAccum sem : semantics.values()) {
            if (sem.referencedComponentId == null) continue;
            UUID refKey = sem.referencedComponentId.asUuidArray()[0];
            byRef.computeIfAbsent(refKey, _ -> Lists.mutable.empty()).add(sem);
        }
        MutableList<ConceptSynthesis> out = Lists.mutable.empty();
        for (ConceptAccum c : concepts.values()) {
            UUID conceptKey = c.publicId.asUuidArray()[0];
            List<SemanticAccum> related = byRef.getOrDefault(conceptKey, Lists.mutable.empty());

            int desc = 0, fqn = 0, stated = 0, inferred = 0, navS = 0, navI = 0, ident = 0, other = 0;
            UUID descPattern = TinkarTerm.DESCRIPTION_PATTERN.asUuidArray()[0];
            UUID statedPattern = TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.asUuidArray()[0];
            UUID inferredPattern = TinkarTerm.EL_PLUS_PLUS_INFERRED_AXIOMS_PATTERN.asUuidArray()[0];
            UUID statedNav = TinkarTerm.STATED_NAVIGATION_PATTERN.asUuidArray()[0];
            UUID inferredNav = TinkarTerm.INFERRED_NAVIGATION_PATTERN.asUuidArray()[0];
            UUID solorNav = TinkarTerm.NAVIGATION_PATTERN.asUuidArray()[0];
            UUID statedDigraph = TinkarTerm.EL_PLUS_PLUS_STATED_DIGRAPH.asUuidArray()[0];
            UUID inferredDigraph = TinkarTerm.EL_PLUS_PLUS_INFERRED_DIGRAPH.asUuidArray()[0];
            UUID identifierPattern = TinkarTerm.IDENTIFIER_PATTERN.asUuidArray()[0];
            UUID fqnTypeUuid = TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.asUuidArray()[0];

            for (SemanticAccum s : related) {
                UUID patternKey = s.patternId.asUuidArray()[0];
                if (patternKey.equals(descPattern)) {
                    if (latestDescriptionTypeIs(s.latestVersion, fqnTypeUuid)) fqn++;
                    else desc++;
                } else if (patternKey.equals(statedPattern) || patternKey.equals(statedDigraph)) {
                    stated++;
                } else if (patternKey.equals(inferredPattern) || patternKey.equals(inferredDigraph)) {
                    inferred++;
                } else if (patternKey.equals(statedNav)) {
                    navS++;
                } else if (patternKey.equals(inferredNav) || patternKey.equals(solorNav)) {
                    navI++;
                } else if (patternKey.equals(identifierPattern)) {
                    ident++;
                } else {
                    other++;
                }
            }

            MutableList<String> notes = Lists.mutable.empty();
            if (desc == 0 && fqn == 0) notes.add("no description in change set");
            if (fqn == 0) notes.add("no FQN in change set");
            if (stated == 0) notes.add("no stated definition");
            if (inferred == 0) notes.add("no inferred definition (reasoner not run?)");
            if (navS == 0 && navI == 0) notes.add("no navigation semantics");

            out.add(new ConceptSynthesis(
                    c.publicId, resolver.resolve(c.publicId),
                    desc, fqn, stated, inferred, navS, navI, ident, other,
                    notes.toImmutable()));
        }
        out.sortThis(Comparator.comparing(ConceptSynthesis::conceptLabel));
        return out.toImmutable();
    }

    private static boolean latestDescriptionTypeIs(SemanticVersion v, UUID typeUuid) {
        if (v == null || v.getFieldsCount() < 4) return false;
        Field typeField = v.getFields(3);
        if (typeField.getValueCase() != Field.ValueCase.PUBLIC_ID) return false;
        for (String u : typeField.getPublicId().getUuidsList()) {
            try {
                if (UUID.fromString(u).equals(typeUuid)) return true;
            } catch (IllegalArgumentException ignored) { /* ignore malformed */ }
        }
        return false;
    }

    private ImmutableList<Diagnostic> buildDiagnostics(
            ManifestInfo manifest,
            Map<UUID, ConceptAccum> concepts,
            Map<UUID, SemanticAccum> semantics,
            Map<UUID, PatternAccum> patterns,
            Map<UUID, StampAccum> stamps,
            Map<UUID, Integer> stampCitations) {
        MutableList<Diagnostic> out = Lists.mutable.empty();

        if (manifest != null) {
            if (manifest.conceptCount() >= 0 && manifest.conceptCount() != concepts.size()) {
                out.add(new Diagnostic(Diagnostic.Severity.WARN, "manifest",
                        "Concept-Count " + manifest.conceptCount() + " ≠ observed " + concepts.size()));
            }
            if (manifest.semanticCount() >= 0 && manifest.semanticCount() != semantics.size()) {
                out.add(new Diagnostic(Diagnostic.Severity.WARN, "manifest",
                        "Semantic-Count " + manifest.semanticCount() + " ≠ observed " + semantics.size()));
            }
            if (manifest.patternCount() >= 0 && manifest.patternCount() != patterns.size()) {
                out.add(new Diagnostic(Diagnostic.Severity.WARN, "manifest",
                        "Pattern-Count " + manifest.patternCount() + " ≠ observed " + patterns.size()));
            }
            if (manifest.stampCount() >= 0 && manifest.stampCount() != stamps.size()) {
                out.add(new Diagnostic(Diagnostic.Severity.WARN, "manifest",
                        "Stamp-Count " + manifest.stampCount() + " ≠ observed " + stamps.size()));
            }
            long observedTotal = (long) concepts.size() + semantics.size() + patterns.size() + stamps.size();
            if (manifest.totalCount() >= 0 && manifest.totalCount() != observedTotal) {
                out.add(new Diagnostic(Diagnostic.Severity.WARN, "manifest",
                        "Total-Count " + manifest.totalCount() + " ≠ observed " + observedTotal));
            }
        }

        // Stamps cited by no entity in this change set.
        for (StampAccum acc : stamps.values()) {
            int n = stampCitations.getOrDefault(acc.publicId.asUuidArray()[0], 0);
            if (n == 0) {
                out.add(new Diagnostic(Diagnostic.Severity.INFO, "stamp",
                        "Stamp " + acc.publicId.idString() + " is in the change set but cited by no version"));
            }
        }

        // Orphan semantics: referenced component not in change set and not in data store (when available).
        for (SemanticAccum s : semantics.values()) {
            if (s.referencedComponentId == null) continue;
            UUID refKey = s.referencedComponentId.asUuidArray()[0];
            boolean inChangeSet = concepts.containsKey(refKey)
                    || semantics.containsKey(refKey)
                    || patterns.containsKey(refKey);
            if (inChangeSet) continue;
            if (!isInDataStore(s.referencedComponentId)) {
                out.add(new Diagnostic(Diagnostic.Severity.WARN, "orphan-reference",
                        "Semantic " + s.publicId.idString() + " references component "
                                + s.referencedComponentId.idString() + " that is not in this change set "
                                + "and not present in the data store"));
            }
        }

        return out.toImmutable();
    }

    // -- Field rendering -----------------------------------------------------

    private String renderFields(PublicId patternId, SemanticVersion v, LabelResolver resolver) {
        if (v == null || v.getFieldsCount() == 0) return "(no fields)";
        UUID patternKey = patternId.asUuidArray()[0];

        if (patternKey.equals(TinkarTerm.DESCRIPTION_PATTERN.asUuidArray()[0])) {
            // language, text, case, descType
            String text = fieldString(v, 1);
            String descType = resolver.resolve(fieldPublicId(v, 3));
            String lang = resolver.resolve(fieldPublicId(v, 0));
            return "[" + descType + "/" + lang + "] " + truncate(text, 120);
        }
        if (patternKey.equals(TinkarTerm.IDENTIFIER_PATTERN.asUuidArray()[0])) {
            String source = resolver.resolve(fieldPublicId(v, 0));
            String id = fieldString(v, 1);
            return source + " = " + id;
        }
        if (patternKey.equals(TinkarTerm.STATED_NAVIGATION_PATTERN.asUuidArray()[0])
                || patternKey.equals(TinkarTerm.INFERRED_NAVIGATION_PATTERN.asUuidArray()[0])
                || patternKey.equals(TinkarTerm.NAVIGATION_PATTERN.asUuidArray()[0])) {
            // Field 0 = destinations (children), Field 1 = origins (parents)
            String children = renderPublicIdSet(v, 0, resolver);
            String parents = renderPublicIdSet(v, 1, resolver);
            return "parents=[" + parents + "] children=[" + children + "]";
        }
        if (patternKey.equals(TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.asUuidArray()[0])
                || patternKey.equals(TinkarTerm.EL_PLUS_PLUS_INFERRED_AXIOMS_PATTERN.asUuidArray()[0])) {
            return summarizeAxiomTree(v, 0);
        }
        return renderGenericFields(v, resolver);
    }

    private static String fieldString(SemanticVersion v, int idx) {
        if (v.getFieldsCount() <= idx) return "?";
        Field f = v.getFields(idx);
        if (f.getValueCase() == Field.ValueCase.STRING_VALUE) return f.getStringValue();
        return "?";
    }

    private static PublicId fieldPublicId(SemanticVersion v, int idx) {
        if (v.getFieldsCount() <= idx) return null;
        Field f = v.getFields(idx);
        if (f.getValueCase() == Field.ValueCase.PUBLIC_ID) return toPublicId(f.getPublicId());
        return null;
    }

    private static String renderPublicIdSet(SemanticVersion v, int idx, LabelResolver resolver) {
        if (v.getFieldsCount() <= idx) return "";
        Field f = v.getFields(idx);
        List<String> labels = new ArrayList<>();
        switch (f.getValueCase()) {
            case PUBLIC_IDSET -> {
                for (var p : f.getPublicIdset().getPublicIdsList()) {
                    labels.add(resolver.resolve(toPublicId(p)));
                }
            }
            case PUBLIC_IDS -> {
                for (var p : f.getPublicIds().getPublicIdsList()) {
                    labels.add(resolver.resolve(toPublicId(p)));
                }
            }
            default -> {}
        }
        return String.join(", ", labels);
    }

    private static String summarizeAxiomTree(SemanticVersion v, int idx) {
        if (v.getFieldsCount() <= idx) return "(no axiom field)";
        Field f = v.getFields(idx);
        if (f.getValueCase() == Field.ValueCase.DI_TREE) {
            return "DiTree(" + f.getDiTree().getVerticesCount() + " vertices)";
        }
        if (f.getValueCase() == Field.ValueCase.DI_GRAPH) {
            return "DiGraph(" + f.getDiGraph().getVerticesCount() + " vertices)";
        }
        return "(non-tree axiom field: " + f.getValueCase() + ")";
    }

    private String renderGenericFields(SemanticVersion v, LabelResolver resolver) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.getFieldsCount(); i++) {
            if (i > 0) sb.append(" | ");
            Field f = v.getFields(i);
            sb.append("f").append(i).append("=");
            sb.append(switch (f.getValueCase()) {
                case BOOLEAN_VALUE -> Boolean.toString(f.getBooleanValue());
                case INT_VALUE -> Integer.toString(f.getIntValue());
                case FLOAT_VALUE -> Float.toString(f.getFloatValue());
                case STRING_VALUE -> "\"" + truncate(f.getStringValue(), 60) + "\"";
                case TIME_VALUE -> formatEpochMs(f.getTimeValue());
                case PUBLIC_ID -> resolver.resolve(toPublicId(f.getPublicId()));
                case PUBLIC_IDS, PUBLIC_IDSET -> "{" + renderPublicIdSet(v, i, resolver) + "}";
                case DI_TREE -> "DiTree(" + f.getDiTree().getVerticesCount() + ")";
                case DI_GRAPH -> "DiGraph(" + f.getDiGraph().getVerticesCount() + ")";
                case BYTES_VALUE -> "bytes(" + f.getBytesValue().size() + ")";
                case BIG_DECIMAL -> "BigDecimal";
                case LONG -> Long.toString(f.getLong().getValue());
                default -> f.getValueCase().toString();
            });
        }
        return sb.toString();
    }

    private static String formatEpochMs(long epochMs) {
        return Instant.ofEpochMilli(epochMs).atZone(ZoneId.of("UTC")).format(STAMP_TIME);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    // -- Public-id helpers ---------------------------------------------------

    private static PublicId toPublicId(dev.ikm.tinkar.schema.PublicId pb) {
        if (pb == null || pb.getUuidsCount() == 0) return null;
        List<UUID> uuids = new ArrayList<>(pb.getUuidsCount());
        for (String u : pb.getUuidsList()) {
            try { uuids.add(UUID.fromString(u)); }
            catch (IllegalArgumentException e) { return null; }
        }
        return PublicIds.of(uuids);
    }

    /** True when a {@link PrimitiveData} service is running and contains the given public id. */
    private static boolean isInDataStore(PublicId pid) {
        if (pid == null) return false;
        try {
            return PrimitiveData.get().hasPublicId(pid);
        } catch (RuntimeException e) {
            return false;
        }
    }

    // -- Accumulators --------------------------------------------------------

    private static final class ConceptAccum {
        final PublicId publicId;
        int versions;
        final List<PublicId> citedStamps = new ArrayList<>();
        ConceptAccum(PublicId publicId) { this.publicId = publicId; }
    }

    private static final class SemanticAccum {
        final PublicId publicId;
        final PublicId patternId;
        final PublicId referencedComponentId;
        int versions;
        final List<PublicId> citedStamps = new ArrayList<>();
        SemanticVersion latestVersion;
        SemanticAccum(PublicId publicId, PublicId patternId, PublicId referencedComponentId) {
            this.publicId = publicId;
            this.patternId = patternId;
            this.referencedComponentId = referencedComponentId;
        }
    }

    private static final class PatternAccum {
        final PublicId publicId;
        int versions;
        final List<PublicId> citedStamps = new ArrayList<>();
        PublicId purpose;
        PublicId meaning;
        int fieldDefCount;
        List<FieldDefinition> latestFieldDefs = List.of();
        PatternAccum(PublicId publicId) { this.publicId = publicId; }
    }

    private static final class StampAccum {
        final PublicId publicId;
        /** One inner list per {@code StampChronology} message; each holds 1–2 versions. */
        final List<List<StampVersion>> messages = new ArrayList<>();
        StampAccum(PublicId publicId) { this.publicId = publicId; }
    }

    /**
     * Resolves public ids to display labels via {@link PrimitiveData#text(int)}
     * when a service is available; falls back to the primordial UUID string when
     * the data store is absent or does not contain the public id.
     */
    private final class LabelResolver {
        String resolve(PublicId pid) {
            if (pid == null) return "—";
            Optional<String> text = lookupText(pid);
            return text.orElse(pid.asUuidArray()[0].toString());
        }

        private Optional<String> lookupText(PublicId pid) {
            try {
                if (!PrimitiveData.get().hasPublicId(pid)) return Optional.empty();
                int nid = PrimitiveData.nid(pid);
                return PrimitiveData.textOptional(nid);
            } catch (RuntimeException e) {
                return Optional.empty();
            }
        }
    }
}
