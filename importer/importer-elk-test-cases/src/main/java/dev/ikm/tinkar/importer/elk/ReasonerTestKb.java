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
package dev.ikm.tinkar.importer.elk;

import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.terms.EntityProxy;

/**
 * The reasoner test knowledge base's own components, which the lift writes in its own module and
 * tests over the knowledge base find cases by. Each identity is
 * {@code T5(}{@link #NAMESPACE}{@code , "shared|<key>")}.
 */
public final class ReasonerTestKb {

    /** The namespace the knowledge base's identities are minted in: T5(URL, its repository URL). */
    public static final String NAMESPACE = "fc6141fd-6baa-5832-818c-fbca4674f4b5";

    /** The module the knowledge base's own components are written in. */
    public static final EntityProxy.Concept MODULE = EntityProxy.Concept.make(
            "Reasoner test KB module (ReasonerTestKb)", PublicIds.of("8c9ee44c-a02e-50e2-a5ec-813557cd37dc"));

    /** The author of every lifted version. */
    public static final EntityProxy.Concept AUTHOR = EntityProxy.Concept.make(
            "ELK test corpus (ReasonerTestKb)", PublicIds.of("48f43be8-be19-50ff-9ddb-718dbfacf20d"));

    /** The concept every case's top is a kind of. */
    public static final EntityProxy.Concept ROOT = EntityProxy.Concept.make(
            "ELK reasoner test cases (ReasonerTestKb)", PublicIds.of("2b664fa7-e1e1-5606-870a-80f3e276fd65"));

    /** The identifier source of the identifier that marks a case's module with the case's path. */
    public static final EntityProxy.Concept CASE_IDENTIFIER_SOURCE = EntityProxy.Concept.make(
            "ELK test case (ReasonerTestKb)", PublicIds.of("dae3e73a-5991-5454-a3e5-2735bda84f2b"));

    private ReasonerTestKb() {
    }
}
