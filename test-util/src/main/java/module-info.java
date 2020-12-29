/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

@SuppressWarnings("module") // 7 in HL7 is not a version reference
module org.hl7.tinkar.test {
    requires java.base;
    requires org.eclipse.collections.api;
    requires org.hl7.tinkar.component;
    exports org.hl7.tinkar.test;
}
