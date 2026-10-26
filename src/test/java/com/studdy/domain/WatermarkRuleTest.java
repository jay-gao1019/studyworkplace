package com.studdy.domain;

import com.studdy.domain.photo.WatermarkRule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 水印行文与布局规则（docs/03 M13.3，v2.4/FR-32）。 */
class WatermarkRuleTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 25, 19, 30, 45);

    @Test
    void fourLinesTimeLabelNoteBrand() {
        List<String> lines = WatermarkRule.lines(T, "任务打卡", "今天自己检查了作业");
        assertEquals(4, lines.size());
        assertEquals("2026-10-25 19:30:45", lines.get(0));
        assertEquals("任务打卡", lines.get(1));
        assertTrue(lines.get(2).startsWith("备注："));
        assertEquals("studyworkplace", lines.get(3));
    }

    @Test
    void blankLabelAndNoteOmitLines() {
        List<String> lines = WatermarkRule.lines(T, "  ", null);
        assertEquals(2, lines.size());
        assertEquals("2026-10-25 19:30:45", lines.get(0));
        assertEquals("studyworkplace", lines.get(1));
    }

    @Test
    void noteClippedTo20Chars() {
        String longNote = "一二三四五六七八九十一二三四五六七八九十一二三四五";
        List<String> lines = WatermarkRule.lines(T, "家长记录", longNote);
        String noteLine = lines.get(2);
        assertEquals("备注：" + longNote.substring(0, 20), noteLine);
        assertTrue(noteLine.length() <= 3 + WatermarkRule.NOTE_CLIP);
    }

    @Test
    void fontSizeFloor24() {
        assertEquals(24, WatermarkRule.fontSize(100));
        assertEquals(24, WatermarkRule.fontSize(700)); // 700×0.032=22.4 < 24
        assertEquals(51, WatermarkRule.fontSize(1600));
    }

    @Test
    void layoutStaysInsidePortraitLandscapeAndTiny() {
        List<String> lines = WatermarkRule.lines(T, "任务打卡", "测试");
        for (int[] img : new int[][]{{1600, 1200}, {1080, 1920}, {320, 480}, {240, 240}}) {
            WatermarkRule.Layout ly = WatermarkRule.layout(img[0], img[1], lines);
            String where = img[0] + "x" + img[1];
            assertTrue(ly.boxX() >= 0, where + " boxX 负");
            assertTrue(ly.boxY() >= 0, where + " boxY 负");
            assertTrue(ly.boxX() + ly.boxW() <= img[0] + 4, where + " 横向出界");
            assertTrue(ly.boxY() + ly.boxH() <= img[1] + 4, where + " 纵向出界");
        }
    }

    @Test
    void labelClippedTo24() {
        List<String> lines = WatermarkRule.lines(T, "这是一个非常非常非常非常非常非常长的科目名称abcdefg", null);
        assertTrue(lines.get(1).length() <= 24);
    }
}
