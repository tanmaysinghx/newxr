package com.tanmaysinghx.newxr.pulse;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Operations teams, alerts and incidents. On-call, status pages and integrations have their own controllers. */
@RestController
@RequestMapping("/api/pulse")
@Transactional
public class PulseService implements HubContributor {

    static final List<String> INCIDENT_STATUSES = List.of("INVESTIGATING", "IDENTIFIED", "MONITORING", "RESOLVED");
    static final List<String> IMPACTS = List.of("DEGRADED", "PARTIAL_OUTAGE", "MAJOR_OUTAGE");

    public record TeamSummary(Long id, String key, String name, String description, String color, long open, long unacknowledged,
                              UserRef onCall, String statusSlug, boolean statusPublic, List<UserRef> members) {}

    public record AlertRow(Long id, String title, String priority, String status, String source, UserRef owner, int count,
                           Instant createdAt, Instant lastOccurredAt, Instant snoozedUntil, String incidentKey) {}

    public record EventView(Long id, UserRef actor, String kind, String text, Instant at) {}

    public record AlertView(Long id, String teamKey, String teamName, String title, String description, String priority, String status,
                            String source, String dedupKey, UserRef owner, int count, Instant createdAt, Instant lastOccurredAt,
                            Instant acknowledgedAt, UserRef acknowledgedBy, Instant closedAt, Instant snoozedUntil, Instant nextEscalationAt,
                            String incidentKey, String incidentTitle, List<EventView> timeline) {}

    public record Stats(long open, long unacknowledged, Long mttaSeconds, Long mttaDeltaSeconds, Long mttrMinutes, Long mttrDeltaMinutes,
                        UserRef onCall, Instant onCallUntil, UserRef nextOnCall) {}

    public record ComponentRef(Long id, String name) {}

    public record IncidentRow(Long id, String key, String title, String status, String severity, String impact, UserRef commander,
                              boolean publicIncident, List<ComponentRef> components, Instant startedAt, Instant resolvedAt, int alerts) {}

    public record UpdateView(Long id, UserRef author, String status, String body, boolean publicUpdate, Instant at) {}

    public record IncidentView(Long id, String key, String teamKey, String teamName, String title, String status, String severity, String impact,
                               UserRef commander, boolean publicIncident, List<ComponentRef> components, Instant startedAt, Instant resolvedAt,
                               List<UpdateView> updates, List<AlertRow> alerts, String statusSlug) {}

    public record CreateAlert(String title, String description, String priority, Long ownerId) {}

    public record AlertAction(String action, Integer minutes, Long ownerId, String note) {}

    public record BulkAction(List<Long> ids, String action, Integer minutes) {}

    public record NoteRequest(String body) {}

    public record CreateIncident(String title, String severity, String impact, Long commanderId, Boolean publicIncident, List<Long> componentIds,
                                 String body, List<Long> alertIds) {}

    public record EditIncident(String title, String severity, String impact, Long commanderId, Boolean publicIncident, List<Long> componentIds) {}

    public record PostUpdate(String status, String body, Boolean publicUpdate, String impact) {}

    private final PulseTeamRepository teams;
    private final AlertRepository alerts;
    private final AlertEventRepository alertEvents;
    private final IncidentRepository incidents;
    private final IncidentUpdateRepository updates;
    private final StatusComponentRepository components;
    private final UserRepository users;
    private final AlertEngine engine;
    private final OnCall onCall;
    private final CurrentUser current;
    private final EventBus events;

    public PulseService(PulseTeamRepository teams, AlertRepository alerts, AlertEventRepository alertEvents, IncidentRepository incidents,
                        IncidentUpdateRepository updates, StatusComponentRepository components, UserRepository users, AlertEngine engine,
                        OnCall onCall, CurrentUser current, EventBus events) {
        this.teams = teams;
        this.alerts = alerts;
        this.alertEvents = alertEvents;
        this.incidents = incidents;
        this.updates = updates;
        this.components = components;
        this.users = users;
        this.engine = engine;
        this.onCall = onCall;
        this.current = current;
        this.events = events;
    }

