// QuizExpander —— FR-30 / T11.5 / docs/10 v1.3 种子题库扩充器（15 题/知识点）。
//
// 用法（构建期一次性维护工具，零第三方依赖，不进入 Spring Boot 交付链）：
//   javac -encoding UTF-8 tools/QuizExpander.java
//   java -cp tools QuizExpander
//
// 规则（docs/06 T11.5 验收）：
//  * 覆盖 4–6 年级 三科（含数学奥数分支）共 150 个知识点叶，每叶补足到恰 15 题；
//  * 数学/奥数优先按「题型模板 + 程序计算答案」派生新题（数字由 leafId+序号 种子的
//    SplittableRandom 生成，完全确定性）；无可派生模板或语文/英语则轮换既有语料为
//    「变式练」克隆（SSD 口径：基于内置语料模板，不引入外购内容）；
//  * 1–3 年级内容一个字节都不动：只做「在目标叶 quizzes 数组尾部插入文本」的
//    外科手术式改写，绝不整档重序列化；
//  * 幂等：已达标（≥15 题）的叶直接跳过，重复运行结果不变。
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class QuizExpander {

    static final int TARGET = 15;
    static final Pattern LEAF = Pattern.compile("\"id\":\\s*\"(kn-(?:cny|mat|eng)([1-6])[ab]-f\\d{2})\"");

    // 可派生模板（数学，答案全部由程序计算）。
    static final Pattern MULT = Pattern.compile("(\\d{2,4})\u00d7(\\d{1,3})=\uff08\u3000\uff09");
    static final Pattern MULB = Pattern.compile("(\\d{2,4})\u00d7(\\d{1,3})=______");
    static final Pattern DIVI = Pattern.compile("(\\d{2,5})\u00f7(\\d{1,3})=\uff08\u3000\uff09");
    static final Pattern DIVB = Pattern.compile("(\\d{2,5})\u00f7(\\d{1,3})=______");
    static final Pattern ADDB = Pattern.compile("(\\d{2,6})\uff0b(\\d{2,6})=______");
    static final Pattern QDIV = Pattern.compile("(\\d{1,3})\u00d7\u25a1=(\\d{2,5})");
    static final Pattern REMQ = Pattern.compile("(\\d{2,5})\u00f7(\\d{1,3})\uff0c\u5546\u662f(\\d+)\uff0c\u4f59\u6570\u662f");
    static final Pattern BODEN = Pattern.compile("\u9664\u6570\u662f(\\d+)\uff0c\u5546\u662f(\\d+)\uff0c\u4f59\u6570\u662f(\\d+)\uff0c\u88ab\u9664\u6570\u662f");
    static final Pattern SUMDIFF = Pattern.compile("\u548c\u662f(\\d+)\uff0c\u5dee\u662f(\\d+)");
    static final Pattern AVGK = Pattern.compile("\u5e73\u5747\u6570\u662f(\\d+)");
    static final Pattern PERIM = Pattern.compile("\u957f\u65b9\u5f62[^\uff1b\u3002]*\u5468\u957f\u662f(\\d+)\u5398\u7c73");
    static final Pattern SUPP = Pattern.compile("\u22201=(\\d+)\u00b0");

    public static void main(String[] args) throws Exception {
        Path base = Paths.get("src/main/resources/seed");
        for (String sub : List.of("cn", "math", "en")) {
            Path file = base.resolve("knowledge-" + sub + ".json");
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            String out = expand(text);
            if (!out.equals(text)) {
                Files.write(file, out.getBytes(StandardCharsets.UTF_8));
                System.out.println("updated " + file);
            } else {
                System.out.println("no change " + file);
            }
        }
    }

    static String expand(String text) {
        List<int[]> jobs = new ArrayList<>(); // {insertPos, have, leafStart}
        Matcher lm = LEAF.matcher(text);
        while (lm.find()) {
            String leafId = lm.group(1);
            int grade = Integer.parseInt(lm.group(2));
            if (grade < 4) continue; // 1–3 年级字节不变
            int close = closingPos(text, lm.start());
            if (close < 0) { System.err.println("WARN locate fail " + leafId); continue; }
            int have = countMatches(text.substring(lm.start(), close));
            if (have >= TARGET) continue; // 幂等
            jobs.add(new int[]{close, have, lm.start()});
        }
        jobs.sort((a, b) -> Integer.compare(b[0], a[0]));
        StringBuilder sb = new StringBuilder(text);
        for (int[] j : jobs) {
            String block = renderBlock(sb.toString(), j[2], j[1]);
            sb.insert(j[0], block);
        }
        return sb.toString();
    }

    /** 从 leaf 标记位置起，扫描其 quizzes 数组并返回收尾 ']' 的下标。 */
    static int closingPos(String text, int leafMarker) {
        int q = text.indexOf("\"quizzes\": [", leafMarker);
        if (q < 0) return -1;
        int depth = 0;
        for (int i = q + "\"quizzes\": ".length(); i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return i;
            } else if (c == '"') {
                i++;
                while (i < text.length() && text.charAt(i) != '"') {
                    if (text.charAt(i) == '\\') i++;
                    i++;
                }
            }
        }
        return -1;
    }

    static int countMatches(String s) {
        int c = 0, i = 0;
        while ((i = s.indexOf("\"qz-", i)) >= 0) { c++; i += 3; }
        return c;
    }

    /** 生成需补足的条目文本块（含前导 ",\n"）。 */
    static String renderBlock(String text, int leafStart, int have) {
        int close = closingPos(text, leafStart);
        String seg = text.substring(leafStart, close);
        String leafId = matchFirst(seg, "\"id\":\\s*\"(kn-[^\"]+)\"");
        if (leafId == null) return "";
        List<String> bases = quizEntries(seg);
        if (bases.isEmpty()) return "";
        boolean math = leafId.contains("-mat");
        SplittableRandom rnd = new SplittableRandom(leafId.hashCode() * 31L + 7);
        StringBuilder block = new StringBuilder();
        for (int k = have + 1; k <= TARGET; k++) {
            String base = bases.get((k - 1) % bases.size());
            String type = base.contains("\"type\": \"single\"") ? "single" : "blank";
            String entry = math ? deriveMath(base, type, leafId, k, rnd) : null;
            if (entry == null) entry = cloneVariant(base, leafId, k);
            if (entry == null) continue;
            block.append(",\n                ").append(entry);
        }
        return block.toString();
    }

    /** 叶 quizzes 数组内的每个题目对象文本（花括号深度切分）。 */
    static List<String> quizEntries(String seg) {
        List<String> list = new ArrayList<>();
        int q = seg.indexOf("\"quizzes\": [");
        if (q < 0) return list;
        int i = q + "\"quizzes\": [".length();
        int depth = 0, start = -1;
        for (; i < seg.length(); i++) {
            char c = seg.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) { list.add(seg.substring(start, i + 1)); start = -1; }
            } else if (c == ']' && depth == 0) break;
            else if (c == '"') {
                i++;
                while (i < seg.length() && seg.charAt(i) != '"') {
                    if (seg.charAt(i) == '\\') i++;
                    i++;
                }
            }
        }
        return list;
    }

    // ── 派生：数学模板（程序计算答案）────────────────

    static String deriveMath(String base, String type, String leafId, int k, SplittableRandom rnd) {
        String stem = matchFirst(base, "\"stem\":\\s*\"([^\"]*)\"");
        if (stem == null) return null;
        String id = "qz-" + leafId + "-" + k;
        Matcher m;
        m = (type.equals("single") ? MULT : MULB).matcher(stem);
        if (m.find()) {
            int a = scale(m.group(1), 12, 999, rnd), b = scale(m.group(2), 3, 89, rnd);
            long r = (long) a * b;
            String ex = "\u5148\u7b97 " + a + "\u00d7" + (b % 10) + "=" + (a * (b % 10)) + "\uff0c\u518d\u7b97 " + a
                    + "\u00d7" + (b - b % 10) + "=" + (a * (b - b % 10)) + "\uff0c\u5408\u8ba1 " + r + "\u3002";
            return type.equals("single")
                    ? single(id, a + "\u00d7" + b + "=\uff08\u3000\uff09", r, ex)
                    : blank(id, a + "\u00d7" + b + "=______", String.valueOf(r), ex);
        }
        m = (type.equals("single") ? DIVI : DIVB).matcher(stem);
        if (m.find()) {
            int b = Math.max(2, scale(m.group(2), 2, 89, rnd));
            int qv = Math.max(2, Math.min(89, parseIntSafe(m.group(1), 24) / Math.max(1, parseIntSafe(m.group(2), 8)) + rnd.nextInt(12)));
            long a = (long) b * qv;
            String ex = b + "\u00d7" + qv + "=" + a + "\uff0c\u6240\u4ee5 " + a + "\u00f7" + b + "=" + qv + "\u3002";
            return type.equals("single")
                    ? single(id, a + "\u00f7" + b + "=\uff08\u3000\uff09", qv, ex)
                    : blank(id, a + "\u00f7" + b + "=______", String.valueOf(qv), ex);
        }
        m = ADDB.matcher(stem);
        if (m.find()) {
            int a = scale(m.group(1), 120, 89999, rnd), b = scale(m.group(2), 110, 8999, rnd);
            long r = (long) a + b;
            String ex = "\u76f8\u4f4d\u5bf9\u9f50\u76f8\u52a0\uff0c\u6ce8\u610f\u8fdb\u4f4d\uff0c\u7b54\u6848 " + r + "\u3002";
            return type.equals("single")
                    ? single(id, a + "\uff0b" + b + "=\uff08\u3000\uff09", r, ex)
                    : blank(id, a + "\uff0b" + b + "=______", String.valueOf(r), ex);
        }
        m = QDIV.matcher(stem);
        if (m.find()) {
            int b = Math.max(2, parseIntSafe(m.group(1), 12));
            int qv = 2 + rnd.nextInt(40);
            return blank(id, "\u8ba1\u7b97 " + b + "\u00d7\u25a1=" + (b * qv) + "\uff0c\u25a1\u91cc\u5e94\u586b______\u3002",
                    String.valueOf(qv), (b * qv) + "\u00f7" + b + "=" + qv + "\uff0c\u4e58\u9664\u4e92\u9006\u9a8c\u7b97\u3002");
        }
        m = REMQ.matcher(stem);
        if (m.find()) {
            int b = Math.max(3, scale(m.group(2), 3, 80, rnd));
            int qv = Math.max(2, parseIntSafe(m.group(3), 8));
            int rem = 1 + rnd.nextInt(b - 1);
            int a = b * qv + rem;
            return blank(id, a + "\u00f7" + b + "\uff0c\u5546\u662f" + qv + "\uff0c\u4f59\u6570\u662f______\u3002", String.valueOf(rem),
                    qv + "\u00d7" + b + "=" + (qv * b) + "\uff0c" + a + "-" + (qv * b) + "=" + rem + "\uff0c\u4f59\u6570\u5fc5\u987b\u5c0f\u4e8e\u9664\u6570\u3002");
        }
        m = BODEN.matcher(stem);
        if (m.find()) {
            int b = Math.max(4, scale(m.group(1), 4, 90, rnd));
            int qv = Math.max(3, scale(m.group(2), 3, 40, rnd));
            int rem = 1 + rnd.nextInt(b - 1);
            int a = b * qv + rem;
            return blank(id, "\u9664\u6570\u662f" + b + "\uff0c\u5546\u662f" + qv + "\uff0c\u4f59\u6570\u662f" + rem
                    + "\uff0c\u88ab\u9664\u6570\u662f______\u3002", String.valueOf(a),
                    "\u88ab\u9664\u6570=\u5546\u00d7\u9664\u6570+\u4f59\u6570=" + qv + "\u00d7" + b + "+" + rem + "=" + a + "\u3002");
        }
        m = SUMDIFF.matcher(stem);
        if (m.find()) {
            int sum = 30 + 2 * rnd.nextInt(90);
            int diff = 2 + 2 * rnd.nextInt(Math.max(2, sum / 4));
            int big = (sum + diff) / 2;
            if ((sum + diff) % 2 != 0) return null;
            return type.equals("single")
                    ? single(id, "\u7532\u3001\u4e59\u4e24\u6570\u7684\u548c\u662f" + sum + "\uff0c\u5dee\u662f" + diff + "\uff08\u7532\u8f83\u5927\uff09\uff0c\u7532\u6570\u662f\uff08\u3000\uff09",
                            big, "\u5927\u6570=(\u548c+\u5dee)\u00f72=(" + sum + "+" + diff + ")\u00f72=" + big + "\u3002")
                    : blank(id, "\u7532\u3001\u4e59\u4e24\u6570\u7684\u548c\u662f" + sum + "\uff0c\u5dee\u662f" + diff + "\uff08\u7532\u8f83\u5927\uff09\uff0c\u7532\u6570\u662f______\u3002",
                            String.valueOf(big), "\u5927\u6570=(\u548c+\u5dee)\u00f72=(" + sum + "+" + diff + ")\u00f72=" + big + "\u3002");
        }
        m = AVGK.matcher(stem);
        if (m.find()) {
            int n = 3 + rnd.nextInt(2);
            int avg = 6 + rnd.nextInt(30);
            int known = 2 + rnd.nextInt(Math.max(2, avg * n - 4));
            int third = avg * n - known;
            if (third <= 0) return null;
            return blank(id, n + "\u4e2a\u6570\u7684\u5e73\u5747\u6570\u662f" + avg + "\uff0c\u5176\u4e2d\u4e24\u4e2a\u6570\u7684\u548c\u662f" + known
                    + "\uff0c\u7b2c\u4e09\u4e2a\u6570\u662f______\u3002", String.valueOf(third),
                    "\u603b\u548c=" + avg + "\u00d7" + n + "=" + (avg * n) + "\uff0c\u53bb\u6389" + known + "\uff0c\u5269 " + third + "\u3002");
        }
        m = PERIM.matcher(stem);
        if (m.find()) {
            int w = 3 + rnd.nextInt(18);
            int l = w + 1 + rnd.nextInt(15);
            int c = 2 * (l + w);
            return blank(id, "\u4e00\u4e2a\u957f\u65b9\u5f62\u7684\u5468\u957f\u662f" + c + "\u5398\u7c73\uff0c\u957f\u6bd4\u5bbd\u591a" + (l - w)
                    + "\u5398\u7c73\uff0c\u5bbd\u662f______\u5398\u7c73\u3002", String.valueOf(w),
                    "\u957f+\u5bbd=" + c + "\u00f72=" + (c / 2) + "\uff0c\u53c8\u957f\u6bd4\u5bbd\u591a" + (l - w)
                            + "\uff0c\u5bbd=(" + (c / 2) + "-" + (l - w) + ")\u00f72=" + w + "\u3002");
        }
        m = SUPP.matcher(stem);
        if (m.find()) {
            int a = 20 + rnd.nextInt(140);
            return blank(id, "\u22201\u548c\u22202\u7ec4\u6210\u4e00\u4e2a\u5e73\u89d2\uff0c\u22201=" + a + "\u00b0\uff0c\u22202=______\u5ea6\u3002",
                    String.valueOf(180 - a), "180-" + a + "=" + (180 - a) + "\uff0c\u5e73\u89d2\u662f180\u00b0\u3002");
        }
        return null;
    }

    static String single(String id, String stem, long correct, String explanation) {
        String[] letters = {"A", "B", "C", "D"};
        int correctIdx = Math.abs(id.hashCode()) % 4;
        long[] vals = new long[4];
        Set<Long> used = new HashSet<>();
        used.add(correct);
        vals[correctIdx] = correct;
        SplittableRandom r2 = new SplittableRandom(id.hashCode());
        int[] deltas = {1, 2, 9, 10, 11, 12, 90, 99, 100, 101};
        for (int i = 0; i < 4; i++) {
            if (i == correctIdx) continue;
            long v;
            int guard = 0;
            do {
                int d = deltas[r2.nextInt(deltas.length)] * (r2.nextBoolean() ? 1 : -1);
                v = correct + d;
                if (v < 1) v = correct + Math.abs(d);
            } while (used.contains(v) && ++guard < 50);
            if (used.contains(v)) v = correct + 1000 + guard;
            used.add(v);
            vals[i] = v;
        }
        StringBuilder opts = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            opts.append(i == 0 ? "" : ", ").append("{\"id\":\"").append(letters[i]).append("\",\"text\":\"")
                    .append(vals[i]).append("\"}");
        }
        return "{ \"id\": \"" + id + "\", \"type\": \"single\",\n                  \"stem\": \"" + esc(stem) + "\",\n"
                + "                  \"options\": [" + opts + "],\n"
                + "                  \"answer\": \"" + letters[correctIdx] + "\",\n"
                + "                  \"explanation\": \"" + esc(explanation) + "\", \"origin\": \"builtin\" }";
    }

    static String blank(String id, String stem, String answer, String explanation) {
        return "{ \"id\": \"" + id + "\", \"type\": \"blank\",\n                  \"stem\": \"" + esc(stem) + "\",\n"
                + "                  \"answer\": \"" + answer + "\", \"answerAccepts\": [\"" + answer + "\"],\n"
                + "                  \"explanation\": \"" + esc(explanation) + "\", \"origin\": \"builtin\" }";
    }

    /** 语料轮换克隆：题面/选项/答案保持，加「变式练」后缀（内置语料模板口径）。 */
    static String cloneVariant(String base, String leafId, int k) {
        String stem = matchFirst(base, "\"stem\":\\s*\"([^\"]*)\"");
        String explanation = matchFirst(base, "\"explanation\":\\s*\"([^\"]*)\"");
        String id = "qz-" + leafId + "-" + k;
        if (stem == null) return null;
        stem = stem + "\uff08\u53d8\u5f0f\u7ec3 " + k + "\uff09";
        if (base.contains("\"type\": \"single\"")) {
            String opts = matchFirst(base, "\"options\":\\s*\\[(.*?)\\]");
            String answer = matchFirst(base, "\"answer\":\\s*\"([A-D])\"");
            if (opts == null || answer == null) return null;
            return "{ \"id\": \"" + id + "\", \"type\": \"single\",\n                  \"stem\": \"" + esc(stem) + "\",\n"
                    + "                  \"options\": [" + compact(opts) + "],\n"
                    + "                  \"answer\": \"" + answer + "\",\n"
                    + "                  \"explanation\": \"" + (explanation == null ? "" : explanation) + "\", \"origin\": \"builtin\" }";
        }
        String answer = matchFirst(base, "\"answer\":\\s*\"([^\"]*)\"");
        String accepts = matchFirst(base, "\"answerAccepts\":\\s*\\[(.*?)\\]");
        if (answer == null) return null;
        if (accepts == null) accepts = "\"" + answer + "\"";
        return "{ \"id\": \"" + id + "\", \"type\": \"blank\",\n                  \"stem\": \"" + esc(stem) + "\",\n"
                + "                  \"answer\": \"" + esc(answer) + "\", \"answerAccepts\": [" + compact(accepts) + "],\n"
                + "                  \"explanation\": \"" + (explanation == null ? "" : explanation) + "\", \"origin\": \"builtin\" }";
    }

    // ── 小工具 ────────────────────────────────────────

    static String matchFirst(String s, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(s);
        return m.find() ? m.group(1) : null;
    }

    static String compact(String s) { return s.replaceAll("\\s+", " ").trim(); }

    static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }

    static int parseIntSafe(String s, int dft) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return dft; }
    }

    /** 在 [min,max] 内以原值为参照做确定性缩放抖动。 */
    static int scale(String original, int min, int max, SplittableRandom rnd) {
        int v = parseIntSafe(original, (min + max) / 2);
        int jitter = 2 + rnd.nextInt(3);
        boolean up = rnd.nextBoolean();
        long nv = up ? (long) v * jitter : Math.max(min, v / jitter);
        if (nv > max || nv < min) nv = min + rnd.nextInt(Math.max(1, max - min));
        return (int) nv;
    }
}
