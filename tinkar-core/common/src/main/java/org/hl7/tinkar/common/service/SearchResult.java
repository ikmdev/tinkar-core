package org.hl7.tinkar.common.service;

public record SearchResult(int nid, int rcNid, int patternNid, float score) {
}
