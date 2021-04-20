/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import org.hl7.tinkar.common.service.DataServiceController;
import org.hl7.tinkar.common.service.LoadDataFromFileController;
import org.hl7.tinkar.entity.EntityService;
import org.hl7.tinkar.provider.spinedarray.SpinedArrayNewController;
import org.hl7.tinkar.provider.spinedarray.SpinedArrayOpenController;

@SuppressWarnings("module") // 7 in HL7 is not a version reference
module org.hl7.tinkar.provider.spinedarray {
    requires org.hl7.tinkar.common;
    requires org.hl7.tinkar.component;
    requires org.hl7.tinkar.entity;
    requires org.hl7.tinkar.collection;
    requires static org.hl7.tinkar.autoservice;

    provides DataServiceController
            with SpinedArrayOpenController, SpinedArrayNewController;

    uses LoadDataFromFileController;

}
