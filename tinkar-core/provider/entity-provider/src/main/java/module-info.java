/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.common.service.DefaultDescriptionForNidService;
import org.hl7.tinkar.common.service.PrimitiveDataService;
import org.hl7.tinkar.common.service.PublicIdService;
import org.hl7.tinkar.entity.EntityService;
import org.hl7.tinkar.entity.StampService;
import org.hl7.tinkar.provider.entity.*;

@SuppressWarnings("module")
        // 7 in HL7 is not a version reference
module org.hl7.tinkar.provider.entity {
    requires java.base;
    requires transitive org.hl7.tinkar.component;
    requires transitive org.hl7.tinkar.common;
    requires transitive org.hl7.tinkar.entity;
    requires transitive org.hl7.tinkar.dto;
    requires static org.hl7.tinkar.autoservice;
    requires transitive org.hl7.tinkar.terms;
    requires org.hl7.tinkar.mutiny;

    provides EntityService
            with EntityServiceFactory;
    provides PublicIdService
            with PublicIdServiceFactory;
    provides DefaultDescriptionForNidService
            with DefaultDescriptionForNidServiceFactory;
    provides StampService
            with StampProvider;
    provides CachingService
            with EntityProvider.CacheProvider;

    uses PrimitiveDataService;
}

