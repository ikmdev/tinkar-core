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

/**
 * <h2>STAMP Coordinate System</h2>
 *
 * <p>Provides coordinates that control temporal versioning and provenance tracking in the Tinkar
 * knowledge graph. STAMP (Status, Time, Author, Module, Path) coordinates specify which versions
 * of concepts, semantics, and patterns are visible based on their status, temporal position,
 * authorship, modular organization, and development path.</p>
 *
 * <p><b>STAMP Metadata Components</b></p>
 *
 * <p>Every version in Tinkar carries STAMP metadata consisting of five fields:</p>
 *
 * <dl>
 * <dt><strong>Status</strong></dt>
 * <dd>The lifecycle state of the version: ACTIVE or INACTIVE. Active versions represent current
 * knowledge, while inactive versions represent deprecated, retired, or erroneous content.</dd>
 *
 * <dt><strong>Time</strong></dt>
 * <dd>The timestamp (milliseconds since epoch) when the version was created. Time provides
 * temporal ordering and enables point-in-time queries.</dd>
 *
 * <dt><strong>Author</strong></dt>
 * <dd>The concept identifying the user or system that created the version. Provides provenance
 * and accountability for changes.</dd>
 *
 * <dt><strong>Module</strong></dt>
 * <dd>The concept identifying the organizational or content module containing the version.
 * Modules enable modular knowledge organization and selective loading.</dd>
 *
 * <dt><strong>Path</strong></dt>
 * <dd>The concept identifying the development or release path where the version exists. Paths
 * enable branching workflows like development → staging → production.</dd>
 * </dl>
 *
 * <p><b>Core Coordinate Types</b></p>
 *
 * <p>The STAMP package provides three levels of coordinate specificity:</p>
 *
 * <p><b>StampPosition</b></p>
 * <p>Specifies a point in time on a specific path:</p>
 * <ul>
 * <li><strong>Time</strong> - Specific timestamp or {@code Long.MAX_VALUE} for latest</li>
 * <li><strong>Path</strong> - Development branch (e.g., DEVELOPMENT_PATH, MASTER_PATH)</li>
 * <li>Represents "what was visible at time T on path P"</li>
 * </ul>
 *
 * <p><b>StampPath</b></p>
 * <p>Represents a development path with its origins:</p>
 * <ul>
 * <li><strong>Path Concept</strong> - Identity of the path</li>
 * <li><strong>Origin Positions</strong> - Set of positions where this path originated (branched from)</li>
 * <li>Enables path-relative versioning and merging</li>
 * </ul>
 *
 * <p><b>StampCoordinate</b></p>
 * <p>Complete specification for version filtering:</p>
 * <ul>
 * <li><strong>Allowed States</strong> - Which statuses to include (active, inactive, or both)</li>
 * <li><strong>Stamp Position</strong> - Time and path specification</li>
 * <li><strong>Module Filters</strong> - Modules to include or exclude</li>
 * <li><strong>Module Priorities</strong> - Preference order when multiple versions exist</li>
 * </ul>
 *
 * <p><b>Version Selection Algorithm</b></p>
 *
 * <p>STAMP coordinates determine the "latest" version through multi-stage filtering:</p>
 *
 * <ol>
 * <li><strong>State Filtering</strong> - Keep only versions with allowed states</li>
 * <li><strong>Module Filtering</strong> - Keep only versions from allowed modules, exclude excluded modules</li>
 * <li><strong>Path Filtering</strong> - Keep only versions visible on the coordinate's path
 * (considering path origins)</li>
 * <li><strong>Time Filtering</strong> - Keep only versions with time ≤ coordinate's time</li>
 * <li><strong>Latest Selection</strong> - Among remaining versions, select most recent by time</li>
 * <li><strong>Module Priority</strong> - If multiple versions have same time, use module priority order</li>
 * </ol>
 *
 * <p><b>Core Interfaces</b></p>
 *
 * <p><b>StampCoordinate</b></p>
 * <p>Primary interface for version filtering. Key methods include:</p>
 * <ul>
 * <li>{@code allowedStates()} - Get state filter</li>
 * <li>{@code stampPosition()} - Get temporal position</li>
 * <li>{@code moduleNids()} - Get included modules</li>
 * <li>{@code excludedModuleNids()} - Get excluded modules</li>
 * <li>{@code modulePriorityNidList()} - Get module preference order</li>
 * <li>{@code withAllowedStates()}, {@code withStampPosition()}, etc. - Create modified coordinates</li>
 * </ul>
 *
 * <p><b>StampPosition</b></p>
 * <p>Represents a temporal position. Key methods:</p>
 * <ul>
 * <li>{@code time()} - Get timestamp</li>
 * <li>{@code getPathForPositionNid()} - Get path identifier</li>
 * <li>{@code withTime()}, {@code withPathForPositionNid()} - Create modified positions</li>
 * </ul>
 *
 * <p><b>StampPath</b></p>
 * <p>Represents a development path. Key methods:</p>
 * <ul>
 * <li>{@code pathConceptNid()} - Get path concept identifier</li>
 * <li>{@code pathOrigins()} - Get origin positions (where path branched from)</li>
 * </ul>
 *
 * <p><b>StateSet Enumeration</b></p>
 *
 * <p>The {@link dev.ikm.tinkar.coordinate.stamp.StateSet} enum defines state filters:</p>
 * <ul>
 * <li><strong>ACTIVE</strong> - Include only active versions</li>
 * <li><strong>INACTIVE</strong> - Include only inactive versions</li>
 * <li><strong>ACTIVE_AND_INACTIVE</strong> - Include both (no state filtering)</li>
 * </ul>
 *
 * <p><b>Implementation Patterns</b></p>
 *
 * <p><b>Record Implementations</b></p>
 * <p>Immutable, thread-safe record types:</p>
 * <ul>
 * <li>{@link dev.ikm.tinkar.coordinate.stamp.StampCoordinateRecord}</li>
 * <li>{@link dev.ikm.tinkar.coordinate.stamp.StampPositionRecord}</li>
 * <li>{@link dev.ikm.tinkar.coordinate.stamp.StampPathImmutable}</li>
 * </ul>
 *
 * <pre>{@code
 * // Create standard coordinates via factory methods
 * StampCoordinateRecord devLatest = Coordinates.Stamp.DevelopmentLatest();
 * StampCoordinateRecord masterActive = Coordinates.Stamp.MasterLatestActiveOnly();
 *
 * // Create custom coordinate
 * StampPositionRecord position = StampPositionRecord.make(
 *     System.currentTimeMillis(),
 *     TinkarTerm.DEVELOPMENT_PATH
 * );
 * StampCoordinateRecord custom = StampCoordinateRecord.make(
 *     StateSet.ACTIVE,
 *     position,
 *     IntIds.set.of(TinkarTerm.SOLOR_MODULE.nid())
 * );
 * }</pre>
 *
 * <p><b>Delegate Implementations</b></p>
 * <p>Delegation interfaces for composition:</p>
 * <ul>
 * <li>{@link dev.ikm.tinkar.coordinate.stamp.StampCoordinateDelegate}</li>
 * <li>{@link dev.ikm.tinkar.coordinate.stamp.StampPositionDelegate}</li>
 * <li>{@link dev.ikm.tinkar.coordinate.stamp.StampPathDelegate}</li>
 * </ul>
 *
 * <p><b>Common Usage Patterns</b></p>
 *
 * <p><b>Latest Active Versions</b></p>
 * <pre>{@code
 * // Most common: latest active versions on development path
 * StampCoordinateRecord stamp = Coordinates.Stamp.DevelopmentLatestActiveOnly();
 *
 * // Or on master/production path
 * StampCoordinateRecord stamp = Coordinates.Stamp.MasterLatestActiveOnly();
 * }</pre>
 *
 * <p><b>Point-in-Time Queries</b></p>
 * <pre>{@code
 * // View knowledge as it was on a specific date
 * long timestampJan1_2024 = Instant.parse("2024-01-01T00:00:00Z")
 *     .toEpochMilli();
 * StampPositionRecord position = StampPositionRecord.make(
 *     timestampJan1_2024,
 *     TinkarTerm.MASTER_PATH
 * );
 * StampCoordinateRecord stamp = StampCoordinateRecord.make(
 *     StateSet.ACTIVE,
 *     position,
 *     IntIds.set.empty()
 * );
 * }</pre>
 *
 * <p><b>Module Filtering</b></p>
 * <pre>{@code
 * // Include only specific modules
 * StampCoordinateRecord filtered = stamp.withModuleNids(
 *     IntIds.set.of(
 *         TinkarTerm.SOLOR_MODULE.nid(),
 *         TinkarTerm.SNOMED_CT_CORE_MODULE.nid()
 *     )
 * );
 *
 * // Exclude specific modules
 * StampCoordinateRecord excluded = stamp.withExcludedModuleNids(
 *     IntIds.set.of(TinkarTerm.DEPRECATED_MODULE.nid())
 * );
 * }</pre>
 *
 * <p><b>Including Inactive Content</b></p>
 * <pre>{@code
 * // Include both active and inactive (useful for administrative views)
 * StampCoordinateRecord all = stamp.withAllowedStates(
 *     StateSet.ACTIVE_AND_INACTIVE
 * );
 *
 * // Only inactive (useful for retirement analysis)
 * StampCoordinateRecord inactiveOnly = stamp.withAllowedStates(
 *     StateSet.INACTIVE
 * );
 * }</pre>
 *
 * <p><b>Path Branching and Merging</b></p>
 *
 * <p>Paths support branching workflows:</p>
 * <pre>{@code
 * // Master path originates from primordial path
 * StampPathImmutable masterPath = StampPathImmutable.make(
 *     TinkarTerm.MASTER_PATH,
 *     Sets.immutable.of(
 *         StampPositionRecord.make(Long.MAX_VALUE, TinkarTerm.PRIMORDIAL_PATH.nid())
 *     )
 * );
 *
 * // Development path branches from master
 * StampPathImmutable devPath = StampPathImmutable.make(
 *     TinkarTerm.DEVELOPMENT_PATH,
 *     Sets.immutable.of(
 *         StampPositionRecord.make(branchTimestamp, TinkarTerm.MASTER_PATH.nid())
 *     )
 * );
 * }</pre>
 *
 * <p><b>Integration with Calculators</b></p>
 *
 * <p>STAMP coordinates are used by {@link dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator}
 * implementations to perform version resolution:</p>
 *
 * <pre>{@code
 * StampCalculator calc = StampCalculatorWithCache.getCalculator(stampCoord);
 *
 * // Get latest version of an entity
 * Latest<ConceptVersion> latest = calc.latest(conceptEntity);
 *
 * // Get latest version of a semantic
 * Latest<SemanticEntityVersion> latest = calc.latest(semanticEntity);
 *
 * // Test relative position of stamps
 * RelativePosition position = calc.relativePosition(stamp1, stamp2);
 * }</pre>
 *
 * <p><b>Change Tracking</b></p>
 *
 * <p>The {@link dev.ikm.tinkar.coordinate.stamp.change} subpackage provides records for tracking
 * changes between versions, enabling change detection, diff generation, and audit trails.</p>
 *
 * <p><b>Thread Safety and Immutability</b></p>
 *
 * <p>All record implementations are immutable and thread-safe. STAMP coordinates can be safely:</p>
 * <ul>
 * <li>Shared across threads</li>
 * <li>Used as map keys</li>
 * <li>Cached in static fields</li>
 * <li>Passed across service boundaries</li>
 * </ul>
 *
 * <p><b>Performance Considerations</b></p>
 *
 * <ul>
 * <li><strong>Content-Based UUIDs</strong> - Coordinates generate stable UUIDs for caching</li>
 * <li><strong>Latest Calculation</strong> - Version resolution is O(V) where V is version count</li>
 * <li><strong>Module Filtering</strong> - Empty module set = wildcard (faster than explicit listing)</li>
 * <li><strong>Calculator Caching</strong> - Use cached calculators for repeated queries</li>
 * </ul>
 *
 * @see dev.ikm.tinkar.coordinate.stamp.StampCoordinate
 * @see dev.ikm.tinkar.coordinate.stamp.StampPosition
 * @see dev.ikm.tinkar.coordinate.stamp.StampPath
 * @see dev.ikm.tinkar.coordinate.stamp.StampCoordinateRecord
 * @see dev.ikm.tinkar.coordinate.stamp.StampPositionRecord
 * @see dev.ikm.tinkar.coordinate.stamp.StateSet
 * @see dev.ikm.tinkar.coordinate.stamp.calculator
 * @see dev.ikm.tinkar.coordinate.stamp.change
 * @see dev.ikm.tinkar.coordinate.Coordinates.Stamp
 */
package dev.ikm.tinkar.coordinate.stamp;