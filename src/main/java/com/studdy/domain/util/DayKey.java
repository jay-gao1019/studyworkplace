package com.studdy.domain.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 服务器本地日期口径（docs/03 M9.3、需求§6.1）。 */
public final class DayKey {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private DayKey() {}

    public static String of(LocalDate date) {
        return date.format(FMT);
    }

    public static String ofToday(LocalDateTime now) {
        return of(now.toLocalDate());
    }

    public static LocalDate parse(String dayKey) {
        return LocalDate.parse(dayKey, FMT);
    }

    /** dayKey 所属月份 yyyy-MM */
    public static String monthOf(String dayKey) {
        return dayKey.substring(0, 7);
    }

    /** dayKey 所属年份 yyyy */
    public static String yearOf(String dayKey) {
        return dayKey.substring(0, 4);
    }

    public static String yesterday(String dayKey) {
        return of(parse(dayKey).minusDays(1));
    }

    public static boolean isBefore(String a, String b) {
        return parse(a).isBefore(parse(b));
    }
}
