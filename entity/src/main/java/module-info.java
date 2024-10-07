/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.common.service.LoadDataFromFileController;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.StampService;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromFileController;

@SuppressWarnings("module")
        // 7 in HL7 is not a version reference
module dev.ikm.tinkar.entity {
    requires transitive dev.ikm.tinkar.common;
    requires transitive dev.ikm.tinkar.component;
    requires transitive com.github.benmanes.caffeine;
    requires transitive dev.ikm.tinkar.terms;

    requires static dev.ikm.jpms.recordbuilder.core;
    requires static java.compiler;

    requires org.slf4j;
    requires dev.ikm.jpms.activej.bytebuf;
    requires java.logging;
    requires dev.ikm.jpms.eclipse.collections;
    requires dev.ikm.jpms.eclipse.collections.api;
    requires java.xml;
    requires dev.ikm.tinkar.schema;
    requires dev.ikm.jpms.protobuf;
    requires org.jgrapht.core;
    exports dev.ikm.tinkar.entity;
    exports dev.ikm.tinkar.entity.aggregator;
    exports dev.ikm.tinkar.entity.graph;
    exports dev.ikm.tinkar.entity.util;
    exports dev.ikm.tinkar.entity.load;
    exports dev.ikm.tinkar.entity.export;
    exports dev.ikm.tinkar.entity.transaction;
    exports dev.ikm.tinkar.entity.transform;
    exports dev.ikm.tinkar.entity.graph.isomorphic;
    exports dev.ikm.tinkar.entity.graph.adaptor.axiom;

    opens dev.ikm.tinkar.entity.graph.adaptor.axiom;

    provides LoadDataFromFileController
            with LoadEntitiesFromFileController;

    uses CachingService;
    uses EntityService;
    uses StampService;
}
