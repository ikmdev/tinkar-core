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
package dev.ikm.tinkar.common.service.internal;

import dev.ikm.tinkar.common.service.DataActivity;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.PrimitiveDataService;
import org.eclipse.collections.api.block.procedure.primitive.IntProcedure;
import org.eclipse.collections.api.factory.primitive.IntLists;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;

import java.util.function.ObjIntConsumer;

/**
 * The part of a data store's contract that only the entity layer uses: an entity's bytes, the
 * merge that writes them, the scans of the whole store, and the enumerations and indexes by
 * kind, by pattern and by referenced component, all in nids.
 *
 * <p>This package is exported only to the entity module, the entity provider and the stores.
 * Everything else asks {@code EntityService} for entities, and the entity service decides how
 * to find them: by nid or by scan, sequential or parallel, decoded or not. Its rules (the
 * binding patterns, absent and canceled entities) then hold for every caller, in one place.
 *
 * <p>Every store implements this beside {@link PrimitiveDataService}.
 */
public interface EntityStore {

    /**
     * The running store's entity contract.
     *
     * @return the running {@link PrimitiveDataService}, as an entity store
     * @throws IllegalStateException if the running store does not implement this contract
     */
    static EntityStore current() {
        PrimitiveDataService service = PrimitiveData.get();
        if (service instanceof EntityStore store) {
            return store;
        }
        throw new IllegalStateException("The running PrimitiveDataService does not implement EntityStore: "
                + service.getClass().getName());
    }

    void forEach(ObjIntConsumer<byte[]> action);

    void forEachParallel(ObjIntConsumer<byte[]> action);

    void forEach(ImmutableIntList nids, ObjIntConsumer<byte[]> action);

    void forEachParallel(ImmutableIntList nids, ObjIntConsumer<byte[]> action);

    byte[] getBytes(int nid);

    /**
     * If the specified nid (native identifier -- an int) is not already associated
     * with a value or is associated with null, associates it with the given non-null value.
     * Otherwise, replaces the associated value with the results of a remapping function
     * (the provider provides remapping function), or removes if the result is {@code null}.
     * This method may be of use when combining multiple mapped values for a nid.
     * For example, merging multiple versions of an entity, where each version is represented as a
     * byte[].
     *
     * Defaults to an activity of DataActivity.SYNCHRONIZABLE_EDIT.
     *
     * @param nid                    native identifier (an int) with which the resulting value is to be associated
     * @param patternNid             if the bytes are for a semantic, its pattern nid, otherwise
     *                               the not-applicable sentinel, {@code Integer.MAX_VALUE}
     *                               ({@link dev.ikm.tinkar.common.id.Nid#NOT_APPLICABLE})
     * @param referencedComponentNid if the bytes are for a semantic, the referenced component nid,
     *                               otherwise the not-applicable sentinel, {@code Integer.MAX_VALUE}.
     * @param value                  the non-null value to be merged with the existing value
     *                               associated with the nid or, if no existing value or a null value
     *                               is associated with the nid, to be associated with the nid
     * @param sourceObject           object that is the source of the bytes to merge.
     * @return the new value associated with the specified nid, or null if no
     * value is associated with the nid
     */
    default byte[] merge(int nid, int patternNid, int referencedComponentNid, byte[] value, Object sourceObject) {
        return this.merge(nid, patternNid, referencedComponentNid, value, sourceObject, DataActivity.SYNCHRONIZABLE_EDIT);
    }

    /**
     * If the specified nid (native identifier -- an int) is not already associated with a value or is associated
     * with null, associates it with the given non-null value. Otherwise, replaces the associated value with the
     * results of a remapping function (the provider provides remapping function), or removes if the result is
     * null. This method may be of use when combining multiple mapped values for a nid. For example, merging multiple
     * versions of an entity, where each version is represented as a byte[].
     *
     * @param nid Native identifier (an int) with which the resulting value is to be associated.
     * @param patternNid If the bytes are for a semantic, its pattern nid, otherwise the
     *                   not-applicable sentinel, {@code Integer.MAX_VALUE}
     *                   ({@link dev.ikm.tinkar.common.id.Nid#NOT_APPLICABLE}).
     * @param referencedComponentNid If the bytes are for a semantic, the referenced component nid,
     *                               otherwise the not-applicable sentinel, {@code Integer.MAX_VALUE}.
     * @param value The non-null value to be merged with the existing value
     *              associated with the nid or, if no existing value or a null value
     *              is associated with the nid, to be associated with the nid.
     * @param sourceObject Object that is the source of the bytes to merge.
     * @param activity The data activity performed, classifying the type of database (and therefore change set) write.
     * @return The new value associated with the specified nid, or null if no
     *         value is associated with the nid.
     */
    byte[] merge(int nid, int patternNid, int referencedComponentNid, byte[] value, Object sourceObject, DataActivity activity);

    default int[] semanticNidsOfPattern(int patternNid) {
        MutableIntList intList = IntLists.mutable.empty();
        forEachSemanticNidOfPattern(patternNid, nid -> intList.add(nid));
        return intList.toArray();
    }

    void forEachSemanticNidOfPattern(int patternNid, IntProcedure procedure);

    void forEachPatternNid(IntProcedure procedure);

    void forEachConceptNid(IntProcedure procedure);

    void forEachStampNid(IntProcedure procedure);

    void forEachSemanticNid(IntProcedure procedure);

    default int[] semanticNidsForComponent(int componentNid) {
        MutableIntList intList = IntLists.mutable.empty();
        forEachSemanticNidForComponent(componentNid, nid -> intList.add(nid));
        return intList.toArray();
    }

    void forEachSemanticNidForComponent(int componentNid, IntProcedure procedure);

    default int[] semanticNidsForComponentOfPattern(int componentNid, int patternNid) {
        MutableIntList intList = IntLists.mutable.empty();
        forEachSemanticNidForComponentOfPattern(componentNid, patternNid, nid -> intList.add(nid));
        return intList.toArray();
    }

    void forEachSemanticNidForComponentOfPattern(int componentNid, int patternNid, IntProcedure procedure);
}
