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
 * Knowledge importers: a provider interface for writing a source release into the running
 * store, and the version-aware {@link dev.ikm.tinkar.importer.StoreWriter} they write through.
 */
module dev.ikm.tinkar.importer {
    requires transitive dev.ikm.tinkar.entity;
    requires transitive dev.ikm.tinkar.common;
    requires transitive dev.ikm.tinkar.terms;
    requires org.eclipse.collections.api;

    exports dev.ikm.tinkar.importer;

    uses dev.ikm.tinkar.importer.KnowledgeImporter;
}
