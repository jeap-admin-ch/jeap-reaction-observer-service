package ch.admin.bit.jeap.reaction.observer.domain.aggregation;

import java.time.LocalDate;
import java.time.ZonedDateTime;

public abstract class TimeUtils {

    public static ZonedDateTime getStartOfDay() {
        ZonedDateTime now = ZonedDateTime.now();
        return now.toLocalDate().atStartOfDay(now.getZone());
    }

    public static LocalDate getToday() {
        return LocalDate.now();
    }

}
