package com.tanmaysinghx.newxr.pulse;

import com.tanmaysinghx.newxr.events.DomainEvent;
import com.tanmaysinghx.newxr.events.EventBus;
import com.tanmaysinghx.newxr.user.User;
import com.tanmaysinghx.newxr.user.UserRef;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alert lifecycle: raise (with de-duplication), acknowledge, close, snooze, and escalation through the team's
 * policy. Escalation notifies people in the app; a background job runs steps that are due every 30 seconds.
 */
@Service
@Transactional
public class AlertEngine {

    private static final Logger log = LoggerFactory.getLogger(AlertEngine.class);
    static final List<String> PRIORITIES = List.of("P1", "P2", "P3", "P4", "P5");

    public record Raise(String title, String description, String priority, String source, String dedupKey, User owner) {}

    private final AlertRepository alerts;
    private final AlertEventRepository log_;
    private final EscalationStepRepository steps;
    private final OnCall onCall;
    private final EventBus events;

    public AlertEngine(AlertRepository alerts, AlertEventRepository log, EscalationStepRepository steps, OnCall onCall, EventBus events) {
        this.alerts = alerts;
        this.log_ = log;
        this.steps = steps;
        this.onCall = onCall;
        this.events = events;
    }

    /** Raises an alert, or bumps the count of the open alert with the same dedup key. */
    public Alert raise(PulseTeam team, Raise r, Integration via, User actor) {
        Instant now = Instant.now();
        String dedup = blank(r.dedupKey()) ? null : r.dedupKey().trim();
        if (dedup != null) {
            Alert existing = alerts.findFirstByTeamAndDedupKeyAndStatusNotOrderByCreatedAtDesc(team, dedup, "CLOSED").orElse(null);
            if (existing != null) {
                existing.setCount(existing.getCount() + 1);
                existing.setLastOccurredAt(now);
                existing.setUpdatedAt(now);
                event(existing, null, "REPEATED", "Happened again (" + existing.getCount() + " times)");
                return existing;
            }
        }
        Alert a = new Alert();
        a.setTeam(team);
        a.setTitle(r.title().trim());
        a.setDescription(r.description());
        a.setPriority(r.priority() != null && PRIORITIES.contains(r.priority()) ? r.priority() : via != null ? via.getDefaultPriority() : "P3");
        a.setSource(blank(r.source()) ? via != null ? via.getName() : "Manual" : r.source().trim());
        a.setDedupKey(dedup);
        a.setIntegration(via);
        a.setOwner(r.owner() != null ? r.owner() : onCall.primaryAt(team, now));
        a.setLastOccurredAt(now);
        a.setNextEscalationAt(now);
        // Same instant as the escalation check below, so "immediately" steps run now rather than on the next job pass.
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        a = alerts.save(a);
        event(a, actor, "RAISED", via != null ? "Raised by " + via.getName() : actor != null ? "Raised by " + actor.getDisplayName() : "Raised");
        escalate(a, now);
        return a;
    }

    public void acknowledge(Alert a, User by) {
        if (!"OPEN".equals(a.getStatus())) return;
        Instant now = Instant.now();
        a.setStatus("ACKNOWLEDGED");
        a.setAcknowledgedAt(now);
        a.setAcknowledgedBy(by);
        if (a.getOwner() == null) a.setOwner(by);
        stopEscalation(a);
        event(a, by, "ACKNOWLEDGED", "Acknowledged");
    }

    public void close(Alert a, User by, String note) {
        if ("CLOSED".equals(a.getStatus())) return;
        Instant now = Instant.now();
        if (a.getAcknowledgedAt() == null) {
            a.setAcknowledgedAt(now);
            a.setAcknowledgedBy(by);
        }
        a.setStatus("CLOSED");
        a.setClosedAt(now);
        a.setSnoozedUntil(null);
        stopEscalation(a);
        event(a, by, "CLOSED", blank(note) ? "Closed" : "Closed: " + note.trim());
    }

    public void reopen(Alert a, User by) {
        if (!"CLOSED".equals(a.getStatus())) return;
        Instant now = Instant.now();
        a.setStatus("OPEN");
        a.setClosedAt(null);
        a.setAcknowledgedAt(null);
        a.setAcknowledgedBy(null);
        a.setEscalationStep(0);
        a.setNextEscalationAt(now);
        event(a, by, "REOPENED", "Reopened");
    }

    public void snooze(Alert a, User by, int minutes) {
        Instant until = Instant.now().plus(Duration.ofMinutes(minutes));
        a.setSnoozedUntil(until);
        if (a.getNextEscalationAt() != null && a.getNextEscalationAt().isBefore(until)) a.setNextEscalationAt(until);
        event(a, by, "SNOOZED", "Snoozed for " + (minutes >= 60 ? minutes / 60 + " h" : minutes + " min"));
    }

