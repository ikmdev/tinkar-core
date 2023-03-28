/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

@SuppressWarnings("module") // 7 in HL7 is not a version reference
module dev.ikm.tinkar.dto {
    requires org.slf4j;
    requires dev.ikm.tinkar.common;
    requires dev.ikm.tinkar.component;
    requires static io.soabase.recordbuilder.core;
    requires transitive static com.google.auto.service;
    requires java.compiler;
    requires org.eclipse.collections.api;
    requires org.eclipse.collections;
    exports dev.ikm.tinkar.dto.binary;
    exports dev.ikm.tinkar.dto.changeset;
    exports dev.ikm.tinkar.dto;
    exports dev.ikm.tinkar.dto.graph;
}
