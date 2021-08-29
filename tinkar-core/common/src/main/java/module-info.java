/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import org.hl7.tinkar.common.service.*;

@SuppressWarnings("module")
        // 7 in HL7 is not a version reference
module org.hl7.tinkar.common {
    requires transitive io.smallrye.mutiny;
    requires transitive java.prefs;
    requires transitive org.hl7.tinkar.activej;
    requires transitive org.hl7.tinkar.eclipse.collections;
    requires transitive org.hl7.tinkar.roaring;
    requires transitive org.reactivestreams;
    requires transitive org.slf4j;
    requires transitive static org.hl7.tinkar.autoservice;


    exports org.hl7.tinkar.common.alert;
    exports org.hl7.tinkar.common.binary;
    exports org.hl7.tinkar.common.id;
    exports org.hl7.tinkar.common.service;
    exports org.hl7.tinkar.common.util.functional;
    exports org.hl7.tinkar.common.util.ints2long;
    exports org.hl7.tinkar.common.util.io;
    exports org.hl7.tinkar.common.util.text;
    exports org.hl7.tinkar.common.util.thread;
    exports org.hl7.tinkar.common.util.time;
    exports org.hl7.tinkar.common.util.uuid;
    exports org.hl7.tinkar.common.validation;

    provides CachingService with ServiceProperties, PrimitiveData;

    uses CachingService;
    uses DataServiceController;
    uses DefaultDescriptionForNidService;
    uses ExecutorController;
    uses PublicIdService;
}
