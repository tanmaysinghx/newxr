package com.tanmaysinghx.newxr.admin;

import com.tanmaysinghx.newxr.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One line of the audit log: sign-ins and administrative changes. Kept even if the actor is deleted. */
@Getter
@Setter
@Entity
@Table(name = "audit_events", indexes = @Index(name = "ix_audit_created", columnList = "createdAt"))
public class AuditEvent extends BaseEntity {
    private Long actorId;
    @Column(length = 120) private String actorName;
    @Column(length = 254) private String actorEmail;
    /** Dotted action, e.g. {@code user.invited}, {@code signin.failed}, {@code settings.sign-in}. */
    @Column(nullable = false, length = 60) private String action;
    @Column(length = 200) private String target;
    @Column(length = 500) private String detail;
    @Column(length = 64) private String ip;
}
