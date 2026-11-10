package com.tanmaysinghx.newxr.goals;

import com.tanmaysinghx.newxr.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Contributing work: a Track issue, Pages page, Pulse alert or Boards card. */
@Getter
@Setter
@Entity
@Table(name = "goals_links")
public class GoalLink extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Goal goal;
    @Column(nullable = false, length = 12) private String app;
    @Column(nullable = false, length = 300) private String title;
    @Column(nullable = false, length = 300) private String link;
    @Column(length = 24) private String status;
}
