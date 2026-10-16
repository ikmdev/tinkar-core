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
package dev.ikm.tinkar.terms;

import dev.ikm.tinkar.common.id.PublicId;

/**
 * A pattern bound as an enum constant: the pattern counterpart of
 * {@link ConceptEnumerationFacade}. The binding generator writes one pattern enum per binding
 * class of a knowledge set ({@code KernelPattern}, {@code IkePattern}), so an enum constant can be
 * passed wherever a pattern is expected, and a {@code switch} over a family of patterns is checked
 * for exhaustiveness.
 *
 * @param <E> the enum type
 */
public interface PatternEnumerationFacade<E extends Enum<E>> extends PatternFacade {

    /**
     * @return the pattern this constant stands for
     */
    PatternFacade patternForEnum();

    /**
     * @return the constant's name, as {@link Enum#name()} gives it
     */
    String name();

    @Override
    default PublicId publicId() {
        return patternForEnum().publicId();
    }

    @Override
    default long nid() {
        return patternForEnum().nid();
    }

    /**
     * @return this constant as its enum type
     */
    @SuppressWarnings("unchecked")
    default E enumValue() {
        return (E) this;
    }
}
