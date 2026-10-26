package com.studdy.util;

import java.util.UUID;

/** 运行时对象 ID 生成（docs/05 §1：前缀+短UUID；内置内容用可读 ID）。 */
public final class Ids {

    private Ids() {}

    public static String next(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
