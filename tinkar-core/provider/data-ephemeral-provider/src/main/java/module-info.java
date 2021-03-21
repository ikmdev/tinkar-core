/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import org.hl7.tinkar.common.service.DataServiceController;
import org.hl7.tinkar.provider.ephemeral.ProviderEphemeralController;

@SuppressWarnings("module") // 7 in HL7 is not a version reference
module org.hl7.tinkar.provider.ephemeral {
    requires java.base;
    requires org.hl7.tinkar.collection;
    requires org.hl7.tinkar.component;
    requires org.hl7.tinkar.common;
    requires org.hl7.tinkar.test;
    provides DataServiceController
            with ProviderEphemeralController;
}
