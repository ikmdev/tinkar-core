/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import dev.ikm.tinkar.common.service.DataServiceController;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.provider.websocket.client.WebsocketServiceController;

@SuppressWarnings("module") // 7 in HL7 is not a version reference
module dev.ikm.tinkar.provider.websocket.client {
    requires org.slf4j;
    requires io.activej.bytebuf;
    requires io.activej.eventloop;
    requires io.activej.http;
    requires io.activej.inject;
    requires io.activej.launcher;
    requires io.activej.promise;
    requires io.activej.service;
    requires org.eclipse.collections.api;
    requires java.base;
    requires dev.ikm.tinkar.common;
    requires dev.ikm.tinkar.component;
    requires java.net.http;
    requires dev.ikm.tinkar.entity;
    requires static transitive com.google.auto.service;

    provides DataServiceController
            with WebsocketServiceController;

    uses EntityService;
    opens dev.ikm.tinkar.provider.websocket.client
            to io.activej.inject;
}
