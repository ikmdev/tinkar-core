package org.hl7.tinkar.component;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.component.Component;
import org.hl7.tinkar.component.Version;

import java.util.Optional;
import java.util.UUID;

public interface ChronologyService {

    default <T extends Chronology<V>, V extends Version> Optional<T> getChronology(UUID... uuids) {
        return getChronology(Lists.immutable.of(uuids));
    }

    default <T extends Chronology<V>, V extends Version> Optional<T> getChronology(Component component) {
        return getChronology(component.componentUuids());
    }

    <T extends Chronology<V>, V extends Version> Optional<T> getChronology(ImmutableList<UUID> uuids);

    void putChronology(Chronology chronology);
}
