package dev.ikm.tinkar.coordinate.navigation.calculator;

import dev.ikm.tinkar.common.id.IntIdSet;

public record EdgeRecord(IntIdSet typeNids, int destinationNid) implements Edge {
}
