package org.hl7.tinkar.component.calculator;

import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.component.Version;

public interface VersionCalculator<V extends Version, C extends Chronology<V>> {
    LatestVersion<V> getLatestVersion(C chronicle);
}
