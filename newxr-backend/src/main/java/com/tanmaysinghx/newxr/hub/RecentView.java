package com.tanmaysinghx.newxr.hub;

import com.tanmaysinghx.newxr.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "hub_recent_views",
        uniqueConstraints = @UniqueConstraint(name = "uq_recent_user_link", columnNames = {"userId", "link"}),
        indexes = @Index(name = "ix_recent_user", columnList = "userId,viewedAt"))
public class RecentView extends BaseEntity {
    @Column(nullable = false) private Long userId;
    @Column(length = 16) private String app;
    @Column(length = 32) private String kind;
    @Column(length = 64) private String itemKey;
    @Column(length = 300) private String title;
    @Column(length = 200) private String subtitle;
    @Column(nullable = false, length = 300) private String link;
    private Instant viewedAt;
}
