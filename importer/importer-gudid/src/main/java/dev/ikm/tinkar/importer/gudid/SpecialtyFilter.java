/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.importer.gudid;

import dev.ikm.tinkar.importer.gudid.GudidRelease.ReleaseFile;
import dev.ikm.tinkar.importer.gudid.GudidTerms.MedicalSpecialty;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Which product codes and devices an import includes: all of them (the full release), or those of
 * some medical specialties. Ported from IKM's {@code GudidUtility}, whose decisions it keeps,
 * including that a device with no product code, or a product code with no specialty, is included.
 */
final class SpecialtyFilter {

    private final Set<String> specialties;
    private final Map<String, Set<String>> productCodesByDevice;
    private final Map<String, String> specialtyByProductCode;

    private SpecialtyFilter(Set<String> specialties, Map<String, Set<String>> productCodesByDevice,
                            Map<String, String> specialtyByProductCode) {
        this.specialties = specialties;
        this.productCodesByDevice = productCodesByDevice;
        this.specialtyByProductCode = specialtyByProductCode;
    }

    /**
     * Everything: the full release.
     *
     * @return the filter
     */
    static SpecialtyFilter everything() {
        return new SpecialtyFilter(Set.of(), Map.of(), Map.of());
    }

    /**
     * The product codes and devices of some specialties, read from a release's product codes and
     * classification files. No specialties is the full release.
     *
     * @param included the specialties
     * @param release  the release
     * @return the filter
     */
    static SpecialtyFilter of(EnumSet<MedicalSpecialty> included, GudidRelease release) {
        if (included.isEmpty()) {
            return everything();
        }
        Set<String> abbreviations = new HashSet<>();
        included.forEach(specialty -> abbreviations.add(specialty.abbreviation));
        Map<String, Set<String>> productCodesByDevice = new HashMap<>();
        try (Stream<String[]> rows = release.rows(ReleaseFile.PRODUCT_CODES)) {
            rows.filter(row -> row.length > 1)
                    .forEach(row -> productCodesByDevice.computeIfAbsent(row[0], k -> new HashSet<>()).add(row[1]));
        }
        Map<String, String> specialtyByProductCode = new HashMap<>();
        try (Stream<String[]> rows = release.rows(ReleaseFile.FOI_CLASS)) {
            rows.filter(row -> row.length > 2)
                    .filter(row -> row[1].isBlank() || abbreviations.contains(row[1]))
                    .forEach(row -> specialtyByProductCode.putIfAbsent(row[2], row[1]));
        }
        return new SpecialtyFilter(abbreviations, productCodesByDevice, specialtyByProductCode);
    }

    /**
     * Whether this is the full release.
     *
     * @return true when nothing is filtered
     */
    boolean full() {
        return specialties.isEmpty();
    }

    /**
     * Whether a classification row is included: its product code classified under the specialty.
     *
     * @param specialty   the row's specialty abbreviation
     * @param productCode the row's product code
     * @return true when included
     */
    boolean includesProductCode(String specialty, String productCode) {
        return full() || specialty.equals(specialtyByProductCode.get(productCode));
    }

    /**
     * Whether a device is included: it has no product code, or one of its product codes is.
     *
     * @param primaryDi the device's primary device identifier
     * @return true when included
     */
    boolean includesDevice(String primaryDi) {
        if (full() || !productCodesByDevice.containsKey(primaryDi)) {
            return true;
        }
        return productCodesByDevice.get(primaryDi).stream()
                .map(specialtyByProductCode::get).anyMatch(Objects::nonNull);
    }

    /**
     * Whether a device's product code row is included: the device has no product code, or this one
     * is among its included product codes.
     *
     * @param primaryDi   the device's primary device identifier
     * @param productCode the product code
     * @return true when included
     */
    boolean includesDevice(String primaryDi, String productCode) {
        if (full() || !productCodesByDevice.containsKey(primaryDi)) {
            return true;
        }
        return productCodesByDevice.get(primaryDi).contains(productCode)
                && specialtyByProductCode.get(productCode) != null;
    }
}
