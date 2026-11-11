package com.tanmaysinghx.newxr.pulse;

import com.tanmaysinghx.newxr.seed.SampleDataService.SampleDataContributor;
import com.tanmaysinghx.newxr.user.User;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Platform Ops (from the design's Pulse frame): alerts, a live checkout incident, weekly on-call and the Orbit status page. */
@Component
public class PulseSampleData implements SampleDataContributor {

    private final PulseTeamRepository teams;
    private final AlertRepository alerts;
    private final AlertEventRepository events;
    private final IncidentRepository incidents;
    private final IncidentUpdateRepository updates;
    private final StatusComponentRepository components;
    private final OnCallLayerRepository layers;
    private final OnCallOverrideRepository overrides;
    private final EscalationStepRepository steps;
    private final IntegrationRepository integrations;

    public PulseSampleData(PulseTeamRepository teams, AlertRepository alerts, AlertEventRepository events, IncidentRepository incidents,
                           IncidentUpdateRepository updates, StatusComponentRepository components, OnCallLayerRepository layers,
                           OnCallOverrideRepository overrides, EscalationStepRepository steps, IntegrationRepository integrations) {
        this.teams = teams;
        this.alerts = alerts;
        this.events = events;
        this.incidents = incidents;
        this.updates = updates;
        this.components = components;
        this.layers = layers;
        this.overrides = overrides;
        this.steps = steps;
        this.integrations = integrations;
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public void load(User admin, Map<String, User> p) {
        if (teams.findByTeamKeyIgnoreCase("OPS").isPresent()) return;
        User ravi = p.get("ravi"), aisha = p.get("aisha"), omar = p.get("omar"), grace = p.get("grace");
        Instant now = Instant.now();

        PulseTeam t = new PulseTeam();
        t.setTeamKey("OPS");
        t.setName("Platform Ops");
        t.setDescription("Checkout, search and the platform behind Orbit");
        t.setMembers(new java.util.LinkedHashSet<>(List.of(admin, ravi, aisha, omar, grace)));
        t.setStatusSlug("orbit");
        t.setStatusTitle("Orbit status");
        t.setStatusPublic(true);
        t.setIncidentSeq(78);
        t = teams.save(t);

        // Integrations (tokens are random and not shown; rotate one to get a webhook URL).
        Map<String, Integration> via = new java.util.HashMap<>();
        for (String[] i : new String[][]{{"Prometheus", "PROMETHEUS", "chart", "P2"}, {"Uptime", "GENERIC", "globe", "P2"},
                {"CloudWatch", "GENERIC", "server", "P3"}, {"Crash reports", "GENERIC", "bug", "P3"}, {"Agent", "GENERIC", "monitor", "P4"}}) {
            Integration in = new Integration();
            in.setTeam(t);
            in.setName(i[0]);
            in.setFormat(i[1]);
            in.setIcon(i[2]);
            in.setDefaultPriority(i[3]);
            IntegrationService.newToken(in);
            in.setLastEventAt(now.minus(Duration.ofMinutes(4)));
            in.setEventCount(40 + i[0].length() * 7L);
            via.put(i[0], integrations.save(in));
        }

        // Status page components.
        Map<String, StatusComponent> c = new java.util.LinkedHashMap<>();
        String[][] comps = {{"Website", "orbit.travel"}, {"Mobile apps", "iOS and Android"}, {"Search & availability", "Flights, hotels and prices"},
                {"Checkout", "Payments and bookings"}, {"Notifications", "Push, email and SMS"}, {"Partner API", "api.orbit.travel"}};
        for (int i = 0; i < comps.length; i++) {
            StatusComponent sc = new StatusComponent();
            sc.setTeam(t);
            sc.setName(comps[i][0]);
            sc.setDescription(comps[i][1]);
            sc.setPosition(i);
            c.put(comps[i][0], components.save(sc));
        }

        // Past incidents that show on the 90-day bars.
        incident(t, "OPS-74", "Search results slow to load", "RESOLVED", "P3", "PARTIAL_OUTAGE", omar, true, Set.of(c.get("Search & availability")),
                now.minus(Duration.ofDays(63)), Duration.ofHours(3), List.of(
                        new String[]{"INVESTIGATING", "Some searches take longer than usual to return results."},
                        new String[]{"RESOLVED", "A slow pricing cache node was replaced. Searches are back to normal."}));
        incident(t, "OPS-75", "Payments failing for some cards", "RESOLVED", "P2", "PARTIAL_OUTAGE", ravi, true, Set.of(c.get("Checkout")),
                now.minus(Duration.ofDays(41)), Duration.ofHours(2), List.of(
                        new String[]{"INVESTIGATING", "Some card payments are being declined."},
                        new String[]{"RESOLVED", "The payment provider fixed a routing issue. All cards work again."}));
        incident(t, "OPS-76", "Checkout timeouts after release 4.11", "RESOLVED", "P2", "PARTIAL_OUTAGE", grace, true, Set.of(c.get("Checkout")),
                now.minus(Duration.ofDays(9)), Duration.ofMinutes(90), List.of(
                        new String[]{"INVESTIGATING", "Some customers see timeouts at checkout."},
                        new String[]{"MONITORING", "We rolled back release 4.11 and are watching error rates."},
                        new String[]{"RESOLVED", "Error rates are back to normal."}));
        Incident vpn = incident(t, "OPS-77", "VPN certificate expired in Lisbon office", "IDENTIFIED", "P3", "DEGRADED", admin, false, Set.of(),
                now.minus(Duration.ofMinutes(130)), null, List.of(
                        new String[]{"INVESTIGATING", "Lisbon office cannot connect to the VPN (TLS handshake failed). Desk request HELP-1093."},
                        new String[]{"IDENTIFIED", "Certificate on vpn-lis-01 expired on 6 Nov. Renewal requested from Network."}));
        Incident pay = incident(t, "OPS-78", "Elevated errors when paying for bookings", "IDENTIFIED", "P1", "PARTIAL_OUTAGE", admin, true,
                Set.of(c.get("Checkout")), now.minus(Duration.ofMinutes(31)), null, List.of(
                        new String[]{"INVESTIGATING", "Some customers see errors at checkout. Bookings already made are not affected."},
                        new String[]{"IDENTIFIED", "The issue is caused by a failing payment provider region. We're shifting traffic to a healthy region."}));

        // Alerts from the design.
        Alert a1 = alert(t, "checkout-api: 5xx rate above 5% for 5 min", "P1", "OPEN", via.get("Prometheus"), ravi, 14, now.minus(Duration.ofMinutes(4)), pay);
        alert(t, "checkout-api p95 latency > 2s", "P2", "ACKNOWLEDGED", via.get("Prometheus"), ravi, 6, now.minus(Duration.ofMinutes(11)), pay);
        alert(t, "vpn-lis-01 certificate expired", "P2", "ACKNOWLEDGED", via.get("Uptime"), admin, 1, now.minus(Duration.ofMinutes(130)), vpn);
        alert(t, "orbit-db replica lag 45s", "P3", "OPEN", via.get("CloudWatch"), null, 3, now.minus(Duration.ofMinutes(125)), null);
        alert(t, "Crash rate Android 4.12.0-rc2 > 0.5%", "P3", "OPEN", via.get("Crash reports"), grace, 2, now.minus(Duration.ofMinutes(185)), null);
        alert(t, "Disk 82% on build-agent-07", "P4", "CLOSED", via.get("Agent"), omar, 1, now.minus(Duration.ofHours(26)), null);
        a1.setDescription("Error ratio for checkout-api over 5 minutes is 7.8% (threshold 5%). Most errors are 502s from the payment provider.");
        // History for MTTA/MTTR: this week faster than last week.
        int[][] hist = {{2, 3, 31}, {3, 4, 40}, {4, 2, 55}, {5, 5, 38}, {6, 3, 47}, {9, 5, 33}, {10, 4, 36}, {12, 3, 41}};
        for (int[] h : hist) {
            Alert a = alert(t, "checkout-api error budget burn", "P3", "CLOSED", via.get("Prometheus"), ravi, 1,
                    now.minus(Duration.ofDays(h[0])), null);
            a.setAcknowledgedAt(a.getCreatedAt().plus(Duration.ofMinutes(h[1])).plusSeconds(12));
            a.setAcknowledgedBy(ravi);
            a.setClosedAt(a.getCreatedAt().plus(Duration.ofMinutes(h[2])));
        }

        // On-call: primary rotates weekly on Monday 09:00 UTC; secondary hands over on Thursday.
        Instant monday = LocalDate.now(ZoneOffset.UTC).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atTime(9, 0).toInstant(ZoneOffset.UTC);
        layer(t, "Primary", 0, monday, List.of(ravi, grace, omar, aisha));
        layer(t, "Secondary", 1, monday.plus(Duration.ofDays(3)).minus(Duration.ofDays(7)), List.of(aisha, omar));
        OnCallOverride o = new OnCallOverride();
        o.setTeam(t);
        o.setUser(grace);
        o.setStartsAt(monday.plus(Duration.ofDays(4)).plus(Duration.ofHours(9)));
        o.setEndsAt(monday.plus(Duration.ofDays(5)).plus(Duration.ofHours(9)));
        o.setReason("Ravi at a wedding Friday evening");
        overrides.save(o);

        step(t, 0, "ON_CALL_PRIMARY", null, 0);
        step(t, 1, "ON_CALL_SECONDARY", null, 5);
        step(t, 2, "USER", aisha, 15);
        step(t, 3, "TEAM", null, 30);
    }

    private Incident incident(PulseTeam t, String key, String title, String status, String sev, String impact, User commander, boolean pub,
                              Set<StatusComponent> comps, Instant started, Duration lasted, List<String[]> ups) {
        Incident i = new Incident();
        i.setTeam(t);
        i.setIncidentKey(key);
        i.setTitle(title);
        i.setStatus(status);
        i.setSeverity(sev);
        i.setImpact(impact);
        i.setCommander(commander);
        i.setPublicIncident(pub);
        i.getComponents().addAll(comps);
        i.setStartedAt(started);
        i.setCreatedAt(started);
        i.setUpdatedAt(started);
        if (lasted != null) i.setResolvedAt(started.plus(lasted));
        i = incidents.save(i);
        Instant end = lasted != null ? started.plus(lasted) : Instant.now().minus(Duration.ofMinutes(4));
        for (int k = 0; k < ups.size(); k++) {
            IncidentUpdate u = new IncidentUpdate();
            u.setIncident(i);
            u.setAuthor(commander);
            u.setStatus(ups.get(k)[0]);
            u.setBody(ups.get(k)[1]);
            u.setPublicUpdate(pub);
            Instant at = ups.size() == 1 ? started : started.plus(Duration.between(started, end).multipliedBy(k).dividedBy(ups.size() - 1));
            u.setCreatedAt(at);
            u.setUpdatedAt(at);
            updates.save(u);
        }
        return i;
    }

    private Alert alert(PulseTeam t, String title, String priority, String status, Integration via, User owner, int count, Instant at, Incident inc) {
        Alert a = new Alert();
        a.setTeam(t);
        a.setTitle(title);
        a.setPriority(priority);
        a.setStatus(status);
        a.setSource(via.getName());
        a.setIntegration(via);
        a.setDedupKey(title.toLowerCase().replaceAll("[^a-z0-9]+", "-"));
        a.setOwner(owner);
        a.setCount(count);
        a.setIncident(inc);
        a.setCreatedAt(at);
        a.setUpdatedAt(at);
        a.setLastOccurredAt(at.plus(Duration.ofMinutes(Math.min(count, 3))));
        // Sample alerts do not page anyone.
        a.setEscalationStep(-1);
        if (!"OPEN".equals(status)) {
            a.setAcknowledgedAt(at.plus(Duration.ofMinutes(3)));
            a.setAcknowledgedBy(owner);
        }
        if ("CLOSED".equals(status)) {
            a.setDedupKey(null);
            a.setClosedAt(at.plus(Duration.ofMinutes(42)));
        }
        a = alerts.save(a);
        ev(a, null, "RAISED", "Raised by " + via.getName(), at);
        ev(a, null, "ESCALATED", "Step 1 · notified " + (owner == null ? "nobody" : owner.getDisplayName()), at);
        if (count > 1) ev(a, null, "REPEATED", "Happened again (" + count + " times)", at.plus(Duration.ofMinutes(2)));
        if (a.getAcknowledgedAt() != null) ev(a, owner, "ACKNOWLEDGED", "Acknowledged", a.getAcknowledgedAt());
        if (inc != null) ev(a, inc.getCommander(), "INCIDENT", "Linked to incident " + inc.getIncidentKey(), at.plus(Duration.ofMinutes(4)));
        if (a.getClosedAt() != null) ev(a, owner, "CLOSED", "Closed", a.getClosedAt());
        return a;
    }

    private void ev(Alert a, User actor, String kind, String text, Instant at) {
        AlertEvent e = new AlertEvent();
        e.setAlert(a);
        e.setActor(actor);
        e.setKind(kind);
        e.setText(text);
        Instant when = at.isAfter(Instant.now()) ? Instant.now() : at;
        e.setCreatedAt(when);
        e.setUpdatedAt(when);
        events.save(e);
    }

    private void layer(PulseTeam t, String name, int pos, Instant start, List<User> members) {
        OnCallLayer l = new OnCallLayer();
        l.setTeam(t);
        l.setName(name);
        l.setPosition(pos);
        l.setRotationStart(start);
        l.setRotationDays(7);
        l.getMembers().addAll(members);
        layers.save(l);
    }

    private void step(PulseTeam t, int pos, String target, User user, int delay) {
        EscalationStep s = new EscalationStep();
        s.setTeam(t);
        s.setPosition(pos);
        s.setTarget(target);
        s.setUser(user);
        s.setDelayMinutes(delay);
        steps.save(s);
    }
}
