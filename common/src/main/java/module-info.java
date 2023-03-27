/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import dev.ikm.tinkar.common.alert.AlertReportingService;
import dev.ikm.tinkar.common.service.*;

@SuppressWarnings("module")
        // 7 in HL7 is not a version reference
module dev.ikm.tinkar.common {
    requires transitive java.prefs;
    requires transitive dev.ikm.tinkar.activej;
    requires transitive dev.ikm.tinkar.eclipse.collections;
    requires transitive dev.ikm.tinkar.roaring;
    requires transitive org.slf4j;
    requires transitive static dev.ikm.tinkar.autoservice;


    exports dev.ikm.tinkar.common.alert;
    exports dev.ikm.tinkar.common.binary;
    exports dev.ikm.tinkar.common.id;
    exports dev.ikm.tinkar.common.service;
    exports dev.ikm.tinkar.common.util.functional;
    exports dev.ikm.tinkar.common.util.ints2long;
    exports dev.ikm.tinkar.common.util.io;
    exports dev.ikm.tinkar.common.util.text;
    exports dev.ikm.tinkar.common.util.thread;
    exports dev.ikm.tinkar.common.util.time;
    exports dev.ikm.tinkar.common.util.uuid;
    exports dev.ikm.tinkar.common.validation;
    exports dev.ikm.tinkar.common.sets;
    exports dev.ikm.tinkar.common.flow;
    exports dev.ikm.tinkar.common.id.impl;
    exports dev.ikm.tinkar.common.util.broadcast;
    exports dev.ikm.tinkar.common.util;

    provides CachingService with
            TinkExecutor.CacheProvider,
            ServiceProperties.CacheProvider,
            PrimitiveData.CacheProvider,
            PrimitiveDataService.CacheProvider;

    uses AlertReportingService;
    uses CachingService;
    uses DataServiceController;
    uses DefaultDescriptionForNidService;
    uses ExecutorController;
    uses PublicIdService;
}
