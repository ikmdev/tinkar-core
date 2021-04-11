import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.coordinate.stamp.StampCalculator;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
module org.hl7.tinkar.coordinate {
    exports org.hl7.tinkar.coordinate;
    exports org.hl7.tinkar.coordinate.edit;
    exports org.hl7.tinkar.coordinate.language;
    exports org.hl7.tinkar.coordinate.logic;
    exports org.hl7.tinkar.coordinate.navigation;
    exports org.hl7.tinkar.coordinate.stamp;
    exports org.hl7.tinkar.coordinate.view;

    requires org.hl7.tinkar.collection;
    requires transitive org.hl7.tinkar.terms;
    requires static org.hl7.tinkar.record.builder;
    requires static java.compiler;
    requires org.hl7.tinkar.entity;

    provides CachingService with StampCalculator.CacheProvider;

    uses CachingService;
}
