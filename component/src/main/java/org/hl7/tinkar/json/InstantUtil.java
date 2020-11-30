package org.hl7.tinkar.json;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

public class InstantUtil {
    private InstantUtil() {}

    public static Optional<Instant> parse(String possibleInstant) {
        try {
            Instant instant = Instant.parse(possibleInstant);
            return Optional.of(instant);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
