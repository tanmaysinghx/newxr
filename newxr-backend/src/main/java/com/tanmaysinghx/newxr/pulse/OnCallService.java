package com.tanmaysinghx.newxr.pulse;

import com.tanmaysinghx.newxr.common.ApiException;
import com.tanmaysinghx.newxr.events.DomainEvent;
import com.tanmaysinghx.newxr.events.EventBus;
import com.tanmaysinghx.newxr.security.CurrentUser;
import com.tanmaysinghx.newxr.user.User;
import com.tanmaysinghx.newxr.user.UserRef;
import com.tanmaysinghx.newxr.user.UserRepository;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** On-call schedule (layers, overrides, final schedule), paging, iCal export and the escalation policy. */
@RestController
@RequestMapping("/api/pulse/teams/{key}")
@Transactional
public class OnCallService {

    public record ShiftView(Instant start, Instant end, UserRef user, boolean override) {}

    public record LayerView(Long id, String name, int rotationDays, Instant rotationStart, List<UserRef> members, List<ShiftView> shifts) {}

    public record OverrideView(Long id, UserRef user, Instant startsAt, Instant endsAt, String reason) {}

    public record StepView(Long id, int position, String target, UserRef user, int delayMinutes, String label) {}

    public record Schedule(Instant from, Instant to, Instant now, ShiftView current, UserRef next, List<LayerView> layers,
                           List<OverrideView> overrides, List<ShiftView> finalShifts, List<StepView> escalation) {}

    public record OverrideRequest(Long userId, Instant startsAt, Instant endsAt, String reason) {}

    public record LayerRequest(String name, List<Long> memberIds, Integer rotationDays, Instant rotationStart) {}

    public record PageRequest(Long userId, String message, String priority) {}

    public record StepRequest(String target, Long userId, int delayMinutes) {}

    private final PulseService pulse;
    private final OnCall onCall;
    private final OnCallLayerRepository layers;
    private final OnCallOverrideRepository overrides;
    private final EscalationStepRepository steps;
    private final UserRepository users;
    private final AlertEngine engine;
    private final CurrentUser current;
    private final EventBus events;

    public OnCallService(PulseService pulse, OnCall onCall, OnCallLayerRepository layers, OnCallOverrideRepository overrides,
                         EscalationStepRepository steps, UserRepository users, AlertEngine engine, CurrentUser current, EventBus events) {
        this.pulse = pulse;
        this.onCall = onCall;
        this.layers = layers;
        this.overrides = overrides;
        this.steps = steps;
        this.users = users;
        this.engine = engine;
        this.current = current;
        this.events = events;
    }

    /** The week (Monday 00:00 UTC) containing {@code from}, or this week. */
    @GetMapping("/oncall")
    @Transactional(readOnly = true)
    public Schedule schedule(@PathVariable String key, @RequestParam(required = false) LocalDate from, @RequestParam(defaultValue = "7") int days) {
        PulseTeam t = pulse.team0(key);
        Instant now = Instant.now();
        LocalDate monday = (from == null ? LocalDate.now(ZoneOffset.UTC) : from).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant start = monday.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = start.plus(Duration.ofDays(Math.max(1, Math.min(days, 35))));
        OnCall.Handover h = onCall.handover(t, now);
        return new Schedule(start, end, now, h.current() == null ? null : shift(h.current()), UserRef.of(h.next()),
                onCall.layers(t).stream().map(l -> new LayerView(l.getId(), l.getName(), l.getRotationDays(), l.getRotationStart(),
                        l.getMembers().stream().map(UserRef::of).toList(), OnCall.shifts(l, start, end).stream().map(OnCallService::shift).toList())).toList(),
                onCall.overrides(t, start, end).stream().map(OnCallService::view).toList(),
                onCall.finalShifts(t, start, end).stream().map(OnCallService::shift).toList(),
                escalation(key));
    }

