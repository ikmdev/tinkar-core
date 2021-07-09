package org.hl7.tinkar.coordinate.navigation.calculator;

import org.hl7.tinkar.common.id.IntIdSet;

public record EdgeRecord(IntIdSet typeNids, int destinationNid) implements Edge {
}
