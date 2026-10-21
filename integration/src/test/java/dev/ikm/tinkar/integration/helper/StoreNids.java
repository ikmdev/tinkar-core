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
package dev.ikm.tinkar.integration.helper;

import dev.ikm.tinkar.common.id.Nid;
import dev.ikm.tinkar.common.id.impl.NidLayout;
import dev.ikm.tinkar.common.service.internal.EntityStore;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds a nid of the open store written in text as a decimal number, the check that text meant
 * for people names components by description or UUID and never by nid. A sequential store's nids
 * are a minus sign and ten digits, a 64-bit store's ten digits or more; a number counts as a nid
 * only when the store holds an entity under it, so a long number that is not one, such as a time
 * in milliseconds, is not taken for one (IKE-Network/ike-issues#1313).
 */
public final class StoreNids {

    /** A decimal number long enough to be a nid of either layout, not part of a word or a UUID. */
    private static final Pattern NUMBER = Pattern.compile("(?<![\\w-])-?\\d{10,19}(?!\\w)");

    private StoreNids() {
    }

    /**
     * The first number in the text that is the nid of a component the open store holds.
     *
     * @param text the text
     * @return the number as written, if one is a nid
     */
    public static Optional<String> find(String text) {
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            long number;
            try {
                number = Long.parseLong(matcher.group());
            } catch (NumberFormatException tooLong) {
                continue;
            }
            if (held(number)) {
                return Optional.of(matcher.group());
            }
        }
        return Optional.empty();
    }

    private static boolean held(long number) {
        boolean aNidOfTheLayout = NidLayout.active() == NidLayout.SIXTY_FOUR_BIT
                ? Nid.isValid64(number)
                : number > Integer.MIN_VALUE && number < Integer.MAX_VALUE && number != 0;
        return aNidOfTheLayout && EntityStore.current().getBytes(number) != null;
    }
}