    @PostMapping("/oncall/overrides")
    @ResponseStatus(HttpStatus.CREATED)
    public OverrideView override(@PathVariable String key, @RequestBody OverrideRequest r) {
        current.require();
        if (r.startsAt() == null || r.endsAt() == null || !r.endsAt().isAfter(r.startsAt())) throw ApiException.badRequest("The override must end after it starts.");
        if (Duration.between(r.startsAt(), r.endsAt()).toDays() > 31) throw ApiException.badRequest("Overrides can last at most 31 days.");
        OnCallOverride o = new OnCallOverride();
        o.setTeam(pulse.team0(key));
        o.setUser(r.userId() == null ? current.require() : user(r.userId()));
        o.setStartsAt(r.startsAt());
        o.setEndsAt(r.endsAt());
        o.setReason(r.reason() == null || r.reason().isBlank() ? null : r.reason().trim());
        return view(overrides.save(o));
    }

    @DeleteMapping("/oncall/overrides/{id}")
    public void removeOverride(@PathVariable String key, @PathVariable Long id) {
        current.require();
        OnCallOverride o = overrides.findById(id).orElseThrow(() -> ApiException.notFound("Override"));
        if (!o.getTeam().getTeamKey().equalsIgnoreCase(key)) throw ApiException.notFound("Override");
        overrides.delete(o);
    }

    @PutMapping("/oncall/layers/{id}")
    public LayerView editLayer(@PathVariable String key, @PathVariable Long id, @RequestBody LayerRequest r) {
        current.require();
        OnCallLayer l = layers.findById(id).filter(x -> x.getTeam().getTeamKey().equalsIgnoreCase(key)).orElseThrow(() -> ApiException.notFound("Layer"));
        if (r.name() != null && !r.name().isBlank()) l.setName(r.name().trim());
        if (r.rotationDays() != null) {
            if (r.rotationDays() < 1 || r.rotationDays() > 28) throw ApiException.badRequest("Rotate every 1 to 28 days.");
            l.setRotationDays(r.rotationDays());
        }
        if (r.rotationStart() != null) l.setRotationStart(r.rotationStart());
        if (r.memberIds() != null) {
            l.getMembers().clear();
            r.memberIds().forEach(m -> l.getMembers().add(user(m)));
        }
        Instant now = Instant.now();
        return new LayerView(l.getId(), l.getName(), l.getRotationDays(), l.getRotationStart(), l.getMembers().stream().map(UserRef::of).toList(),
                OnCall.shifts(l, now, now.plus(Duration.ofDays(7))).stream().map(OnCallService::shift).toList());
    }

    /** Pages someone (default: the primary on call) by raising an alert they own. */
    @PostMapping("/page")
    public PulseService.AlertRow page(@PathVariable String key, @RequestBody PageRequest r) {
        User me = current.require();
        PulseTeam t = pulse.team0(key);
        User target = r.userId() != null ? user(r.userId()) : onCall.primaryAt(t, Instant.now());
        if (target == null) throw ApiException.badRequest("Nobody is on call.");
        String msg = r.message() == null || r.message().isBlank() ? "Page from " + me.getDisplayName() : r.message().trim();
        Alert a = engine.raise(t, new AlertEngine.Raise(msg, null, r.priority() == null ? "P2" : r.priority(), "Page", null, target), null, me);
        return PulseService.row(a);
    }

    /** Sends the on-call person (or someone else) an in-app message. */
    @PostMapping("/message")
    public Map<String, String> message(@PathVariable String key, @RequestBody PageRequest r) {
        User me = current.require();
        PulseTeam t = pulse.team0(key);
        User target = r.userId() != null ? user(r.userId()) : onCall.primaryAt(t, Instant.now());
        if (target == null) throw ApiException.badRequest("Nobody is on call.");
        if (r.message() == null || r.message().isBlank()) throw ApiException.badRequest("Write a message.");
        events.publish(DomainEvent.of("oncall.message", "pulse", "team", t.getTeamKey(), t.getName(), "/pulse/" + t.getTeamKey() + "/oncall",
                UserRef.of(me), "sent you a message: " + r.message().trim(), List.of(target.getId()), Map.of()));
        return Map.of("sentTo", target.getDisplayName());
    }

