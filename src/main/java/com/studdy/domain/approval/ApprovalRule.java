package com.studdy.domain.approval;

import java.util.Set;

/**
 * 审批状态机核心规则（docs/03 M11.2/M11.9，v2.3/FR-29）。纯逻辑，无 Spring/JPA 依赖。
 * 状态：pending → approved | rejected | withdrawn；终态不可再迁移（approve 幂等重放除外，由服务层处理）。
 */
public final class ApprovalRule {

    public static final String PENDING = "pending";
    public static final String APPROVED = "approved";
    public static final String REJECTED = "rejected";
    public static final String WITHDRAWN = "withdrawn";

    /** 支持的全部 kind（M11.3）。 */
    public static final Set<String> KINDS = Set.of(
            "TASK_CHECK", "CHECKIN", "TEXTBOOK_CHECK", "HABIT_MILESTONE",
            "MASTERY_REWARD", "LIFE_CHECK", "MANUAL_TX", "PENALTY");

    /** 免审批可配置的 kind（M11.7；PENALTY 恒需审批、MANUAL_TX 由家长发起仍纳入开关）。 */
    public static final Set<String> CONFIGURABLE_KINDS = Set.of(
            "TASK_CHECK", "CHECKIN", "TEXTBOOK_CHECK", "HABIT_MILESTONE",
            "MASTERY_REWARD", "LIFE_CHECK", "MANUAL_TX");

    private static final Set<String> STATUSES = Set.of(PENDING, APPROVED, REJECTED, WITHDRAWN);

    private ApprovalRule() {}

    public static boolean isKind(String kind) {
        return kind != null && KINDS.contains(kind);
    }

    public static boolean isConfigurableKind(String kind) {
        return kind != null && CONFIGURABLE_KINDS.contains(kind);
    }

    /** needsApproval 判定：总开关关闭或 kind 在免审批清单 → 即时落账（v2.2 行为）。 */
    public static boolean needsApproval(boolean enabled, Set<String> autoKinds, String kind, int amount) {
        if (amount <= 0) {
            return false; // 0/负额无需审批占位
        }
        if (!enabled) {
            return false;
        }
        return !(autoKinds != null && autoKinds.contains(kind));
    }

    /** 状态迁移校验：合法返回 null，非法返回中文原因。 */
    public static String transitionReject(String from, String to) {
        if (from == null || !STATUSES.contains(from) || !STATUSES.contains(to)) {
            return "非法的审批状态";
        }
        if (!PENDING.equals(from)) {
            return "该申请已是终态（" + from + "），不可再操作";
        }
        return switch (to) {
            case APPROVED, REJECTED, WITHDRAWN -> null;
            default -> "不允许的迁移: " + from + " → " + to;
        };
    }

    /** 占位去重（M11.5）：同 (profileId,kind,refId,dayKey) 已有 pending 则拒绝重复申请。 */
    public static String duplicatePendingMessage(String existingStatus) {
        if (PENDING.equals(existingStatus)) {
            return "已有申请正在等待审批";
        }
        return null;
    }
}
