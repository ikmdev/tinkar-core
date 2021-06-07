package org.hl7.tinkar.entity;

import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.MutableSet;
import org.hl7.tinkar.common.id.IntIdSet;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.entity.internal.StampServiceGetter;
import org.hl7.tinkar.entity.util.StampRealizer;
import org.hl7.tinkar.terms.ConceptFacade;
import org.hl7.tinkar.terms.ConceptProxy;

public interface StampService {

    static StampService get() {
        return StampServiceGetter.INSTANCE.get();
    }
    /**
     * Very inefficient. Please override.
     * @return IntIdSet of the stamp nids.
     */
    default IntIdSet getStampNids() {
        StampRealizer stampRealizer = new StampRealizer();
        PrimitiveData.get().forEach(stampRealizer);
        return stampRealizer.stampNids();
    }

    IntIdSet getAuthorNidsInUse();

    default ImmutableSet<ConceptFacade> getAuthorsInUse() {
        MutableSet<ConceptFacade> authors = Sets.mutable.empty();
        for (int authorNid: getAuthorNidsInUse().toArray()) {
            authors.add(ConceptProxy.make(authorNid));
        }
        return authors.toImmutable();
    }

    IntIdSet getModuleNidsInUse();

    default ImmutableSet<ConceptFacade> getModulesInUse() {
        MutableSet<ConceptFacade> modules = Sets.mutable.empty();
        for (int moduleNid: getModuleNidsInUse().toArray()) {
            modules.add(ConceptProxy.make(moduleNid));
        }
        return modules.toImmutable();
    }

    IntIdSet getPathNidsInUse();

    default ImmutableSet<ConceptFacade> getPathsInUse() {
        MutableSet<ConceptFacade> paths = Sets.mutable.empty();
        for (int pathNid: getPathNidsInUse().toArray()) {
            paths.add(ConceptProxy.make(pathNid));
        }
        return paths.toImmutable();
    }

    ImmutableLongList getTimesInUse();

}
