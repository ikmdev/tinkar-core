package dev.ikm.tinkar.coordinate.stamp.calculator;

import dev.ikm.tinkar.entity.SemanticEntityVersion;

public record LatestVersionSearchResult(Latest<SemanticEntityVersion> latestVersion, int fieldIndex, float score,
                                        String highlightedString) {
}
