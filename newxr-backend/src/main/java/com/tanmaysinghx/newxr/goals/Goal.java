package com.tanmaysinghx.newxr.goals;

import com.tanmaysinghx.newxr.common.BaseEntity;
import com.tanmaysinghx.newxr.user.User;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "goals_goals")
public class Goal extends BaseEntity {
    @Column(nullable = false, length = 200) private String name;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) private String description;
    @ManyToOne(fetch = FetchType.LAZY) private User owner;
    @ManyToOne(fetch = FetchType.LAZY) private Goal parent;
    @Column(length = 80) private String team;
    /** ON_TRACK, AT_RISK, OFF_TRACK, COMPLETED, PAUSED, PENDING. */
    @Column(nullable = false, length = 12) private String status = "PENDING";
    private int progress;
    private LocalDate targetDate;
    private LocalDate startDate;
    /** Optional measurable target, e.g. "Crash rate" in "%", from 0.44 to 0.2. */
    @Column(length = 80) private String metricName;
    @Column(length = 12) private String metricUnit;
    private Double metricStart;
    private Double metricTarget;
    private Double metricCurrent;
    @ManyToMany
    @JoinTable(name = "goals_followers", joinColumns = @JoinColumn(name = "goal_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> followers = new LinkedHashSet<>();
    private boolean archived;
}
