/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import org.hl7.tinkar.common.service.CachingService;

@SuppressWarnings("module") // 7 in HL7 is not a version reference
module org.hl7.tinkar.common {
    requires java.base;
    requires io.activej.bytebuf;
    requires org.eclipse.collections.api;
    requires org.eclipse.collections.impl;
    requires RoaringBitmap;
    requires java.prefs;
    exports org.hl7.tinkar.common.service;
    exports org.hl7.tinkar.common.util;
    exports org.hl7.tinkar.common.util.id;
    exports org.hl7.tinkar.common.util.time;
    uses CachingService;
}
