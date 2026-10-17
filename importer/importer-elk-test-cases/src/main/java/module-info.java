/*
 * Copyright © 2026 IKE Network (support@ike.network)
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
 * The ELK test-case importer: the ELK reasoner's OWL test corpus, lifted into the reasoner test
 * knowledge base.
 */
module dev.ikm.tinkar.importer.elk {
    requires dev.ikm.tinkar.importer;
    requires org.semanticweb.elk.owl.model;
    requires org.semanticweb.elk.owl.implementation;
    requires org.semanticweb.elk.owl.parsing.javacc;
    requires org.eclipse.collections.api;
    requires org.slf4j;

    exports dev.ikm.tinkar.importer.elk;
    opens dev.ikm.tinkar.importer.elk;

    provides dev.ikm.tinkar.importer.KnowledgeImporter with dev.ikm.tinkar.importer.elk.ElkTestCaseImporter;
}
