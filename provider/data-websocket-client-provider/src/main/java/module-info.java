/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

@SuppressWarnings("module") // 7 in HL7 is not a version reference
module org.hl7.tinkar.provider.websocket.client {
    requires com.google.auto.service;
    requires io.activej.bytebuf;
    requires io.activej.eventloop;
    requires io.activej.http;
    requires io.activej.inject;
    requires io.activej.launcher;
    requires io.activej.promise;
    requires io.activej.service;
    requires java.base;
    requires org.apache.logging.log4j;
    requires org.eclipse.collections.api;
    requires org.eclipse.collections.impl;
    requires org.hl7.tinkar.common;
    requires org.hl7.tinkar.component;
    requires org.hl7.tinkar.test;
    requires java.net.http;
    requires org.hl7.tinkar.entity;
    opens org.hl7.tinkar.provider.websocket.client
            to io.activej.inject;
}
