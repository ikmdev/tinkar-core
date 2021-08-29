package org.hl7.tinkar.common.alert;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.map.MutableMap;
import org.hl7.tinkar.common.id.PublicIdStringKey;
import org.hl7.tinkar.common.id.PublicIds;

public class AlertStreams {
    public static final PublicIdStringKey<AlertStream> ROOT_ALERT_STREAM_KEY =
            new PublicIdStringKey(PublicIds.of("d2733c61-fef3-4051-bc96-137819a18d0a"), "root alert stream");
    public static final ImmutableList<PublicIdStringKey<AlertStream>> KEYS =
            Lists.immutable.of(ROOT_ALERT_STREAM_KEY);
    private static final AlertLogSubscriber rootSubscriber = new AlertLogSubscriber();
    private static ImmutableMap<PublicIdStringKey<AlertStream>, AlertStream> alertStreamMap;

    static {
        MutableMap<PublicIdStringKey<AlertStream>, AlertStream> tempMap = Maps.mutable.ofInitialCapacity(KEYS.size());
        AlertStream rootAlertStream = new AlertStream();
        rootAlertStream.subscribe(rootSubscriber);
        tempMap.put(ROOT_ALERT_STREAM_KEY, rootAlertStream);
        AlertStreams.alertStreamMap = tempMap.toImmutable();
    }

    public static AlertStream get(PublicIdStringKey<AlertStream> alertStreamKey) {
        return AlertStreams.alertStreamMap.get(alertStreamKey);
    }

    public static AlertStream getRoot() {
        return AlertStreams.alertStreamMap.get(ROOT_ALERT_STREAM_KEY);
    }
}
