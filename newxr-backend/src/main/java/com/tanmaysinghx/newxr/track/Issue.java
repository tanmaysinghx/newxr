package com.tanmaysinghx.newxr.track;

import com.tanmaysinghx.newxr.common.BaseEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.tanmaysinghx.newxr.user.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "track_issues", indexes = {
        @Index(name = "ix_issue_project", columnList = "project_id,status"),
        @Index(name = "ix_issue_assignee", columnList = "assignee_id,status"),
        @Index(name = "ix_issue_sprint", columnList = "sprint_id")})
public class Issue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Project project;

    private int number;

    @Column(nullable = false, unique = true, length = 24)
    private String issueKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private IssueType type = IssueType.TASK;

    @Column(nullable = false, length = 255)
    private String summary;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IssueStatus status = IssueStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private IssuePriority priority = IssuePriority.MEDIUM;

    @ManyToOne(fetch = FetchType.LAZY)
    private User assignee;

    @ManyToOne(fetch = FetchType.LAZY)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    private Sprint sprint;

    /** Epic for stories/tasks/bugs; parent issue for sub-tasks. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Issue parent;

    private Integer storyPoints;

    /** Comma-separated, lower case. */
    @Column(length = 400)
    private String labels;

    @Column(length = 40)
    private String fixVersion;

    private LocalDate startDate;
    private LocalDate dueDate;

    /** Ordering in backlog and board columns (lower first). */
    private double rank;

    private Instant startedAt;
    private Instant resolvedAt;
    private Instant addedToSprintAt;

    @ElementCollection
    @CollectionTable(name = "track_issue_checklist", joinColumns = @JoinColumn(name = "issue_id"))
    @OrderColumn(name = "position")
    private List<ChecklistItem> checklist = new ArrayList<>();

    public List<String> labelList() {
        return labels == null || labels.isBlank() ? List.of() : Arrays.stream(labels.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public int points() {
        return storyPoints == null ? 0 : storyPoints;
    }
}
