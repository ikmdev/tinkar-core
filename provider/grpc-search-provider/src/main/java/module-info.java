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

/**
 * gRPC-backed implementation of {@link dev.ikm.tinkar.provider.search.SearchService}.
 *
 * <p>Provides {@link dev.ikm.tinkar.provider.grpc.GrpcSearchService}, which delegates
 * concept search to a remote tinkar-core gRPC service via
 * {@link dev.ikm.tinkar.provider.grpc.GrpcSearchClient}.
 *
 * <p>The gRPC runtime libraries (grpc-api, grpc-stub, grpc-protobuf, guava) are shaded
 * into this jar so that jlink sees a single named module.
 * grpc-netty-shaded (the transport) remains on the classpath as a runtime-only
 * automatic module discovered via ServiceLoader.
 */
module dev.ikm.tinkar.provider.grpc {

    exports dev.ikm.tinkar.provider.grpc;
    // Generated proto/gRPC stub classes
    exports dev.ikm.tinkar.service.proto;

    // Protobuf runtime — JPMS-wrapped
    requires dev.ikm.jpms.protobuf;
    // Tinkar schema message classes (from Tinkar.proto)
    requires dev.ikm.tinkar.schema;
    // JPMS-wrapped javax.annotation
    requires dev.ikm.jpms.javax.annotation;

    // SearchService contract and PrimitiveDataSearchResult
    requires dev.ikm.tinkar.provider.search;
    requires dev.ikm.tinkar.common;

    requires org.slf4j;
}
