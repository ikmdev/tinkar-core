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
package dev.ikm.tinkar.coordinate;

import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import dev.ikm.tinkar.common.id.LongIds;
import dev.ikm.tinkar.coordinate.edit.EditCoordinateRecord;
import dev.ikm.tinkar.coordinate.language.LanguageCoordinateRecord;
import dev.ikm.tinkar.coordinate.logic.LogicCoordinateRecord;
import dev.ikm.tinkar.coordinate.navigation.NavigationCoordinate;
import dev.ikm.tinkar.coordinate.navigation.NavigationCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StampCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StampPositionRecord;
import dev.ikm.tinkar.coordinate.stamp.StateSet;
import dev.ikm.tinkar.coordinate.view.ViewCoordinateRecord;
import org.eclipse.collections.api.factory.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Coordinates {

    private static final Logger LOG = LoggerFactory.getLogger(Coordinates.class);

    public static class Edit {
        public static EditCoordinateRecord Default() {
            return EditCoordinateRecord.make(
                    KernelConcept.USER.nid(), KernelConcept.SOLOR_OVERLAY_MODULE.nid(),
                    KernelConcept.SOLOR_OVERLAY_MODULE.nid(), KernelConcept.DEVELOPMENT_PATH.nid(), KernelConcept.DEVELOPMENT_PATH.nid()
            );
        }
    }

    public static class Logic {
        /**
         * The EL++ logic coordinate, rooted at the root vertex: the concept every other is a kind of,
         * a primitive with no parents (the IKE starter set's "IKE root concept").
         */
        public static LogicCoordinateRecord ElPlusPlus() {
            return LogicCoordinateRecord.make(KernelConcept.SNOROCKET_CLASSIFIER,
                    KernelConcept.EL_PLUS_PLUS_PROFILE,
                    KernelPattern.EL_PLUS_PLUS_INFERRED_AXIOMS_PATTERN,
                    KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN,
                    KernelPattern.SOLOR_CONCEPT_ASSEMBLAGE,
                    KernelPattern.STATED_NAVIGATION_PATTERN,
                    KernelPattern.INFERRED_NAVIGATION_PATTERN,
                    KernelConcept.ROOT_VERTEX);
        }
    }

    public static class Language {
        /**
         * A coordinate that completely ignores language - descriptions ranked by this coordinate will only be ranked by
         * description type and module preference.  This coordinate is primarily useful as a fallback coordinate if no
         *
         * @return the language coordinate
         */
        public static LanguageCoordinateRecord AnyLanguageRegularName() {
            return LanguageCoordinateRecord.make(
                    KernelConcept.LANGUAGE,
                    LongIds.list.of(KernelPattern.DESCRIPTION_PATTERN.nid()),
                    LongIds.list.of(KernelConcept.REGULAR_NAME_DESCRIPTION_TYPE.nid()),
                    LongIds.list.empty(),
                    LongIds.list.empty()
            );
        }

        /**
         * A coordinate that completely ignores language - descriptions ranked by this coordinate will only be ranked by
         * description type and module preference.  This coordinate is primarily useful as a fallback coordinate
         *
         * @return the language coordinate
         */
        public static LanguageCoordinateRecord AnyLanguageFullyQualifiedName() {
            return LanguageCoordinateRecord.make(
                    KernelConcept.LANGUAGE,
                    LongIds.list.of(KernelPattern.DESCRIPTION_PATTERN.nid()),
                    LongIds.list.of(KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid()),
                    LongIds.list.empty(),
                    LongIds.list.empty()
            );
        }

        /**
         * A coordinate that completely ignores language - descriptions ranked by this coordinate will only be ranked by
         * description type and module preference.  This coordinate is primarily useful as a fallback coordinate.
         *
         * @return a coordinate that prefers definitions, of arbitrary language.
         * type
         */
        public static LanguageCoordinateRecord AnyLanguageDefinition() {
            return LanguageCoordinateRecord.make(
                    KernelConcept.LANGUAGE,
                    LongIds.list.of(KernelPattern.DESCRIPTION_PATTERN.nid()),
                    LongIds.list.of(KernelConcept.DEFINITION_DESCRIPTION_TYPE.nid()),
                    LongIds.list.empty(),
                    LongIds.list.empty()
            );
        }

        /**
         * @return US English language coordinate, preferring FQNs, but allowing regular names, if no FQN is found.
         */
        public static LanguageCoordinateRecord UsEnglishFullyQualifiedName() {
            return LanguageCoordinateRecord.make(
                    KernelConcept.ENGLISH_LANGUAGE.nid(),
                    LongIds.list.of(KernelPattern.DESCRIPTION_PATTERN.nid()),
                    LongIds.list.of(KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid(),
                            KernelConcept.REGULAR_NAME_DESCRIPTION_TYPE.nid()),
                    LongIds.list.of(KernelPattern.US_DIALECT_PATTERN.nid(), KernelPattern.GB_DIALECT_PATTERN.nid()),
                    LongIds.list.of(KernelConcept.SOLOR_OVERLAY_MODULE.nid(), KernelConcept.SOLOR_MODULE.nid())
            );
        }

        /**
         * @return US English language coordinate, preferring regular name, but allowing FQN names is no regular name is found
         */
        public static LanguageCoordinateRecord UsEnglishRegularName() {
            return LanguageCoordinateRecord.make(
                    KernelConcept.ENGLISH_LANGUAGE.nid(),
                    LongIds.list.of(KernelPattern.DESCRIPTION_PATTERN.nid()),
                    LongIds.list.of(KernelConcept.REGULAR_NAME_DESCRIPTION_TYPE.nid(),
                            KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid()),
                    LongIds.list.of(KernelPattern.US_DIALECT_PATTERN.nid(), KernelPattern.GB_DIALECT_PATTERN.nid()),
                    LongIds.list.of(KernelConcept.SOLOR_OVERLAY_MODULE.nid(), KernelConcept.SOLOR_MODULE.nid())
            );
        }

        public static LanguageCoordinateRecord GbEnglishFullyQualifiedName() {
            return LanguageCoordinateRecord.make(
                    KernelConcept.ENGLISH_LANGUAGE.nid(),
                    LongIds.list.of(KernelPattern.DESCRIPTION_PATTERN.nid()),
                    LongIds.list.of(KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid(),
                            KernelConcept.REGULAR_NAME_DESCRIPTION_TYPE.nid()),
                    LongIds.list.of(KernelPattern.GB_DIALECT_PATTERN.nid(),
                            KernelPattern.US_DIALECT_PATTERN.nid()),
                    LongIds.list.of(KernelConcept.SOLOR_OVERLAY_MODULE.nid(), KernelConcept.SOLOR_MODULE.nid())
            );
        }

        public static LanguageCoordinateRecord GbEnglishPreferredName() {
            return LanguageCoordinateRecord.make(
                    KernelConcept.ENGLISH_LANGUAGE.nid(),
                    LongIds.list.of(KernelPattern.DESCRIPTION_PATTERN.nid()),
                    LongIds.list.of(KernelConcept.REGULAR_NAME_DESCRIPTION_TYPE.nid(),
                            KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid()),
                    LongIds.list.of(KernelPattern.GB_DIALECT_PATTERN.nid(),
                            KernelPattern.US_DIALECT_PATTERN.nid()),
                    LongIds.list.of(KernelConcept.SOLOR_OVERLAY_MODULE.nid(), KernelConcept.SOLOR_MODULE.nid())
            );
        }

        public static LanguageCoordinateRecord SpanishFullyQualifiedName() {
            return LanguageCoordinateRecord.make(
                    KernelConcept.SPANISH_LANGUAGE.nid(),
                    LongIds.list.of(KernelPattern.DESCRIPTION_PATTERN.nid()),
                    LongIds.list.of(KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid(),
                            KernelConcept.REGULAR_NAME_DESCRIPTION_TYPE.nid()),
                    LongIds.list.empty(),
                    LongIds.list.of(KernelConcept.SOLOR_OVERLAY_MODULE.nid(), KernelConcept.SOLOR_MODULE.nid())
            );
        }

        public static LanguageCoordinateRecord SpanishPreferredName() {
            return LanguageCoordinateRecord.make(
                    KernelConcept.SPANISH_LANGUAGE.nid(),
                    LongIds.list.of(KernelPattern.DESCRIPTION_PATTERN.nid()),
                    LongIds.list.of(KernelConcept.REGULAR_NAME_DESCRIPTION_TYPE.nid(),
                            KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid()),
                    LongIds.list.empty(),
                    LongIds.list.of(KernelConcept.SOLOR_OVERLAY_MODULE.nid(), KernelConcept.SOLOR_MODULE.nid())
            );
        }
    }

    public static class Stamp {
        public static StampCoordinateRecord DevelopmentLatestInactiveOnly() {
            return StampCoordinateRecord.make(StateSet.INACTIVE,
                    Position.LatestOnDevelopment(),
                    LongIds.set.empty());
        }


        public static StampCoordinateRecord DevelopmentLatest() {
            return StampCoordinateRecord.make(StateSet.ACTIVE_AND_INACTIVE,
                    Position.LatestOnDevelopment(),
                    LongIds.set.empty());
        }

        public static StampCoordinateRecord DevelopmentLatestActiveOnly() {
            return StampCoordinateRecord.make(StateSet.ACTIVE,
                    Position.LatestOnDevelopment(),
                    LongIds.set.empty());
        }

        public static StampCoordinateRecord MasterLatest() {
            return StampCoordinateRecord.make(StateSet.ACTIVE_AND_INACTIVE,
                    Position.LatestOnMaster(),
                    LongIds.set.empty());
        }

        public static StampCoordinateRecord MasterLatestActiveOnly() {
            return StampCoordinateRecord.make(StateSet.ACTIVE,
                    Position.LatestOnMaster(),
                    LongIds.set.empty());
        }
    }

    public static class Position {
        public static StampPositionRecord LatestOnDevelopment() {
            return StampPositionRecord.make(Long.MAX_VALUE, KernelConcept.DEVELOPMENT_PATH);
        }

        public static StampPositionRecord LatestOnMaster() {
            return StampPositionRecord.make(Long.MAX_VALUE, KernelConcept.MASTER_PATH);
        }
    }

    public static class Navigation {
        public static final NavigationCoordinate inferred() {
            return NavigationCoordinateRecord.makeInferred();
        }

        public static final NavigationCoordinate stated() {
            return NavigationCoordinateRecord.makeStated();
        }
    }

    public static class View {
        public static final ViewCoordinateRecord DefaultView() {

            return ViewCoordinateRecord.make(Stamp.DevelopmentLatest(),
                    Language.UsEnglishRegularName(),
                    Logic.ElPlusPlus(),
                    Navigation.inferred(),
                    Edit.Default());
        }
    }
}
