/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import dev.ikm.tinkar.common.service.PrimitiveDataService;

@SuppressWarnings("module") // 7 in HL7 is not a version reference
module dev.ikm.tinkar.provider.websocket.server {
    requires org.slf4j;
    requires io.activej.bytebuf;
    requires io.activej.http;
    requires io.activej.inject;
    requires io.activej.launchers.http;
    requires io.activej.promise;
    requires java.base;
    requires dev.ikm.tinkar.common;
    requires dev.ikm.tinkar.component;
    requires dev.ikm.tinkar.entity;
    uses PrimitiveDataService;
    opens dev.ikm.tinkar.provider.websocket.server
            to io.activej.inject;
}
