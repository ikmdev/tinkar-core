/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import org.hl7.tinkar.common.service.DataServiceController;
import org.hl7.tinkar.common.service.LoadDataFromFileController;
import org.hl7.tinkar.provider.mvstore.MvStoreOpenController;
import org.hl7.tinkar.provider.mvstore.MvStoreNewController;

@SuppressWarnings("module") // 7 in HL7 is not a version reference
module org.hl7.tinkar.provider.mvstore {
    requires java.base;
    requires org.hl7.tinkar.collection;
    requires org.hl7.tinkar.common;
    requires org.hl7.tinkar.component;
    requires org.hl7.tinkar.entity;
    requires org.hl7.tinkar.dto;
    requires org.hl7.tinkar.mvstore;

    uses LoadDataFromFileController;

    provides DataServiceController
            with MvStoreOpenController, MvStoreNewController;
}
