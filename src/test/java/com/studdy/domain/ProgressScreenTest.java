package com.studdy.domain;

import com.studdy.domain.knowledge.ProgressCalc;
import com.studdy.domain.life.ScreenQuota;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProgressScreenTest {

    @Test
    void aoShuNotInSchoolProgress() {
        var leaves = List.of(
                new ProgressCalc.LeafWithBranch("tb-mat4a-l01", "校内"),
                new ProgressCalc.LeafWithBranch("tb-mat4a-l02", "校内"),
                new ProgressCalc.LeafWithBranch("tb-mat4a-l03", "校内"),
                new ProgressCalc.LeafWithBranch("tb-mat4a-l04", "校内"),
                new ProgressCalc.LeafWithBranch("tb-mat4a-o01", "奥数"),
                new ProgressCalc.LeafWithBranch("tb-mat4a-o02", "奥数"));
        var p = ProgressCalc.progressAll(leaves, Set.of("tb-mat4a-l01", "tb-mat4a-o01"));
        assertEquals(25, p.schoolPercent());   // 1/4
        assertEquals(50, p.olympiadPercent()); // 1/2
        assertTrue(p.hasOlympiad());

        var mathless = ProgressCalc.progressAll(
                List.of(new ProgressCalc.LeafWithBranch("tb-cny4a-l01", "校内"),
                        new ProgressCalc.LeafWithBranch("tb-cny4a-l02", "校内")),
                Set.of());
        assertFalse(mathless.hasOlympiad());
        assertEquals(0, mathless.olympiadPercent());
    }

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 20, 0);
    private static final ScreenQuota.Settings ST = new ScreenQuota.Settings(40, 60, 10);

    @Test
    void resumeByStartAt() {
        // 刷新/换设备：进行中会话按 startAt 与服务器 now 重算（M6.10）
        var s = new ScreenQuota.Session("s1", NOW.minusMinutes(25), null);
        assertEquals(25, ScreenQuota.usedToday(List.of(s), NOW), 0.001);
        assertNotNull(ScreenQuota.active(List.of(s)));
        assertNull(ScreenQuota.active(List.of(new ScreenQuota.Session("s2", NOW.minusMinutes(5), NOW.minusMinutes(1)))));
        assertEquals(4, ScreenQuota.usedToday(
                List.of(new ScreenQuota.Session("s3", NOW.minusMinutes(5), NOW.minusMinutes(1))), NOW), 0.001);
    }

    @Test
    void overLimitLockOnce() {
        var sessions = List.of(new ScreenQuota.Session("s1", NOW.minusMinutes(45), null));
        double used = ScreenQuota.usedToday(sessions, NOW);
        assertTrue(ScreenQuota.isOver(ST, used, 0));               // 45 > 40
        assertFalse(ScreenQuota.isOver(ST, used, 10));             // 结转补足（M6.9）
        assertFalse(ScreenQuota.canStartNew(ST, sessions, NOW, 0, true));  // 已罚扣当日锁定
        assertTrue(ScreenQuota.canStartNew(ST, List.of(), NOW, 0, false));
        assertEquals(5, ScreenQuota.bankConsume(ST, 45.0, 60));    // 动用结转 5 分钟
        assertEquals(2, ScreenQuota.bankConsume(ST, 41.5, 60));    // 41.5-40=1.5 → ceil 2
    }
}
