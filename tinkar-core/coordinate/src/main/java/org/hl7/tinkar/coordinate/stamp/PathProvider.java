package org.hl7.tinkar.coordinate.stamp;

import com.google.auto.service.AutoService;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.MutableSet;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.terms.ConceptFacade;
import org.hl7.tinkar.terms.ConceptProxy;
import org.hl7.tinkar.terms.EntityFacade;
import org.hl7.tinkar.terms.TinkarTerm;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class PathProvider {

    private static final ConcurrentHashMap<Integer, ImmutableSet<StampPositionImmutable>> SINGLETONS =
            new ConcurrentHashMap<>();

    @AutoService(CachingService.class)
    public static class CachingProvider implements CachingService {

        @Override
        public void reset() {
            SINGLETONS.clear();
        }
    }

    public static ImmutableSet<StampPositionImmutable> getPathOrigins(int pathNid) {

        return SINGLETONS.computeIfAbsent(pathNid, newPathNid -> {
            MutableSet<StampPositionImmutable> originSet = Sets.mutable.empty();
            EntityService.get().forEachSemanticForComponentOfType(pathNid, TinkarTerm.PATH_ORIGINS_ASSEMBLAGE.nid(), semanticEntity -> {
                // Get versions, get fields.
                // TODO assumption 1... Only one version.
                if (semanticEntity.versions().size() == 1) {
                    SemanticEntityVersion originVersion = semanticEntity.versions().get(0);
                    ImmutableList<Object> fields = originVersion.fields();
                    ConceptFacade pathConcept;
                    if (fields.get(0) instanceof ConceptFacade conceptFacade) {
                        pathConcept = conceptFacade;
                    } else if (fields.get(0) instanceof EntityFacade entityFacade) {
                        pathConcept = ConceptProxy.make(entityFacade.nid());
                    } else {
                        throw new IllegalStateException("Can't construct ConceptFacade from: " + fields.get(0));
                    }
                    originSet.add(StampPositionImmutable.make((Instant) fields.get(1), pathConcept));
                } else {
                    throw new UnsupportedOperationException("Can't handle more than one version yet...");
                }
            });

            if (originSet.isEmpty() && pathNid != TinkarTerm.PRIMORDIAL_PATH.nid()) {
                // A boot strap issue, only the primordial path should have no origins.
                // If terminology not completely loaded, content may not yet be ready.
                if (pathNid != TinkarTerm.SANDBOX_PATH.nid() && pathNid != TinkarTerm.MASTER_PATH.nid() && pathNid != TinkarTerm.DEVELOPMENT_PATH.nid()) {
                    throw new IllegalStateException("Path with no origin: " + EntityService.get().getEntityFast(pathNid));
                }
                if (pathNid == TinkarTerm.DEVELOPMENT_PATH.nid()) {
                    return Sets.immutable.with(StampPositionImmutable.make(Long.MAX_VALUE, TinkarTerm.SANDBOX_PATH.nid()));
                }
                return Sets.immutable.with(StampPositionImmutable.make(Long.MAX_VALUE, TinkarTerm.PRIMORDIAL_PATH.nid()));
            }
            return originSet.toImmutable();
        });
    }

}