    /** Final schedule for the next 8 weeks as an iCalendar file. */
    @GetMapping(value = "/oncall.ics", produces = "text/calendar")
    @Transactional(readOnly = true)
    public ResponseEntity<String> ics(@PathVariable String key) {
        PulseTeam t = pulse.team0(key);
        Instant from = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
        StringBuilder b = new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Newxr//Pulse//EN\r\nCALSCALE:GREGORIAN\r\n")
                .append("X-WR-CALNAME:").append(esc(t.getName())).append(" on-call\r\n");
        for (OnCall.Shift s : onCall.finalShifts(t, from, from.plus(Duration.ofDays(56)))) {
            b.append("BEGIN:VEVENT\r\nUID:").append(t.getTeamKey()).append('-').append(s.start().getEpochSecond()).append("@newxr\r\n")
                    .append("DTSTAMP:").append(f.format(Instant.now())).append("\r\n")
                    .append("DTSTART:").append(f.format(s.start())).append("\r\nDTEND:").append(f.format(s.end())).append("\r\n")
                    .append("SUMMARY:").append(esc(s.user().getDisplayName() + " on call · " + t.getName() + (s.override() ? " (override)" : ""))).append("\r\n")
                    .append("END:VEVENT\r\n");
        }
        b.append("END:VCALENDAR\r\n");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + t.getTeamKey().toLowerCase() + "-oncall.ics\"")
                .contentType(MediaType.parseMediaType("text/calendar; charset=utf-8")).body(b.toString());
    }

    // ---- escalation policy -------------------------------------------------------

    @GetMapping("/escalation")
    @Transactional(readOnly = true)
    public List<StepView> escalation(@PathVariable String key) {
        return steps.findByTeamOrderByPositionAsc(pulse.team0(key)).stream().map(OnCallService::view).toList();
    }

    @PutMapping("/escalation")
    public List<StepView> saveEscalation(@PathVariable String key, @RequestBody List<StepRequest> body) {
        current.require();
        PulseTeam t = pulse.team0(key);
        if (body.size() > 10) throw ApiException.badRequest("Keep the policy to 10 steps or fewer.");
        int last = -1;
        List<EscalationStep> out = new ArrayList<>();
        for (int i = 0; i < body.size(); i++) {
            StepRequest r = body.get(i);
            if (!List.of("ON_CALL_PRIMARY", "ON_CALL_SECONDARY", "USER", "TEAM").contains(r.target())) throw ApiException.badRequest("Unknown target " + r.target());
            if (r.delayMinutes() < 0 || r.delayMinutes() > 24 * 60) throw ApiException.badRequest("Delays run from 0 to 1440 minutes.");
            if (r.delayMinutes() < last) throw ApiException.badRequest("Each step must come at or after the one before it.");
            if ("USER".equals(r.target()) && r.userId() == null) throw ApiException.badRequest("Pick who step " + (i + 1) + " notifies.");
            last = r.delayMinutes();
            EscalationStep s = new EscalationStep();
            s.setTeam(t);
            s.setPosition(i);
            s.setTarget(r.target());
            s.setUser("USER".equals(r.target()) ? user(r.userId()) : null);
            s.setDelayMinutes(r.delayMinutes());
            out.add(s);
        }
        steps.deleteByTeam(t);
        steps.flush();
        return steps.saveAll(out).stream().map(OnCallService::view).toList();
    }

    // ---- views --------------------------------------------------------------------

    static StepView view(EscalationStep s) {
        String who = switch (s.getTarget()) {
            case "ON_CALL_PRIMARY" -> "Notify on-call primary";
            case "ON_CALL_SECONDARY" -> "Notify secondary";
            case "USER" -> "Notify " + (s.getUser() == null ? "someone" : s.getUser().getDisplayName());
            default -> "Notify whole team";
        };
        String when = s.getDelayMinutes() == 0 ? "immediately" : "after " + s.getDelayMinutes() + " min if not acknowledged";
        return new StepView(s.getId(), s.getPosition(), s.getTarget(), UserRef.of(s.getUser()), s.getDelayMinutes(), who + " · " + when);
    }

    private static ShiftView shift(OnCall.Shift s) {
        return new ShiftView(s.start(), s.end(), UserRef.of(s.user()), s.override());
    }

    private static OverrideView view(OnCallOverride o) {
        return new OverrideView(o.getId(), UserRef.of(o.getUser()), o.getStartsAt(), o.getEndsAt(), o.getReason());
    }

    private User user(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n");
    }
}
