/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import org.hl7.tinkar.common.service.DataServiceController;
import org.hl7.tinkar.common.service.LoadDataFromFileController;
import org.hl7.tinkar.provider.ephemeral.ProviderEphemeralNewController;

@SuppressWarnings("module")
        // 7 in HL7 is not a version reference
module org.hl7.tinkar.provider.ephemeral {
    requires java.base;
    requires org.hl7.tinkar.collection;
    requires org.hl7.tinkar.common;
    requires org.hl7.tinkar.component;
    requires org.hl7.tinkar.lucene;
    requires org.hl7.tinkar.provider.search;
    requires org.slf4j;
    provides DataServiceController
            with ProviderEphemeralNewController;

    uses LoadDataFromFileController;

}
