/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import org.hl7.tinkar.common.service.PrimitiveDataService;

@SuppressWarnings("module") // 7 in HL7 is not a version reference
module org.hl7.tinkar.provider.openhft {
    requires chronicle.bytes;
    requires chronicle.core;
    requires chronicle.map;
    requires chronicle.values;

    requires java.base;
    requires jdk.unsupported;

    requires org.apache.logging.log4j;

    requires org.hl7.tinkar.common;
    requires org.hl7.tinkar.component;
    requires org.hl7.tinkar.entity;
    requires org.hl7.tinkar.lombok.dto;
    provides PrimitiveDataService
            with OpenHFTProvider;
}
