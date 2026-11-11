package com.tanmaysinghx.newxr.pulse;

import com.tanmaysinghx.newxr.common.ApiException;
import com.tanmaysinghx.newxr.security.CurrentUser;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Status pages. A component's status is the worst of its manual status and the impact of open incidents that
 * affect it. Daily history and uptime come from public incidents: a major outage counts fully as downtime, a
 * partial outage 30%, degraded performance not at all (it still colours the day).
 */
@RestController
@Transactional
public class StatusPageService {

    static final List<String> ORDER = List.of("OPERATIONAL", "MAINTENANCE", "DEGRADED", "PARTIAL_OUTAGE", "MAJOR_OUTAGE");
    static final int DAYS = 90;
    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9-]{1,38}");

    public record Day(LocalDate date, String status, int downtimeMinutes, List<String> incidents) {}

    public record ComponentView(Long id, String name, String description, int position, String manualStatus, String status, double uptime, List<Day> days) {}

    public record PublicUpdate(String status, String body, Instant at) {}

    public record PublicIncident(String key, String title, String status, String impact, List<String> components, Instant startedAt,
                                 Instant resolvedAt, List<PublicUpdate> updates) {}

    public record PageView(String slug, String title, boolean published, String overall, List<ComponentView> components,
                           List<PublicIncident> active, List<PublicIncident> recent, Instant updatedAt) {}

    public record PageSettings(String slug, String title, Boolean published) {}

    public record ComponentRequest(String name, String description, String manualStatus, Integer position) {}

    private final PulseService pulse;
    private final PulseTeamRepository teams;
    private final StatusComponentRepository components;
    private final IncidentRepository incidents;
    private final IncidentUpdateRepository updates;
    private final CurrentUser current;

    public StatusPageService(PulseService pulse, PulseTeamRepository teams, StatusComponentRepository components, IncidentRepository incidents,
                             IncidentUpdateRepository updates, CurrentUser current) {
        this.pulse = pulse;
        this.teams = teams;
        this.components = components;
        this.incidents = incidents;
        this.updates = updates;
        this.current = current;
    }

    // ---- agent side --------------------------------------------------------------

    @GetMapping("/api/pulse/teams/{key}/status-page")
    @Transactional(readOnly = true)
    public PageView admin(@PathVariable String key) {
        PulseTeam t = pulse.team0(key);
        return page(t);
    }

    @PutMapping("/api/pulse/teams/{key}/status-page")
    public PageView settings(@PathVariable String key, @RequestBody PageSettings r) {
        current.require();
        PulseTeam t = pulse.team0(key);
        if (r.slug() != null) {
            String slug = r.slug().trim().toLowerCase(Locale.ROOT);
            if (!SLUG.matcher(slug).matches()) throw ApiException.badRequest("Use 2–39 lower-case letters, digits and dashes for the address.");
            teams.findByStatusSlugIgnoreCase(slug).filter(o -> !o.getId().equals(t.getId()))
                    .ifPresent(o -> { throw ApiException.conflict("Another team already uses /status/" + slug + "."); });
            t.setStatusSlug(slug);
        }
        if (r.title() != null && !r.title().isBlank()) t.setStatusTitle(r.title().trim());
        if (r.published() != null) {
            if (r.published() && t.getStatusSlug() == null) throw ApiException.badRequest("Choose an address before publishing.");
            t.setStatusPublic(r.published());
        }
        return page(t);
    }

    @PostMapping("/api/pulse/teams/{key}/components")
    @ResponseStatus(HttpStatus.CREATED)
    public PageView addComponent(@PathVariable String key, @RequestBody ComponentRequest r) {
        current.require();
        PulseTeam t = pulse.team0(key);
        if (r.name() == null || r.name().isBlank()) throw ApiException.badRequest("Name the component.");
        StatusComponent c = new StatusComponent();
        c.setTeam(t);
        c.setName(r.name().trim());
        c.setDescription(r.description());
        c.setPosition(components.findByTeamOrderByPositionAscIdAsc(t).size());
        components.save(c);
        return page(t);
    }

    @PutMapping("/api/pulse/components/{id}")
    public PageView editComponent(@PathVariable Long id, @RequestBody ComponentRequest r) {
        current.require();
        StatusComponent c = components.findById(id).orElseThrow(() -> ApiException.notFound("Component"));
        if (r.name() != null && !r.name().isBlank()) c.setName(r.name().trim());
        if (r.description() != null) c.setDescription(r.description());
        if (r.manualStatus() != null) {
            if (!ORDER.contains(r.manualStatus())) throw ApiException.badRequest("Unknown status " + r.manualStatus());
            c.setManualStatus(r.manualStatus());
        }
        if (r.position() != null) c.setPosition(r.position());
        return page(c.getTeam());
    }

    @DeleteMapping("/api/pulse/components/{id}")
    public PageView removeComponent(@PathVariable Long id) {
        current.require();
        StatusComponent c = components.findById(id).orElseThrow(() -> ApiException.notFound("Component"));
        PulseTeam t = c.getTeam();
        incidents.findByTeamOrderByStartedAtDesc(t).forEach(i -> i.getComponents().removeIf(x -> x.getId().equals(id)));
        incidents.flush();
        components.delete(c);
        return page(t);
    }

    // ---- public ------------------------------------------------------------------

    @GetMapping("/api/public/status/{slug}")
    @Transactional(readOnly = true)
    public PageView publicPage(@PathVariable String slug) {
        return page(published(slug));
    }

    @GetMapping(value = "/api/public/status/{slug}/feed.atom", produces = "application/atom+xml")
    @Transactional(readOnly = true)
    public ResponseEntity<String> feed(@PathVariable String slug) {
        PulseTeam t = published(slug);
        PageView p = page(t);
        StringBuilder b = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<feed xmlns=\"http://www.w3.org/2005/Atom\">\n")
                .append("  <title>").append(xml(p.title())).append("</title>\n")
                .append("  <id>urn:newxr:status:").append(xml(t.getStatusSlug())).append("</id>\n")
                .append("  <updated>").append(p.updatedAt()).append("</updated>\n");
        List<PublicIncident> all = new ArrayList<>(p.active());
        all.addAll(p.recent());
        for (PublicIncident i : all) {
            for (PublicUpdate u : i.updates()) {
                b.append("  <entry>\n    <title>").append(xml(i.title() + " — " + label(u.status()))).append("</title>\n")
                        .append("    <id>urn:newxr:status:").append(xml(i.key())).append(':').append(u.at().toEpochMilli()).append("</id>\n")
                        .append("    <updated>").append(u.at()).append("</updated>\n")
                        .append("    <content type=\"text\">").append(xml(u.body())).append("</content>\n  </entry>\n");
            }
        }
        b.append("</feed>\n");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/atom+xml; charset=utf-8")).body(b.toString());
    }

    private PulseTeam published(String slug) {
        return teams.findByStatusSlugIgnoreCase(slug).filter(PulseTeam::isStatusPublic).orElseThrow(() -> ApiException.notFound("Status page"));
    }

    // ---- maths -------------------------------------------------------------------

    PageView page(PulseTeam t) {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant since = today.minusDays(DAYS - 1L).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<Incident> all = incidents.findByTeamOrderByStartedAtDesc(t);
        List<Incident> pub = all.stream().filter(Incident::isPublicIncident)
                .filter(i -> i.getResolvedAt() == null || i.getResolvedAt().isAfter(since)).toList();
        List<Incident> open = all.stream().filter(i -> !"RESOLVED".equals(i.getStatus())).toList();
        List<ComponentView> cs = components.findByTeamOrderByPositionAscIdAsc(t).stream().map(c -> {
            String status = c.getManualStatus();
            for (Incident i : open) if (affects(i, c)) status = worst(status, i.getImpact());
            List<Day> days = new ArrayList<>();
            double down = 0;
            for (int d = DAYS - 1; d >= 0; d--) {
                LocalDate date = today.minusDays(d);
                Instant a = date.atStartOfDay().toInstant(ZoneOffset.UTC);
                Instant b = d == 0 ? now : a.plus(Duration.ofDays(1));
                String ds = "OPERATIONAL";
                double minutes = 0;
                List<String> names = new ArrayList<>();
                for (Incident i : pub) {
                    if (!affects(i, c)) continue;
                    Instant s = i.getStartedAt().isAfter(a) ? i.getStartedAt() : a;
                    Instant e = i.getResolvedAt() == null || i.getResolvedAt().isAfter(b) ? b : i.getResolvedAt();
                    if (!e.isAfter(s)) continue;
                    ds = worst(ds, i.getImpact());
                    minutes += Duration.between(s, e).toMinutes() * weight(i.getImpact());
                    names.add(i.getTitle());
                }
                down += minutes;
                days.add(new Day(date, ds, (int) Math.round(minutes), names));
            }
            double total = Duration.between(since, now).toMinutes();
            double uptime = Math.floor(Math.max(0, 1 - down / Math.max(1, total)) * 10000) / 100.0;
            return new ComponentView(c.getId(), c.getName(), c.getDescription(), c.getPosition(), c.getManualStatus(), status, uptime, days);
        }).toList();
        String overall = cs.stream().map(ComponentView::status).max(Comparator.comparingInt(ORDER::indexOf)).orElse("OPERATIONAL");
        Instant fortnight = now.minus(Duration.ofDays(14));
        List<PublicIncident> active = pub.stream().filter(i -> !"RESOLVED".equals(i.getStatus())).map(this::publicView).toList();
        List<PublicIncident> recent = pub.stream().filter(i -> "RESOLVED".equals(i.getStatus()) && i.getResolvedAt().isAfter(fortnight))
                .map(this::publicView).toList();
        Instant updated = active.stream().flatMap(i -> i.updates().stream()).map(PublicUpdate::at).max(Comparator.naturalOrder()).orElse(now);
        return new PageView(t.getStatusSlug(), t.getStatusTitle() == null ? t.getName() + " status" : t.getStatusTitle(), t.isStatusPublic(),
                overall, cs, active, recent, updated);
    }

    private PublicIncident publicView(Incident i) {
        return new PublicIncident(i.getIncidentKey(), i.getTitle(), i.getStatus(), i.getImpact(),
                i.getComponents().stream().sorted(Comparator.comparingInt(StatusComponent::getPosition)).map(StatusComponent::getName).toList(),
                i.getStartedAt(), i.getResolvedAt(),
                updates.findByIncidentOrderByCreatedAtDesc(i).stream().filter(IncidentUpdate::isPublicUpdate)
                        .map(u -> new PublicUpdate(u.getStatus(), u.getBody(), u.getCreatedAt())).toList());
    }

    private static boolean affects(Incident i, StatusComponent c) {
        return i.getComponents().stream().anyMatch(x -> x.getId().equals(c.getId()));
    }

    static String worst(String a, String b) {
        return ORDER.indexOf(b) > ORDER.indexOf(a) ? b : a;
    }

    private static double weight(String impact) {
        return "MAJOR_OUTAGE".equals(impact) ? 1 : "PARTIAL_OUTAGE".equals(impact) ? 0.3 : 0;
    }

    private static String label(String s) {
        String l = s.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(l.charAt(0)) + l.substring(1);
    }

    private static String xml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
