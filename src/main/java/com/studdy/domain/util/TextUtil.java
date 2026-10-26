package com.studdy.domain.util;

import java.text.Normalizer;

/** 文本规范化（docs/03 M5.2 填空判分口径）。 */
public final class TextUtil {

    private TextUtil() {}

    /** trim + 全角转半角 + 连续空白折叠为单个空格 + 统一小写（判分不区分大小写）。 */
    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String half = Normalizer.normalize(s, Normalizer.Form.NFKC);
        return half.strip().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }
}
