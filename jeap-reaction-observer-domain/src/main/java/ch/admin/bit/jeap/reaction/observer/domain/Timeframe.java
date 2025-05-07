package ch.admin.bit.jeap.reaction.observer.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public record Timeframe(ZonedDateTime start, ZonedDateTime end) {

    public static Timeframe ofInstantsInDefaultTimezone(Instant start, Instant end) {
        return new Timeframe(
                zonedDateTimeFromInstant(start),
                zonedDateTimeFromInstant(end));
    }

    private static ZonedDateTime zonedDateTimeFromInstant(Instant instant) {
        return ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
