/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

@SuppressWarnings("module") // 7 in HL7 is not a version reference
module dev.ikm.tinkar.collection {
    requires transitive dev.ikm.tinkar.common;
    requires org.eclipse.collections.api;
    requires org.eclipse.collections;
    requires org.slf4j;
    requires transitive static com.google.auto.service;

    exports dev.ikm.tinkar.collection;
    opens dev.ikm.tinkar.collection to org.eclipse.collections.api, org.eclipse.collections;
    exports dev.ikm.tinkar.collection.store;
}