    public void unsnooze(Alert a, User by) {
        a.setSnoozedUntil(null);
        if ("OPEN".equals(a.getStatus()) && a.getEscalationStep() >= 0) a.setNextEscalationAt(Instant.now());
        event(a, by, "UNSNOOZED", "Snooze removed");
    }

    public void assign(Alert a, User to, User by) {
        a.setOwner(to);
        event(a, by, "ASSIGNED", to == null ? "Unassigned" : "Assigned to " + to.getDisplayName());
        if (to != null && (by == null || !to.getId().equals(by.getId()))) {
            publish(a, "alert.assigned", by, "assigned you alert " + a.getPriority() + " " + a.getTitle(), List.of(to.getId()), "assigned");
        }
    }

    public void note(Alert a, User by, String body) {
        event(a, by, "NOTE", body.trim());
    }

    /** Runs every escalation step that is due. Snoozed alerts wait until the snooze ends. */
    void escalate(Alert a, Instant now) {
        if (!"OPEN".equals(a.getStatus()) || a.getEscalationStep() < 0) return;
        if (a.getSnoozedUntil() != null && a.getSnoozedUntil().isAfter(now)) {
            a.setNextEscalationAt(a.getSnoozedUntil());
            return;
        }
        List<EscalationStep> policy = steps.findByTeamOrderByPositionAsc(a.getTeam());
        if (policy.isEmpty()) {
            if (a.getEscalationStep() == 0 && a.getOwner() != null) notify(a, List.of(a.getOwner()), "Notified " + a.getOwner().getDisplayName());
            stopEscalation(a);
            return;
        }
        int i = a.getEscalationStep();
        while (i < policy.size() && !a.getCreatedAt().plus(Duration.ofMinutes(policy.get(i).getDelayMinutes())).isAfter(now)) {
            EscalationStep s = policy.get(i);
            List<User> who = targets(a.getTeam(), s, now);
            notify(a, who, "Step " + (i + 1) + " · notified " + (who.isEmpty() ? "nobody (no one on call)"
                    : String.join(", ", who.stream().map(User::getDisplayName).toList())));
            i++;
        }
        if (i < policy.size()) {
            a.setEscalationStep(i);
            a.setNextEscalationAt(a.getCreatedAt().plus(Duration.ofMinutes(policy.get(i).getDelayMinutes())));
        } else {
            stopEscalation(a);
        }
    }

    List<User> targets(PulseTeam team, EscalationStep s, Instant now) {
        List<User> out = new ArrayList<>();
        switch (s.getTarget()) {
            case "ON_CALL_PRIMARY" -> add(out, onCall.primaryAt(team, now));
            case "ON_CALL_SECONDARY" -> add(out, onCall.secondaryAt(team, now));
            case "USER" -> add(out, s.getUser());
            case "TEAM" -> team.getMembers().forEach(u -> add(out, u));
            default -> { }
        }
        return out;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void runDueEscalations() {
        Instant now = Instant.now();
        try {
            for (Alert a : alerts.dueForEscalation(now)) escalate(a, now);
        } catch (RuntimeException e) {
            log.warn("Escalation run failed: {}", e.getMessage());
        }
    }

    private void notify(Alert a, List<User> who, String text) {
        event(a, null, "ESCALATED", text);
        if (!who.isEmpty()) {
            publish(a, "alert.escalated", null, "paged you: " + a.getPriority() + " " + a.getTitle(), who.stream().map(User::getId).toList(), "direct");
        }
    }

    private void publish(Alert a, String type, User actor, String summary, List<Long> to, String kind) {
        events.publish(DomainEvent.of(type, "pulse", "alert", String.valueOf(a.getId()), a.getTitle(), link(a), UserRef.of(actor), summary, to,
                Map.of("priority", a.getPriority(), "notifyKind", kind)));
    }

    private static void stopEscalation(Alert a) {
        a.setEscalationStep(-1);
        a.setNextEscalationAt(null);
    }

    void event(Alert a, User actor, String kind, String text) {
        AlertEvent e = new AlertEvent();
        e.setAlert(a);
        e.setActor(actor);
        e.setKind(kind);
        e.setText(text);
        log_.save(e);
    }

    private static void add(List<User> out, User u) {
        if (u != null && out.stream().noneMatch(x -> x.getId().equals(u.getId()))) out.add(u);
    }

    static String link(Alert a) {
        return "/pulse/" + a.getTeam().getTeamKey() + "/alerts/" + a.getId();
    }

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
