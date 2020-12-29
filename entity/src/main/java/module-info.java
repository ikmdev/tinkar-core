/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import org.hl7.tinkar.service.CachingService;
import org.hl7.tinkar.entity.EntityService;

@SuppressWarnings("module") // 7 in HL7 is not a version reference
module org.hl7.tinkar.entity {
    requires org.hl7.tinkar.component;
    requires org.eclipse.collections.api;
    requires org.hl7.tinkar.dto;
    requires io.activej.bytebuf;
    requires org.apache.logging.log4j;
    requires org.hl7.tinkar.common;
    exports org.hl7.tinkar.entity;
    uses CachingService;
    uses EntityService;
}