    // ---- teams --------------------------------------------------------------------

    @GetMapping("/teams")
    @Transactional(readOnly = true)
    public List<TeamSummary> teams() {
        Instant now = Instant.now();
        return teams.findAllByOrderByNameAsc().stream().map(t -> summary(t, now)).toList();
    }

    @GetMapping("/teams/{key}")
    @Transactional(readOnly = true)
    public TeamSummary team(@PathVariable String key) {
        return summary(team0(key), Instant.now());
    }

    // ---- alerts -------------------------------------------------------------------

    /**
     * @param status ACTIVE (default: open + acknowledged, not snoozed), OPEN, ACKNOWLEDGED, CLOSED, SNOOZED, ALL
     */
    @GetMapping("/teams/{key}/alerts")
    @Transactional(readOnly = true)
    public List<AlertRow> alerts(@PathVariable String key, @RequestParam(defaultValue = "ACTIVE") String status,
                                 @RequestParam(required = false) String q, @RequestParam(required = false) String priority,
                                 @RequestParam(required = false) String source, @RequestParam(required = false) Long owner) {
        Instant now = Instant.now();
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return alerts.findByTeamOrderByCreatedAtDesc(team0(key)).stream()
                .filter(a -> matchesStatus(a, status, now))
                .filter(a -> query.isEmpty() || a.getTitle().toLowerCase(Locale.ROOT).contains(query))
                .filter(a -> blank(priority) || a.getPriority().equals(priority))
                .filter(a -> blank(source) || source.equals(a.getSource()))
                .filter(a -> owner == null || (owner == 0 ? a.getOwner() == null : a.getOwner() != null && a.getOwner().getId().equals(owner)))
                .sorted(Comparator.comparing((Alert a) -> "CLOSED".equals(a.getStatus())).thenComparing(Alert::getPriority)
                        .thenComparing(Alert::getCreatedAt, Comparator.reverseOrder()))
                .map(PulseService::row).toList();
    }

    @GetMapping("/teams/{key}/stats")
    @Transactional(readOnly = true)
    public Stats stats(@PathVariable String key) {
        PulseTeam t = team0(key);
        Instant now = Instant.now();
        Instant w1 = now.minus(Duration.ofDays(7)), w2 = now.minus(Duration.ofDays(14));
        List<Alert> recent = alerts.findByTeamAndCreatedAtAfter(t, w2);
        List<Alert> all = alerts.findByTeamOrderByCreatedAtDesc(t);
        long open = all.stream().filter(a -> !"CLOSED".equals(a.getStatus())).count();
        long unacked = all.stream().filter(a -> "OPEN".equals(a.getStatus())).count();
        Long mtta = avgSeconds(recent, w1, now, Alert::getAcknowledgedAt);
        Long mttaPrev = avgSeconds(recent, w2, w1, Alert::getAcknowledgedAt);
        Long mttr = avgSeconds(recent, w1, now, Alert::getClosedAt);
        Long mttrPrev = avgSeconds(recent, w2, w1, Alert::getClosedAt);
        OnCall.Handover h = onCall.handover(t, now);
        return new Stats(open, unacked, mtta, mtta == null || mttaPrev == null ? null : mtta - mttaPrev,
                mttr == null ? null : mttr / 60, mttr == null || mttrPrev == null ? null : (mttr - mttrPrev) / 60,
                h.current() == null ? null : UserRef.of(h.current().user()), h.current() == null ? null : h.current().end(), UserRef.of(h.next()));
    }

    @GetMapping("/teams/{key}/sources")
    @Transactional(readOnly = true)
    public List<String> sources(@PathVariable String key) {
        return alerts.findByTeamOrderByCreatedAtDesc(team0(key)).stream().map(Alert::getSource).filter(Objects::nonNull).distinct().sorted().toList();
    }

