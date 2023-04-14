/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
module dev.ikm.tinkar.terms {
    requires transitive dev.ikm.tinkar.component;
    requires dev.ikm.tinkar.common;
    requires java.xml;
    requires org.eclipse.collections.api;
    requires org.eclipse.collections;
    requires org.slf4j;
    requires transitive static com.google.auto.service;
    exports dev.ikm.tinkar.terms;
}
