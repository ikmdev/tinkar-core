package org.hl7.tinkar.coordinate;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.coordinate.language.LanguageCoordinateRecord;
import org.hl7.tinkar.coordinate.language.calculator.LanguageCalculatorWithCache;
import org.hl7.tinkar.coordinate.navigation.NavigationCoordinateRecord;
import org.hl7.tinkar.coordinate.navigation.calculator.NavigationCalculatorWithCache;
import org.hl7.tinkar.coordinate.stamp.StampFilter;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import org.hl7.tinkar.coordinate.view.calculator.ViewCalculatorWithCache;

public class Calculators {

    public static class View {
        public static ViewCalculatorWithCache Default() {
            return ViewCalculatorWithCache.getCalculator(
                    Coordinates.Stamp.DevelopmentLatest(),
                    Lists.immutable.of(Coordinates.Language.UsEnglishRegularName()),
                    Coordinates.Navigation.inferred().toNavigationCoordinateImmutable(),
                    Coordinates.View.DefaultView());
        }
    }

    public static class Navigation {
        public static final NavigationCalculatorWithCache inferred(StampFilter stampFilter,
                                                                   ImmutableList<LanguageCoordinateRecord> languageCoordinateList) {
            return NavigationCalculatorWithCache.getCalculator(stampFilter.toStampFilterImmutable(),
                    languageCoordinateList,
                    NavigationCoordinateRecord.makeInferred());
        }

        public static final NavigationCalculatorWithCache stated(StampFilter stampFilter,
                                                                 ImmutableList<LanguageCoordinateRecord> languageCoordinateList) {
            return NavigationCalculatorWithCache.getCalculator(stampFilter.toStampFilterImmutable(),
                    languageCoordinateList,
                    NavigationCoordinateRecord.makeStated());
        }
    }

    public static class Stamp {
        public static StampCalculatorWithCache DevelopmentLatest() {
            return StampCalculatorWithCache.getCalculator(Coordinates.Stamp.DevelopmentLatest());
        }

        public static StampCalculatorWithCache DevelopmentLatestActiveOnly() {
            return StampCalculatorWithCache.getCalculator(Coordinates.Stamp.DevelopmentLatestActiveOnly());
        }

        public static StampCalculatorWithCache MasterLatest() {
            return StampCalculatorWithCache.getCalculator(Coordinates.Stamp.MasterLatest());
        }

        public static StampCalculatorWithCache MasterLatestActiveOnly() {
            return StampCalculatorWithCache.getCalculator(Coordinates.Stamp.MasterLatestActiveOnly());
        }
    }


    public static class Language {
        /**
         * A stampFilter that completely ignores language - descriptions ranked by this coordinate will only be ranked by
         * description type and module preference.  This stampFilter is primarily useful as a fallback coordinate.
         *
         *
         * @return the language stampFilter
         *
         */
        public static LanguageCalculatorWithCache AnyLanguageRegularName(StampFilter stampFilter) {
            return LanguageCalculatorWithCache.getCalculator(stampFilter.toStampFilterImmutable(),
                    Lists.immutable.of(Coordinates.Language.AnyLanguageRegularName()));
        }

        /**
         * A stampFilter that completely ignores language - descriptions ranked by this coordinate will only be ranked by
         * description type and module preference.  This stampFilter is primarily useful as a fallback coordinate.
         *
         * @return the language stampFilter
         */
        public static LanguageCalculatorWithCache AnyLanguageFullyQualifiedName(StampFilter stampFilter) {
            return LanguageCalculatorWithCache.getCalculator(stampFilter.toStampFilterImmutable(),
                    Lists.immutable.of(Coordinates.Language.AnyLanguageFullyQualifiedName()));
        }

        /**
         * A stampFilter that completely ignores language - descriptions ranked by this coordinate will only be ranked by
         * description type and module preference.  This stampFilter is primarily useful as a fallback coordinate.
         *
         * @return a stampFilter that prefers definitions, of arbitrary language.
         * type
         */
        public static LanguageCalculatorWithCache AnyLanguageDefinition(StampFilter stampFilter) {
            return LanguageCalculatorWithCache.getCalculator(stampFilter.toStampFilterImmutable(),
                    Lists.immutable.of(Coordinates.Language.AnyLanguageDefinition()));
        }

        /**
         * @return US English language stampFilter, preferring FQNs, but allowing regular names, if no FQN is found.
         */
        public static LanguageCalculatorWithCache UsEnglishFullyQualifiedName(StampFilter stampFilter) {
            return LanguageCalculatorWithCache.getCalculator(stampFilter.toStampFilterImmutable(),
                    Lists.immutable.of(Coordinates.Language.UsEnglishFullyQualifiedName()));
        }

        /**
         * @return US English language stampFilter, preferring regular name, but allowing FQN names is no regular name is found.
         */
        public static LanguageCalculatorWithCache UsEnglishRegularName(StampFilter stampFilter) {
            return LanguageCalculatorWithCache.getCalculator(stampFilter.toStampFilterImmutable(),
                    Lists.immutable.of(Coordinates.Language.UsEnglishRegularName()));
        }

        public static LanguageCalculatorWithCache GbEnglishFullyQualifiedName(StampFilter stampFilter) {
            return LanguageCalculatorWithCache.getCalculator(stampFilter.toStampFilterImmutable(),
                    Lists.immutable.of(Coordinates.Language.GbEnglishFullyQualifiedName()));
        }

        public static LanguageCalculatorWithCache GbEnglishPreferredName(StampFilter stampFilter) {
            return LanguageCalculatorWithCache.getCalculator(stampFilter.toStampFilterImmutable(),
                    Lists.immutable.of(Coordinates.Language.GbEnglishPreferredName()));
        }

        public static LanguageCalculatorWithCache SpanishFullyQualifiedName(StampFilter stampFilter) {
            return LanguageCalculatorWithCache.getCalculator(stampFilter.toStampFilterImmutable(),
                    Lists.immutable.of(Coordinates.Language.SpanishFullyQualifiedName()));
        }

        public static LanguageCalculatorWithCache SpanishPreferredName(StampFilter stampFilter) {
            return LanguageCalculatorWithCache.getCalculator(stampFilter.toStampFilterImmutable(),
                    Lists.immutable.of(Coordinates.Language.SpanishPreferredName()));
        }
    }

}
