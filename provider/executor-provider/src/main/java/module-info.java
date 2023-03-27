/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import dev.ikm.tinkar.common.alert.AlertReportingService;
import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.common.service.ExecutorController;
import dev.ikm.tinkar.provider.executor.AlertLogSubscriber;
import dev.ikm.tinkar.provider.executor.ExecutorProviderController;

@SuppressWarnings("module")
        // 7 in HL7 is not a version reference
module dev.ikm.tinkar.provider.executor {
    
    provides AlertReportingService with AlertLogSubscriber;
    provides CachingService with ExecutorProviderController.CacheProvider;
    provides ExecutorController with ExecutorProviderController;

    requires java.base;
    requires dev.ikm.tinkar.common;
    //requires static dev.ikm.tinkar.autoservice;
}

