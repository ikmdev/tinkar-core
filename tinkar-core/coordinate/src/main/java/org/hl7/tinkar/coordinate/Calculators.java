package org.hl7.tinkar.coordinate;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.coordinate.language.LanguageCoordinateRecord;
import org.hl7.tinkar.coordinate.language.calculator.LanguageCalculatorWithCache;
import org.hl7.tinkar.coordinate.navigation.NavigationCoordinateRecord;
import org.hl7.tinkar.coordinate.navigation.calculator.NavigationCalculatorWithCache;
import org.hl7.tinkar.coordinate.stamp.StampCoordinate;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import org.hl7.tinkar.coordinate.view.calculator.ViewCalculatorWithCache;

public class Calculators {

    public static class View {
        public static ViewCalculatorWithCache Default() {
            return ViewCalculatorWithCache.getCalculator(
                    Coordinates.Stamp.DevelopmentLatest(),
                    Lists.immutable.of(Coordinates.Language.UsEnglishRegularName()),
                    Coordinates.Navigation.inferred().toNavigationCoordinateRecord(),
                    Coordinates.View.DefaultView());
        }
    }

    public static class Navigation {
        public static final NavigationCalculatorWithCache inferred(StampCoordinate stampCoordinate,
                                                                   ImmutableList<LanguageCoordinateRecord> languageCoordinateList) {
            return NavigationCalculatorWithCache.getCalculator(stampCoordinate.toStampCoordinateRecord(),
                    languageCoordinateList,
                    NavigationCoordinateRecord.makeInferred());
        }

        public static final NavigationCalculatorWithCache stated(StampCoordinate stampCoordinate,
                                                                 ImmutableList<LanguageCoordinateRecord> languageCoordinateList) {
            return NavigationCalculatorWithCache.getCalculator(stampCoordinate.toStampCoordinateRecord(),
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
         * A stampCoordinateRecord that completely ignores language - descriptions ranked by this coordinate will only be ranked by
         * description type and module preference.  This stampCoordinateRecord is primarily useful as a fallback coordinate.
         *
         *
         * @return the language stampCoordinateRecord
         *
         */
        public static LanguageCalculatorWithCache AnyLanguageRegularName(StampCoordinate stampCoordinate) {
            return LanguageCalculatorWithCache.getCalculator(stampCoordinate.toStampCoordinateRecord(),
                    Lists.immutable.of(Coordinates.Language.AnyLanguageRegularName()));
        }

        /**
         * A stampCoordinateRecord that completely ignores language - descriptions ranked by this coordinate will only be ranked by
         * description type and module preference.  This stampCoordinateRecord is primarily useful as a fallback coordinate.
         *
         * @return the language stampCoordinateRecord
         */
        public static LanguageCalculatorWithCache AnyLanguageFullyQualifiedName(StampCoordinate stampCoordinate) {
            return LanguageCalculatorWithCache.getCalculator(stampCoordinate.toStampCoordinateRecord(),
                    Lists.immutable.of(Coordinates.Language.AnyLanguageFullyQualifiedName()));
        }

        /**
         * A stampCoordinateRecord that completely ignores language - descriptions ranked by this coordinate will only be ranked by
         * description type and module preference.  This stampCoordinateRecord is primarily useful as a fallback coordinate.
         *
         * @return a stampCoordinateRecord that prefers definitions, of arbitrary language.
         * type
         */
        public static LanguageCalculatorWithCache AnyLanguageDefinition(StampCoordinate stampCoordinate) {
            return LanguageCalculatorWithCache.getCalculator(stampCoordinate.toStampCoordinateRecord(),
                    Lists.immutable.of(Coordinates.Language.AnyLanguageDefinition()));
        }

        /**
         * @return US English language stampCoordinateRecord, preferring FQNs, but allowing regular names, if no FQN is found.
         */
        public static LanguageCalculatorWithCache UsEnglishFullyQualifiedName(StampCoordinate stampCoordinate) {
            return LanguageCalculatorWithCache.getCalculator(stampCoordinate.toStampCoordinateRecord(),
                    Lists.immutable.of(Coordinates.Language.UsEnglishFullyQualifiedName()));
        }

        /**
         * @return US English language stampCoordinateRecord, preferring regular name, but allowing FQN names is no regular name is found.
         */
        public static LanguageCalculatorWithCache UsEnglishRegularName(StampCoordinate stampCoordinate) {
            return LanguageCalculatorWithCache.getCalculator(stampCoordinate.toStampCoordinateRecord(),
                    Lists.immutable.of(Coordinates.Language.UsEnglishRegularName()));
        }

        public static LanguageCalculatorWithCache GbEnglishFullyQualifiedName(StampCoordinate stampCoordinate) {
            return LanguageCalculatorWithCache.getCalculator(stampCoordinate.toStampCoordinateRecord(),
                    Lists.immutable.of(Coordinates.Language.GbEnglishFullyQualifiedName()));
        }

        public static LanguageCalculatorWithCache GbEnglishPreferredName(StampCoordinate stampCoordinate) {
            return LanguageCalculatorWithCache.getCalculator(stampCoordinate.toStampCoordinateRecord(),
                    Lists.immutable.of(Coordinates.Language.GbEnglishPreferredName()));
        }

        public static LanguageCalculatorWithCache SpanishFullyQualifiedName(StampCoordinate stampCoordinate) {
            return LanguageCalculatorWithCache.getCalculator(stampCoordinate.toStampCoordinateRecord(),
                    Lists.immutable.of(Coordinates.Language.SpanishFullyQualifiedName()));
        }

        public static LanguageCalculatorWithCache SpanishPreferredName(StampCoordinate stampCoordinate) {
            return LanguageCalculatorWithCache.getCalculator(stampCoordinate.toStampCoordinateRecord(),
                    Lists.immutable.of(Coordinates.Language.SpanishPreferredName()));
        }
    }

}
