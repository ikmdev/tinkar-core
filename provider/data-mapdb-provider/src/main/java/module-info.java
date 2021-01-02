/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import org.hl7.tinkar.provider.chronology.mapdb.MapDbProvider;
@SuppressWarnings("module") // 7 in HL7 is not a version reference
module org.hl7.tinkar.provider.chronology.mapdb {
    requires java.base;
    requires org.eclipse.collections.api;
    requires org.hl7.tinkar.component;
    requires org.hl7.tinkar.common;
    requires com.google.auto.service;
    requires org.hl7.tinkar.dto;
    requires mapdb;
    requires org.apache.logging.log4j;
    requires org.hl7.tinkar.entity;
    requires org.eclipse.collections.impl;
    provides org.hl7.tinkar.service.PrimitiveDataService
            with MapDbProvider;
}
