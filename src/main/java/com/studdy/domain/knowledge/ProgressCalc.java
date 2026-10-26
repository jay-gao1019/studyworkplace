package com.studdy.domain.knowledge;

import java.util.List;
import java.util.Set;

/** 教材目录进度（docs/03 M4.3/4.4）：向下取整到 1%；奥数单列不计入校内进度。 */
public final class ProgressCalc {

    private ProgressCalc() {}

    public record SubjectProgress(int schoolPercent, int olympiadPercent, boolean hasOlympiad) {}

    /**
     * @param schoolLeaves   校内叶子 id 集合
     * @param olympiadLeaves 奥数叶子 id 集合（可为空）
     * @param checked        已勾选叶子 id 集合
     */
    public static SubjectProgress progress(Set<String> schoolLeaves, Set<String> olympiadLeaves, Set<String> checked) {
        int school = percent(count(schoolLeaves, checked), schoolLeaves.size());
        boolean hasOly = !olympiadLeaves.isEmpty();
        int oly = hasOly ? percent(count(olympiadLeaves, checked), olympiadLeaves.size()) : 0;
        return new SubjectProgress(school, oly, hasOly);
    }

    private static int count(Set<String> universe, Set<String> checked) {
        return (int) universe.stream().filter(checked::contains).count();
    }

    private static int percent(int done, int total) {
        return total == 0 ? 0 : (int) Math.floor(100.0 * done / total);
    }

    /** 分支拆分辅助：按 branch 属性把叶子 id 分到校内/奥数两桶。 */
    public record LeafWithBranch(String id, String branch) {}

    public static SubjectProgress progressAll(List<LeafWithBranch> leaves, Set<String> checked) {
        Set<String> school = leaves.stream().filter(l -> !"奥数".equals(l.branch())).map(LeafWithBranch::id).collect(java.util.stream.Collectors.toSet());
        Set<String> oly = leaves.stream().filter(l -> "奥数".equals(l.branch())).map(LeafWithBranch::id).collect(java.util.stream.Collectors.toSet());
        return progress(school, oly, checked);
    }
}
