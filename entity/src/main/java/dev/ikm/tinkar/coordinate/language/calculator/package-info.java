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
 * <h2>Language Calculator System</h2>
 *
 * <p>Provides computational implementations that combine language coordinates with STAMP coordinates
 * to retrieve, rank, and select preferred descriptions for concepts and other entities. Language
 * calculators implement the description selection algorithm and provide high-level access methods
 * for obtaining human-readable text.</p>
 *
 * <p><b>Core Responsibilities</b></p>
 *
 * <p>Language calculators execute the multi-stage description selection and ranking process:</p>
 *
 * <ol>
 * <li><strong>Description Discovery</strong> - Find all description semantics for a given entity</li>
 * <li><strong>Version Filtering</strong> - Apply STAMP coordinate to get latest visible versions</li>
 * <li><strong>Language Matching</strong> - Filter by language and pattern preferences</li>
 * <li><strong>Type Ranking</strong> - Rank by description type priority</li>
 * <li><strong>Dialect Ranking</strong> - Further rank by dialect preferences</li>
 * <li><strong>Module Ranking</strong> - Final ranking by module preferences</li>
 * <li><strong>Natural Order Fallback</strong> - Sort alphabetically when all else is equal</li>
 * </ol>
 *
 * <p><b>Core Interfaces and Classes</b></p>
 *
 * <p><b>LanguageCalculator</b></p>
 * <p>The primary interface defining description retrieval operations. Key methods include:</p>
 *
 * <ul>
 * <li><strong>getDescriptionText()</strong> - Get preferred text for an entity</li>
 * <li><strong>getFullyQualifiedName()</strong> - Get FQN for an entity</li>
 * <li><strong>getPreferredDescription()</strong> - Get highest-ranked description semantic</li>
 * <li><strong>getDescription()</strong> - Get description for specific description type</li>
 * <li><strong>getDescriptionsForComponent()</strong> - Get all descriptions, ranked</li>
 * <li><strong>languageCoordinateList()</strong> - Get cascading language coordinate chain</li>
 * </ul>
 *
 * <p><b>LanguageCalculatorWithCache</b></p>
 * <p>Cached implementation that stores description lookup results for performance. Features:</p>
 * <ul>
 * <li>Thread-safe caching of description text and semantic results</li>
 * <li>Content-based coordinate UUID for cache key generation</li>
 * <li>Static factory method {@code getCalculator()} for instance retrieval</li>
 * <li>Automatic cache invalidation on coordinate changes</li>
 * </ul>
 *
 * <pre>{@code
 * // Get a cached calculator instance
 * StampCoordinateRecord stampCoord = Coordinates.Stamp.DevelopmentLatestActiveOnly();
 * List<LanguageCoordinate> langCoords = Lists.immutable.of(
 *     Coordinates.Language.UsEnglishRegularName()
 * );
 * LanguageCalculator calculator =
 *     LanguageCalculatorWithCache.getCalculator(stampCoord, langCoords);
 *
 * // Retrieve preferred text (cached on subsequent calls)
 * String text = calculator.getDescriptionText(conceptNid);
 * }</pre>
 *
 * <p><b>LanguageCalculatorDelegate</b></p>
 * <p>Delegation interface allowing classes to provide language calculator functionality by
 * delegating to an underlying calculator instance. Used extensively in view calculators and
 * other composite coordinate types:</p>
 * <pre>{@code
 * public class ViewCalculator implements LanguageCalculatorDelegate {
 *     private final LanguageCalculator languageCalculator;
 *
 *     @Override
 *     public LanguageCalculator languageCalculator() {
 *         return languageCalculator;
 *     }
 *
 *     // Inherits all LanguageCalculator methods via delegation
 * }
 * }</pre>
 *
 * <p><b>Description Selection Algorithm</b></p>
 *
 * <p>The calculator implements a sophisticated multi-criteria ranking algorithm:</p>
 *
 * <p><b>Stage 1: Discovery and Version Filtering</b></p>
 * <pre>{@code
 * // Find all description semantics for the concept
 * List<SemanticEntity> descriptions = findDescriptionsForConcept(conceptNid);
 *
 * // Filter to latest versions based on STAMP coordinate
 * List<Latest<SemanticEntityVersion>> latestVersions =
 *     descriptions.stream()
 *         .map(desc -> stampCalculator.latest(desc))
 *         .filter(Latest::isPresent)
 *         .collect(toList());
 * }</pre>
 *
 * <p><b>Stage 2: Language and Pattern Filtering</b></p>
 * <pre>{@code
 * // For each language coordinate in priority order
 * for (LanguageCoordinate langCoord : languageCoordinateList()) {
 *     // Filter by language
 *     List<SemanticEntityVersion> matching = latestVersions.stream()
 *         .filter(v -> matchesLanguage(v, langCoord.languageConceptNid()))
 *         .filter(v -> matchesPattern(v, langCoord.descriptionPatternPreferenceNidList()))
 *         .collect(toList());
 *
 *     if (!matching.isEmpty()) {
 *         // Found matches in this language, proceed to ranking
 *         return rankDescriptions(matching, langCoord);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Stage 3: Type, Dialect, and Module Ranking</b></p>
 * <pre>{@code
 * // Rank by description type priority
 * for (int typeNid : langCoord.descriptionTypePreferenceNidList()) {
 *     List<SemanticEntityVersion> ofType = descriptions.stream()
 *         .filter(v -> v.descriptionTypeNid() == typeNid)
 *         .collect(toList());
 *
 *     if (!ofType.isEmpty()) {
 *         // Rank by dialect, then module, then natural order
 *         return rankByDialectModuleAndNaturalOrder(ofType, langCoord);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Cascading Language Coordinates</b></p>
 *
 * <p>Calculators support multiple language coordinates in priority order, enabling graceful
 * fallback behavior when descriptions aren't available in the preferred language:</p>
 *
 * <pre>{@code
 * List<LanguageCoordinate> cascade = Lists.immutable.of(
 *     Coordinates.Language.SpanishPreferredName(),      // 1st priority: Spanish
 *     Coordinates.Language.UsEnglishRegularName(),      // 2nd priority: English
 *     Coordinates.Language.AnyLanguageRegularName()     // 3rd priority: Any language
 * );
 *
 * LanguageCalculator calculator =
 *     LanguageCalculatorWithCache.getCalculator(stampCoord, cascade);
 *
 * // Returns Spanish if available, else English, else any language
 * String text = calculator.getDescriptionText(conceptNid);
 * }</pre>
 *
 * <p><b>Common Usage Patterns</b></p>
 *
 * <p><b>Getting Preferred Text</b></p>
 * <pre>{@code
 * // Simplest: get text according to coordinate preferences
 * String text = calculator.getDescriptionText(conceptNid);
 *
 * // With fallback default
 * String text = calculator.getDescriptionTextOrDefault(conceptNid, "Unknown Concept");
 *
 * // For multiple concepts efficiently
 * Map<Integer, String> texts = calculator.getDescriptionTexts(conceptNids);
 * }</pre>
 *
 * <p><b>Getting Specific Description Types</b></p>
 * <pre>{@code
 * // Get fully qualified name specifically
 * Optional<String> fqn = calculator.getFullyQualifiedName(conceptNid);
 *
 * // Get definition specifically
 * Optional<String> definition = calculator.getDefinition(conceptNid);
 *
 * // Get description of specific type
 * Latest<SemanticEntityVersion> desc = calculator.getDescription(
 *     conceptNid,
 *     TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE
 * );
 * }</pre>
 *
 * <p><b>Getting All Descriptions (Ranked)</b></p>
 * <pre>{@code
 * // Get all descriptions in preference order
 * List<SemanticEntityVersion> descriptions =
 *     calculator.getDescriptionsForComponent(conceptNid);
 *
 * // First is most preferred, process in order
 * for (SemanticEntityVersion desc : descriptions) {
 *     String text = desc.fieldValues().get(0).toString();
 *     int typeNid = desc.fieldValues().get(1);
 *     // ... process description
 * }
 * }</pre>
 *
 * <p><b>Performance and Caching</b></p>
 *
 * <p>LanguageCalculatorWithCache provides significant performance benefits:</p>
 * <ul>
 * <li><strong>Result Caching</strong> - Description text and semantic lookups are cached</li>
 * <li><strong>Coordinate-Based Keys</strong> - Cache keys derived from coordinate UUIDs</li>
 * <li><strong>Thread-Safe</strong> - Concurrent access supported via concurrent maps</li>
 * <li><strong>Lazy Computation</strong> - Results computed only when first requested</li>
 * </ul>
 *
 * <p>Cache performance characteristics:</p>
 * <ul>
 * <li>First access: Full description search and ranking algorithm</li>
 * <li>Subsequent accesses: O(1) map lookup</li>
 * <li>Memory overhead: Proportional to number of unique entities accessed</li>
 * </ul>
 *
 * <p><b>Integration with View Calculators</b></p>
 *
 * <p>Language calculators are typically accessed through view calculators, which combine all
 * coordinate types:</p>
 * <pre>{@code
 * ViewCalculator viewCalc = ViewCalculatorWithCache.getCalculator(viewCoord);
 *
 * // ViewCalculator delegates to LanguageCalculator
 * String text = viewCalc.getDescriptionText(conceptNid);
 * String fqn = viewCalc.getFullyQualifiedName(conceptNid).orElse("Unknown");
 * }</pre>
 *
 * <p><b>Thread Safety</b></p>
 *
 * <p>All calculator implementations are thread-safe and can be safely shared across threads.
 * The cached implementation uses concurrent data structures for safe concurrent access.</p>
 *
 * @see dev.ikm.tinkar.coordinate.language.calculator.LanguageCalculator
 * @see dev.ikm.tinkar.coordinate.language.calculator.LanguageCalculatorWithCache
 * @see dev.ikm.tinkar.coordinate.language.calculator.LanguageCalculatorDelegate
 * @see dev.ikm.tinkar.coordinate.language.LanguageCoordinate
 * @see dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator
 */
package dev.ikm.tinkar.coordinate.language.calculator;
