package com.tanmaysinghx.newxr.track;

import com.tanmaysinghx.newxr.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "track_sprints")
public class Sprint extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Project project;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 500)
    private String goal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private SprintState state = SprintState.FUTURE;

    private LocalDate startDate;
    private LocalDate endDate;
    private Instant startedAt;
    private Instant completedAt;

    /** Snapshots for reports: points when started, and points done when completed. */
    private Integer committedPoints;
    private Integer completedPoints;
    private Integer committedIssues;
    private Integer completedIssues;
}
