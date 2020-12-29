package org.hl7.tinkar.dto.digraph;

import java.util.Map;
import java.util.UUID;

public class TreeNodeDTO {
    UUID nodeConceptUuid;
    UUID[][] typeUuidDestinationUuid;
    Map<UUID, Object> nodeProperties;
}
