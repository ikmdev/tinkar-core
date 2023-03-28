/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import dev.ikm.tinkar.common.service.DataServiceController;
import dev.ikm.tinkar.common.service.LoadDataFromFileController;
import dev.ikm.tinkar.provider.ephemeral.ProviderEphemeralNewController;

@SuppressWarnings("module")
        // 7 in HL7 is not a version reference
module dev.ikm.tinkar.provider.ephemeral {
    requires java.base;
    requires dev.ikm.tinkar.collection;
    requires org.eclipse.collections;
    requires org.eclipse.collections.api;
    requires dev.ikm.tinkar.common;
    requires dev.ikm.tinkar.component;
    requires dev.ikm.tinkar.provider.search;
    requires org.slf4j;
    requires java.logging;
    requires dev.ikm.tinkar.entity;
    provides DataServiceController
            with ProviderEphemeralNewController;

    uses LoadDataFromFileController;

}
