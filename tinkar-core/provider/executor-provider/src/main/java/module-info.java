/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import org.hl7.tinkar.common.service.*;
import org.hl7.tinkar.provider.executor.ExecutorProviderController;

@SuppressWarnings("module") // 7 in HL7 is not a version reference
module org.hl7.tinkar.provider.executor {
    requires java.base;
    requires org.hl7.tinkar.common;
    requires static org.hl7.tinkar.autoservice;
    provides ExecutorController
            with ExecutorProviderController;
}

