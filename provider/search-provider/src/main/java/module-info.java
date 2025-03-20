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
module dev.ikm.tinkar.provider.search {
    requires org.slf4j;
    requires transitive dev.ikm.tinkar.entity;
    requires dev.ikm.jpms.eclipse.collections.api;
    requires org.apache.lucene.queryparser;
    requires org.apache.lucene.queries;
    requires org.apache.lucene.highlighter;
    requires org.apache.lucene.core;
    requires dev.ikm.tinkar.coordinate;
    requires dev.ikm.jpms.eclipse.collections;
    requires org.apache.lucene.suggest;
    requires dev.ikm.tinkar.common;
    requires dev.ikm.tinkar.terms;

    exports dev.ikm.tinkar.provider.search;
}