    @PostMapping("/teams/{key}/alerts")
    @ResponseStatus(HttpStatus.CREATED)
    public AlertView create(@PathVariable String key, @RequestBody CreateAlert r) {
        User me = current.require();
        if (blank(r.title())) throw ApiException.badRequest("Say what is wrong.");
        User owner = r.ownerId() == null ? null : user(r.ownerId());
        Alert a = engine.raise(team0(key), new AlertEngine.Raise(r.title(), r.description(), r.priority(), "Manual", null, owner), null, me);
        return view(a);
    }

    @GetMapping("/alerts/{id}")
    @Transactional(readOnly = true)
    public AlertView alert(@PathVariable Long id) {
        current.require();
        return view(alert0(id));
    }

    @PostMapping("/alerts/{id}")
    public AlertView act(@PathVariable Long id, @RequestBody AlertAction r) {
        User me = current.require();
        Alert a = alert0(id);
        apply(a, r.action(), r.minutes(), r.ownerId(), r.note(), me);
        a.setUpdatedAt(Instant.now());
        return view(a);
    }

    @PostMapping("/alerts/bulk")
    public Map<String, Integer> bulk(@RequestBody BulkAction r) {
        User me = current.require();
        if (r.ids() == null || r.ids().isEmpty()) throw ApiException.badRequest("Select alerts first.");
        int n = 0;
        for (Alert a : alerts.findAllById(r.ids())) {
            apply(a, r.action(), r.minutes(), null, null, me);
            a.setUpdatedAt(Instant.now());
            n++;
        }
        return Map.of("updated", n);
    }

    @PostMapping("/alerts/{id}/notes")
    public AlertView note(@PathVariable Long id, @RequestBody NoteRequest r) {
        User me = current.require();
        if (blank(r.body())) throw ApiException.badRequest("Write a note first.");
        Alert a = alert0(id);
        engine.note(a, me, r.body());
        return view(a);
    }

    private void apply(Alert a, String action, Integer minutes, Long ownerId, String note, User me) {
        switch (action == null ? "" : action) {
            case "acknowledge" -> engine.acknowledge(a, me);
            case "close" -> engine.close(a, me, note);
            case "reopen" -> engine.reopen(a, me);
            case "snooze" -> {
                int m = minutes == null ? 60 : minutes;
                if (m < 5 || m > 7 * 24 * 60) throw ApiException.badRequest("Snooze for between 5 minutes and 7 days.");
                engine.snooze(a, me, m);
            }
            case "unsnooze" -> engine.unsnooze(a, me);
            case "assign" -> engine.assign(a, ownerId == null ? null : user(ownerId), me);
            case "take" -> engine.assign(a, me, me);
            default -> throw ApiException.badRequest("Unknown action " + action);
        }
    }

    // ---- incidents ----------------------------------------------------------------

    @GetMapping("/teams/{key}/incidents")
    @Transactional(readOnly = true)
    public List<IncidentRow> incidents(@PathVariable String key, @RequestParam(defaultValue = "false") boolean resolved) {
        return incidents.findByTeamOrderByStartedAtDesc(team0(key)).stream()
                .filter(i -> resolved == "RESOLVED".equals(i.getStatus()))
                .map(this::row).toList();
    }

    @PostMapping("/teams/{key}/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentView declare(@PathVariable String key, @RequestBody CreateIncident r) {
        User me = current.require();
        if (blank(r.title())) throw ApiException.badRequest("Name the incident.");
        PulseTeam t = teams.lockById(team0(key).getId()).orElseThrow(() -> ApiException.notFound("Team"));
        t.setIncidentSeq(t.getIncidentSeq() + 1);
        Incident i = new Incident();
        i.setTeam(t);
        i.setIncidentKey(t.getTeamKey() + "-" + t.getIncidentSeq());
        i.setTitle(r.title().trim());
        i.setSeverity(r.severity() != null && List.of("P1", "P2", "P3", "P4").contains(r.severity()) ? r.severity() : "P2");
        i.setImpact(r.impact() != null && IMPACTS.contains(r.impact()) ? r.impact() : "DEGRADED");
        i.setCommander(r.commanderId() == null ? me : user(r.commanderId()));
        i.setPublicIncident(Boolean.TRUE.equals(r.publicIncident()));
        i.setStartedAt(Instant.now());
        setComponents(i, t, r.componentIds());
        i = incidents.save(i);
        addUpdate(i, me, "INVESTIGATING", blank(r.body()) ? "We are investigating." : r.body(), i.isPublicIncident());
        if (r.alertIds() != null) {
            for (Alert a : alerts.findAllById(r.alertIds())) {
                a.setIncident(i);
                engine.event(a, me, "INCIDENT", "Linked to incident " + i.getIncidentKey());
            }
        }
        List<Long> to = new ArrayList<>(t.getMembers().stream().map(User::getId).toList());
        events.publish(DomainEvent.of("incident.declared", "pulse", "incident", i.getIncidentKey(), i.getTitle(), link(i), UserRef.of(me),
                "declared incident " + i.getIncidentKey() + " " + i.getTitle(), to, Map.of("severity", i.getSeverity(), "notifyKind", "watching")));
        return view(i);
    }

