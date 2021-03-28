/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.entity.EntityService;

@SuppressWarnings("module") // 7 in HL7 is not a version reference
module org.hl7.tinkar.entity {
    requires transitive org.hl7.tinkar.component;
    requires org.hl7.tinkar.dto;
    requires java.logging;
    requires transitive org.hl7.tinkar.common;
    requires transitive org.hl7.tinkar.caffeine;
    requires static org.hl7.tinkar.autoservice;
    requires org.hl7.tinkar.terms;

    exports org.hl7.tinkar.entity;
    exports org.hl7.tinkar.entity.calculator;
    exports org.hl7.tinkar.entity.graph;
    exports org.hl7.tinkar.entity.util;

    uses CachingService;
    uses EntityService;
}
