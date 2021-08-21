package org.hl7.tinkar.coordinate.stamp.calculator;

import org.hl7.tinkar.entity.SemanticEntityVersion;

public record LatestVersionSearchResult(Latest<SemanticEntityVersion> latestVersion, float score) {
}
