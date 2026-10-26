package com.studdy.domain.life;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 屏幕时间额度（docs/03 M6.6–6.10，服务器时钟口径）。
 * 会话用 startAt/endAt 表达；时长在查询时对进行中会话实时计算。
 */
public final class ScreenQuota {

    public record Session(String id, LocalDateTime startAt, LocalDateTime endAt) {
        public double minutesAsOf(LocalDateTime now) {
            LocalDateTime effEnd = endAt == null ? now : endAt;
            return Math.max(0, Duration.between(startAt, effEnd).toMillis() / 60000.0);
        }
    }

    public record Settings(int dailyMinutes, int weeklyBankCap, int overPenalty) {}

    /** 当日已用分钟（进行中会话按 now 实时）。 */
    public static double usedToday(List<Session> sessions, LocalDateTime now) {
        return sessions.stream().mapToDouble(s -> s.minutesAsOf(now)).sum();
    }

    /**
     * 是否应判超时（M6.8）：已用 > 日额度+可用结转。
     * 结转消费顺序：先当日额度后结转（M6.9）——体现在 allowance 总额一致，展示拆分由 service 做。
     */
    public static boolean isOver(Settings st, double usedMinutes, int bankAvailable) {
        return usedMinutes > st.dailyMinutes() + bankAvailable;
    }

    /** 是否允许开始新会话（M6.8：超时锁定）。 */
    public static boolean canStartNew(Settings st, List<Session> todaySessions, LocalDateTime now,
                                      int bankAvailable, boolean alreadyPenalizedToday) {
        if (alreadyPenalizedToday) {
            return false;
        }
        double used = usedToday(todaySessions, now);
        return used < st.dailyMinutes() + bankAvailable;
    }

    /** 进行中会话（最多一个，M6.6）。 */
    public static Session active(List<Session> sessions) {
        return sessions.stream().filter(s -> s.endAt() == null).findFirst().orElse(null);
    }

    /** 结束会话时的结转扣减分钟数（M6.9 先当日后结转）：返回需要动用的结转额度。 */
    public static int bankConsume(Settings st, double usedAfterEnd, int bankAvailable) {
        double beyondDaily = Math.max(0, usedAfterEnd - st.dailyMinutes());
        return (int) Math.min(Math.ceil(beyondDaily), Math.max(0, bankAvailable));
    }
}
