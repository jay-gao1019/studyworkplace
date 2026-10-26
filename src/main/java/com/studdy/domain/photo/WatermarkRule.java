package com.studdy.domain.photo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 佐证照片水印规则（docs/03 M13.3，v2.4/FR-32）。纯计算：行文 composing + 几何布局。
 * 时间源=上传落库时刻（服务器本地时区），不解析 EXIF（M13.3 MUST）。
 */
public final class WatermarkRule {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final int MAX_LINES = 4;
    public static final int NOTE_CLIP = 20;

    private WatermarkRule() {}

    /** 四行水印：时间 / 事项 / 备注截 20 字 / 品牌角标。事项或备注为空则省略该行（不少于 2 行）。 */
    public static List<String> lines(LocalDateTime takenAt, String label, String note) {
        List<String> out = new ArrayList<>();
        out.add(TS.format(takenAt == null ? LocalDateTime.now() : takenAt));
        if (label != null && !label.isBlank()) {
            out.add(clip(label.trim(), 24));
        }
        if (note != null && !note.isBlank()) {
            out.add("备注：" + clip(note.trim(), NOTE_CLIP));
        }
        out.add("studyworkplace");
        return out;
    }

    /** 字号自适应图宽（M13.3：下限 24px）。 */
    public static int fontSize(int imgWidth) {
        return Math.max(24, Math.round(imgWidth * 0.032f));
    }

    public record Layout(int fontSize, int lineH, int pad, int boxW, int boxH, int boxX, int boxY, int textX, int textBaseY) {}

    /** 右下角半透明黑底白字块布局；任何尺寸图片都不出界（留 2×pad 边距）。 */
    public static Layout layout(int imgW, int imgH, List<String> lines) {
        int fs = fontSize(imgW);
        int lh = Math.round(fs * 1.45f);
        int pad = Math.max(4, fs / 2);
        int textW = 0;
        for (String l : lines) {
            textW = Math.max(textW, estWidth(l, fs));
        }
        int boxW = Math.min(textW + pad * 2, imgW - pad * 2);
        int boxH = lines.size() * lh + pad;
        int boxX = imgW - pad - boxW;
        int boxY = imgH - pad - boxH;
        if (boxX < pad) boxX = pad;
        if (boxY < pad) boxY = pad;
        return new Layout(fs, lh, pad, boxW, boxH, boxX, boxY, boxX + pad, boxY + lh - (lh - fs) / 2);
    }

    /** 估算行宽：CJK 全角字符≈1em，其余≈0.62em（PhotoService 落笔时以真实 FontMetrics 重排）。 */
    private static int estWidth(String line, int fs) {
        int w = 0;
        for (int i = 0; i < line.length(); i++) {
            w += line.charAt(i) > 0x2E7F ? fs : Math.round(fs * 0.62f);
        }
        return w;
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
