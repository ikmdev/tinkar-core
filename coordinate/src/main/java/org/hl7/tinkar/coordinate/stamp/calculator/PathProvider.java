package org.hl7.tinkar.coordinate.stamp.calculator;


import com.google.auto.service.AutoService;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.MutableSet;
import org.hl7.tinkar.common.util.time.DateTimeUtil;
import org.hl7.tinkar.coordinate.PathService;
import org.hl7.tinkar.coordinate.stamp.StampBranchRecord;
import org.hl7.tinkar.coordinate.stamp.StampPathImmutable;
import org.hl7.tinkar.coordinate.stamp.StampPositionRecord;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.EntityService;
import org.hl7.tinkar.entity.SemanticEntity;
import org.hl7.tinkar.entity.SemanticEntityVersion;
import org.hl7.tinkar.terms.ConceptFacade;
import org.hl7.tinkar.terms.EntityFacade;
import org.hl7.tinkar.terms.EntityProxy;
import org.hl7.tinkar.terms.TinkarTerm;

import java.time.Instant;

@AutoService(PathService.class)
public class PathProvider implements PathService {
    public ImmutableSet<StampBranchRecord> getPathBranches(int pathNid) {
        MutableSet<StampBranchRecord> branchSet = Sets.mutable.empty();
        EntityService.get().forEachSemanticOfPattern(TinkarTerm.PATH_ORIGINS_PATTERN.nid(), semanticEntity -> {
            // Referenced component = path for which this is an origin
            // Field 0 = path from which the origin is derived
            // Field 1 = instant of the origin
            // Get versions, get fieldValues.
            // TODO assumption 1... Only one version.
            if (semanticEntity.versions().size() == 1) {
                SemanticEntityVersion originVersion = semanticEntity.versions().get(0);
                ImmutableList<Object> fields = originVersion.fieldValues();
                ConceptFacade pathFromWhichOriginDerived = EntityProxy.Concept.make(((EntityFacade) fields.get(0)).nid());
                if (pathFromWhichOriginDerived.nid() == pathNid) {
                    Instant originTime = (Instant) fields.get(1);
                    StampBranchRecord stampBranchRecord = new StampBranchRecord(semanticEntity.referencedComponentNid(),
                            DateTimeUtil.instantToEpochMs(originTime));
                    branchSet.add(stampBranchRecord);
                }
            } else {
                throw new UnsupportedOperationException("Can't handle more than one version yet...");
            }
        });
        return branchSet.toImmutable();
    }

    @Override
    public ImmutableSet<StampPathImmutable> getPaths() {
        int[] pathsPatternSemanticNids = EntityService.get().semanticNidsOfPattern(TinkarTerm.PATHS_PATTERN.nid());
        MutableSet<StampPathImmutable> pathSet = Sets.mutable.ofInitialCapacity(pathsPatternSemanticNids.length);
        for (int pathsPatternSemanticNid : pathsPatternSemanticNids) {
            SemanticEntity semanticEntity = Entity.getFast(pathsPatternSemanticNid);
            int pathNid = semanticEntity.referencedComponentNid();
            pathSet.add(StampPathImmutable.make(pathNid, getPathOrigins(pathNid)));
        }
        return pathSet.toImmutable();
    }

    public ImmutableSet<StampPositionRecord> getPathOrigins(int pathNid) {
        MutableSet<StampPositionRecord> originSet = Sets.mutable.empty();
        EntityService.get().forEachSemanticForComponentOfPattern(pathNid, TinkarTerm.PATH_ORIGINS_PATTERN.nid(), semanticEntity -> {
            // Get versions, get fieldValues.
            // TODO assumption 1... Only one version.
            if (semanticEntity.versions().size() == 1) {
                SemanticEntityVersion originVersion = semanticEntity.versions().get(0);
                ImmutableList<Object> fields = originVersion.fieldValues();
                ConceptFacade pathConcept;
                if (fields.get(0) instanceof ConceptFacade conceptFacade) {
                    pathConcept = conceptFacade;
                } else if (fields.get(0) instanceof EntityFacade entityFacade) {
                    pathConcept = EntityProxy.Concept.make(entityFacade.nid());
                } else {
                    throw new IllegalStateException("Can't construct ConceptFacade from: " + fields.get(0));
                }
                originSet.add(StampPositionRecord.make((Instant) fields.get(1), pathConcept));
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
                return Sets.immutable.with(StampPositionRecord.make(Long.MAX_VALUE, TinkarTerm.SANDBOX_PATH.nid()));
            }
            return Sets.immutable.with(StampPositionRecord.make(Long.MAX_VALUE, TinkarTerm.PRIMORDIAL_PATH.nid()));
        }
        return originSet.toImmutable();
    }

}
