/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import org.hl7.tinkar.common.service.DefaultDescriptionForNidService;
import org.hl7.tinkar.common.service.PublicIdService;
import org.hl7.tinkar.entity.EntityService;
import org.hl7.tinkar.entity.StampService;
import org.hl7.tinkar.provider.entity.EntityProvider;
import org.hl7.tinkar.common.service.PrimitiveDataService;
import org.hl7.tinkar.provider.entity.StampProvider;

@SuppressWarnings("module") // 7 in HL7 is not a version reference
module org.hl7.tinkar.provider.entity {
    requires java.base;
    requires org.hl7.tinkar.component;
    requires org.hl7.tinkar.common;
    requires org.hl7.tinkar.entity;
    requires org.hl7.tinkar.dto;
    requires static org.hl7.tinkar.autoservice;
    requires org.hl7.tinkar.terms;
    requires io.smallrye.mutiny;
    requires org.reactivestreams;

    provides EntityService
            with EntityProvider;
    provides PublicIdService
            with EntityProvider;
    provides DefaultDescriptionForNidService
            with EntityProvider;
    provides StampService
            with StampProvider;

    uses PrimitiveDataService;
}

