package com.tanmaysinghx.newxr.goals;

import com.tanmaysinghx.newxr.common.ApiException;
import com.tanmaysinghx.newxr.events.DomainEvent;
import com.tanmaysinghx.newxr.events.EventBus;
import com.tanmaysinghx.newxr.hub.HubContributor;
import com.tanmaysinghx.newxr.hub.WorkItem;
import com.tanmaysinghx.newxr.security.CurrentUser;
import com.tanmaysinghx.newxr.user.User;
import com.tanmaysinghx.newxr.user.UserRef;
import com.tanmaysinghx.newxr.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Goals with weekly updates, metrics and contributing work. */
@RestController
@RequestMapping("/api/goals")
@Transactional
public class GoalsService implements HubContributor {

    static final Set<String> STATUSES = Set.of("ON_TRACK", "AT_RISK", "OFF_TRACK", "COMPLETED", "PAUSED", "PENDING");

    public record GoalRow(Long id, String name, String status, int progress, UserRef owner, String team, LocalDate targetDate,
                          Long parentId, List<GoalRow> children, Instant lastUpdateAt) {}

    public record LinkView(Long id, String app, String title, String link, String status) {}

    public record UpdateView(Long id, UserRef author, String status, Integer progress, Double metricValue, String body, int cheers, Instant createdAt,
                             Long goalId, String goalName) {}

    public record MetricPoint(Instant at, Double actual, Double plan) {}

    public record GoalView(Long id, String name, String description, String status, int progress, UserRef owner, String team,
                           LocalDate startDate, LocalDate targetDate, Long parentId, String parentName, String metricName, String metricUnit,
                           Double metricStart, Double metricTarget, Double metricCurrent, List<UserRef> followers, boolean following,
                           List<GoalRow> children, List<UpdateView> updates, List<LinkView> links, List<MetricPoint> series) {}

    public record GoalRequest(String name, String description, Long ownerId, String team, Long parentId, LocalDate startDate,
                              LocalDate targetDate, String metricName, String metricUnit, Double metricStart, Double metricTarget, String status) {}

    public record PostUpdate(String status, Integer progress, Double metricValue, String body) {}

    public record LinkRequest(String app, String title, String link, String status) {}

    private final GoalRepository goals;
    private final GoalUpdateRepository updates;
    private final GoalLinkRepository links;
    private final UserRepository users;
    private final CurrentUser current;
    private final EventBus events;

