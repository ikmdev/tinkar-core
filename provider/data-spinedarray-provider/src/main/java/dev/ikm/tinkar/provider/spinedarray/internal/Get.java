package dev.ikm.tinkar.provider.spinedarray.internal;

import dev.ikm.tinkar.provider.spinedarray.SpinedArrayProvider;
import org.eclipse.collections.api.list.ImmutableList;
import dev.ikm.tinkar.component.Stamp;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.EntityRecordFactory;
import dev.ikm.tinkar.entity.StampEntity;

import java.util.UUID;

public class Get {
    public static SpinedArrayProvider singleton;

    public static ConceptEntity concept(int nid) {
        return EntityRecordFactory.make(singleton.getBytes(nid));
    }

    public static StampEntity stamp(int nid) {
        return EntityRecordFactory.make(singleton.getBytes(nid));
    }

    public static int nidForUuids(ImmutableList<UUID> uuidList) {
        return singleton.nidForUuids(uuidList);
    }

    public static int stampNid(Stamp stamp) {
        throw new UnsupportedOperationException();
    }
}
