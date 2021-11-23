import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.coordinate.PathService;
import org.hl7.tinkar.coordinate.edit.EditCoordinateImmutable;
import org.hl7.tinkar.coordinate.language.calculator.LanguageCalculatorWithCache;
import org.hl7.tinkar.coordinate.logic.calculator.LogicCalculatorWithCache;
import org.hl7.tinkar.coordinate.navigation.calculator.NavigationCalculatorWithCache;
import org.hl7.tinkar.coordinate.stamp.StampPathImmutable;
import org.hl7.tinkar.coordinate.stamp.calculator.PathProvider;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import org.hl7.tinkar.coordinate.view.calculator.ViewCalculatorWithCache;


/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
module org.hl7.tinkar.coordinate {
    exports org.hl7.tinkar.coordinate.edit;
    exports org.hl7.tinkar.coordinate.language.calculator;
    exports org.hl7.tinkar.coordinate.language;
    exports org.hl7.tinkar.coordinate.logic.calculator;
    exports org.hl7.tinkar.coordinate.logic;
    exports org.hl7.tinkar.coordinate.navigation.calculator;
    exports org.hl7.tinkar.coordinate.navigation;
    exports org.hl7.tinkar.coordinate.stamp.calculator;
    exports org.hl7.tinkar.coordinate.stamp;
    exports org.hl7.tinkar.coordinate.view.calculator;
    exports org.hl7.tinkar.coordinate.view;
    exports org.hl7.tinkar.coordinate;

    requires org.hl7.tinkar.collection;
    requires transitive org.hl7.tinkar.terms;
    requires static org.hl7.tinkar.record.builder;
    requires static java.compiler;
    requires org.hl7.tinkar.entity;
    requires org.slf4j;

    provides CachingService with
            LanguageCalculatorWithCache.CacheProvider,
            LogicCalculatorWithCache.CacheProvider,
            NavigationCalculatorWithCache.CacheProvider,
            StampCalculatorWithCache.CacheProvider,
            ViewCalculatorWithCache.CacheProvider,
            EditCoordinateImmutable.CacheProvider,
            StampPathImmutable.CachingProvider;

    provides PathService with PathProvider;

    uses CachingService;
    uses PathService;
}