    public GoalsService(GoalRepository goals, GoalUpdateRepository updates, GoalLinkRepository links, UserRepository users,
                        CurrentUser current, EventBus events) {
        this.goals = goals;
        this.updates = updates;
        this.links = links;
        this.users = users;
        this.current = current;
        this.events = events;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<GoalRow> tree(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                              @RequestParam(required = false) Long owner) {
        List<Goal> all = goals.findByArchivedFalseOrderByIdAsc();
        boolean filtered = (q != null && !q.isBlank()) || (status != null && !status.isBlank()) || owner != null;
        if (filtered) {
            return all.stream()
                    .filter(g -> q == null || q.isBlank() || g.getName().toLowerCase().contains(q.toLowerCase()))
                    .filter(g -> status == null || status.isBlank() || g.getStatus().equals(status))
                    .filter(g -> owner == null || (g.getOwner() != null && g.getOwner().getId().equals(owner)))
                    .map(g -> row(g, List.of())).toList();
        }
        return all.stream().filter(g -> g.getParent() == null).map(g -> row(g, children(g, 0))).toList();
    }

    private List<GoalRow> children(Goal g, int depth) {
        if (depth > 6) return List.of();
        return goals.findByParentOrderByIdAsc(g).stream().filter(c -> !c.isArchived()).map(c -> row(c, children(c, depth + 1))).toList();
    }

    private GoalRow row(Goal g, List<GoalRow> kids) {
        Instant last = updates.findByGoalOrderByCreatedAtDesc(g).stream().findFirst().map(GoalUpdate::getCreatedAt).orElse(null);
        return new GoalRow(g.getId(), g.getName(), g.getStatus(), g.getProgress(), UserRef.of(g.getOwner()), g.getTeam(), g.getTargetDate(),
                g.getParent() == null ? null : g.getParent().getId(), kids, last);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalView create(@RequestBody GoalRequest r) {
        User me = current.require();
        if (r.name() == null || r.name().isBlank()) throw ApiException.badRequest("Name the goal.");
        Goal g = new Goal();
        apply(g, r);
        if (g.getOwner() == null) g.setOwner(me);
        g.getFollowers().add(me);
        g = goals.save(g);
        events.publish(DomainEvent.of("goal.created", "goals", "goal", String.valueOf(g.getId()), g.getName(), link(g), UserRef.of(me),
                "created goal " + g.getName(), g.getOwner() == null ? List.of() : List.of(g.getOwner().getId()), Map.of()));
        return view(g, me);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public GoalView get(@PathVariable Long id) {
        return view(goal(id), current.require());
    }

    @PutMapping("/{id}")
    public GoalView update(@PathVariable Long id, @RequestBody GoalRequest r) {
        Goal g = goal(id);
        apply(g, r);
        return view(g, current.require());
    }

    @DeleteMapping("/{id}")
    public void archive(@PathVariable Long id) {
        goal(id).setArchived(true);
    }

    @PostMapping("/{id}/updates")
    public GoalView postUpdate(@PathVariable Long id, @RequestBody PostUpdate r) {
        User me = current.require();
        Goal g = goal(id);
        if (r.body() == null || r.body().isBlank()) throw ApiException.badRequest("Write what changed this week.");
        String status = r.status() == null ? g.getStatus() : r.status();
        if (!STATUSES.contains(status)) throw ApiException.badRequest("Unknown status " + status);
        GoalUpdate u = new GoalUpdate();
        u.setGoal(g);
        u.setAuthor(me);
        u.setStatus(status);
        u.setProgress(r.progress());
        u.setMetricValue(r.metricValue());
        u.setBody(r.body().trim());
        updates.save(u);
        g.setStatus(status);
        if (r.progress() != null) g.setProgress(Math.max(0, Math.min(100, r.progress())));
        if (r.metricValue() != null) {
            g.setMetricCurrent(r.metricValue());
            if (r.progress() == null && g.getMetricStart() != null && g.getMetricTarget() != null && !g.getMetricStart().equals(g.getMetricTarget())) {
                double pct = (g.getMetricStart() - r.metricValue()) / (g.getMetricStart() - g.getMetricTarget()) * 100;
                g.setProgress((int) Math.round(Math.max(0, Math.min(100, pct))));
            }
        }
        if ("COMPLETED".equals(status)) g.setProgress(100);
        g.setUpdatedAt(Instant.now());
        events.publish(DomainEvent.of("goal.updated", "goals", "goal", String.valueOf(g.getId()), g.getName(), link(g), UserRef.of(me),
                "posted an update on " + g.getName(), g.getFollowers().stream().map(User::getId).toList(),
                Map.of("status", status, "progress", g.getProgress(), "notifyKind", "watching")));
        return view(g, me);
    }

    @PostMapping("/updates/{updateId}/cheer")
    public Map<String, Integer> cheer(@PathVariable Long updateId) {
        GoalUpdate u = updates.findById(updateId).orElseThrow(() -> ApiException.notFound("Update"));
        u.setCheers(u.getCheers() + 1);
        return Map.of("cheers", u.getCheers());
    }

    @PostMapping("/{id}/follow")
    public GoalView follow(@PathVariable Long id) {
        User me = current.require();
        Goal g = goal(id);
        if (!g.getFollowers().removeIf(u -> u.getId().equals(me.getId()))) g.getFollowers().add(me);
        return view(g, me);
    }

    @PostMapping("/{id}/links")
    public GoalView addLink(@PathVariable Long id, @RequestBody LinkRequest r) {
        Goal g = goal(id);
        if (r.link() == null || !r.link().startsWith("/") || r.title() == null || r.title().isBlank()) {
            throw ApiException.badRequest("A link needs a title and a Newxr path such as /track/issue/ORB-1.");
        }
        GoalLink l = new GoalLink();
        l.setGoal(g);
        l.setApp(r.app() == null ? "track" : r.app());
        l.setTitle(r.title().trim());
        l.setLink(r.link().trim());
        l.setStatus(r.status());
        links.save(l);
        return view(g, current.require());
    }

    @DeleteMapping("/links/{linkId}")
    public void removeLink(@PathVariable Long linkId) {
        links.deleteById(linkId);
    }

    @GetMapping("/updates")
    @Transactional(readOnly = true)
    public List<UpdateView> feed() {
        return updates.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 50)).stream().map(GoalsService::view).toList();
    }

    // ---- helpers ------------------------------------------------------------------

    private void apply(Goal g, GoalRequest r) {
        if (r.name() != null && !r.name().isBlank()) g.setName(r.name().trim());
        if (r.description() != null) g.setDescription(r.description());
        if (r.ownerId() != null) g.setOwner(users.findById(r.ownerId()).orElseThrow(() -> ApiException.notFound("User")));
        if (r.team() != null) g.setTeam(r.team().isBlank() ? null : r.team().trim());
        if (r.parentId() != null) {
            Goal parent = goal(r.parentId());
            for (Goal a = parent; a != null; a = a.getParent()) {
                if (a == g) throw ApiException.badRequest("A goal cannot be its own sub-goal.");
            }
            g.setParent(parent);
        }
        if (r.startDate() != null) g.setStartDate(r.startDate());
        if (r.targetDate() != null) g.setTargetDate(r.targetDate());
        if (r.metricName() != null) g.setMetricName(r.metricName().isBlank() ? null : r.metricName().trim());
        if (r.metricUnit() != null) g.setMetricUnit(r.metricUnit());
        if (r.metricStart() != null) {
            g.setMetricStart(r.metricStart());
            if (g.getMetricCurrent() == null) g.setMetricCurrent(r.metricStart());
        }
        if (r.metricTarget() != null) g.setMetricTarget(r.metricTarget());
        if (r.status() != null && STATUSES.contains(r.status())) g.setStatus(r.status());
    }

    Goal goal(Long id) {
        return goals.findById(id).orElseThrow(() -> ApiException.notFound("Goal"));
    }

    private GoalView view(Goal g, User me) {
        List<GoalUpdate> us = updates.findByGoalOrderByCreatedAtDesc(g);
        List<MetricPoint> series = new ArrayList<>();
        if (g.getMetricName() != null && g.getMetricStart() != null && g.getMetricTarget() != null) {
            Instant start = g.getStartDate() != null ? g.getStartDate().atStartOfDay().toInstant(java.time.ZoneOffset.UTC) : g.getCreatedAt();
            Instant end = g.getTargetDate() != null ? g.getTargetDate().atStartOfDay().toInstant(java.time.ZoneOffset.UTC) : start.plus(Duration.ofDays(90));
            double span = Math.max(1, Duration.between(start, end).toSeconds());
            series.add(new MetricPoint(start, g.getMetricStart(), g.getMetricStart()));
            for (GoalUpdate u : us.reversed()) {
                if (u.getMetricValue() == null) continue;
                double f = Math.min(1, Math.max(0, Duration.between(start, u.getCreatedAt()).toSeconds() / span));
                series.add(new MetricPoint(u.getCreatedAt(), u.getMetricValue(), g.getMetricStart() + (g.getMetricTarget() - g.getMetricStart()) * f));
            }
            series.add(new MetricPoint(end, null, g.getMetricTarget()));
        }
        return new GoalView(g.getId(), g.getName(), g.getDescription(), g.getStatus(), g.getProgress(), UserRef.of(g.getOwner()), g.getTeam(),
                g.getStartDate(), g.getTargetDate(), g.getParent() == null ? null : g.getParent().getId(),
                g.getParent() == null ? null : g.getParent().getName(), g.getMetricName(), g.getMetricUnit(), g.getMetricStart(), g.getMetricTarget(),
                g.getMetricCurrent(), g.getFollowers().stream().map(UserRef::of).toList(),
                g.getFollowers().stream().anyMatch(u -> u.getId().equals(me.getId())), children(g, 0),
                us.stream().map(GoalsService::view).toList(),
                links.findByGoalOrderByIdAsc(g).stream().map(l -> new LinkView(l.getId(), l.getApp(), l.getTitle(), l.getLink(), l.getStatus())).toList(),
                series);
    }

    private static UpdateView view(GoalUpdate u) {
        return new UpdateView(u.getId(), UserRef.of(u.getAuthor()), u.getStatus(), u.getProgress(), u.getMetricValue(), u.getBody(), u.getCheers(),
                u.getCreatedAt(), u.getGoal().getId(), u.getGoal().getName());
    }

    static String link(Goal g) {
        return "/goals/" + g.getId();
    }

    // ---- hub ----------------------------------------------------------------------

    @Override
    public String app() {
        return "goals";
    }

    @Override
    public List<WorkItem> waitingOn(User user) {
        Instant weekAgo = Instant.now().minus(Duration.ofDays(7));
        return goals.findByOwnerAndArchivedFalse(user).stream()
                .filter(g -> !"COMPLETED".equals(g.getStatus()))
                .filter(g -> updates.findByGoalOrderByCreatedAtDesc(g).stream().findFirst().map(u -> u.getCreatedAt().isBefore(weekAgo)).orElse(true))
                .map(g -> WorkItem.of("goals", "goal", null, "Post a weekly update: " + g.getName(), "Goals · no update this week",
                        g.getStatus(), null, link(g), g.getUpdatedAt()))
                .toList();
    }

    @Override
    public List<WorkItem> search(String q, User user, int limit) {
        return goals.search(q).stream().limit(limit)
                .map(g -> WorkItem.of("goals", "goal", null, g.getName(), "Goal · " + (g.getOwner() == null ? "" : g.getOwner().getDisplayName()),
                        g.getStatus(), null, link(g), g.getUpdatedAt()))
                .toList();
    }
}
