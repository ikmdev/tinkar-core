/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import org.hl7.tinkar.common.provider.chronology.ephemeral.ProviderEphemeralFactory;
import org.hl7.tinkar.common.service.PrimitiveDataService;

@SuppressWarnings("module") // 7 in HL7 is not a version reference
module org.hl7.tinkar.provider.chronology.ephemeral {
    requires java.base;
    requires org.eclipse.collections.api;
    requires org.hl7.tinkar.component;
    requires org.hl7.tinkar.common;
    requires org.hl7.tinkar.test;
    requires com.google.auto.service;
    requires org.apache.logging.log4j;
    requires org.eclipse.collections.impl;
    requires lombok;
    provides PrimitiveDataService
            with ProviderEphemeralFactory;
}
