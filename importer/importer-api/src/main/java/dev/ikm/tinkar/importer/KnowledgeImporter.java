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
package dev.ikm.tinkar.importer;

/**
 * Writes one kind of source release into the running store: SNOMED CT RF2 (the International
 * edition or an extension), RxNorm OWL, and so on. An importer is a provider, found through
 * {@link java.util.ServiceLoader} and chosen by {@link #name()}; {@link KnowledgeImporters} does
 * the finding.
 *
 * <p>The caller owns the store: it is started, and seeded with whatever the source builds on (the
 * starter set, and for an extension its edition), before {@link #importInto} runs, and is
 * classified, exported, or stopped after. An importer writes only through the {@link StoreWriter}
 * it is given, so its writes are version-aware and replaying a source writes nothing new.
 */
public interface KnowledgeImporter {

    /**
     * The name an importer is chosen by: lower case, words joined by hyphens, naming the format
     * it reads ({@code snomed-rf2}, {@code rxnorm-owl}).
     *
     * @return the name
     */
    String name();

    /**
     * One line on what the importer reads, for listings and errors.
     *
     * @return the description
     */
    String description();

    /**
     * Writes a source into the store.
     *
     * @param source the source and its options
     * @param writer the writer every write goes through
     * @return what was written
     * @throws ImportException when the source cannot be read or does not say what the importer needs
     */
    ImportReport importInto(ImportSource source, StoreWriter writer);

    /**
     * Whether an option changes what the importer writes. Such options are part of a release
     * registration's identity (IKE-Network/ike-issues#1304): the same release read with different
     * ones is a different registration. Every option counts unless the importer says otherwise; an
     * importer overrides this to leave out options that only change how it runs.
     *
     * @param option the option's name
     * @return true when the option changes what is written
     */
    default boolean changesOutput(String option) {
        return true;
    }
}
