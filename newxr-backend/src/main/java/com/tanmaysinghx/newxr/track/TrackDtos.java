package com.tanmaysinghx.newxr.track;

import com.tanmaysinghx.newxr.user.UserRef;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Request and response shapes for the Track API. */
public final class TrackDtos {

    private TrackDtos() {}

    public record ProjectView(Long id, String key, String name, String description, String type, UserRef lead,
                              String color, long issueCount, String activeSprint) {}

    public record CreateProject(String key, String name, String description, String type, Long leadId) {}

    public record SprintView(Long id, String name, String goal, String state, LocalDate startDate, LocalDate endDate,
                             int issueCount, int points, int donePoints, Integer committedPoints, Integer completedPoints) {}

    public record CreateSprint(String name, String goal, LocalDate startDate, LocalDate endDate) {}

    public record StartSprint(String name, String goal, LocalDate startDate, LocalDate endDate) {}

    /** {@code moveTo}: "backlog" or a future sprint id for unfinished issues. */
    public record CompleteSprint(String moveTo) {}

    public record IssueRow(Long id, String key, String type, String summary, String status, String priority,
                           UserRef assignee, Integer points, List<String> labels, Long sprintId, String epicKey,
                           String epicSummary, double rank, Instant updatedAt, LocalDate dueDate, int childCount, int childDone) {}

    public record Child(String key, String type, String summary, String status, UserRef assignee) {}

    public record CommentView(Long id, UserRef author, String body, boolean edited, Instant createdAt) {}

    public record HistoryView(UserRef actor, String field, String from, String to, Instant at) {}

    public record IssueDetail(Long id, String key, String type, String summary, String description, String status,
                              String priority, UserRef assignee, UserRef reporter, Integer points, List<String> labels,
                              Long sprintId, String sprintName, String fixVersion, LocalDate startDate, LocalDate dueDate,
                              String parentKey, String parentSummary, String parentType, List<ChecklistItem> checklist,
                              List<Child> children, List<CommentView> comments, List<HistoryView> history,
                              ProjectView project, Instant createdAt, Instant updatedAt) {}

    public record CreateIssue(String projectKey, String type, String summary, String description, String priority,
                              Long assigneeId, Long sprintId, Integer points, List<String> labels, String parentKey,
                              LocalDate startDate, LocalDate dueDate, List<String> checklist) {}

    /**
     * Partial update: only non-null fields change. Use {@code clear} to null out assignee, sprint,
     * parent, points, dueDate, startDate or fixVersion.
     */
    public record UpdateIssue(String summary, String description, String type, String status, String priority,
                              Long assigneeId, Long sprintId, Integer points, List<String> labels, String parentKey,
                              String fixVersion, LocalDate startDate, LocalDate dueDate, List<ChecklistItem> checklist,
                              List<String> clear) {}

    /** Drag and drop: new status and/or sprint, placed between two neighbours (by key). */
    public record MoveIssue(String status, Long sprintId, boolean toBacklog, String beforeKey, String afterKey) {}

    public record CommentRequest(String body) {}

    public record BoardView(ProjectView project, SprintView sprint, Map<String, List<IssueRow>> columns, List<UserRef> people) {}

    public record BacklogView(ProjectView project, List<SprintBlock> sprints, List<IssueRow> backlog, List<IssueRow> epics) {}

    public record SprintBlock(SprintView sprint, List<IssueRow> issues) {}

    public record EpicView(String key, String summary, String status, LocalDate startDate, LocalDate dueDate,
                           int childCount, int childDone, UserRef assignee) {}

    public record Point(String label, double value, Double guide) {}

    public record VelocityBar(String sprint, int committed, int completed) {}

    public record Workload(UserRef user, int points, int issues) {}

    public record Report(SprintView sprint, List<SprintView> sprints, int committedPoints, int completedPoints,
                         int committedIssues, int addedPoints, int addedIssues, Double avgCycleDays, Double prevAvgCycleDays,
                         List<Point> burndown, List<VelocityBar> velocity, Map<String, Integer> statusCounts,
                         List<Workload> workload) {}
}
