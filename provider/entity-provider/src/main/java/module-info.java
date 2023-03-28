/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import dev.ikm.tinkar.provider.entity.*;
import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.common.service.DefaultDescriptionForNidService;
import dev.ikm.tinkar.common.service.PrimitiveDataService;
import dev.ikm.tinkar.common.service.PublicIdService;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.StampService;

@SuppressWarnings("module")
        // 7 in HL7 is not a version reference
module dev.ikm.tinkar.provider.entity {
    requires org.slf4j;
    requires transitive dev.ikm.tinkar.component;
    requires transitive dev.ikm.tinkar.common;
    requires transitive dev.ikm.tinkar.entity;
    requires transitive dev.ikm.tinkar.dto;
    requires transitive dev.ikm.tinkar.terms;
    requires org.eclipse.collections.api;
    requires org.eclipse.collections;

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

