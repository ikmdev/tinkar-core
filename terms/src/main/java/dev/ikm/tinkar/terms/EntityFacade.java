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

import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.component.Component;

/**
 * {@code <entity desc="" uuids=""/>}
 */
public interface EntityFacade extends Component, ComponentWithNid {

    static EntityFacade make(long nid) {
        return EntityProxy.make(nid);
    }

    static long toNid(EntityFacade entityFacade) {
        return entityFacade.nid();
    }

    /**
     * Whether two references name the same component, whatever form each has: an
     * {@link EntityProxy}, a binding enum ({@link ConceptEnumerationFacade}), an entity record, or
     * any other facade. Compare components with this, never with {@code ==} or {@code equals}:
     * {@code ==} is false whenever the forms differ, and {@code equals} depends on the receiver's
     * class ({@code Enum.equals} is identity, so {@code KernelConcept.X.equals(proxyOfX)} is
     * false).
     *
     * @param a a reference, or {@code null}
     * @param b a reference, or {@code null}
     * @return whether both are {@code null} or both name the component with the same nid
     */
    static boolean same(EntityFacade a, EntityFacade b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.nid() == b.nid();
    }

    default String description() {
        return PrimitiveData.text(nid());
    }

    default String toXmlFragment() {
        return ProxyFactory.toXmlFragment(this);
    }

    default <T extends EntityProxy> T toProxy() {
        return ProxyFactory.fromFacade(this);
    }

}