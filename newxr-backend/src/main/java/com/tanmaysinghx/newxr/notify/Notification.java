package com.tanmaysinghx.newxr.notify;

import com.tanmaysinghx.newxr.common.BaseEntity;
import com.tanmaysinghx.newxr.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "notifications", indexes = @Index(name = "ix_notifications_recipient", columnList = "recipientId,createdAt"))
public class Notification extends BaseEntity {

    @Column(nullable = false)
    private Long recipientId;

    @ManyToOne(fetch = FetchType.LAZY)
    private User actor;

    @Column(length = 16)
    private String app;

    /** direct, watching or mention. */
    @Column(length = 16)
    private String kind = "direct";

    @Column(length = 500)
    private String text;

    @Column(length = 400)
    private String link;

    private boolean readFlag;
}
