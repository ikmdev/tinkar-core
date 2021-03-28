package org.hl7.tinkar.coordinate.stamp;

import com.google.auto.service.AutoService;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.set.primitive.ImmutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.hl7.tinkar.collection.ConcurrentReferenceHashMap;
import org.hl7.tinkar.common.binary.Decoder;
import org.hl7.tinkar.common.binary.DecoderInput;
import org.hl7.tinkar.common.binary.Encoder;
import org.hl7.tinkar.common.binary.EncoderOutput;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.coordinate.ImmutableCoordinate;
import org.hl7.tinkar.entity.ConceptEntity;

import java.util.Objects;
import java.util.Set;
@AutoService(CachingService.class)
public class StampFilterTemplateImmutable  implements StampFilterTemplate, ImmutableCoordinate, CachingService {

    private static final ConcurrentReferenceHashMap<StampFilterTemplateImmutable, StampFilterTemplateImmutable> SINGLETONS =
            new ConcurrentReferenceHashMap<>(ConcurrentReferenceHashMap.ReferenceType.WEAK,
                    ConcurrentReferenceHashMap.ReferenceType.WEAK);
    private static final int marshalVersion = 1;

    private final StateSet allowedStates;
    private final ImmutableIntSet moduleNids;
    private final ImmutableIntSet excludedModuleNids;
    private final ImmutableIntList modulePreferenceOrder;

    protected StampFilterTemplateImmutable() {
        // No arg constructor for HK2 managed instance
        // This instance just enables reset functionality...
        this.allowedStates = null;
        this.moduleNids = null;
        this.modulePreferenceOrder = null;
        this.excludedModuleNids = null;
    }

    @Override
    public void reset() {
        SINGLETONS.clear();
    }

    private StampFilterTemplateImmutable(StateSet allowedStates,
                                         ImmutableIntSet moduleNids,
                                         ImmutableIntSet excludedModuleNids,
                                         ImmutableIntList modulePreferenceOrder) {
        this.allowedStates = allowedStates;
        this.moduleNids = moduleNids;
        this.excludedModuleNids = excludedModuleNids;
        this.modulePreferenceOrder = modulePreferenceOrder;
    }

    @Override
    @Encoder
    public void encode(EncoderOutput out) {
        this.allowedStates.encode(out);
        out.writeNidArray(moduleNids.toArray());
        out.writeNidArray(excludedModuleNids.toArray());
        out.writeNidArray(modulePreferenceOrder.toArray());
    }

    @Decoder
    public static StampFilterTemplateImmutable decode(DecoderInput in) {
        int objectMarshalVersion = in.encodingFormatVersion();
        switch (objectMarshalVersion) {
            case marshalVersion:
                return SINGLETONS.computeIfAbsent(new StampFilterTemplateImmutable(StateSet.decode(in),
                                IntSets.immutable.of(in.readNidArray()),
                                IntSets.immutable.of(in.readNidArray()),
                                IntLists.immutable.of(in.readNidArray())),
                        stampFilterImmutable -> stampFilterImmutable);
            default:
                throw new UnsupportedOperationException("Unsupported version: " + objectMarshalVersion);
        }
    }

    public static StampFilterTemplateImmutable make(StateSet allowedStates,
                                                    ImmutableIntSet moduleNids,
                                                    ImmutableIntSet excludedModuleNids,
                                                    ImmutableIntList modulePreferenceOrder) {
        return SINGLETONS.computeIfAbsent(new StampFilterTemplateImmutable(allowedStates,
                moduleNids, excludedModuleNids, modulePreferenceOrder), stampFilterImmutable -> stampFilterImmutable);
    }


    public static StampFilterTemplateImmutable make(StateSet allowedStates,
                                                    ImmutableIntSet moduleNids,
                                                    ImmutableIntList modulePreferenceOrder) {
        return SINGLETONS.computeIfAbsent(new StampFilterTemplateImmutable(allowedStates,
                moduleNids, IntSets.immutable.empty(), modulePreferenceOrder), stampFilterImmutable -> stampFilterImmutable);
    }


    public static StampFilterTemplateImmutable make(StateSet allowedStates,
                                                    ImmutableIntSet moduleNids) {
        return SINGLETONS.computeIfAbsent(new StampFilterTemplateImmutable(allowedStates,
                moduleNids, IntSets.immutable.empty(), IntLists.immutable.empty()), stampFilterImmutable -> stampFilterImmutable);
    }

    public static StampFilterTemplateImmutable make(StateSet allowedStates,
                                                    Set<ConceptEntity> modules) {
        ImmutableIntSet moduleNids = IntSets.immutable.of(modules.stream().mapToInt(value -> value.nid()).toArray());

        return SINGLETONS.computeIfAbsent(new StampFilterTemplateImmutable(allowedStates,
                moduleNids, IntSets.immutable.empty(), IntLists.immutable.empty()), stampFilterImmutable -> stampFilterImmutable);
    }

    public static StampFilterTemplateImmutable make(StateSet allowedStates) {

        return SINGLETONS.computeIfAbsent(new StampFilterTemplateImmutable(allowedStates,
                IntSets.immutable.empty(),
                IntSets.immutable.empty(),
                IntLists.immutable.empty()), stampFilterImmutable -> stampFilterImmutable);
    }

    @Override
    public StateSet allowedStates() {
        return this.allowedStates;
    }

    @Override
    public ImmutableIntSet moduleNids() {
        return this.moduleNids;
    }

    @Override
    public ImmutableIntSet excludedModuleNids() {
        return this.excludedModuleNids;
    }

    @Override
    public ImmutableIntList modulePriorityOrder() {
        return this.modulePreferenceOrder;
    }

    @Override
    public String toString() {
        return "StampFilterTemplateImmutable{" + toUserString() + "}";
    }


    public StampFilterTemplateImmutable toStampFilterTemplateImmutable() {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StampFilterTemplate that)) return false;
        return allowedStates().equals(that.allowedStates()) &&
                moduleNids().equals(that.moduleNids()) &&
                excludedModuleNids().equals(that.excludedModuleNids()) &&
                modulePriorityOrder().equals(that.modulePriorityOrder());
    }

    @Override
    public int hashCode() {
        return Objects.hash(allowedStates(),
                moduleNids(),
                excludedModuleNids(),
                modulePriorityOrder());
    }
}