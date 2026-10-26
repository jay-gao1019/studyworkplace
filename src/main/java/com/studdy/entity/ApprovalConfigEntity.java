package com.studdy.entity;

import jakarta.persistence.*;

/** 审批开关配置单例行（docs/03 M11.7，v2.3）。auto_kinds=免审批 kind 的 JSON 数组。 */
@Entity
@Table(name = "approval_config")
public class ApprovalConfigEntity {

    @Id
    @Column(length = 20)
    private String code;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "auto_kinds", columnDefinition = "JSON")
    private String autoKinds;

    protected ApprovalConfigEntity() {}

    public ApprovalConfigEntity(String code, boolean enabled, String autoKinds) {
        this.code = code;
        this.enabled = enabled;
        this.autoKinds = autoKinds;
    }

    public String getCode() { return code; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
    public String getAutoKinds() { return autoKinds; }
    public void setAutoKinds(String v) { autoKinds = v; }
}
