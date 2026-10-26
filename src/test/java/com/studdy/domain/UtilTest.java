package com.studdy.domain;

import com.studdy.domain.util.DayKey;
import com.studdy.domain.util.TextUtil;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class UtilTest {

    @Test
    void dayKeyLocalBoundary() {
        // 23:59 与 00:00 归属相邻两个自然日（需求§6.1）
        assertEquals("2026-10-25", DayKey.ofToday(LocalDateTime.of(2026, 10, 25, 23, 59, 59)));
        assertEquals("2026-10-26", DayKey.ofToday(LocalDateTime.of(2026, 10, 26, 0, 0, 0)));
        assertEquals("2026-10", DayKey.monthOf("2026-10-25"));
        assertEquals("2026", DayKey.yearOf("2026-10-25"));
        assertEquals("2026-10-24", DayKey.yesterday("2026-10-25"));
        assertTrue(DayKey.isBefore("2026-10-24", "2026-10-25"));
    }

    @Test
    void normalizeFullHalfWidthAndSpace() {
        assertEquals("3050040", TextUtil.normalize("３０５００４０"));
        assertEquals("a b", TextUtil.normalize("  A   B  "));   // 空白折叠+小写
        assertEquals("ab", TextUtil.normalize("ＡＢ"));           // 全角转半角
        assertEquals("12", TextUtil.normalize("１２"));
        assertEquals("", TextUtil.normalize(null));
    }
}
