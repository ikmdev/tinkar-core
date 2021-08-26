package org.hl7.tinkar.common.service;

public record PrimitiveDataSearchResult(int nid, int rcNid, int patternNid, int fieldIndex, float score,
                                        String highlightedString) {
}
