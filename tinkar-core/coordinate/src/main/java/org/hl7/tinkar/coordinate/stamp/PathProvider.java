package org.hl7.tinkar.coordinate.stamp;

import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.MutableSet;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.entity.EntityService;
import org.hl7.tinkar.entity.SemanticEntityVersion;
import org.hl7.tinkar.terms.TinkarTerm;

import java.util.Optional;

//@TODO Service annotation
public class PathProvider implements CachingService {

    public static ImmutableSet<StampPositionImmutable> getPathOrigins(int pathNid) {

        MutableSet<StampPositionImmutable> originSet = Sets.mutable.empty();
        EntityService.get().forEachSemanticForComponentOfType(pathNid, TinkarTerm.PATH_ORIGINS_ASSEMBLAGE.nid(), semanticEntity -> {
            // Get versions, get fields.
            // TODO assumption 1... Only one version.
            if (semanticEntity.versions().size() == 1) {
                SemanticEntityVersion originVersion = semanticEntity.versions().get(0);
                ImmutableList<Object> fields = originVersion.fields();
                originSet.add(StampPositionImmutable.make((long) fields.get(1), (int) fields.get(0)));
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
    }

    @Override
    public void reset() {
        // TODO
    }


}