    @GetMapping("/incidents/{key}")
    @Transactional(readOnly = true)
    public IncidentView incident(@PathVariable String key) {
        current.require();
        return view(incident0(key));
    }

    @PutMapping("/incidents/{key}")
    public IncidentView editIncident(@PathVariable String key, @RequestBody EditIncident r) {
        current.require();
        Incident i = incident0(key);
        if (!blank(r.title())) i.setTitle(r.title().trim());
        if (r.severity() != null && List.of("P1", "P2", "P3", "P4").contains(r.severity())) i.setSeverity(r.severity());
        if (r.impact() != null && IMPACTS.contains(r.impact())) i.setImpact(r.impact());
        if (r.commanderId() != null) i.setCommander(user(r.commanderId()));
        if (r.publicIncident() != null) i.setPublicIncident(r.publicIncident());
        if (r.componentIds() != null) setComponents(i, i.getTeam(), r.componentIds());
        i.setUpdatedAt(Instant.now());
        return view(i);
    }

    @PostMapping("/incidents/{key}/updates")
    public IncidentView postUpdate(@PathVariable String key, @RequestBody PostUpdate r) {
        User me = current.require();
        Incident i = incident0(key);
        if (blank(r.body())) throw ApiException.badRequest("Write what changed.");
        String status = r.status() == null ? i.getStatus() : r.status();
        if (!INCIDENT_STATUSES.contains(status)) throw ApiException.badRequest("Unknown status " + status);
        if (r.impact() != null && IMPACTS.contains(r.impact())) i.setImpact(r.impact());
        boolean pub = r.publicUpdate() == null ? i.isPublicIncident() : r.publicUpdate();
        addUpdate(i, me, status, r.body(), pub);
        i.setStatus(status);
        Instant now = Instant.now();
        if ("RESOLVED".equals(status)) {
            if (i.getResolvedAt() == null) i.setResolvedAt(now);
            for (Alert a : alerts.findByIncidentOrderByCreatedAtAsc(i)) engine.close(a, me, "incident " + i.getIncidentKey() + " resolved");
        } else {
            i.setResolvedAt(null);
        }
        i.setUpdatedAt(now);
        events.publish(DomainEvent.of("incident.updated", "pulse", "incident", i.getIncidentKey(), i.getTitle(), link(i), UserRef.of(me),
                "posted an update on " + i.getIncidentKey() + ": " + status.toLowerCase(Locale.ROOT),
                i.getTeam().getMembers().stream().map(User::getId).toList(), Map.of("status", status, "notifyKind", "watching")));
        return view(i);
    }

    @PostMapping("/incidents/{key}/alerts/{alertId}")
    public IncidentView linkAlert(@PathVariable String key, @PathVariable Long alertId) {
        User me = current.require();
        Incident i = incident0(key);
        Alert a = alert0(alertId);
        a.setIncident(i);
        engine.event(a, me, "INCIDENT", "Linked to incident " + i.getIncidentKey());
        return view(i);
    }

