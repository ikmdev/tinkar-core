/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import org.hl7.tinkar.entity.EntityService;
import org.hl7.tinkar.common.provider.entity.EntityProvider;
import org.hl7.tinkar.common.service.PrimitiveDataService;

@SuppressWarnings("module") // 7 in HL7 is not a version reference
module org.hl7.tinkar.provider.entity {
    requires java.base;
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j.slf4j;
    requires org.apache.logging.log4j;
    requires org.eclipse.collections.api;
    requires org.eclipse.collections.impl;
    requires org.hl7.tinkar.component;
    requires org.hl7.tinkar.common;
    requires org.hl7.tinkar.entity;
    requires io.activej.bytebuf;
    requires com.google.auto.service;
    requires org.hl7.tinkar.dto;
    provides EntityService
            with EntityProvider;
    uses PrimitiveDataService;
}

