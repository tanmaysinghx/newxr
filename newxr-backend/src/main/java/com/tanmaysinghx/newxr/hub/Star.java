package com.tanmaysinghx.newxr.hub;

import com.tanmaysinghx.newxr.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "hub_stars", uniqueConstraints = @UniqueConstraint(name = "uq_star_user_link", columnNames = {"userId", "link"}))
public class Star extends BaseEntity {
    @Column(nullable = false) private Long userId;
    @Column(length = 16) private String app;
    @Column(length = 32) private String kind;
    @Column(length = 300) private String title;
    @Column(nullable = false, length = 300) private String link;
}