    private void addUpdate(Incident i, User me, String status, String body, boolean pub) {
        IncidentUpdate u = new IncidentUpdate();
        u.setIncident(i);
        u.setAuthor(me);
        u.setStatus(status);
        u.setBody(body.trim());
        u.setPublicUpdate(pub);
        updates.save(u);
    }

    private void setComponents(Incident i, PulseTeam t, List<Long> ids) {
        if (ids == null) return;
        Set<Long> want = new HashSet<>(ids);
        i.getComponents().clear();
        components.findByTeamOrderByPositionAscIdAsc(t).stream().filter(c -> want.contains(c.getId())).forEach(i.getComponents()::add);
    }

    // ---- views --------------------------------------------------------------------

    private TeamSummary summary(PulseTeam t, Instant now) {
        List<Alert> all = alerts.findByTeamOrderByCreatedAtDesc(t);
        return new TeamSummary(t.getId(), t.getTeamKey(), t.getName(), t.getDescription(), t.getColor(),
                all.stream().filter(a -> !"CLOSED".equals(a.getStatus())).count(), all.stream().filter(a -> "OPEN".equals(a.getStatus())).count(),
                UserRef.of(onCall.primaryAt(t, now)), t.getStatusSlug(), t.isStatusPublic(), t.getMembers().stream().map(UserRef::of).toList());
    }

    static boolean matchesStatus(Alert a, String status, Instant now) {
        boolean snoozed = a.getSnoozedUntil() != null && a.getSnoozedUntil().isAfter(now) && !"CLOSED".equals(a.getStatus());
        return switch (status) {
            case "ALL" -> true;
            case "SNOOZED" -> snoozed;
            case "OPEN", "ACKNOWLEDGED", "CLOSED" -> a.getStatus().equals(status);
            default -> !"CLOSED".equals(a.getStatus()) && !snoozed;
        };
    }

    static AlertRow row(Alert a) {
        return new AlertRow(a.getId(), a.getTitle(), a.getPriority(), a.getStatus(), a.getSource(), UserRef.of(a.getOwner()), a.getCount(),
                a.getCreatedAt(), a.getLastOccurredAt(), a.getSnoozedUntil(), a.getIncident() == null ? null : a.getIncident().getIncidentKey());
    }

    private AlertView view(Alert a) {
        Incident i = a.getIncident();
        return new AlertView(a.getId(), a.getTeam().getTeamKey(), a.getTeam().getName(), a.getTitle(), a.getDescription(), a.getPriority(),
                a.getStatus(), a.getSource(), a.getDedupKey(), UserRef.of(a.getOwner()), a.getCount(), a.getCreatedAt(), a.getLastOccurredAt(),
                a.getAcknowledgedAt(), UserRef.of(a.getAcknowledgedBy()), a.getClosedAt(), a.getSnoozedUntil(), a.getNextEscalationAt(),
                i == null ? null : i.getIncidentKey(), i == null ? null : i.getTitle(),
                alertEvents.findByAlertOrderByCreatedAtAsc(a).stream()
                        .map(e -> new EventView(e.getId(), UserRef.of(e.getActor()), e.getKind(), e.getText(), e.getCreatedAt())).toList());
    }

    private IncidentRow row(Incident i) {
        return new IncidentRow(i.getId(), i.getIncidentKey(), i.getTitle(), i.getStatus(), i.getSeverity(), i.getImpact(), UserRef.of(i.getCommander()),
                i.isPublicIncident(), refs(i), i.getStartedAt(), i.getResolvedAt(), alerts.findByIncidentOrderByCreatedAtAsc(i).size());
    }

    private IncidentView view(Incident i) {
        return new IncidentView(i.getId(), i.getIncidentKey(), i.getTeam().getTeamKey(), i.getTeam().getName(), i.getTitle(), i.getStatus(),
                i.getSeverity(), i.getImpact(), UserRef.of(i.getCommander()), i.isPublicIncident(), refs(i), i.getStartedAt(), i.getResolvedAt(),
                updates.findByIncidentOrderByCreatedAtDesc(i).stream()
                        .map(u -> new UpdateView(u.getId(), UserRef.of(u.getAuthor()), u.getStatus(), u.getBody(), u.isPublicUpdate(), u.getCreatedAt())).toList(),
                alerts.findByIncidentOrderByCreatedAtAsc(i).stream().map(PulseService::row).toList(),
                i.getTeam().isStatusPublic() ? i.getTeam().getStatusSlug() : null);
    }

