package com.tanmaysinghx.newxr.goals;

import com.tanmaysinghx.newxr.common.BaseEntity;
import com.tanmaysinghx.newxr.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "goals_updates")
public class GoalUpdate extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Goal goal;
    @ManyToOne(fetch = FetchType.LAZY) private User author;
    @Column(nullable = false, length = 12) private String status;
    private Integer progress;
    private Double metricValue;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) @Column(nullable = false) private String body;
    private int cheers;
}
