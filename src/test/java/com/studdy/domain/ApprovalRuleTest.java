package com.studdy.domain;

import com.studdy.domain.approval.ApprovalRule;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 审批状态机核心规则单测（docs/03 M11，v2.3/FR-29，T11.2）。 */
class ApprovalRuleTest {

    @Test
    void needsApprovalHonorsSwitchAutoKindsAndAmount() {
        assertTrue(ApprovalRule.needsApproval(true, Set.of(), "TASK_CHECK", 5));
        assertFalse(ApprovalRule.needsApproval(false, Set.of(), "TASK_CHECK", 5), "总开关关 → v2.2 即时行为");
        assertFalse(ApprovalRule.needsApproval(true, Set.of("CHECKIN"), "CHECKIN", 20), "免审批 kind");
        assertTrue(ApprovalRule.needsApproval(true, Set.of("CHECKIN"), "TASK_CHECK", 5));
        assertFalse(ApprovalRule.needsApproval(true, Set.of(), "MANUAL_TX", 0), "非正额不占审批位");
        assertFalse(ApprovalRule.needsApproval(true, Set.of(), "MANUAL_TX", -5), "家长直扣不审批");
        assertTrue(ApprovalRule.needsApproval(true, null, "TASK_CHECK", 5), "免审批清单缺失按空集处理");
    }

    @Test
    void kindsCoverM11Contract() {
        assertTrue(ApprovalRule.isKind("PENALTY"));
        assertTrue(ApprovalRule.isKind("MASTERY_REWARD"));
        assertFalse(ApprovalRule.isKind("REDEEM"), "商店兑换豁免审批");
        assertFalse(ApprovalRule.isKind("screen_over"));
        assertFalse(ApprovalRule.isConfigurableKind("PENALTY"), "扣分面板恒审批");
    }

    @Test
    void transitionOnlyFromPending() {
        assertNull(ApprovalRule.transitionReject("pending", "approved"));
        assertNull(ApprovalRule.transitionReject("pending", "rejected"));
        assertNull(ApprovalRule.transitionReject("pending", "withdrawn"));
        assertNotNull(ApprovalRule.transitionReject("approved", "rejected"), "终态不可再迁移");
        assertNotNull(ApprovalRule.transitionReject("rejected", "approved"));
        assertNotNull(ApprovalRule.transitionReject("withdrawn", "withdrawn"));
        assertNotNull(ApprovalRule.transitionReject("pending", "pending"));
        assertNotNull(ApprovalRule.transitionReject(null, "approved"));
        assertNotNull(ApprovalRule.transitionReject("bogus", "approved"));
    }

    @Test
    void duplicatePendingMessageOnlyForLivePending() {
        assertNotNull(ApprovalRule.duplicatePendingMessage("pending"));
        assertNull(ApprovalRule.duplicatePendingMessage("approved"));
        assertNull(ApprovalRule.duplicatePendingMessage(null));
    }
}