    private static List<ComponentRef> refs(Incident i) {
        return i.getComponents().stream().sorted(Comparator.comparingInt(StatusComponent::getPosition))
                .map(c -> new ComponentRef(c.getId(), c.getName())).toList();
    }

    private static Long avgSeconds(List<Alert> as, Instant from, Instant to, java.util.function.Function<Alert, Instant> end) {
        return as.stream().filter(a -> a.getCreatedAt().isAfter(from) && !a.getCreatedAt().isAfter(to) && end.apply(a) != null)
                .mapToLong(a -> Duration.between(a.getCreatedAt(), end.apply(a)).toSeconds()).average().stream()
                .mapToObj(v -> Math.round(v)).findFirst().orElse(null);
    }

    PulseTeam team0(String key) {
        return teams.findByTeamKeyIgnoreCase(key).orElseThrow(() -> ApiException.notFound("Team"));
    }

    private Alert alert0(Long id) {
        return alerts.findById(id).orElseThrow(() -> ApiException.notFound("Alert"));
    }

    private Incident incident0(String key) {
        return incidents.findByIncidentKeyIgnoreCase(key).orElseThrow(() -> ApiException.notFound("Incident"));
    }

    private User user(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    static String link(Incident i) {
        return "/pulse/" + i.getTeam().getTeamKey() + "/incidents/" + i.getIncidentKey();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    // ---- hub ----------------------------------------------------------------------

    @Override
    public String app() {
        return "pulse";
    }

    @Override
    public List<WorkItem> assignedTo(User user) {
        List<WorkItem> out = new ArrayList<>();
        alerts.findByOwnerAndStatusNotOrderByUpdatedAtDesc(user, "CLOSED").forEach(a -> out.add(WorkItem.of("pulse", "alert", null, a.getTitle(),
                a.getTeam().getName() + " · " + (a.getSource() == null ? "alert" : a.getSource()), a.getStatus(), a.getPriority(), AlertEngine.link(a), a.getUpdatedAt())));
        incidents.findByCommanderAndStatusNot(user, "RESOLVED").forEach(i -> out.add(WorkItem.of("pulse", "incident", i.getIncidentKey(), i.getTitle(),
                i.getTeam().getName() + " · incident commander", i.getStatus(), i.getSeverity(), link(i), i.getUpdatedAt())));
        return out;
    }

    @Override
    public List<WorkItem> waitingOn(User user) {
        return alerts.findByOwnerAndStatusNotOrderByUpdatedAtDesc(user, "CLOSED").stream().filter(a -> "OPEN".equals(a.getStatus()))
                .map(a -> WorkItem.of("pulse", "alert", null, "Acknowledge: " + a.getTitle(), a.getTeam().getName() + " · unacknowledged",
                        a.getStatus(), a.getPriority(), AlertEngine.link(a), a.getCreatedAt()))
                .toList();
    }

    @Override
    public List<WorkItem> search(String q, User user, int limit) {
        List<WorkItem> out = new ArrayList<>();
        incidents.search(q, q.trim().toUpperCase(Locale.ROOT), PageRequest.of(0, limit)).forEach(i -> out.add(WorkItem.of("pulse", "incident",
                i.getIncidentKey(), i.getTitle(), "Incident · " + i.getTeam().getName(), i.getStatus(), i.getSeverity(), link(i), i.getUpdatedAt())));
        alerts.search(q, PageRequest.of(0, limit)).forEach(a -> out.add(WorkItem.of("pulse", "alert", null, a.getTitle(),
                "Alert · " + a.getTeam().getName(), a.getStatus(), a.getPriority(), AlertEngine.link(a), a.getUpdatedAt())));
        return out.stream().limit(limit).toList();
    }
}
