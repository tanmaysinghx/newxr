package com.tanmaysinghx.newxr.track;

import com.tanmaysinghx.newxr.common.ApiException;
import com.tanmaysinghx.newxr.events.DomainEvent;
import com.tanmaysinghx.newxr.events.EventBus;
import com.tanmaysinghx.newxr.track.TrackDtos.*;
import com.tanmaysinghx.newxr.user.User;
import com.tanmaysinghx.newxr.user.UserRef;
import com.tanmaysinghx.newxr.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TrackService {

    private final ProjectRepository projects;
    private final SprintRepository sprints;
    private final IssueRepository issues;
    private final IssueCommentRepository comments;
    private final IssueHistoryRepository history;
    private final UserRepository users;
    private final EventBus events;

    public TrackService(ProjectRepository projects, SprintRepository sprints, IssueRepository issues,
                        IssueCommentRepository comments, IssueHistoryRepository history, UserRepository users, EventBus events) {
        this.projects = projects;
        this.sprints = sprints;
        this.issues = issues;
        this.comments = comments;
        this.history = history;
        this.users = users;
        this.events = events;
    }

    // ===================================================================== projects

    @Transactional(readOnly = true)
    public List<ProjectView> listProjects() {
        return projects.findByArchivedFalseOrderByNameAsc().stream().map(this::view).toList();
    }

    public Project project(String key) {
        return projects.findByProjectKeyIgnoreCase(key).orElseThrow(() -> ApiException.notFound("Project " + key));
    }

    @Transactional(readOnly = true)
    public ProjectView getProject(String key) {
        return view(project(key));
    }

    public ProjectView createProject(CreateProject r, User actor) {
        String key = r.key() == null ? "" : r.key().trim().toUpperCase(Locale.ROOT);
        if (!key.matches("[A-Z][A-Z0-9]{1,9}")) {
            throw ApiException.badRequest("Project key: 2–10 letters or digits, starting with a letter (e.g. ORB).");
        }
        if (r.name() == null || r.name().isBlank()) throw ApiException.badRequest("Enter a project name.");
        if (projects.existsByProjectKeyIgnoreCase(key)) throw ApiException.conflict("Project key " + key + " is already used.");
        Project p = new Project();
        p.setProjectKey(key);
        p.setName(r.name().trim());
        p.setDescription(r.description());
        p.setType(r.type() == null ? ProjectType.SOFTWARE : ProjectType.valueOf(r.type()));
        p.setLead(r.leadId() == null ? actor : users.findById(r.leadId()).orElse(actor));
        p.setColor(new String[]{"#5a4bf5", "#0e9f8e", "#e8892b", "#2f6fde", "#d9466f", "#2f9a57"}[Math.floorMod(key.hashCode(), 6)]);
        p = projects.save(p);
        Sprint s = new Sprint();
        s.setProject(p);
        s.setName(key + " Sprint 1");
        sprints.save(s);
        events.publish(DomainEvent.of("project.created", "track", "project", key, p.getName(), "/track/" + key + "/board",
                UserRef.of(actor), "created project " + p.getName(), List.of(), Map.of()));
        return view(p);
    }

    public ProjectView updateProject(String key, CreateProject r) {
        Project p = project(key);
        if (r.name() != null && !r.name().isBlank()) p.setName(r.name().trim());
        if (r.description() != null) p.setDescription(r.description());
        if (r.leadId() != null) p.setLead(users.findById(r.leadId()).orElse(p.getLead()));
        return view(p);
    }

    ProjectView view(Project p) {
        String active = sprints.findFirstByProjectAndState(p, SprintState.ACTIVE).map(Sprint::getName).orElse(null);
        return new ProjectView(p.getId(), p.getProjectKey(), p.getName(), p.getDescription(), p.getType().name(),
                UserRef.of(p.getLead()), p.getColor(), issues.countByProject(p), active);
    }

    // ===================================================================== sprints

    @Transactional(readOnly = true)
    public List<SprintView> listSprints(String key) {
        Project p = project(key);
        return sprints.findByProjectOrderByIdAsc(p).stream().map(this::view).toList();
    }

    public SprintView createSprint(String key, CreateSprint r) {
        Project p = project(key);
        Sprint s = new Sprint();
        s.setProject(p);
        s.setName(r.name() == null || r.name().isBlank() ? key + " Sprint " + (sprints.countByProject(p) + 1) : r.name().trim());
        s.setGoal(r.goal());
        s.setStartDate(r.startDate());
        s.setEndDate(r.endDate());
        return view(sprints.save(s));
    }

    public SprintView startSprint(Long id, StartSprint r, User actor) {
        Sprint s = sprint(id);
        if (s.getState() != SprintState.FUTURE) throw ApiException.badRequest("Only a future sprint can be started.");
        if (sprints.findFirstByProjectAndState(s.getProject(), SprintState.ACTIVE).isPresent()) {
            throw ApiException.conflict("Complete the active sprint before starting another.");
        }
        if (r != null) {
            if (r.name() != null && !r.name().isBlank()) s.setName(r.name().trim());
            if (r.goal() != null) s.setGoal(r.goal());
            if (r.startDate() != null) s.setStartDate(r.startDate());
            if (r.endDate() != null) s.setEndDate(r.endDate());
        }
        if (s.getStartDate() == null) s.setStartDate(LocalDate.now());
        if (s.getEndDate() == null) s.setEndDate(s.getStartDate().plusWeeks(2));
        List<Issue> list = issues.findBySprintOrderByRankAsc(s);
        Instant now = Instant.now();
        s.setState(SprintState.ACTIVE);
        s.setStartedAt(now);
        s.setCommittedPoints(list.stream().mapToInt(Issue::points).sum());
        s.setCommittedIssues(list.size());
        list.forEach(i -> i.setAddedToSprintAt(now));
        Set<Long> people = list.stream().map(Issue::getAssignee).filter(Objects::nonNull).map(User::getId).collect(Collectors.toSet());
        events.publish(DomainEvent.of("sprint.started", "track", "sprint", s.getProject().getProjectKey(), s.getName(),
                "/track/" + s.getProject().getProjectKey() + "/board", UserRef.of(actor), "started " + s.getName(),
                people, Map.of("notifyKind", "watching", "committedPoints", s.getCommittedPoints())));
        return view(s);
    }

    public SprintView completeSprint(Long id, CompleteSprint r, User actor) {
        Sprint s = sprint(id);
        if (s.getState() != SprintState.ACTIVE) throw ApiException.badRequest("Only the active sprint can be completed.");
        List<Issue> list = issues.findBySprintOrderByRankAsc(s);
        Sprint target = null;
        if (r != null && r.moveTo() != null && !"backlog".equals(r.moveTo())) {
            target = sprint(Long.valueOf(r.moveTo()));
            if (target.getState() != SprintState.FUTURE || !target.getProject().getId().equals(s.getProject().getId())) {
                throw ApiException.badRequest("Move unfinished issues to the backlog or a future sprint of this project.");
            }
        }
        int donePoints = 0, doneIssues = 0;
        for (Issue i : list) {
            if (i.getStatus() == IssueStatus.DONE) {
                donePoints += i.points();
                doneIssues++;
            } else {
                i.setSprint(target);
                i.setAddedToSprintAt(null);
            }
        }
        s.setState(SprintState.CLOSED);
        s.setCompletedAt(Instant.now());
        s.setCompletedPoints(donePoints);
        s.setCompletedIssues(doneIssues);
        events.publish(DomainEvent.of("sprint.completed", "track", "sprint", s.getProject().getProjectKey(), s.getName(),
                "/track/" + s.getProject().getProjectKey() + "/reports", UserRef.of(actor), "completed " + s.getName(),
                List.of(), Map.of("completedPoints", donePoints, "completedIssues", doneIssues)));
        // Keep a next sprint available, as the backlog always offers one.
        if (sprints.findByProjectAndStateOrderByIdAsc(s.getProject(), SprintState.FUTURE).isEmpty()) {
            Sprint next = new Sprint();
            next.setProject(s.getProject());
            next.setName(s.getProject().getProjectKey() + " Sprint " + (sprints.countByProject(s.getProject()) + 1));
            sprints.save(next);
        }
        return view(s);
    }

    public void deleteSprint(Long id) {
        Sprint s = sprint(id);
        if (s.getState() != SprintState.FUTURE) throw ApiException.badRequest("Only future sprints can be deleted.");
        issues.findBySprintOrderByRankAsc(s).forEach(i -> i.setSprint(null));
        sprints.delete(s);
    }

    Sprint sprint(Long id) {
        return sprints.findById(id).orElseThrow(() -> ApiException.notFound("Sprint"));
    }

    SprintView view(Sprint s) {
        List<Issue> list = issues.findBySprintOrderByRankAsc(s);
        int points = list.stream().mapToInt(Issue::points).sum();
        int done = list.stream().filter(i -> i.getStatus() == IssueStatus.DONE).mapToInt(Issue::points).sum();
        return new SprintView(s.getId(), s.getName(), s.getGoal(), s.getState().name(), s.getStartDate(), s.getEndDate(),
                list.size(), points, done, s.getCommittedPoints(), s.getCompletedPoints());
    }

    // ===================================================================== issues

    public Issue issue(String key) {
        return issues.findByIssueKeyIgnoreCase(key).orElseThrow(() -> ApiException.notFound("Issue " + key));
    }

    public IssueDetail createIssue(CreateIssue r, User actor) {
        if (r.summary() == null || r.summary().isBlank()) throw ApiException.badRequest("Enter a summary.");
        Project p = projects.lockById(project(r.projectKey()).getId()).orElseThrow();
        p.setIssueSeq(p.getIssueSeq() + 1);
        Issue i = new Issue();
        i.setProject(p);
        i.setNumber(p.getIssueSeq());
        i.setIssueKey(p.getProjectKey() + "-" + p.getIssueSeq());
        i.setType(r.type() == null ? IssueType.TASK : IssueType.valueOf(r.type()));
        i.setSummary(r.summary().trim());
        i.setDescription(r.description());
        i.setPriority(r.priority() == null ? IssuePriority.MEDIUM : IssuePriority.valueOf(r.priority()));
        i.setReporter(actor);
        i.setAssignee(r.assigneeId() == null ? null : users.findById(r.assigneeId()).orElse(null));
        i.setStoryPoints(r.points());
        i.setLabels(labels(r.labels()));
        i.setStartDate(r.startDate());
        i.setDueDate(r.dueDate());
        i.setRank(issues.maxRank(p) + 1000);
        if (r.parentKey() != null && !r.parentKey().isBlank()) i.setParent(issue(r.parentKey()));
        if (r.sprintId() != null) {
            Sprint s = sprint(r.sprintId());
            i.setSprint(s);
            if (s.getState() == SprintState.ACTIVE) i.setAddedToSprintAt(Instant.now());
        }
        if (r.checklist() != null) {
            r.checklist().stream().filter(t -> t != null && !t.isBlank()).forEach(t -> i.getChecklist().add(new ChecklistItem(t.trim(), false)));
        }
        Issue saved = issues.save(i);
        record(saved, actor, "created", null, saved.getIssueKey());
        events.publish(DomainEvent.of("issue.created", "track", "issue", saved.getIssueKey(), saved.getSummary(), link(saved),
                UserRef.of(actor), (saved.getAssignee() != null ? "assigned you " : "created ") + saved.getIssueKey() + " " + saved.getSummary(),
                saved.getAssignee() == null ? List.of() : List.of(saved.getAssignee().getId()), eventData(saved)));
        return detail(saved);
    }

    @Transactional(readOnly = true)
    public IssueDetail getIssue(String key) {
        return detail(issue(key));
    }

    public IssueDetail updateIssue(String key, UpdateIssue r, User actor) {
        Issue i = issue(key);
        List<String> clear = r.clear() == null ? List.of() : r.clear();
        User oldAssignee = i.getAssignee();
        if (r.summary() != null && !r.summary().isBlank() && !r.summary().equals(i.getSummary())) {
            record(i, actor, "summary", i.getSummary(), r.summary().trim());
            i.setSummary(r.summary().trim());
        }
        if (r.description() != null && !r.description().equals(i.getDescription())) {
            record(i, actor, "description", null, null);
            i.setDescription(r.description());
        }
        if (r.type() != null && !r.type().equals(i.getType().name())) {
            record(i, actor, "type", i.getType().name(), r.type());
            i.setType(IssueType.valueOf(r.type()));
        }
        if (r.priority() != null && !r.priority().equals(i.getPriority().name())) {
            record(i, actor, "priority", i.getPriority().name(), r.priority());
            i.setPriority(IssuePriority.valueOf(r.priority()));
        }
        if (r.status() != null && !r.status().equals(i.getStatus().name())) {
            transition(i, IssueStatus.valueOf(r.status()), actor);
        }
        if (clear.contains("assignee")) {
            if (i.getAssignee() != null) record(i, actor, "assignee", i.getAssignee().getDisplayName(), null);
            i.setAssignee(null);
        } else if (r.assigneeId() != null && (i.getAssignee() == null || !i.getAssignee().getId().equals(r.assigneeId()))) {
            User u = users.findById(r.assigneeId()).orElseThrow(() -> ApiException.notFound("User"));
            record(i, actor, "assignee", i.getAssignee() == null ? null : i.getAssignee().getDisplayName(), u.getDisplayName());
            i.setAssignee(u);
        }
        if (clear.contains("sprint")) {
            setSprint(i, null, actor);
        } else if (r.sprintId() != null && (i.getSprint() == null || !i.getSprint().getId().equals(r.sprintId()))) {
            setSprint(i, sprint(r.sprintId()), actor);
        }
        if (clear.contains("points")) {
            i.setStoryPoints(null);
        } else if (r.points() != null && !r.points().equals(i.getStoryPoints())) {
            record(i, actor, "story points", str(i.getStoryPoints()), str(r.points()));
            i.setStoryPoints(r.points());
        }
        if (r.labels() != null) i.setLabels(labels(r.labels()));
        if (clear.contains("parent")) {
            i.setParent(null);
        } else if (r.parentKey() != null && !r.parentKey().isBlank()) {
            Issue parent = issue(r.parentKey());
            if (parent.getId().equals(i.getId())) throw ApiException.badRequest("An issue cannot be its own parent.");
            i.setParent(parent);
        }
        if (clear.contains("fixVersion")) i.setFixVersion(null); else if (r.fixVersion() != null) i.setFixVersion(r.fixVersion().trim());
        if (clear.contains("startDate")) i.setStartDate(null); else if (r.startDate() != null) i.setStartDate(r.startDate());
        if (clear.contains("dueDate")) i.setDueDate(null); else if (r.dueDate() != null) i.setDueDate(r.dueDate());
        if (r.checklist() != null) {
            i.getChecklist().clear();
            r.checklist().stream().filter(c -> c.getText() != null && !c.getText().isBlank()).forEach(i.getChecklist()::add);
        }
        i.setUpdatedAt(Instant.now());
        if (i.getAssignee() != null && !Objects.equals(oldAssignee, i.getAssignee())) {
            events.publish(DomainEvent.of("issue.assigned", "track", "issue", i.getIssueKey(), i.getSummary(), link(i),
                    UserRef.of(actor), "assigned you " + i.getIssueKey() + " " + i.getSummary(), List.of(i.getAssignee().getId()), eventData(i)));
        } else {
            events.publish(DomainEvent.of("issue.updated", "track", "issue", i.getIssueKey(), i.getSummary(), link(i),
                    UserRef.of(actor), "updated " + i.getIssueKey(), List.of(), eventData(i)));
        }
        return detail(i);
    }

    /** Board and backlog drag and drop. */
    public IssueRow move(String key, MoveIssue r, User actor) {
        Issue i = issue(key);
        if (r.status() != null && !r.status().equals(i.getStatus().name())) {
            transition(i, IssueStatus.valueOf(r.status()), actor);
        }
        if (r.toBacklog()) {
            setSprint(i, null, actor);
        } else if (r.sprintId() != null && (i.getSprint() == null || !i.getSprint().getId().equals(r.sprintId()))) {
            setSprint(i, sprint(r.sprintId()), actor);
        }
        Double before = r.beforeKey() == null ? null : issues.findByIssueKeyIgnoreCase(r.beforeKey()).map(Issue::getRank).orElse(null);
        Double after = r.afterKey() == null ? null : issues.findByIssueKeyIgnoreCase(r.afterKey()).map(Issue::getRank).orElse(null);
        if (before != null && after != null) i.setRank((before + after) / 2);
        else if (before != null) i.setRank(before + 1000);
        else if (after != null) i.setRank(after - 1000);
        i.setUpdatedAt(Instant.now());
        return row(i);
    }

    public void deleteIssue(String key, User actor) {
        Issue i = issue(key);
        if (!actor.isAdmin() && (i.getReporter() == null || !i.getReporter().getId().equals(actor.getId()))) {
            throw ApiException.forbidden("Only the reporter or an administrator can delete an issue.");
        }
        issues.findByParentOrderByIdAsc(i).forEach(c -> c.setParent(null));
        comments.deleteAll(comments.findByIssueOrderByCreatedAtAsc(i));
        history.deleteAll(history.findByIssueOrderByCreatedAtDesc(i));
        issues.delete(i);
        events.publish(DomainEvent.of("issue.deleted", "track", "issue", key, i.getSummary(), null, UserRef.of(actor),
                "deleted " + key, List.of(), Map.of()));
    }

    private void transition(Issue i, IssueStatus to, User actor) {
        IssueStatus from = i.getStatus();
        record(i, actor, "status", from.name(), to.name());
        i.setStatus(to);
        Instant now = Instant.now();
        if (to != IssueStatus.TODO && i.getStartedAt() == null) i.setStartedAt(now);
        i.setResolvedAt(to == IssueStatus.DONE ? now : null);
        Set<Long> notify = new LinkedHashSet<>();
        if (i.getReporter() != null) notify.add(i.getReporter().getId());
        if (i.getAssignee() != null) notify.add(i.getAssignee().getId());
        Map<String, Object> data = new LinkedHashMap<>(eventData(i));
        data.put("from", from.name());
        data.put("notifyKind", "watching");
        events.publish(DomainEvent.of("issue.transitioned", "track", "issue", i.getIssueKey(), i.getSummary(), link(i),
                UserRef.of(actor), "moved " + i.getIssueKey() + " to " + to.name().replace('_', ' ').toLowerCase(Locale.ROOT), notify, data));
    }

    private void setSprint(Issue i, Sprint s, User actor) {
        record(i, actor, "sprint", i.getSprint() == null ? "Backlog" : i.getSprint().getName(), s == null ? "Backlog" : s.getName());
        i.setSprint(s);
        i.setAddedToSprintAt(s != null && s.getState() == SprintState.ACTIVE ? Instant.now() : null);
    }

    private void record(Issue i, User actor, String field, String from, String to) {
        if (i.getId() == null) return;
        IssueHistory h = new IssueHistory();
        h.setIssue(i);
        h.setActor(actor);
        h.setField(field);
        h.setFromValue(cut(from));
        h.setToValue(cut(to));
        history.save(h);
    }

    // ===================================================================== comments

    public CommentView addComment(String key, String body, User actor) {
        if (body == null || body.isBlank()) throw ApiException.badRequest("Write a comment first.");
        Issue i = issue(key);
        IssueComment c = new IssueComment();
        c.setIssue(i);
        c.setAuthor(actor);
        c.setBody(body.trim());
        comments.save(c);
        i.setUpdatedAt(Instant.now());
        Set<Long> notify = new LinkedHashSet<>();
        if (i.getAssignee() != null) notify.add(i.getAssignee().getId());
        if (i.getReporter() != null) notify.add(i.getReporter().getId());
        String kind = "watching";
        for (User u : mentioned(body)) {
            notify.add(u.getId());
            kind = "mention";
        }
        events.publish(DomainEvent.of("issue.commented", "track", "issue", i.getIssueKey(), i.getSummary(), link(i),
                UserRef.of(actor), (kind.equals("mention") ? "mentioned you in " : "commented on ") + i.getIssueKey() + " " + i.getSummary(),
                notify, Map.of("notifyKind", kind)));
        return new CommentView(c.getId(), UserRef.of(actor), c.getBody(), false, c.getCreatedAt());
    }

    public CommentView editComment(Long id, String body, User actor) {
        IssueComment c = comments.findById(id).orElseThrow(() -> ApiException.notFound("Comment"));
        if (c.getAuthor() == null || !c.getAuthor().getId().equals(actor.getId())) throw ApiException.forbidden("You can only edit your own comments.");
        if (body == null || body.isBlank()) throw ApiException.badRequest("A comment cannot be empty.");
        c.setBody(body.trim());
        c.setEdited(true);
        return new CommentView(c.getId(), UserRef.of(c.getAuthor()), c.getBody(), true, c.getCreatedAt());
    }

    public void deleteComment(Long id, User actor) {
        IssueComment c = comments.findById(id).orElseThrow(() -> ApiException.notFound("Comment"));
        if (!actor.isAdmin() && (c.getAuthor() == null || !c.getAuthor().getId().equals(actor.getId()))) {
            throw ApiException.forbidden("You can only delete your own comments.");
        }
        comments.delete(c);
    }

    /** "@Grace Lee" or "@grace.lee@northwind.dev" — matched against active users. */
    private List<User> mentioned(String body) {
        if (!body.contains("@")) return List.of();
        String lower = body.toLowerCase(Locale.ROOT);
        return users.findAllByOrderByDisplayNameAsc().stream().filter(User::isActive)
                .filter(u -> lower.contains("@" + u.getDisplayName().toLowerCase(Locale.ROOT)) || lower.contains("@" + u.getEmail().toLowerCase(Locale.ROOT)))
                .toList();
    }

    // ===================================================================== views

    @Transactional(readOnly = true)
    public BoardView board(String key, String q, Long assigneeId, String type, String label, String epic) {
        Project p = project(key);
        Sprint active = sprints.findFirstByProjectAndState(p, SprintState.ACTIVE).orElse(null);
        List<Issue> list = active != null ? issues.findBySprintOrderByRankAsc(active)
                : issues.findByProjectOrderByRankAsc(p).stream().filter(i -> i.getType() != IssueType.EPIC).toList();
        Predicate<Issue> f = filter(q, assigneeId, type, label, epic);
        Map<String, List<IssueRow>> cols = new LinkedHashMap<>();
        for (IssueStatus s : IssueStatus.values()) cols.put(s.name(), new ArrayList<>());
        list.stream().filter(f).filter(i -> i.getType() != IssueType.EPIC).forEach(i -> cols.get(i.getStatus().name()).add(row(i)));
        List<UserRef> people = list.stream().map(Issue::getAssignee).filter(Objects::nonNull).distinct().map(UserRef::of).toList();
        return new BoardView(view(p), active == null ? null : view(active), cols, people);
    }

    @Transactional(readOnly = true)
    public BacklogView backlog(String key, String q, Long assigneeId, String type, String epic) {
        Project p = project(key);
        Predicate<Issue> f = filter(q, assigneeId, type, null, epic);
        List<SprintBlock> blocks = new ArrayList<>();
        for (Sprint s : sprints.findByProjectOrderByIdAsc(p)) {
            if (s.getState() == SprintState.CLOSED) continue;
            blocks.add(new SprintBlock(view(s), issues.findBySprintOrderByRankAsc(s).stream().filter(f).map(this::row).toList()));
        }
        blocks.sort(Comparator.comparing(b -> b.sprint().state().equals("ACTIVE") ? 0 : 1));
        List<IssueRow> backlog = issues.findByProjectAndSprintIsNullAndTypeNotOrderByRankAsc(p, IssueType.EPIC).stream()
                .filter(i -> i.getStatus() != IssueStatus.DONE).filter(f).map(this::row).toList();
        List<IssueRow> epics = issues.findByProjectAndTypeOrderByStartDateAsc(p, IssueType.EPIC).stream().map(this::row).toList();
        return new BacklogView(view(p), blocks, backlog, epics);
    }

    @Transactional(readOnly = true)
    public List<IssueRow> list(String key, String q, Long assigneeId, String type, String status, String label, String epic, String sort) {
        Project p = project(key);
        Predicate<Issue> f = filter(q, assigneeId, type, label, epic);
        if (status != null && !status.isBlank()) {
            Set<String> st = Set.of(status.split(","));
            f = f.and(i -> st.contains(i.getStatus().name()));
        }
        Comparator<Issue> order = switch (sort == null ? "" : sort) {
            case "priority" -> Comparator.comparing(Issue::getPriority);
            case "key" -> Comparator.comparingInt(Issue::getNumber).reversed();
            case "status" -> Comparator.comparing(Issue::getStatus);
            default -> Comparator.comparing(Issue::getUpdatedAt).reversed();
        };
        return issues.findByProjectOrderByRankAsc(p).stream().filter(f).sorted(order).map(this::row).toList();
    }

    @Transactional(readOnly = true)
    public List<EpicView> timeline(String key) {
        Project p = project(key);
        return issues.findByProjectAndTypeOrderByStartDateAsc(p, IssueType.EPIC).stream().map(e -> {
            List<Issue> kids = issues.findByParentOrderByIdAsc(e);
            int done = (int) kids.stream().filter(k -> k.getStatus() == IssueStatus.DONE).count();
            return new EpicView(e.getIssueKey(), e.getSummary(), e.getStatus().name(), e.getStartDate(), e.getDueDate(),
                    kids.size(), done, UserRef.of(e.getAssignee()));
        }).toList();
    }

    @Transactional(readOnly = true)
    public Report report(String key, Long sprintId) {
        Project p = project(key);
        List<Sprint> all = sprints.findByProjectOrderByIdAsc(p);
        Sprint s = sprintId != null ? sprint(sprintId)
                : all.stream().filter(x -> x.getState() == SprintState.ACTIVE).findFirst()
                .orElse(all.stream().filter(x -> x.getState() == SprintState.CLOSED).reduce((a, b) -> b).orElse(null));
        List<SprintView> sprintViews = all.stream().filter(x -> x.getState() != SprintState.FUTURE).map(this::view).toList();
        List<VelocityBar> velocity = all.stream().filter(x -> x.getState() != SprintState.FUTURE).map(x -> {
            List<Issue> li = issues.findBySprintOrderByRankAsc(x);
            int committed = x.getCommittedPoints() != null ? x.getCommittedPoints() : li.stream().mapToInt(Issue::points).sum();
            int completed = x.getCompletedPoints() != null ? x.getCompletedPoints()
                    : li.stream().filter(i -> i.getStatus() == IssueStatus.DONE).mapToInt(Issue::points).sum();
            return new VelocityBar(x.getName().replaceAll("^.*?(\\d+)$", "S$1"), committed, completed);
        }).toList();
        velocity = velocity.subList(Math.max(0, velocity.size() - 6), velocity.size());
        if (s == null) {
            return new Report(null, sprintViews, 0, 0, 0, 0, 0, null, null, List.of(), velocity, Map.of(), List.of());
        }
        List<Issue> li = issues.findBySprintOrderByRankAsc(s);
        int committed = s.getCommittedPoints() != null ? s.getCommittedPoints() : li.stream().mapToInt(Issue::points).sum();
        Instant started = s.getStartedAt() != null ? s.getStartedAt() : Instant.now();
        List<Issue> added = li.stream().filter(i -> i.getAddedToSprintAt() != null && i.getAddedToSprintAt().isAfter(started.plusSeconds(60))).toList();
        int addedPoints = added.stream().mapToInt(Issue::points).sum();
        int completed = li.stream().filter(i -> i.getStatus() == IssueStatus.DONE).mapToInt(Issue::points).sum();
        Map<String, Integer> statusCounts = new LinkedHashMap<>();
        for (IssueStatus st : IssueStatus.values()) statusCounts.put(st.name(), (int) li.stream().filter(i -> i.getStatus() == st).count());
        Map<User, int[]> load = new LinkedHashMap<>();
        li.stream().filter(i -> i.getAssignee() != null).forEach(i -> {
            int[] v = load.computeIfAbsent(i.getAssignee(), k -> new int[2]);
            v[0] += i.points();
            v[1]++;
        });
        List<Workload> workload = load.entrySet().stream().map(e -> new Workload(UserRef.of(e.getKey()), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingInt(Workload::points).reversed()).toList();

        // Burndown: remaining points at the end of each sprint day, from resolution times.
        List<Point> burn = new ArrayList<>();
        LocalDate start = s.getStartDate() != null ? s.getStartDate() : LocalDate.now();
        LocalDate end = s.getEndDate() != null ? s.getEndDate() : start.plusWeeks(2);
        long days = Math.max(1, Duration.between(start.atStartOfDay(), end.atStartOfDay()).toDays());
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
        int total = committed + addedPoints;
        for (int d = 0; d <= days; d++) {
            LocalDate day = start.plusDays(d);
            Instant endOfDay = day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            double guide = committed - (double) committed * d / days;
            Double remaining = null;
            if (!day.isAfter(today)) {
                int scope = committed + added.stream().filter(i -> i.getAddedToSprintAt().isBefore(endOfDay)).mapToInt(Issue::points).sum();
                int doneBy = li.stream().filter(i -> i.getResolvedAt() != null && i.getResolvedAt().isBefore(endOfDay)).mapToInt(Issue::points).sum();
                remaining = (double) Math.max(0, scope - doneBy);
            }
            burn.add(new Point(day.format(fmt), remaining == null ? -1 : remaining, guide));
        }
        Double cycle = avgCycleDays(li);
        Sprint prev = all.stream().filter(x -> x.getState() == SprintState.CLOSED && x.getId() < s.getId()).reduce((a, b) -> b).orElse(null);
        Double prevCycle = prev == null ? null : avgCycleDays(issues.findBySprintOrderByRankAsc(prev));
        return new Report(view(s), sprintViews, committed, completed, s.getCommittedIssues() == null ? li.size() : s.getCommittedIssues(),
                addedPoints, added.size(), cycle, prevCycle, burn, velocity, statusCounts, workload);
    }

    private static Double avgCycleDays(List<Issue> li) {
        var done = li.stream().filter(i -> i.getResolvedAt() != null && i.getStartedAt() != null).toList();
        if (done.isEmpty()) return null;
        double avg = done.stream().mapToLong(i -> Duration.between(i.getStartedAt(), i.getResolvedAt()).toMinutes()).average().orElse(0);
        return Math.round(avg / 1440 * 10) / 10.0;
    }

    private Predicate<Issue> filter(String q, Long assigneeId, String type, String label, String epic) {
        Predicate<Issue> f = i -> true;
        if (q != null && !q.isBlank()) {
            String ql = q.trim().toLowerCase(Locale.ROOT);
            f = f.and(i -> i.getSummary().toLowerCase(Locale.ROOT).contains(ql) || i.getIssueKey().toLowerCase(Locale.ROOT).equals(ql));
        }
        if (assigneeId != null) {
            f = assigneeId == 0 ? f.and(i -> i.getAssignee() == null) : f.and(i -> i.getAssignee() != null && i.getAssignee().getId().equals(assigneeId));
        }
        if (type != null && !type.isBlank()) f = f.and(i -> i.getType().name().equals(type));
        if (label != null && !label.isBlank()) f = f.and(i -> i.labelList().contains(label.toLowerCase(Locale.ROOT)));
        if (epic != null && !epic.isBlank()) f = f.and(i -> i.getParent() != null && i.getParent().getIssueKey().equalsIgnoreCase(epic));
        return f;
    }

    IssueRow row(Issue i) {
        Issue epic = i.getParent() != null && i.getParent().getType() == IssueType.EPIC ? i.getParent() : null;
        List<Issue> kids = i.getType() == IssueType.SUBTASK ? List.of() : issues.findByParentOrderByIdAsc(i);
        int kidsDone = (int) kids.stream().filter(k -> k.getStatus() == IssueStatus.DONE).count();
        return new IssueRow(i.getId(), i.getIssueKey(), i.getType().name(), i.getSummary(), i.getStatus().name(), i.getPriority().name(),
                UserRef.of(i.getAssignee()), i.getStoryPoints(), i.labelList(), i.getSprint() == null ? null : i.getSprint().getId(),
                epic == null ? null : epic.getIssueKey(), epic == null ? null : epic.getSummary(), i.getRank(), i.getUpdatedAt(),
                i.getDueDate(), kids.size(), kidsDone);
    }

    IssueDetail detail(Issue i) {
        List<Child> children = issues.findByParentOrderByIdAsc(i).stream()
                .map(c -> new Child(c.getIssueKey(), c.getType().name(), c.getSummary(), c.getStatus().name(), UserRef.of(c.getAssignee()))).toList();
        List<CommentView> cs = comments.findByIssueOrderByCreatedAtAsc(i).stream()
                .map(c -> new CommentView(c.getId(), UserRef.of(c.getAuthor()), c.getBody(), c.isEdited(), c.getCreatedAt())).toList();
        List<HistoryView> hs = history.findByIssueOrderByCreatedAtDesc(i).stream()
                .map(h -> new HistoryView(UserRef.of(h.getActor()), h.getField(), h.getFromValue(), h.getToValue(), h.getCreatedAt())).toList();
        Issue parent = i.getParent();
        return new IssueDetail(i.getId(), i.getIssueKey(), i.getType().name(), i.getSummary(), i.getDescription(), i.getStatus().name(),
                i.getPriority().name(), UserRef.of(i.getAssignee()), UserRef.of(i.getReporter()), i.getStoryPoints(), i.labelList(),
                i.getSprint() == null ? null : i.getSprint().getId(), i.getSprint() == null ? null : i.getSprint().getName(), i.getFixVersion(),
                i.getStartDate(), i.getDueDate(), parent == null ? null : parent.getIssueKey(), parent == null ? null : parent.getSummary(),
                parent == null ? null : parent.getType().name(), List.copyOf(i.getChecklist()), children, cs, hs, view(i.getProject()),
                i.getCreatedAt(), i.getUpdatedAt());
    }

    static String link(Issue i) {
        return "/track/issue/" + i.getIssueKey();
    }

    private static Map<String, Object> eventData(Issue i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("project", i.getProject().getProjectKey());
        m.put("type", i.getType().name());
        m.put("status", i.getStatus().name());
        m.put("priority", i.getPriority().name());
        m.put("assignee", i.getAssignee() == null ? null : i.getAssignee().getEmail());
        m.put("points", i.getStoryPoints());
        return m;
    }

    private static String labels(List<String> labels) {
        if (labels == null) return null;
        String s = labels.stream().filter(Objects::nonNull).map(l -> l.trim().toLowerCase(Locale.ROOT).replace(",", ""))
                .filter(l -> !l.isEmpty()).distinct().collect(Collectors.joining(","));
        return s.isEmpty() ? null : s;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String cut(String s) {
        return s == null ? null : (s.length() > 300 ? s.substring(0, 299) + "…" : s);
    }
}
