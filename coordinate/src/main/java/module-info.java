import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.coordinate.PathService;
import dev.ikm.tinkar.coordinate.edit.EditCoordinateRecord;
import dev.ikm.tinkar.coordinate.language.calculator.LanguageCalculatorWithCache;
import dev.ikm.tinkar.coordinate.logic.calculator.LogicCalculatorWithCache;
import dev.ikm.tinkar.coordinate.navigation.calculator.NavigationCalculatorWithCache;
import dev.ikm.tinkar.coordinate.stamp.StampPathImmutable;
import dev.ikm.tinkar.coordinate.stamp.calculator.PathProvider;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculatorWithCache;


/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
module dev.ikm.tinkar.coordinate {
    exports dev.ikm.tinkar.coordinate.edit;
    exports dev.ikm.tinkar.coordinate.language.calculator;
    exports dev.ikm.tinkar.coordinate.language;
    exports dev.ikm.tinkar.coordinate.logic.calculator;
    exports dev.ikm.tinkar.coordinate.logic;
    exports dev.ikm.tinkar.coordinate.navigation.calculator;
    exports dev.ikm.tinkar.coordinate.navigation;
    exports dev.ikm.tinkar.coordinate.stamp.calculator;
    exports dev.ikm.tinkar.coordinate.stamp;
    exports dev.ikm.tinkar.coordinate.view.calculator;
    exports dev.ikm.tinkar.coordinate.view;
    exports dev.ikm.tinkar.coordinate;

    requires dev.ikm.tinkar.collection;
    requires dev.ikm.tinkar.common;
    requires transitive dev.ikm.tinkar.terms;
    requires static io.soabase.recordbuilder.core;
    requires static java.compiler;
    requires dev.ikm.tinkar.entity;
    requires org.slf4j;
    requires org.eclipse.collections.api;
    requires org.eclipse.collections;

    provides CachingService with
            LanguageCalculatorWithCache.CacheProvider,
            LogicCalculatorWithCache.CacheProvider,
            NavigationCalculatorWithCache.CacheProvider,
            StampCalculatorWithCache.CacheProvider,
            ViewCalculatorWithCache.CacheProvider,
            EditCoordinateRecord.CacheProvider,
            StampPathImmutable.CachingProvider;

    provides PathService with PathProvider;

    uses CachingService;
    uses PathService;
}
