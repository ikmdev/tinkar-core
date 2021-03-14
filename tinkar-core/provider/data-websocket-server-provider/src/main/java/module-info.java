/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import org.hl7.tinkar.common.service.PrimitiveDataService;

@SuppressWarnings("module") // 7 in HL7 is not a version reference
module org.hl7.tinkar.provider.websocket.server {
    requires io.activej.http;
    requires io.activej.inject;
    requires io.activej.launchers.http;
    requires io.activej.promise;
    requires java.base;
    requires org.hl7.tinkar.common;
    requires org.hl7.tinkar.component;
    requires org.hl7.tinkar.entity;
    uses PrimitiveDataService;
    opens org.hl7.tinkar.provider.websocket.server
            to io.activej.inject;
}
