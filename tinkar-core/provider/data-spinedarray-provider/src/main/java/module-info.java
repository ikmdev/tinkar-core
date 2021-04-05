/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import org.hl7.tinkar.common.service.DataServiceController;
import org.hl7.tinkar.common.service.PrimitiveDataService;
import org.hl7.tinkar.entity.EntityService;
import org.hl7.tinkar.provider.spinedarray.SpinedArrayController;
import org.hl7.tinkar.provider.spinedarray.SpinedArrayProvider;

@SuppressWarnings("module") // 7 in HL7 is not a version reference
module org.hl7.tinkar.provider.openhft {
    requires org.hl7.tinkar.common;
    requires org.hl7.tinkar.component;
    requires org.hl7.tinkar.entity;
    requires org.hl7.tinkar.collection;
    requires static org.hl7.tinkar.autoservice;

    provides DataServiceController
            with SpinedArrayController;
    uses EntityService;

}
