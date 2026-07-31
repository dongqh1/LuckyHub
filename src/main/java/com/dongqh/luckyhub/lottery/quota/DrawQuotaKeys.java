package com.dongqh.luckyhub.lottery.quota;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class DrawQuotaKeys {

    private static final DateTimeFormatter DRAW_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String RESERVATION_TIMEOUTS = "draw:reservation:timeouts";

    private DrawQuotaKeys() {
    }

    public static String quota(long activityId, long userId, LocalDate drawDate) {
        return "draw:quota:" + activityId + ":" + userId + ":" + DRAW_DATE.format(drawDate);
    }

    public static String reservation(String requestId) {
        return "draw:reservation:" + requestId;
    }

    public static String reservationTimeouts() {
        return RESERVATION_TIMEOUTS;
    }

    public static String drawLock(long activityId, long userId) {
        return "lock:draw:" + activityId + ":" + userId;
    }
}
