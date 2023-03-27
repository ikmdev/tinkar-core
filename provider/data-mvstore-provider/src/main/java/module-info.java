/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import dev.ikm.tinkar.common.service.DataServiceController;
import dev.ikm.tinkar.common.service.LoadDataFromFileController;
import dev.ikm.tinkar.provider.mvstore.MvStoreNewController;
import dev.ikm.tinkar.provider.mvstore.MvStoreOpenController;

@SuppressWarnings("module")
        // 7 in HL7 is not a version reference
module dev.ikm.tinkar.provider.mvstore {
    requires java.base;
    requires dev.ikm.tinkar.collection;
    requires dev.ikm.tinkar.common;
    requires dev.ikm.tinkar.component;
    requires dev.ikm.tinkar.entity;
    requires dev.ikm.tinkar.dto;
    requires dev.ikm.tinkar.mvstore;
    requires dev.ikm.tinkar.provider.search;

    uses LoadDataFromFileController;

    provides DataServiceController
            with MvStoreOpenController, MvStoreNewController;
}
