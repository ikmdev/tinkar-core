/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import org.hl7.tinkar.component.ChronologyService;

@SuppressWarnings("module") // 7 in HL7 is not a version reference
module org.hl7.tinkar.launcher {
    requires io.activej.inject;
    requires io.activej.launcher;
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j.slf4j;
    requires org.apache.logging.log4j;
    requires org.hl7.tinkar.component;
    requires org.hl7.tinkar.dto;
    requires org.hl7.tinkar.common;
    uses ChronologyService;
}
