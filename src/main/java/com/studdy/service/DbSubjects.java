package com.studdy.service;

/** API 科目代码（cn/math/en）与内置 seed 代码（cny/mat/eng）、数据库代码（cn/mat/eng）的三方映射。 */
public final class DbSubjects {

    private DbSubjects() {}

    /** 数据库 tree_nodes/quizzes 的 subject 列值（与内置叶 ID 中段一致）。 */
    public static String db(String api) {
        return switch (api) {
            case "cn" -> "cny";
            case "math" -> "mat";
            case "en" -> "eng";
            default -> api;
        };
    }

    /** seed 文件名用（cn/math/en）。 */
    public static String seed(String api) {
        return switch (api) {
            case "cny" -> "cn";
            case "mat" -> "math";
            case "eng" -> "en";
            default -> api;
        };
    }
}
