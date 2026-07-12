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
package dev.ikm.tinkar.entity.builder;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.State;

/**
 * A declared stamp whose status dimension is {@link State#INACTIVE}. Scoping a builder
 * with an inactive stamp ({@link ConceptBuilder#at(InactiveStamp)}) exposes only the
 * retirement verbs, so authoring content under a retirement stamp does not compile.
 *
 * @param time             the declared time in epoch milliseconds
 * @param author           the author dimension
 * @param module           the module dimension
 * @param path             the path dimension
 * @param declaredIdentity the established identity this stamp adopts, or null when
 *                         identity is tuple-derived (see {@link Stamp#publicId()})
 * @see Stamp#inactive(String, ConceptFacade, ConceptFacade, ConceptFacade)
 * @see Stamp#inactive(PublicId, String, ConceptFacade, ConceptFacade, ConceptFacade)
 */
public record InactiveStamp(long time, ConceptFacade author, ConceptFacade module,
                            ConceptFacade path, PublicId declaredIdentity) implements Stamp {

    /**
     * Declares an inactive stamp with tuple-derived identity — the common authoring form.
     *
     * @param time   the declared time in epoch milliseconds
     * @param author the author dimension
     * @param module the module dimension
     * @param path   the path dimension
     */
    public InactiveStamp(long time, ConceptFacade author, ConceptFacade module, ConceptFacade path) {
        this(time, author, module, path, null);
    }

    /**
     * The status dimension of this stamp.
     *
     * @return always {@link State#INACTIVE}
     */
    @Override
    public State state() {
        return State.INACTIVE;
    }
}
