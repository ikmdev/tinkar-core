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

import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.common.service.DataServiceController;
import dev.ikm.tinkar.common.service.DefaultDescriptionForNidService;
import dev.ikm.tinkar.common.service.PublicIdService;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.StampService;

open module dev.ikm.tinkar.integration.test {
    requires dev.ikm.tinkar.common;
    requires dev.ikm.tinkar.entity;
    requires dev.ikm.tinkar.provider.entity;
    requires dev.ikm.tinkar.terms;
    requires dev.ikm.tinkar.coordinate;
    requires com.google.protobuf;
    requires org.junit.jupiter.api;
    requires dev.ikm.tinkar.integration;
    requires org.slf4j;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires org.eclipse.collections.api;
    requires org.mockito; //TODO: delete with stale ITs
    requires dev.ikm.tinkar.ext.lang.owl;
    requires dev.ikm.tinkar.schema;
    requires dev.ikm.tinkar.provider.search;
    requires org.apache.lucene.highlighter;
    requires org.apache.lucene.queryparser;
    requires org.apache.lucene.core;
    requires dev.ikm.tinkar.fhir.transformers;
    requires dev.ikm.tinkar.ext.binding;
    requires io.soabase.recordbuilder.core;
    requires java.compiler;

    requires transitive dev.ikm.jpms.hapi.fhir.base;
    requires transitive dev.ikm.jpms.org.hl7.fhir.r4;
    requires transitive dev.ikm.jpms.hapi.fhir.structures.r4;
    requires transitive dev.ikm.jpms.org.hl7.fhir.utilities;
    requires dev.ikm.tinkar.composer;

    uses CachingService;
    uses DataServiceController;
    uses DefaultDescriptionForNidService;
    uses EntityService;
    uses PublicIdService;
    uses StampService;
}
