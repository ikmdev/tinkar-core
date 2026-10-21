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

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.StampEntityVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The commit validator of IKE-Network/ike-issues#1313 (design {@code design-2026-10-20-instance-data}):
 * before a transaction's stamps are finalized, every version it wrote is checked against the
 * boundary between knowledge, ephemeral and instance data.
 *
 * <ul>
 * <li><em>Knowledge never references ephemeral or instance data.</em> A version written under a
 * knowledge module must not carry a reference to a component of another kind.</li>
 * <li><em>The two kinds do not cross.</em> Ephemeral data does not reference instance data, nor the
 * reverse.</li>
 * <li><em>Birth is final.</em> A later version of a component is written under a module of its
 * first version's kind.</li>
 * <li><em>An instance pattern holds only its own kind.</em> An element of an ephemeral or instance
 * pattern is written under a module of that kind. A knowledge pattern may hold elements of any
 * kind, each of its own module's kind: the names of an instance pattern are descriptions.</li>
 * <li><em>The parents are disjoint.</em> No module is a kind of both.</li>
 * </ul>
 *
 * <p>The mode is the system property {@value #MODE_PROPERTY}: {@code warn} (the default) logs what
 * a commit crosses and lets it proceed, {@code fail} refuses the commit with a
 * {@link BoundaryViolationException}, and {@code off} skips the check. The check costs nothing
 * until the store holds one of the two module parents, since before then every module is
 * knowledge and nothing can cross.
 */
public final class CommitBoundary {

    /** The system property that sets the mode: {@code warn}, {@code fail} or {@code off}. */
    public static final String MODE_PROPERTY = "ike.boundary.mode";

    private static final Logger LOG = LoggerFactory.getLogger(CommitBoundary.class);
    private static final int LOGGED = 20;

    /** What the validator does with a violation. */
    public enum Mode { OFF, WARN, FAIL }

    private CommitBoundary() {
    }

    /** @return the mode in force now */
    public static Mode mode() {
        String value = System.getProperty(MODE_PROPERTY, "warn").strip().toUpperCase();
        try {
            return Mode.valueOf(value);
        } catch (IllegalArgumentException e) {
            return Mode.WARN;
        }
    }

    /** @return whether a commit is checked now: the mode is not off and the store holds a module parent */
    public static boolean active() {
        return mode() != Mode.OFF && ModuleKinds.parentsPresent();
    }

    /**
     * Checks the versions a transaction wrote, and acts by the mode.
     *
     * @param transactionName the transaction's name, for messages
     * @param versions        every version written under the transaction's stamps
     * @return what the versions cross
     * @throws BoundaryViolationException in fail mode, when they cross anything
     */
    public static List<BoundaryViolation> enforce(String transactionName, List<? extends EntityVersion> versions) {
        Mode mode = mode();
        if (mode == Mode.OFF || !ModuleKinds.parentsPresent()) {
            return List.of();
        }
        List<BoundaryViolation> violations = check(versions);
        if (violations.isEmpty()) {
            return violations;
        }
        if (mode == Mode.FAIL) {
            throw new BoundaryViolationException(transactionName, violations);
        }
        LOG.warn("Commit of {} crosses the data boundary (IKE-Network/ike-issues#1313; {}=warn): {}",
                transactionName, MODE_PROPERTY, summary(violations));
        return violations;
    }

    /**
     * Checks versions against every rule, whatever the mode.
     *
     * @param versions the versions
     * @return what they cross
     */
    public static List<BoundaryViolation> check(List<? extends EntityVersion> versions) {
        List<BoundaryViolation> violations = new ArrayList<>();
        for (EntityVersion version : versions) {
            if (version instanceof StampEntityVersion) {
                continue;
            }
            Entity<?> entity = version.entity();
            PublicId source = entity.publicId();
            ModuleKinds.Resolution module = EntityBoundaries.stampKind(version.stampNid());
            if (module.conflicting()) {
                violations.add(new BoundaryViolation(BoundaryViolation.Rule.MODULE_UNDER_BOTH_PARENTS, source, null,
                        "its module is a kind of both the ephemeral and the instance module"));
            }
            DataKind kind = module.kind();
            DataKind birth = EntityBoundaries.birthKind(entity.nid());
            if (birth != kind) {
                violations.add(new BoundaryViolation(BoundaryViolation.Rule.BIRTH_IS_FINAL, source, null,
                        "first written as " + birth.word() + ", now under a " + kind.word() + " module"));
            }
            if (entity instanceof SemanticEntity<?> semantic) {
                DataKind patternKind = EntityBoundaries.birthKind(semantic.patternNid());
                if (patternKind != DataKind.KNOWLEDGE && patternKind != kind) {
                    violations.add(new BoundaryViolation(BoundaryViolation.Rule.ELEMENT_OUTSIDE_ITS_PATTERN, source,
                            semantic.publicId(), "an element of a " + patternKind.word() + " pattern written under a "
                            + kind.word() + " module"));
                }
            }
            References.of(version, (where, target) -> {
                if (target == 0 || target == entity.nid()) {
                    return;
                }
                DataKind targetKind = EntityBoundaries.kindOf(target);
                if (targetKind == kind) {
                    return;
                }
                if (kind == DataKind.KNOWLEDGE) {
                    violations.add(new BoundaryViolation(BoundaryViolation.Rule.KNOWLEDGE_REFERENCES_DATA, source,
                            publicId(target), "knowledge references " + targetKind.word() + " data through its " + where));
                } else if (targetKind != DataKind.KNOWLEDGE) {
                    violations.add(new BoundaryViolation(BoundaryViolation.Rule.CROSSES_KINDS, source,
                            publicId(target), kind.word() + " data references " + targetKind.word() + " data through its " + where));
                }
            });
        }
        return violations;
    }

    /**
     * The violations in words: how many, and the first few.
     *
     * @param violations the violations
     * @return the summary
     */
    public static String summary(List<BoundaryViolation> violations) {
        String shown = violations.stream().limit(LOGGED).map(BoundaryViolation::toString).collect(Collectors.joining("; "));
        return violations.size() + (violations.size() == 1 ? " violation: " : " violations: ") + shown
                + (violations.size() > LOGGED ? "; …" : "");
    }

    private static PublicId publicId(long nid) {
        return EntityBoundaries.read(nid).<PublicId>map(Entity::publicId).orElse(null);
    }
}
